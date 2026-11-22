<template>
  <div>
    <PageHead title="角色" subtitle="权限的容器：建角色、配直接权限、查有效权限" seal="角">
      <template #actions>
        <button v-if="writable" class="btn btn--seal" @click="openCreate">建角色</button>
      </template>
    </PageHead>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th style="width: 56px">编号</th>
            <th>角色码</th>
            <th>名称</th>
            <th>说明</th>
            <th>状态</th>
            <th style="width: 200px; text-align: right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id">
            <td class="num">{{ r.id }}</td>
            <td><span class="chip mono">{{ r.code }}</span></td>
            <td><b>{{ r.name }}</b></td>
            <td class="muted small">{{ r.description ?? '—' }}</td>
            <td>
              <Toggle v-if="writable && !isBuiltInRole(r.code)" :model-value="r.enabled" @change="(v) => toggle(r, v)" />
              <span v-else class="stamp" :class="r.enabled ? 'stamp--on' : ''">{{ r.enabled ? '启用' : '停用' }}</span>
            </td>
            <td style="text-align: right; white-space: nowrap">
              <button v-if="grantable" class="btn btn--ghost btn--sm" @click="openGrants(r)">配权限</button>
              <button v-if="writable" class="btn btn--ghost btn--sm" @click="openEdit(r)">改</button>
              <button v-if="writable && !isBuiltInRole(r.code)" class="btn btn--danger-ghost btn--sm" @click="remove(r)">销</button>
              <span v-else-if="isBuiltInRole(r.code)" class="stamp stamp--hot" :title="BUILT_IN_HINT">内置</span>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!list.length" glyph="空" text="还没有角色" hint="角色是授权链的起点，先建一个" />
    </div>

    <!-- 建/改角色 -->
    <Modal :open="editor.open" :title="editor.id ? '修改角色' : '建角色'" @close="editor.open = false">
      <div class="field">
        <label>角色码 <b>*</b></label>
        <input v-model.trim="editor.code" class="input mono" :disabled="!!editor.id" placeholder="如 teacher" />
      </div>
      <div class="field">
        <label>角色名 <b>*</b></label>
        <input v-model.trim="editor.name" class="input" placeholder="如 教师" />
      </div>
      <div class="field">
        <label>说明</label>
        <textarea v-model.trim="editor.description" class="textarea" rows="2" placeholder="这个角色管什么" />
      </div>
      <template #foot>
        <button class="btn" @click="editor.open = false">取消</button>
        <button class="btn btn--seal" :disabled="!editor.name || (!editor.id && !editor.code)" @click="save">落账</button>
      </template>
    </Modal>

    <!-- 配权限：直接授予，继承的在权限来源页看 -->
    <Modal :open="grants.open" :title="`为「${grants.role?.name ?? ''}」配权限`" wide @close="grants.open = false">
      <p class="notice notice--tip" style="margin: 0 0 14px">
        这里配的是<b>直接权限</b>；通过继承得到的权限请去「权限来源」页核对完整授权链。
      </p>
      <div class="field">
        <label>应用</label>
        <select v-model="grants.systemId" class="select" @change="loadTree">
          <option v-for="s in systems" :key="s.id" :value="s.id">{{ s.name }}（{{ s.code }}）</option>
        </select>
      </div>
      <div class="grant-tree">
        <template v-for="node in grants.tree" :key="node.permission.id">
          <GrantRow :node="node" :busy="grants.busy" @flip="flip" />
        </template>
        <EmptyState v-if="!grants.tree.length" glyph="无" text="该应用还没有权限项" hint="先去「前端权限」页登记菜单与按钮" />
      </div>
      <template #foot>
        <button class="btn btn--seal" @click="grants.open = false">配好了</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, type PropType } from 'vue';
