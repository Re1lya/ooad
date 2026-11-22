<template>
  <div>
    <PageHead title="API 资源" subtitle="后端接口登记册：方法 + 路径 + 权限码" seal="口">
      <template #actions>
        <select v-model="filterSystemId" class="select" style="width: 200px">
          <option :value="0">全部应用</option>
          <option v-for="s in systems" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
        <input v-model.trim="keyword" class="input" style="width: 200px" placeholder="搜路径或权限码…" />
        <button class="btn btn--seal" @click="openCreate">登记资源</button>
      </template>
    </PageHead>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th style="width: 56px">编号</th>
            <th>应用</th>
            <th>方法</th>
            <th>路径</th>
            <th>权限码</th>
            <th>名称</th>
            <th>状态</th>
            <th style="width: 130px; text-align: right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in filtered" :key="r.id">
            <td class="num">{{ r.id }}</td>
            <td class="muted small">{{ systemName(r.systemId) }}</td>
            <td><span class="chip mono" :class="methodClass(r.method)">{{ r.method }}</span></td>
            <td class="mono small">{{ r.path }}</td>
            <td><span v-if="r.code" class="chip mono chip--hot">{{ r.code }}</span><span v-else class="faint">—</span></td>
            <td>{{ r.name }}</td>
            <td><Toggle :model-value="r.active" @change="() => flip(r)" /></td>
            <td style="text-align: right">
              <button class="btn btn--ghost btn--sm" @click="openEdit(r)">改名</button>
              <button class="btn btn--danger-ghost btn--sm" @click="remove(r)">销</button>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!filtered.length" glyph="口" text="没有匹配的 API 资源" hint="登记资源后即可与前端权限绑定" />
    </div>

    <Modal :open="editor.open" :title="editor.id ? '修改资源名' : '登记资源'" @close="editor.open = false">
      <div v-if="!editor.id" class="grid2">
        <div class="field">
          <label>所属应用 <b>*</b></label>
          <select v-model="editor.systemId" class="select">
            <option :value="0" disabled>选择应用</option>
            <option v-for="s in systems" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>方法 <b>*</b></label>
          <select v-model="editor.method" class="select">
            <option v-for="m in ['GET', 'POST', 'PUT', 'DELETE']" :key="m" :value="m">{{ m }}</option>
          </select>
        </div>
        <div class="field" style="grid-column: 1 / -1">
          <label>路径 <b>*</b></label>
          <input v-model.trim="editor.path" class="input mono" placeholder="/api/…" />
        </div>
        <div class="field">
          <label>后端权限码 <b>*</b></label>
          <input v-model.trim="editor.code" class="input mono" placeholder="如 TC、CV" />
        </div>
      </div>
      <div class="field">
        <label>名称 <b>*</b></label>
        <input v-model.trim="editor.name" class="input" placeholder="这个接口干什么用" />
      </div>
      <template #foot>
        <button class="btn" @click="editor.open = false">取消</button>
        <button
          class="btn btn--seal"
          :disabled="!editor.name || (!editor.id && (!editor.systemId || !editor.path || !editor.code))"
          @click="save"
        >
          落账
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { resourcesApi, systemsApi } from '@/api';
import type { ResourceView, SystemView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Modal from '@/components/Modal.vue';
import Toggle from '@/components/Toggle.vue';
import EmptyState from '@/components/EmptyState.vue';
import { toast } from '@/stores/toast';

const systems = ref<SystemView[]>([]);
const list = ref<ResourceView[]>([]);
const keyword = ref('');
const filterSystemId = ref(0);
const editor = reactive({ open: false, id: 0, systemId: 0, method: 'GET', path: '', code: '', name: '' });

const filtered = computed(() => {
  const kw = keyword.value.toLowerCase();
  return list.value.filter(
    (r) =>
      (!filterSystemId.value || r.systemId === filterSystemId.value) &&
      (!kw ||
        r.path.toLowerCase().includes(kw) ||
        (r.code ?? '').toLowerCase().includes(kw) ||
        r.name.toLowerCase().includes(kw)),
  );
});

function systemName(id: number): string {
  return systems.value.find((s) => s.id === id)?.name ?? `#${id}`;
}

function methodClass(m: string): string {
  return m === 'GET' ? 'chip--pine' : m === 'DELETE' ? 'chip--hot' : '';
}

async function reload(): Promise<void> {
  try {
    list.value = await resourcesApi.list();
  } catch (e) {
    toast.fail(e);
  }
}

function openCreate(): void {
  editor.id = 0;
  editor.systemId = filterSystemId.value || systems.value[0]?.id || 0;
  editor.method = 'GET';
  editor.path = '';
  editor.code = '';
  editor.name = '';
  editor.open = true;
}

function openEdit(r: ResourceView): void {
  editor.id = r.id;
  editor.name = r.name;
  editor.open = true;
}

async function save(): Promise<void> {
  try {
    if (editor.id) {
      await resourcesApi.update(editor.id, editor.name);
      toast.ok('资源已更新');
    } else {
      await resourcesApi.create({
        systemId: editor.systemId,
        name: editor.name,
        method: editor.method,
        path: editor.path,
        code: editor.code,
      });
      toast.ok('资源已登记');
    }
    editor.open = false;
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function flip(r: ResourceView): Promise<void> {
  try {
    await resourcesApi.status(r.id, !r.active);
    toast.ok(r.active ? '已停用' : '已启用');
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function remove(r: ResourceView): Promise<void> {
  if (!window.confirm(`确认注销资源「${r.method} ${r.path}」？`)) return;
  try {
    await resourcesApi.remove(r.id);
    toast.ok('资源已注销');
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

onMounted(async () => {
  try {
    systems.value = await systemsApi.list();
  } catch (e) {
    toast.fail(e);
  }
  void reload();
});
</script>
