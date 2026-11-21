<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { permissionsApi } from '@/api/permissions';
import type { Permission, PermissionType, Resource } from '@/api/types';
import Figures from '@/components/Figures.vue';
import FormDialog from '@/components/FormDialog.vue';
import PageHero from '@/components/PageHero.vue';
import { catalog, load, systemName } from '@/stores/catalog';
import { can, SELF_SYSTEM } from '@/stores/session';
import { act, ask, track } from '@/stores/ui';

const keyword = ref('');
const app = ref<number | null>(null);
const writable = computed(() => can('rbac.permissions.write'));

onMounted(() => void track(load(['systems']).then(() => load(['permissions'], true))));
watch(
  () => catalog.systems,
  (systems) => {
    if (app.value === null && systems?.length)
      app.value = (systems.find((s) => s.code !== SELF_SYSTEM) ?? systems[0]).id;
  },
  { immediate: true },
);

const inApp = computed(() => (catalog.permissions ?? []).filter((p) => p.systemId === app.value));

/** 菜单在前、其下按钮紧随其后，按层级缩进。 */
const rows = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (text)
    return inApp.value
      .filter((p) => `${p.name} ${p.code}`.toLowerCase().includes(text))
      .map((p) => ({ p, level: 0 }));
  const result: { p: Permission; level: number }[] = [];
  const known = new Set(inApp.value.map((p) => p.id));
  const walk = (parent: number | null, level: number) => {
    for (const p of inApp.value.filter((x) =>
      parent === null ? x.parentId === null || !known.has(x.parentId) : x.parentId === parent,
    )) {
      result.push({ p, level });
      walk(p.id, level + 1);
    }
  };
  walk(null, 0);
  return result;
});

const editor = reactive({
  open: false,
  id: null as number | null,
  code: '',
  name: '',
  type: 'MENU' as PermissionType,
  menuPath: '',
  parentId: '' as string | number,
});
function edit(p?: Permission) {
  Object.assign(editor, {
    open: true,
    id: p?.id ?? null,
    code: p?.code ?? '',
    name: p?.name ?? '',
    type: p?.type ?? 'MENU',
    menuPath: p?.menuPath ?? '',
    parentId: p?.parentId ?? '',
  });
}
const menus = computed(() => inApp.value.filter((p) => p.type === 'MENU' && p.id !== editor.id));
async function save() {
  const menuPath = editor.menuPath.trim() || null;
  const parentId = editor.parentId === '' ? null : Number(editor.parentId);
  if (editor.id === null)
    await permissionsApi.create({
      systemId: app.value!,
      code: editor.code.trim(),
      name: editor.name.trim(),
      type: editor.type,
      menuPath,
      parentId,
    });
  else
    await permissionsApi.update(editor.id, {
      name: editor.name.trim(),
      type: editor.type,
      menuPath,
      parentId,
    });
  await load(['permissions'], true);
}
async function toggle(p: Permission) {
  const verb = p.active ? '停用' : '启用';
  if (
    !(await ask(
      `${verb}${p.name}`,
      p.active
        ? '停用后，所有角色暂时失去这项权限，授权关系保留。'
        : '启用后，保留的授权重新生效。',
      { confirmLabel: `确认${verb}`, danger: p.active },
    ))
  )
    return;
  if (await act(() => permissionsApi.status(p.id, !p.active), `已${verb}`))
    await load(['permissions'], true);
}
async function remove(p: Permission) {
  if (
    !(await ask(`删除${p.name}`, '还有下级按钮、角色授权或接口绑定时不能删除。删除后无法恢复。', {
      confirmLabel: '确认删除',
    }))
  )
    return;
  if (await act(() => permissionsApi.remove(p.id), '已删除')) await load(['permissions'], true);
}

// ── 绑定接口 ─────────────────────────────────
const binding = reactive({
  open: false,
  permission: null as Permission | null,
  bound: [] as Resource[],
  pick: '',
});
async function openBinding(p: Permission) {
  await track(load(['resources']));
  const bound = await track(permissionsApi.resources(p.id));
  Object.assign(binding, { open: true, permission: p, bound: bound ?? [], pick: '' });
}
const bindable = computed(() =>
  (catalog.resources ?? []).filter(
    (r) =>
      r.systemId === binding.permission?.systemId &&
      r.active &&
      !binding.bound.some((b) => b.id === r.id),
  ),
);
async function bind() {
  const p = binding.permission;
  if (!p || !binding.pick) return;
  await permissionsApi.bind(p.id, Number(binding.pick));
  binding.bound = await permissionsApi.resources(p.id);
  binding.pick = '';
}
async function unbind(r: Resource) {
  const p = binding.permission;
  if (p && (await act(() => permissionsApi.unbind(p.id, r.id), `已解除「${r.name}」`)))
    binding.bound = binding.bound.filter((x) => x.id !== r.id);
}
</script>

