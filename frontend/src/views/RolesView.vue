<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { rolesApi } from '@/api/roles';
import type { Role } from '@/api/types';
import Figures from '@/components/Figures.vue';
import FormDialog from '@/components/FormDialog.vue';
import PageHero from '@/components/PageHero.vue';
import { allowed } from '@/router';
import { catalog, load, roleName } from '@/stores/catalog';
import { can } from '@/stores/session';
import { act, ask, track } from '@/stores/ui';
import { BUILT_IN_HINT, isBuiltInRole } from '@/utils/builtIn';
import { juniorsOf } from '@/utils/graph';

const keyword = ref('');
const writable = computed(() => can('rbac.roles.write'));
onMounted(() => void track(load(['roles', 'edges'], true)));

const rows = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return (catalog.roles ?? []).filter(
    (r) => !text || `${r.name} ${r.code} ${r.description ?? ''}`.toLowerCase().includes(text),
  );
});
const inherits = (id: number) => juniorsOf(catalog.edges ?? [], id);

const editor = reactive({
  open: false,
  id: null as number | null,
  code: '',
  name: '',
  description: '',
});
function edit(r?: Role) {
  Object.assign(editor, {
    open: true,
    id: r?.id ?? null,
    code: r?.code ?? '',
    name: r?.name ?? '',
    description: r?.description ?? '',
  });
}
async function save() {
  const description = editor.description.trim() || null;
  if (editor.id === null)
    await rolesApi.create({ code: editor.code.trim(), name: editor.name.trim(), description });
  else await rolesApi.update(editor.id, { name: editor.name.trim(), description });
  await load(['roles'], true);
}
async function toggle(r: Role) {
  const verb = r.enabled ? '停用' : '启用';
  const ok = await ask(
    `${verb}${r.name}`,
    r.enabled
      ? '停用后，该角色不再提供权限，经过它的继承也被切断；继承关系和分配本身保留。'
      : '启用后，保留的继承关系和分配重新参与权限计算。',
    { confirmLabel: `确认${verb}`, danger: r.enabled },
  );
  if (ok && (await act(() => rolesApi.status(r.id, !r.enabled), `已${verb}`)))
    await load(['roles'], true);
}
async function remove(r: Role) {
  if (
    !(await ask(
      `删除${r.name}`,
      '角色仍有继承、授权或用户分配时，系统会拒绝删除。删除后无法恢复。',
      { confirmLabel: '确认删除' },
    ))
  )
    return;
  if (await act(() => rolesApi.remove(r.id), '已删除')) await load(['roles', 'edges'], true);
}
</script>

<template>
  <PageHero />
  <Figures
    :items="[
      { label: '角色', value: catalog.roles?.length ?? 0 },
      { label: '继承关系', value: catalog.edges?.length ?? 0 },
      {
        label: '已停用',
        value: catalog.roles?.filter((r) => !r.enabled).length ?? 0,
        red: catalog.roles?.some((r) => !r.enabled),
      },
    ]"
  />
  <div class="tools">
    <input
      v-model="keyword"
      class="grow"
      type="search"
      placeholder="搜索角色名称或编码"
      aria-label="搜索当前列表"
    />
    <button v-if="writable" class="btn" @click="edit()">新增角色</button>
  </div>
  <div class="table-wrap">
    <table class="t">
      <thead>
        <tr>
          <th>角色</th>
          <th>编码</th>
          <th class="hide-sm">继承自</th>
          <th>状态</th>
          <th class="right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.id">
          <td>
            <span class="nm">{{ r.name }}</span>
            <span v-if="r.description" class="sub">{{ r.description }}</span>
          </td>
          <td>
            <code style="color: var(--ink)">{{ r.code }}</code>
          </td>
          <td class="hide-sm inl">
            <span v-for="j in inherits(r.id)" :key="j">{{ roleName(j) }}</span>
            <span v-if="!inherits(r.id).length" class="mute">—</span>
          </td>
          <td><span v-if="r.enabled">已启用</span><span v-else class="off-t">已停用</span></td>
          <td>
            <div class="acts">
              <RouterLink v-if="allowed('hierarchy')" class="act" :to="`/hierarchy?role=${r.id}`"
                >继承关系</RouterLink
              >
              <template v-if="writable">
                <button class="act" @click="edit(r)">编辑</button>
                <button v-if="!isBuiltInRole(r.code) || !r.enabled" class="act" @click="toggle(r)">
                  {{ r.enabled ? '停用' : '启用' }}
                </button>
                <button
                  v-if="!isBuiltInRole(r.code)"
                  class="act warn"
                  :aria-label="`删除${r.name}`"
                  @click="remove(r)"
                >
                  删除
                </button>
                <span v-else class="mute" :title="BUILT_IN_HINT">内置</span>
              </template>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-if="!rows.length" class="empty">
    <h3>{{ keyword ? '没有匹配的结果' : '还没有角色' }}</h3>
    <p>{{ keyword ? '换一个名称或编码再试。' : '新增角色后，为它授权菜单与按钮。' }}</p>
  </div>

  <FormDialog
    :open="editor.open"
    :title="editor.id === null ? '新增角色' : '编辑角色'"
    submit-label="保存角色"
    :submit="save"
    success="已保存角色"
    @close="editor.open = false"
  >
    <label v-if="editor.id === null" class="field"
      >角色编码 <span class="req">*</span>
      <input v-model="editor.code" required pattern="[a-z][a-z0-9_\-]{0,63}" maxlength="64" />
      <small>小写字母开头，只含小写字母、数字、下划线和连字符；创建后不能修改。</small>
    </label>
    <label class="field"
      >角色名称 <span class="req">*</span><input v-model="editor.name" required maxlength="128"
    /></label>
    <label class="field">角色说明<input v-model="editor.description" maxlength="255" /></label>
  </FormDialog>
</template>
