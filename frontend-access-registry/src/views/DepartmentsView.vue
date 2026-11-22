<template>
  <div>
    <PageHead title="部门" subtitle="组织层级：公司之下逐级建部门" seal="部">
      <template #actions>
        <button class="btn btn--seal" @click="openCreate(null)">建一级部门</button>
      </template>
    </PageHead>

    <div class="panel">
      <EmptyState v-if="!tree.length" glyph="立" text="还没有部门" hint="先建一级部门，再逐级往下立" />
      <ul v-else class="dept-tree">
        <DeptNode
          v-for="node in tree"
          :key="node.data.id"
          :node="node"
          :depth="0"
          @add="openCreate"
          @edit="openEdit"
          @remove="remove"
        />
      </ul>
    </div>

    <Modal :open="editor.open" :title="editor.id ? '修改部门' : '新建部门'" @close="editor.open = false">
      <div class="field">
        <label>部门名 <b>*</b></label>
        <input v-model.trim="editor.name" class="input" placeholder="部门名称" />
      </div>
      <div class="field">
        <label>上级部门</label>
        <select v-model="editor.parentId" class="select">
          <option :value="null">无（一级部门）</option>
          <option v-for="d in flat" :key="d.data.id" :value="d.data.id" :disabled="d.data.id === editor.id">
            {{ '　'.repeat(d.depth) }}{{ d.data.name }}
          </option>
        </select>
      </div>
      <template #foot>
        <button class="btn" @click="editor.open = false">取消</button>
        <button class="btn btn--seal" :disabled="!editor.name" @click="save">落账</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, type PropType } from 'vue';
import { departmentsApi } from '@/api';
import type { DepartmentView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Modal from '@/components/Modal.vue';
import EmptyState from '@/components/EmptyState.vue';
import { toast } from '@/stores/toast';

interface DeptNodeData {
  data: DepartmentView;
  children: DeptNodeData[];
}

/* 递归节点用渲染函数写，避免组件自引用声明的麻烦。 */
const DeptNode = (props: {
  node: DeptNodeData;
  depth: number;
  onAdd: (d: DepartmentView) => void;
  onEdit: (d: DepartmentView) => void;
  onRemove: (d: DepartmentView) => void;
}) =>
  h('li', { class: 'dept-node' }, [
    h('div', { class: 'dept-row', style: { paddingLeft: `${props.depth * 26 + 12}px` } }, [
      h('span', { class: 'dept-corner' }, props.depth ? '└' : '▣'),
      h('b', { class: 'dept-name' }, props.node.data.name),
      props.node.data.managerName ? h('span', { class: 'chip' }, `管 ${props.node.data.managerName}`) : null,
      h('span', { class: 'grow' }),
      h('button', { class: 'btn btn--ghost btn--sm', onClick: () => props.onAdd(props.node.data) }, '下设'),
      h('button', { class: 'btn btn--ghost btn--sm', onClick: () => props.onEdit(props.node.data) }, '改'),
      h('button', { class: 'btn btn--danger-ghost btn--sm', onClick: () => props.onRemove(props.node.data) }, '撤'),
    ]),
    props.node.children.length
      ? h(
          'ul',
          { class: 'dept-tree' },
          props.node.children.map((c) =>
            h(DeptNode as never, {
              key: c.data.id,
              node: c,
              depth: props.depth + 1,
              onAdd: props.onAdd,
              onEdit: props.onEdit,
              onRemove: props.onRemove,
            }),
          ),
        )
      : null,
  ]);
DeptNode.props = {
  node: { type: Object as PropType<DeptNodeData>, required: true },
  depth: { type: Number, required: true },
  onAdd: { type: Function as PropType<(d: DepartmentView) => void>, required: true },
  onEdit: { type: Function as PropType<(d: DepartmentView) => void>, required: true },
  onRemove: { type: Function as PropType<(d: DepartmentView) => void>, required: true },
};

const list = ref<DepartmentView[]>([]);
const editor = reactive({ open: false, id: 0, name: '', parentId: null as number | null });

const tree = computed<DeptNodeData[]>(() => {
  const map = new Map<number, DeptNodeData>();
  list.value.forEach((d) => map.set(d.id, { data: d, children: [] }));
  const roots: DeptNodeData[] = [];
  for (const node of map.values()) {
    const parent = node.data.parentId != null ? map.get(node.data.parentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }
  return roots;
});

const flat = computed(() => {
  const out: { data: DepartmentView; depth: number }[] = [];
  const walk = (nodes: DeptNodeData[], depth: number): void => {
    for (const n of nodes) {
      out.push({ data: n.data, depth });
      walk(n.children, depth + 1);
    }
  };
  walk(tree.value, 0);
  return out;
});

async function reload(): Promise<void> {
  try {
    list.value = await departmentsApi.list();
  } catch (e) {
    toast.fail(e);
  }
}

function openCreate(parent: DepartmentView | null): void {
  editor.id = 0;
  editor.name = '';
  editor.parentId = parent ? parent.id : null;
  editor.open = true;
}

function openEdit(d: DepartmentView): void {
  editor.id = d.id;
  editor.name = d.name;
  editor.parentId = d.parentId;
  editor.open = true;
}

async function save(): Promise<void> {
  try {
    if (editor.id) {
      await departmentsApi.update(editor.id, { name: editor.name, parentId: editor.parentId });
      toast.ok('部门已更新');
    } else {
      await departmentsApi.create({ name: editor.name, parentId: editor.parentId });
      toast.ok('部门已落账');
    }
    editor.open = false;
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function remove(d: DepartmentView): Promise<void> {
  if (!window.confirm(`确认撤销部门「${d.name}」？下设部门与成员需先另行安置。`)) return;
  try {
    await departmentsApi.remove(d.id);
    toast.ok('部门已撤销');
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

onMounted(reload);
</script>

<style>
.dept-tree {
  list-style: none;
  margin: 0;
  padding: 0;
}

.dept-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-bottom: 1px solid var(--line);
}

.dept-row:hover {
  background: rgba(33, 29, 22, 0.025);
}

.dept-corner {
  color: var(--line-strong);
  font-family: var(--font-mono);
  flex: none;
}

.dept-name {
  font-size: 14px;
}
</style>
