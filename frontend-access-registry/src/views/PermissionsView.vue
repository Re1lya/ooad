<template>
  <div>
    <PageHead title="前端权限" subtitle="菜单与按钮：按应用成树，向下绑定 API 资源" seal="权">
      <template #actions>
        <select v-model="systemId" class="select" style="width: 220px" @change="reload">
          <option v-for="s in systems" :key="s.id" :value="s.id">{{ s.name }}（{{ s.code }}）</option>
        </select>
        <button v-if="writable" class="btn btn--seal" :disabled="!systemId" @click="openCreate(null)">登记权限</button>
      </template>
    </PageHead>

    <div class="panel">
      <EmptyState v-if="!tree.length" glyph="权" text="该应用还没有权限项" hint="先登记顶层菜单，再往下挂按钮" />
      <div v-else class="perm-tree">
        <template v-for="node in tree" :key="node.permission.id">
          <PermRow
            :node="node"
            :depth="0"
            :system-code="systemCode"
            :writable="writable"
            :bindable="bindableByMe"
            @add="openCreate"
            @edit="openEdit"
            @flip="flip"
            @remove="remove"
            @bind="openBind"
          />
        </template>
      </div>
    </div>

    <!-- 登记/编辑权限 -->
    <Modal :open="editor.open" :title="editor.id ? '修改权限' : '登记权限'" @close="editor.open = false">
      <div class="grid2">
        <div class="field">
          <label>权限码 <b>*</b></label>
          <input v-model.trim="editor.code" class="input mono" :disabled="!!editor.id" placeholder="如 course:list" />
        </div>
        <div class="field">
          <label>名称 <b>*</b></label>
          <input v-model.trim="editor.name" class="input" placeholder="如 课程列表" />
        </div>
        <div class="field">
          <label>类型</label>
          <select v-model="editor.type" class="select">
            <option value="MENU">菜单</option>
            <option value="BUTTON">按钮</option>
          </select>
        </div>
        <div class="field">
          <label>父级权限</label>
          <select v-model="editor.parentId" class="select">
            <option :value="null">无（顶层）</option>
            <option v-for="p in flatPerms.filter((x) => x.id !== editor.id && x.type === 'MENU')" :key="p.id" :value="p.id">
              {{ '　'.repeat(p.depth) }}{{ p.name }}
            </option>
          </select>
        </div>
      </div>
      <div class="field">
        <label>菜单路径</label>
        <input v-model.trim="editor.menuPath" class="input mono" placeholder="如 /courses（按钮可留空）" />
      </div>
      <template #foot>
        <button class="btn" @click="editor.open = false">取消</button>
        <button class="btn btn--seal" :disabled="!editor.name || (!editor.id && !editor.code)" @click="save">落账</button>
      </template>
    </Modal>

    <!-- 绑定 API 资源 -->
    <Modal :open="binder.open" :title="`「${binder.perm?.name ?? ''}」绑定的 API`" wide @close="binder.open = false">
      <div v-if="bindableByMe" class="field">
        <label>绑定新资源</label>
        <div class="row">
          <select v-model="binder.pick" class="select grow">
            <option :value="0" disabled>选择该应用下的 API 资源…</option>
            <option v-for="r in bindable" :key="r.id" :value="r.id">
              {{ r.method }} {{ r.path }} — {{ r.name }}
            </option>
          </select>
          <button class="btn btn--seal" :disabled="!binder.pick" @click="bind">绑</button>
        </div>
      </div>
      <table class="ledger">
        <thead>
          <tr><th>方法</th><th>路径</th><th>名称</th><th style="text-align: right">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in binder.bound" :key="r.id">
            <td><span class="chip mono">{{ r.method }}</span></td>
            <td class="mono small">{{ r.path }}</td>
            <td>{{ r.name }}</td>
            <td style="text-align: right">
              <button v-if="bindableByMe" class="btn btn--danger-ghost btn--sm" @click="unbind(r)">解绑</button>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!binder.bound.length" glyph="绑" text="还没有绑定任何 API" hint="绑定后，勾中该权限的角色即可调用对应接口" />
      <template #foot>
        <button class="btn btn--seal" @click="binder.open = false">好了</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, type PropType } from 'vue';
