<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { departmentsApi } from '@/api/departments';
import type { Department } from '@/api/types';
import Figures from '@/components/Figures.vue';
import FormDialog from '@/components/FormDialog.vue';
import PageHero from '@/components/PageHero.vue';
import { catalog, departmentName, load } from '@/stores/catalog';
import { can } from '@/stores/session';
import { act, ask, track } from '@/stores/ui';

const keyword = ref('');
const open = ref(new Set<number>());
const writable = computed(() => can('rbac.departments.write'));

onMounted(() => void track(load(['departments'], true)));

const all = computed(() => catalog.departments ?? []);
const children = (id: number | null) => all.value.filter((d) => d.parentId === id);

/** 部门树按层展开；搜索时平铺显示所有匹配的部门。 */
const rows = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (text)
    return all.value
      .filter(
        (d) => d.name.toLowerCase().includes(text) || (d.code ?? '').toLowerCase().includes(text),
      )
      .map((d) => ({ d, level: 0, kids: false }));
  const result: { d: Department; level: number; kids: boolean }[] = [];
  const walk = (parent: number | null, level: number) => {
    for (const d of children(parent)) {
      const kids = children(d.id).length > 0;
      result.push({ d, level, kids });
      if (kids && open.value.has(d.id)) walk(d.id, level + 1);
    }
  };
  walk(null, 0);
  return result;
});

function toggle(id: number) {
  const next = new Set(open.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  open.value = next;
}

const parentName = (id: number | null) =>
  id == null ? '—' : (all.value.find((d) => d.id === id)?.name ?? '—');

const editor = reactive({
  open: false,
  id: null as number | null,
  name: '',
  code: '',
  parentId: '' as string | number,
});
function edit(d?: Department) {
  Object.assign(editor, {
    open: true,
    id: d?.id ?? null,
    name: d?.name ?? '',
    code: d?.code ?? '',
    parentId: d?.parentId ?? '',
  });
}
async function save() {
  const body = {
    name: editor.name.trim(),
    parentId: editor.parentId === '' ? null : Number(editor.parentId),
    ...(editor.code.trim() ? { code: editor.code.trim() } : {}),
  };
  if (editor.id === null) await departmentsApi.create(body);
  else await departmentsApi.update(editor.id, body);
  await load(['departments'], true);
}
async function remove(d: Department) {
  if (
    !(await ask(`删除${d.name}`, '还有下级部门或成员的部门不能删除。删除后无法恢复。', {
      confirmLabel: '确认删除',
    }))
  )
    return;
  if (await act(() => departmentsApi.remove(d.id), '已删除')) await load(['departments'], true);
}
</script>

<template>
  <PageHero />
  <Figures
    :items="[
      { label: '部门', value: all.length },
      { label: '顶级部门', value: children(null).length },
    ]"
  />
  <div class="tools">
    <input
      v-model="keyword"
      class="grow"
      type="search"
      placeholder="搜索部门名称或编码"
      aria-label="搜索当前列表"
    />
    <button v-if="writable" class="btn" @click="edit()">新增部门</button>
  </div>
  <div class="table-wrap">
    <table class="t">
      <thead>
        <tr>
          <th>部门</th>
          <th class="hide-sm">编码</th>
          <th>上级部门</th>
          <th v-if="writable" class="right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.d.id">
          <td>
            <span
              v-if="row.level"
              class="tree1"
              :style="{ marginLeft: `${(row.level - 1) * 24}px` }"
              >{{ row.d.name }}</span
            >
            <span v-else class="nm">
              <button
                v-if="row.kids"
                class="car"
                :aria-expanded="open.has(row.d.id)"
                :aria-label="`${open.has(row.d.id) ? '收起' : '展开'}${row.d.name}`"
                @click="toggle(row.d.id)"
              >
                {{ open.has(row.d.id) ? '−' : '+' }}
              </button>
              <span v-else class="car"></span>{{ row.d.name }}
            </span>
          </td>
          <td class="hide-sm"><code>{{ row.d.code ?? '—' }}</code></td>
          <td>{{ parentName(row.d.parentId) }}</td>
          <td v-if="writable">
            <div class="acts">
              <button class="act" @click="edit(row.d)">编辑</button>
              <button class="act warn" :aria-label="`删除${row.d.name}`" @click="remove(row.d)">
                删除
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-if="!rows.length" class="empty">
    <h3>{{ keyword ? '没有匹配的结果' : '还没有部门' }}</h3>
    <p>{{ keyword ? '换一个名称再试。' : '新增部门，或在用户管理中同步员工信息。' }}</p>
  </div>

  <FormDialog
    :open="editor.open"
    :title="editor.id === null ? '新增部门' : '编辑部门'"
    submit-label="保存部门"
    :submit="save"
    success="已保存部门"
    @close="editor.open = false"
  >
    <label class="field"
      >部门名称 <span class="req">*</span>
      <input v-model="editor.name" required maxlength="128" />
    </label>
    <label class="field"
      >部门编码
      <input
        v-model="editor.code"
        maxlength="64"
        pattern="[A-Za-z0-9][A-Za-z0-9_.\-]*"
        placeholder="不填则自动生成"
      />
    </label>
    <label class="field"
      >上级部门
      <select v-model="editor.parentId">
        <option value="">无（顶级部门）</option>
        <option v-for="d in all.filter((x) => x.id !== editor.id)" :key="d.id" :value="d.id">
          {{ departmentName(d.id) }}
        </option>
      </select>
    </label>
  </FormDialog>
</template>
