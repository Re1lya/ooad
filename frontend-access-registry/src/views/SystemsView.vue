<template>
  <div>
    <PageHead title="接入应用" subtitle="登记纳入统一身份的应用系统" seal="用">
      <template #actions>
        <button v-if="writable" class="btn btn--seal" @click="openCreate">登记应用</button>
      </template>
    </PageHead>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th style="width: 56px">编号</th>
            <th>应用编码</th>
            <th>名称</th>
            <th>基准地址</th>
            <th>状态</th>
            <th style="width: 140px; text-align: right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in list" :key="s.id">
            <td class="num">{{ s.id }}</td>
            <td><span class="chip mono">{{ s.code }}</span></td>
            <td><b>{{ s.name }}</b></td>
            <td class="mono muted small">{{ s.baseUrl ?? '—' }}</td>
            <td>
              <Toggle v-if="writable && !isBuiltInSystem(s.code)" :model-value="s.enabled" @change="(v) => toggle(s, v)" />
              <span v-else class="stamp" :class="s.enabled ? 'stamp--on' : ''">{{ s.enabled ? '启用' : '停用' }}</span>
            </td>
            <td style="text-align: right; white-space: nowrap">
              <button v-if="writable" class="btn btn--ghost btn--sm" @click="openEdit(s)">改</button>
              <button v-if="writable && !isBuiltInSystem(s.code)" class="btn btn--danger-ghost btn--sm" @click="remove(s)">销</button>
              <span v-else-if="isBuiltInSystem(s.code)" class="stamp stamp--hot" :title="BUILT_IN_HINT">内置</span>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!list.length" glyph="接" text="还没有接入应用" hint="登记应用后即可为它配菜单、按钮与 API" />
    </div>

    <Modal :open="editor.open" :title="editor.id ? '修改应用' : '登记应用'" @close="editor.open = false">
      <div class="field">
        <label>应用编码 <b>*</b></label>
        <input v-model.trim="editor.code" class="input mono" :disabled="!!editor.id" placeholder="如 course-project-management" />
      </div>
      <div class="field">
        <label>应用名称 <b>*</b></label>
        <input v-model.trim="editor.name" class="input" placeholder="如 课程项目管理系统" />
      </div>
      <div class="field">
        <label>基准地址</label>
        <input v-model.trim="editor.baseUrl" class="input mono" placeholder="https://…（可留空）" />
      </div>
      <template #foot>
        <button class="btn" @click="editor.open = false">取消</button>
        <button class="btn btn--seal" :disabled="!editor.name || (!editor.id && !editor.code)" @click="save">落账</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { systemsApi } from '@/api';
import type { SystemView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Modal from '@/components/Modal.vue';
import Toggle from '@/components/Toggle.vue';
import EmptyState from '@/components/EmptyState.vue';
import { confirmDanger } from '@/stores/confirm';
import { toast } from '@/stores/toast';
import { can } from '@/stores/session';
import { BUILT_IN_HINT, isBuiltInSystem } from '@/utils/builtIn';

const list = ref<SystemView[]>([]);
/** 登记、修改、启停、注销应用都要“维护应用”按钮。 */
const writable = computed(() => can('rbac.systems.write'));
const editor = reactive({ open: false, id: 0, code: '', name: '', baseUrl: '' });

async function reload(): Promise<void> {
  try {
    list.value = await systemsApi.list();
  } catch (e) {
    toast.fail(e);
  }
}

function openCreate(): void {
  editor.id = 0;
  editor.code = '';
  editor.name = '';
  editor.baseUrl = '';
  editor.open = true;
}

function openEdit(s: SystemView): void {
  editor.id = s.id;
  editor.code = s.code;
  editor.name = s.name;
  editor.baseUrl = s.baseUrl ?? '';
  editor.open = true;
}

async function save(): Promise<void> {
  try {
    if (editor.id) {
      await systemsApi.update(editor.id, { name: editor.name, baseUrl: editor.baseUrl || null });
      toast.ok('应用已更新');
    } else {
      await systemsApi.create({ code: editor.code, name: editor.name, baseUrl: editor.baseUrl || null });
      toast.ok('应用已登记');
    }
    editor.open = false;
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function toggle(s: SystemView, enabled: boolean): Promise<void> {
  try {
    await systemsApi.status(s.id, enabled);
    toast.ok(enabled ? `已启用 ${s.name}` : `已停用 ${s.name}`);
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function remove(s: SystemView): Promise<void> {
  if (!(await confirmDanger('注销应用', `注销「${s.name}」？还有菜单、按钮或 API 资源时不能注销，需先清理。`, '注销'))) return;
  try {
    await systemsApi.remove(s.id);
    toast.ok('应用已注销');
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

onMounted(reload);
</script>
