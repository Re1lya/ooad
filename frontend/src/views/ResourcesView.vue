<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { resourcesApi } from '@/api/resources';
import type { Resource } from '@/api/types';
import Figures from '@/components/Figures.vue';
import FormDialog from '@/components/FormDialog.vue';
import PageHero from '@/components/PageHero.vue';
import { catalog, load, systemName } from '@/stores/catalog';
import { can, SELF_SYSTEM } from '@/stores/session';
import { act, ask, track } from '@/stores/ui';
import { BUILT_IN_HINT, isBuiltInResource } from '@/utils/builtIn';

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const;
const keyword = ref('');
const app = ref<number | null>(null);
const writable = computed(() => can('rbac.resources.write'));
/** 内置管理接口不提供停用、删除（后端也会拒绝）。 */
const builtIn = (r: Resource) =>
  isBuiltInResource(catalog.systems?.find((s) => s.id === r.systemId)?.code);

onMounted(() => void track(load(['systems']).then(() => load(['resources'], true))));
watch(
  () => catalog.systems,
  (systems) => {
    if (app.value === null && systems?.length)
      app.value = (systems.find((s) => s.code !== SELF_SYSTEM) ?? systems[0]).id;
  },
  { immediate: true },
);

const rows = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return (catalog.resources ?? [])
    .filter((r) => r.systemId === app.value)
    .filter((r) => !text || `${r.name} ${r.path} ${r.code ?? ''}`.toLowerCase().includes(text));
});

const editor = reactive({
  open: false,
  id: null as number | null,
  name: '',
  method: 'GET' as Resource['method'],
  path: '',
  code: '',
});
function edit(r?: Resource) {
  Object.assign(editor, {
    open: true,
    id: r?.id ?? null,
    name: r?.name ?? '',
    method: r?.method ?? 'GET',
    path: r?.path ?? '',
    code: r?.code ?? '',
  });
}
async function save() {
  if (editor.id === null)
    await resourcesApi.create({
      systemId: app.value!,
      name: editor.name.trim(),
      method: editor.method,
      path: editor.path.trim(),
      code: editor.code.trim() || null,
    });
  else await resourcesApi.update(editor.id, { name: editor.name.trim() });
  await load(['resources'], true);
}
async function toggle(r: Resource) {
  const verb = r.active ? '停用' : '启用';
  if (
    !(await ask(
      `${verb}${r.name}`,
      r.active
        ? '停用后，任何角色都不能再通过授权调用这个接口。'
        : '启用后，绑定关系重新参与鉴权。',
      { confirmLabel: `确认${verb}`, danger: r.active },
    ))
  )
    return;
  if (await act(() => resourcesApi.status(r.id, !r.active), `已${verb}`))
    await load(['resources'], true);
}
async function remove(r: Resource) {
  if (
    !(await ask(`删除${r.name}`, '接口仍绑定在菜单或按钮上时不能删除。删除后无法恢复。', {
      confirmLabel: '确认删除',
    }))
  )
    return;
  if (await act(() => resourcesApi.remove(r.id), '已删除')) await load(['resources'], true);
}
</script>

<template>
  <PageHero />
  <Figures
    :items="[
      { label: '接口', value: rows.length },
      { label: '已登记权限码', value: rows.filter((r) => r.code).length },
    ]"
  />
  <div class="tools">
    <input
      v-model="keyword"
      class="grow"
      type="search"
      placeholder="搜索接口名称、路径或权限码"
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
    <button v-if="writable && app !== null" class="btn" @click="edit()">登记接口</button>
  </div>
  <div class="table-wrap">
    <table class="t">
      <thead>
        <tr>
          <th>接口</th>
          <th>请求</th>
          <th class="hide-sm">权限码</th>
          <th>状态</th>
          <th v-if="writable" class="right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.id">
          <td class="nm">{{ r.name }}</td>
          <td>
            <span :class="['meth', r.method]">{{ r.method }}</span><code
              style="color: var(--ink)"
              >{{ r.path }}</code
            >
          </td>
          <td class="hide-sm">
            <code>{{ r.code ?? '—' }}</code>
          </td>
          <td><span v-if="r.active">已启用</span><span v-else class="off-t">已停用</span></td>
          <td v-if="writable">
            <div class="acts">
              <button class="act" @click="edit(r)">编辑</button>
              <button v-if="!builtIn(r) || !r.active" class="act" @click="toggle(r)">
                {{ r.active ? '停用' : '启用' }}
              </button>
              <button
                v-if="!builtIn(r)"
                class="act warn"
                :aria-label="`删除${r.name}`"
                @click="remove(r)"
              >
                删除
              </button>
              <span v-else class="mute" :title="BUILT_IN_HINT">内置</span>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-if="!rows.length" class="empty">
    <h3>{{ keyword ? '没有匹配的结果' : '这个应用还没有登记接口' }}</h3>
    <p>
      {{ keyword ? '换一个关键字再试。' : '登记接口后，在「菜单与按钮」中把它绑定到对应的按钮。' }}
    </p>
  </div>

  <FormDialog
    :open="editor.open"
    :title="editor.id === null ? `登记接口 · ${systemName(app)}` : '编辑接口'"
    :description="
      editor.id === null ? '请求方法、路径和权限码创建后不能修改。' : '只能修改接口名称。'
    "
    submit-label="保存接口"
    :submit="save"
    success="已保存接口"
    @close="editor.open = false"
  >
    <label class="field"
      >接口名称 <span class="req">*</span><input v-model="editor.name" required maxlength="128"
    /></label>
    <template v-if="editor.id === null">
      <label class="field"
        >请求方法 <span class="req">*</span>
        <select v-model="editor.method" required>
          <option v-for="m in METHODS" :key="m" :value="m">{{ m }}</option>
        </select>
      </label>
      <label class="field"
        >接口路径 <span class="req">*</span
        ><input v-model="editor.path" required maxlength="255" placeholder="/terms/{termId}"
      /></label>
      <label class="field"
        >后端权限码
        <input v-model="editor.code" maxlength="64" pattern="[A-Za-z][A-Za-z0-9_:.\-]{0,63}" />
        <small>接入应用用它做后端鉴权；同一个权限码可以对应多个接口。</small>
      </label>
    </template>
  </FormDialog>
</template>