import { permissionsApi, rolesApi, systemsApi } from '@/api';
import type { PermissionTree, RoleView, SystemView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Modal from '@/components/Modal.vue';
import Toggle from '@/components/Toggle.vue';
import EmptyState from '@/components/EmptyState.vue';
import { confirmDanger } from '@/stores/confirm';
import { toast } from '@/stores/toast';
import { can } from '@/stores/session';
import { BUILT_IN_HINT, isBuiltInRole } from '@/utils/builtIn';

const list = ref<RoleView[]>([]);
/** 建角色、改、启停、注销要“维护角色”；给角色配直接权限要“授予与撤销权限”。 */
const writable = computed(() => can('rbac.roles.write'));
const grantable = computed(() => can('rbac.grants.write'));
const systems = ref<SystemView[]>([]);
const editor = reactive({ open: false, id: 0, code: '', name: '', description: '' });
const grants = reactive({
  open: false,
  role: null as RoleView | null,
  systemId: 0,
  tree: [] as PermissionTree[],
  busy: false,
});

/** 权限树的一行：行内递归渲染。 */
const GrantRow = (props: {
  node: PermissionTree;
  busy: boolean;
  onFlip: (node: PermissionTree) => void;
}) =>
  h('div', { class: 'grant-node' }, [
    h('div', { class: 'grant-row' }, [
      h('span', { class: 'chip mono', style: { minWidth: '110px', textAlign: 'center' } }, props.node.permission.code),
      h('span', { class: 'grow' }, [
        h('b', props.node.permission.name),
        ' ',
        h('span', { class: 'faint small' }, props.node.permission.type === 'MENU' ? '菜单' : '按钮'),
      ]),
      h('span', {
        class: ['stamp', props.node.granted ? 'stamp--hot' : ''],
        style: { cursor: 'pointer' },
        onClick: () => !props.busy && props.onFlip(props.node),
      }, props.node.granted ? '已授予' : '未授予'),
    ]),
    props.node.children.length
      ? h(
          'div',
          { class: 'grant-children' },
          props.node.children.map((c) => h(GrantRow as never, { key: c.permission.id, node: c, busy: props.busy, onFlip: props.onFlip })),
        )
      : null,
  ]);
GrantRow.props = {
  node: { type: Object as PropType<PermissionTree>, required: true },
  busy: { type: Boolean, required: true },
  onFlip: { type: Function as PropType<(n: PermissionTree) => void>, required: true },
};

async function reload(): Promise<void> {
  try {
    list.value = await rolesApi.list();
  } catch (e) {
    toast.fail(e);
  }
}

function openCreate(): void {
  editor.id = 0;
  editor.code = '';
  editor.name = '';
  editor.description = '';
  editor.open = true;
}

function openEdit(r: RoleView): void {
  editor.id = r.id;
  editor.code = r.code;
  editor.name = r.name;
  editor.description = r.description ?? '';
  editor.open = true;
}

async function save(): Promise<void> {
  try {
    if (editor.id) {
      await rolesApi.update(editor.id, { name: editor.name, description: editor.description || null });
      toast.ok('角色已更新');
    } else {
      await rolesApi.create({ code: editor.code, name: editor.name, description: editor.description || null });
      toast.ok('角色已落账');
    }
    editor.open = false;
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function toggle(r: RoleView, enabled: boolean): Promise<void> {
  try {
    await rolesApi.status(r.id, enabled);
    toast.ok(enabled ? `已启用 ${r.name}` : `已停用 ${r.name}`);
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function remove(r: RoleView): Promise<void> {
  if (!(await confirmDanger('注销角色', `注销「${r.name}」？还有用户、权限或继承关系时不能注销，需先解除。`, '注销'))) return;
  try {
    await rolesApi.remove(r.id);
    toast.ok('角色已注销');
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function openGrants(r: RoleView): Promise<void> {
  grants.role = r;
  grants.open = true;
  if (!systems.value.length) {
    try {
      systems.value = await systemsApi.list();
    } catch (e) {
      toast.fail(e);
      return;
    }
  }
  grants.systemId = grants.systemId || systems.value[0]?.id || 0;
  await loadTree();
}

async function loadTree(): Promise<void> {
  if (!grants.role || !grants.systemId) return;
  try {
    // tree 接口对管理员返回全部节点及 granted 标记时按当前角色判定做不到，
    // 所以拉全树 + 角色直接权限，本地标记 granted。
    const [tree, direct] = await Promise.all([
      permissionsApi.tree(grants.systemId),
      rolesApi.permissions(grants.role.id),
    ]);
    const granted = new Set(direct.map((p) => p.id));
    const mark = (nodes: PermissionTree[]): PermissionTree[] =>
      nodes.map((n) => ({
        ...n,
        granted: granted.has(n.permission.id),
        children: mark(n.children),
      }));
    grants.tree = mark(tree);
  } catch (e) {
    toast.fail(e);
  }
}

async function flip(node: PermissionTree): Promise<void> {
  if (!grants.role) return;
  grants.busy = true;
  try {
    if (node.granted) {
      await rolesApi.revoke(grants.role.id, node.permission.id);
      toast.ok(`已收回「${node.permission.name}」`);
    } else {
      await rolesApi.grant(grants.role.id, node.permission.id);
      toast.ok(`已授予「${node.permission.name}」`);
    }
    await loadTree();
  } catch (e) {
    toast.fail(e);
  } finally {
    grants.busy = false;
  }
}

onMounted(reload);
</script>

<style>
.grant-tree {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  max-height: 46vh;
  overflow-y: auto;
}

.grant-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
}

.grant-row:hover {
  background: rgba(33, 29, 22, 0.025);
}

.grant-children {
  margin-left: 26px;
  border-left: 1px dashed var(--line-strong);
}
</style>
