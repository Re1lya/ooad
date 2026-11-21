<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue';
import { systemsApi } from '@/api/systems';
import type { System } from '@/api/types';
import Figures from '@/components/Figures.vue';
import FormDialog from '@/components/FormDialog.vue';
import PageHero from '@/components/PageHero.vue';
import { catalog, load } from '@/stores/catalog';
import { can } from '@/stores/session';
import { act, ask, track } from '@/stores/ui';
import { BUILT_IN_HINT, isBuiltInSystem } from '@/utils/builtIn';

const writable = computed(() => can('rbac.systems.write'));
onMounted(() => {
  void track(load(['systems'], true));
  void load(['permissions', 'resources']).catch(() => undefined);
});

const rows = computed(() =>
  (catalog.systems ?? []).map((s) => ({
    ...s,
    menus: catalog.permissions?.filter((p) => p.systemId === s.id).length,
    apis: catalog.resources?.filter((r) => r.systemId === s.id).length,
  })),
);

const editor = reactive({
  open: false,
  id: null as number | null,
  code: '',
  name: '',
  baseUrl: '',
});
function edit(s?: System) {
  Object.assign(editor, {
    open: true,
    id: s?.id ?? null,
    code: s?.code ?? '',
    name: s?.name ?? '',
    baseUrl: s?.baseUrl ?? '',
  });
}
async function save() {
  const baseUrl = editor.baseUrl.trim() || null;
  if (editor.id === null)
    await systemsApi.create({ code: editor.code.trim(), name: editor.name.trim(), baseUrl });
  else await systemsApi.update(editor.id, { name: editor.name.trim(), baseUrl });
  await load(['systems'], true);
}
async function toggle(s: System) {
  const verb = s.enabled ? '停用' : '启用';
  if (
    !(await ask(
      `${verb}${s.name}`,
      s.enabled
        ? '停用后，该应用下的菜单、按钮和接口授权暂时全部失效，配置保留。'
        : '启用后，该应用下保留的授权重新生效。',
      { confirmLabel: `确认${verb}`, danger: s.enabled },
    ))
  )
    return;
  if (await act(() => systemsApi.status(s.id, !s.enabled), `已${verb}`))
    await load(['systems'], true);
}
async function remove(s: System) {
  if (
    !(await ask(`删除${s.name}`, '应用下还有菜单、按钮或接口时不能删除。删除后无法恢复。', {
      confirmLabel: '确认删除',
    }))
  )
    return;
  if (await act(() => systemsApi.remove(s.id), '已删除')) await load(['systems'], true);
}
</script>

<template>
  <PageHero />
  <Figures
    :items="[
      { label: '接入应用', value: rows.length },
      {
        label: '已停用',
        value: rows.filter((s) => !s.enabled).length,
        red: rows.some((s) => !s.enabled),
      },
    ]"
  />
  <div v-if="writable" class="tools">
    <span class="grow"></span>
    <button class="btn" @click="edit()">接入新应用</button>
  </div>
  <div class="table-wrap">
    <table class="t">
      <thead>
        <tr>
          <th>应用</th>
          <th>编码</th>
          <th class="hide-sm">访问地址</th>
          <th class="hide-sm">菜单与按钮</th>
          <th class="hide-sm">接口</th>
          <th>状态</th>
          <th v-if="writable" class="right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in rows" :key="s.id">
          <td class="nm">{{ s.name }}</td>
          <td>
            <code style="color: var(--ink)">{{ s.code }}</code>
          </td>
          <td class="hide-sm">
            <code>{{ s.baseUrl ?? '—' }}</code>
          </td>
          <td class="hide-sm">{{ s.menus ?? '—' }}</td>
          <td class="hide-sm">{{ s.apis ?? '—' }}</td>
          <td><span v-if="s.enabled">已启用</span><span v-else class="off-t">已停用</span></td>
          <td v-if="writable">
            <div class="acts">
              <button class="act" @click="edit(s)">编辑</button>
              <button v-if="!isBuiltInSystem(s.code) || !s.enabled" class="act" @click="toggle(s)">
                {{ s.enabled ? '停用' : '启用' }}
              </button>
              <button
                v-if="!isBuiltInSystem(s.code)"
                class="act warn"
                :aria-label="`删除${s.name}`"
                @click="remove(s)"
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
    <h3>还没有接入应用</h3>
    <p>接入应用后，再登记它的菜单、按钮和接口。</p>
  </div>

  <FormDialog
    :open="editor.open"
    :title="editor.id === null ? '接入新应用' : '编辑应用'"
    :description="
      editor.id === null ? '应用编码创建后不能修改；接入应用以此编码查询统一身份。' : undefined
    "
    submit-label="保存应用"
    :submit="save"
    success="已保存应用"
    @close="editor.open = false"
  >
    <label v-if="editor.id === null" class="field"
      >应用编码 <span class="req">*</span>
      <input v-model="editor.code" required pattern="[a-z][a-z0-9_\-]{0,63}" maxlength="64" />
      <small>小写字母开头，只含小写字母、数字、下划线和连字符。</small>
    </label>
    <label class="field"
      >应用名称 <span class="req">*</span><input v-model="editor.name" required maxlength="128"
    /></label>
    <label class="field">访问地址<input v-model="editor.baseUrl" maxlength="255" /></label>
  </FormDialog>
</template>