<template>
  <PageHero />
  <Figures
    :items="[
      { label: '菜单', value: inApp.filter((p) => p.type === 'MENU').length },
      { label: '按钮', value: inApp.filter((p) => p.type === 'BUTTON').length },
      {
        label: '已停用',
        value: inApp.filter((p) => !p.active).length,
        red: inApp.some((p) => !p.active),
      },
    ]"
  />
  <div class="tools">
    <input
      v-model="keyword"
      class="grow"
      type="search"
      placeholder="搜索名称或编码"
      aria-label="搜索当前列表"
    />
    <div class="filters" role="group" aria-label="应用">
      <button
        v-for="s in catalog.systems ?? []"
        :key="s.id"
        :class="['textbtn', { on: app === s.id }]"
        :aria-pressed="app === s.id"
        @click="app = s.id"
      >
        {{ s.name }}
      </button>
    </div>
    <button v-if="writable && app !== null" class="btn" @click="edit()">新增菜单或按钮</button>
  </div>
  <div class="table-wrap">
    <table class="t">
      <thead>
        <tr>
          <th>名称</th>
          <th>编码</th>
          <th>类型</th>
          <th class="hide-sm">菜单地址</th>
          <th>状态</th>
          <th class="right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.p.id">
          <td>
            <span
              v-if="row.level"
              class="tree1"
              :style="{ marginLeft: `${(row.level - 1) * 24}px` }"
              >{{ row.p.name }}</span
            >
            <span v-else class="nm">{{ row.p.name }}</span>
          </td>
          <td>
            <code style="color: var(--ink)">{{ row.p.code }}</code>
          </td>
          <td>{{ row.p.type === 'MENU' ? '菜单' : '按钮' }}</td>
          <td class="hide-sm">
            <code>{{ row.p.menuPath ?? '—' }}</code>
          </td>
          <td><span v-if="row.p.active">已启用</span><span v-else class="off-t">已停用</span></td>
          <td>
            <div class="acts">
              <button class="act" @click="openBinding(row.p)">接口</button>
              <template v-if="writable">
                <button class="act" @click="edit(row.p)">编辑</button>
                <button class="act" @click="toggle(row.p)">
                  {{ row.p.active ? '停用' : '启用' }}
                </button>
                <button class="act warn" :aria-label="`删除${row.p.name}`" @click="remove(row.p)">
                  删除
                </button>
              </template>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-if="!rows.length" class="empty">
    <h3>{{ keyword ? '没有匹配的结果' : '这个应用还没有菜单' }}</h3>
    <p>{{ keyword ? '换一个关键字再试。' : '先新增菜单，再在菜单下新增按钮。' }}</p>
  </div>

  <FormDialog
    :open="editor.open"
    :title="editor.id === null ? `新增菜单或按钮 · ${systemName(app)}` : '编辑菜单或按钮'"
    submit-label="保存"
    :submit="save"
    success="已保存"
    @close="editor.open = false"
  >
    <label v-if="editor.id === null" class="field"
      >权限编码 <span class="req">*</span>
      <input v-model="editor.code" required maxlength="128" />
      <small>接入应用的前端以这个编码判断菜单或按钮是否可用；创建后不能修改。</small>
    </label>
    <label class="field"
      >显示名称 <span class="req">*</span><input v-model="editor.name" required maxlength="128"
    /></label>
    <label class="field"
      >类型 <span class="req">*</span>
      <select v-model="editor.type" required>
        <option value="MENU">菜单</option>
        <option value="BUTTON">按钮</option>
      </select>
    </label>
    <label v-if="editor.type === 'MENU'" class="field"
      >菜单地址<input v-model="editor.menuPath" maxlength="255" placeholder="/admin/terms"
    /></label>
    <label class="field"
      >上级菜单 <span v-if="editor.type === 'BUTTON'" class="req">*</span>
      <select v-model="editor.parentId" :required="editor.type === 'BUTTON'">
        <option value="">{{ editor.type === 'BUTTON' ? '请选择' : '无（顶级菜单）' }}</option>
        <option v-for="m in menus" :key="m.id" :value="m.id">{{ m.name }}</option>
      </select>
      <small v-if="editor.type === 'BUTTON'">按钮必须挂在同一应用的菜单下。</small>
    </label>
  </FormDialog>

  <FormDialog
    :open="binding.open"
    :title="`「${binding.permission?.name ?? ''}」的接口`"
    description="拥有这项权限的角色可以调用以下接口。"
    submit-label="保存绑定"
    :submit="bind"
    keep-open
    :readonly="!writable"
    @close="binding.open = false"
  >
    <p class="h-sub">已绑定</p>
    <ul v-if="binding.bound.length" class="rows">
      <li v-for="r in binding.bound" :key="r.id">
        <span :class="['meth', r.method]">{{ r.method }}</span>{{ r.name }}
        <button
          v-if="writable"
          type="button"
          class="act"
          :aria-label="`解除绑定${r.name}`"
          @click="unbind(r)"
        >
          解除
        </button>
      </li>
    </ul>
    <p v-else class="none">还没有绑定接口</p>
    <label v-if="writable" class="field"
      >待绑定 API
      <select v-model="binding.pick" required aria-label="待绑定 API">
        <option value="">选择接口</option>
        <option v-for="r in bindable" :key="r.id" :value="r.id">
          {{ r.method }} {{ r.path }} · {{ r.name }}
        </option>
      </select>
    </label>
  </FormDialog>
</template>