import { permissionsApi, resourcesApi, systemsApi } from '@/api';
import type { PermissionTree, PermissionView, ResourceView, SystemView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Modal from '@/components/Modal.vue';
import Toggle from '@/components/Toggle.vue';
import EmptyState from '@/components/EmptyState.vue';
import { confirmDanger } from '@/stores/confirm';
import { toast } from '@/stores/toast';
import { can } from '@/stores/session';
import { BUILT_IN_HINT, isBuiltInPermission } from '@/utils/builtIn';

const systems = ref<SystemView[]>([]);
/** 登记、修改、启停、注销菜单与按钮要“维护菜单与按钮”；给它们绑定接口要“维护 API 资源”。 */
const writable = computed(() => can('rbac.permissions.write'));
const bindableByMe = computed(() => can('rbac.resources.write'));
const systemCode = computed(() => systems.value.find((x) => x.id === systemId.value)?.code ?? '');
const systemId = ref(0);
const tree = ref<PermissionTree[]>([]);
const allResources = ref<ResourceView[]>([]);

const editor = reactive({
  open: false,
  id: 0,
  code: '',
  name: '',
  type: 'MENU',
  menuPath: '',
  parentId: null as number | null,
});

const binder = reactive({
  open: false,
  perm: null as PermissionView | null,
  bound: [] as ResourceView[],
  pick: 0,
});

const flatPerms = computed(() => {
  const out: { id: number; name: string; depth: number; type: string }[] = [];
  const walk = (nodes: PermissionTree[], depth: number): void => {
    for (const n of nodes) {
      out.push({ id: n.permission.id, name: n.permission.name, depth, type: n.permission.type });
      walk(n.children, depth + 1);
    }
  };
  walk(tree.value, 0);
  return out;
});

const bindable = computed(() => {
  const boundIds = new Set(binder.bound.map((r) => r.id));
  return allResources.value.filter((r) => r.systemId === systemId.value && !boundIds.has(r.id));
});

const PermRow = (props: {
  node: PermissionTree;
  depth: number;
  systemCode: string;
  writable: boolean;
  bindable: boolean;
  onAdd: (p: PermissionView) => void;
  onEdit: (p: PermissionView) => void;
  onFlip: (p: PermissionView) => void;
  onRemove: (p: PermissionView) => void;
  onBind: (p: PermissionView) => void;
}) => {
  const p = props.node.permission;
  const builtIn = isBuiltInPermission(props.systemCode, p.code);
  return h('div', [
    h('div', { class: 'perm-row', style: { paddingLeft: `${props.depth * 28 + 12}px` } }, [
      h('span', { class: 'faint mono', style: { flex: 'none' } }, props.depth ? '└' : '▣'),
      h('span', { class: 'chip mono', style: { minWidth: '120px', textAlign: 'center' } }, p.code),
      h('span', { class: 'grow' }, [
        h('b', p.name),
        ' ',
        h('span', { class: 'faint small' }, p.type === 'MENU' ? `菜单 ${p.menuPath ?? ''}` : '按钮'),
      ]),
      props.writable && !builtIn
        ? h(Toggle, { modelValue: p.active, 'onUpdate:modelValue': () => props.onFlip(p) })
        : h('span', { class: ['stamp', p.active ? 'stamp--on' : ''] }, p.active ? '启用' : '停用'),
      props.writable && p.type === 'MENU'
        ? h('button', { class: 'btn btn--ghost btn--sm', onClick: () => props.onAdd(p) }, '下设')
        : null,
      h('button', { class: 'btn btn--ghost btn--sm', onClick: () => props.onBind(p) }, props.bindable ? '绑 API' : '看 API'),
      props.writable ? h('button', { class: 'btn btn--ghost btn--sm', onClick: () => props.onEdit(p) }, '改') : null,
      props.writable && !builtIn
        ? h('button', { class: 'btn btn--danger-ghost btn--sm', onClick: () => props.onRemove(p) }, '销')
        : builtIn
          ? h('span', { class: 'stamp stamp--hot', title: BUILT_IN_HINT }, '内置')
          : null,
    ]),
    props.node.children.length
      ? h(
          'div',
          props.node.children.map((c) =>
            h(PermRow as never, {
              key: c.permission.id,
              node: c,
              depth: props.depth + 1,
              systemCode: props.systemCode,
              writable: props.writable,
              bindable: props.bindable,
              onAdd: props.onAdd,
              onEdit: props.onEdit,
              onFlip: props.onFlip,
              onRemove: props.onRemove,
              onBind: props.onBind,
            }),
          ),
        )
      : null,
  ]);
};
PermRow.props = {
  node: { type: Object as PropType<PermissionTree>, required: true },
  depth: { type: Number, required: true },
  systemCode: { type: String, required: true },
  writable: { type: Boolean, required: true },
  bindable: { type: Boolean, required: true },
  onAdd: { type: Function as PropType<(p: PermissionView) => void>, required: true },
  onEdit: { type: Function as PropType<(p: PermissionView) => void>, required: true },
  onFlip: { type: Function as PropType<(p: PermissionView) => void>, required: true },
  onRemove: { type: Function as PropType<(p: PermissionView) => void>, required: true },
  onBind: { type: Function as PropType<(p: PermissionView) => void>, required: true },
};

async function reload(): Promise<void> {
  if (!systemId.value) return;
  try {
    [tree.value, allResources.value] = await Promise.all([
      permissionsApi.tree(systemId.value),
      resourcesApi.list(),
    ]);
  } catch (e) {
    toast.fail(e);
  }
}

function openCreate(parent: PermissionView | null): void {
  editor.id = 0;
  editor.code = '';
  editor.name = '';
  editor.type = parent ? 'BUTTON' : 'MENU';
  editor.menuPath = '';
  editor.parentId = parent ? parent.id : null;
  editor.open = true;
}

function openEdit(p: PermissionView): void {
  editor.id = p.id;
  editor.code = p.code;
  editor.name = p.name;
  editor.type = p.type;
  editor.menuPath = p.menuPath ?? '';
  editor.parentId = p.parentId;
  editor.open = true;
}

async function save(): Promise<void> {
  try {
    if (editor.id) {
      await permissionsApi.update(editor.id, {
        name: editor.name,
        type: editor.type,
        menuPath: editor.menuPath || null,
        parentId: editor.parentId,
      });
      toast.ok('权限已更新');
    } else {
      await permissionsApi.create({
        systemId: systemId.value,
        code: editor.code,
        name: editor.name,
        type: editor.type,
        menuPath: editor.menuPath || null,
        parentId: editor.parentId,
      });
      toast.ok('权限已登记');
    }
    editor.open = false;
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function flip(p: PermissionView): Promise<void> {
  try {
    await permissionsApi.status(p.id, !p.active);
    toast.ok(p.active ? `已停用「${p.name}」` : `已启用「${p.name}」`);
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function remove(p: PermissionView): Promise<void> {
  if (!(await confirmDanger('注销权限', `注销「${p.name}（${p.code}）」？还有下级权限、角色授权或 API 绑定时不能注销，需先解除。`, '注销'))) return;
  try {
    await permissionsApi.remove(p.id);
    toast.ok('权限已注销');
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function openBind(p: PermissionView): Promise<void> {
  binder.perm = p;
  binder.pick = 0;
  binder.open = true;
  try {
    binder.bound = await permissionsApi.resources(p.id);
  } catch (e) {
    toast.fail(e);
  }
}

async function bind(): Promise<void> {
  if (!binder.perm || !binder.pick) return;
  try {
    await permissionsApi.bindResource(binder.perm.id, binder.pick);
    toast.ok('已绑定');
    binder.pick = 0;
    binder.bound = await permissionsApi.resources(binder.perm.id);
  } catch (e) {
    toast.fail(e);
  }
}

async function unbind(r: ResourceView): Promise<void> {
  if (!binder.perm) return;
  try {
    await permissionsApi.unbindResource(binder.perm.id, r.id);
    toast.ok('已解绑');
    binder.bound = await permissionsApi.resources(binder.perm.id);
  } catch (e) {
    toast.fail(e);
  }
}

onMounted(async () => {
  try {
    systems.value = await systemsApi.list();
    systemId.value = systems.value[0]?.id ?? 0;
    await reload();
  } catch (e) {
    toast.fail(e);
  }
});
</script>

<style>
.perm-tree {
  padding: 4px 0;
}

.perm-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
}

.perm-row:hover {
  background: rgba(33, 29, 22, 0.025);
}
</style>
