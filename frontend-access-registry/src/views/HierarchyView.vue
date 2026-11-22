<template>
  <div>
    <PageHead title="继承图谱" subtitle="RBAC1 角色继承：高级角色居上，朱砂箭头指向被继承者" seal="承">
      <template #actions>
        <button class="btn btn--seal" @click="linker.open = true">立继承</button>
      </template>
    </PageHead>

    <div class="panel graph-panel">
      <EmptyState v-if="!roles.length" glyph="谱" text="还没有角色，图谱无从画起" hint="先去「角色」页建几个角色" />
      <svg v-else :viewBox="`0 0 ${W} ${H}`" class="graph" role="img" aria-label="角色继承关系图">
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--cinnabar)" />
          </marker>
        </defs>

        <!-- 继承边 -->
        <g v-for="e in laidEdges" :key="`${e.from.id}-${e.to.id}`">
          <path :d="e.d" class="edge" />
          <g class="edge-hit" @click="askUnlink(e)">
            <path :d="e.d" class="edge-hit-line" />
            <circle :cx="e.mx" :cy="e.my" r="10" class="edge-knot" />
            <text :x="e.mx" :y="e.my + 3.5" class="edge-x">×</text>
          </g>
        </g>

        <!-- 角色节点 -->
        <g v-for="n in laidNodes" :key="n.role.id" class="node" :transform="`translate(${n.x}, ${n.y})`">
          <rect :width="NW" :height="NH" rx="4" class="node-box" :class="{ 'node-box--off': !n.role.enabled }" />
          <text :x="NW / 2" y="26" class="node-name">{{ n.role.name }}</text>
          <text :x="NW / 2" y="44" class="node-code">{{ n.role.code }}</text>
        </g>
      </svg>
      <p v-if="roles.length" class="graph-note faint small">点朱砂结点可解除该条继承；高级角色自动获得被继承角色的全部权限。</p>
    </div>

    <!-- 立继承 -->
    <Modal :open="linker.open" title="立继承" @close="linker.open = false">
      <div class="field">
        <label>高级角色（继承方）<b>*</b></label>
        <select v-model="linker.seniorId" class="select">
          <option :value="0" disabled>选择继承方</option>
          <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}（{{ r.code }}）</option>
        </select>
      </div>
      <div class="field">
        <label>被继承角色（权限来源）<b>*</b></label>
        <select v-model="linker.inheritedId" class="select">
          <option :value="0" disabled>选择被继承方</option>
          <option v-for="r in roles.filter((x) => x.id !== linker.seniorId)" :key="r.id" :value="r.id">
            {{ r.name }}（{{ r.code }}）
          </option>
        </select>
      </div>
      <p v-if="linker.seniorId && linker.inheritedId" class="notice notice--tip" style="margin: 0">
        「{{ nameOf(linker.seniorId) }}」将获得「{{ nameOf(linker.inheritedId) }}」的全部权限。
      </p>
      <template #foot>
        <button class="btn" @click="linker.open = false">取消</button>
        <button class="btn btn--seal" :disabled="!linker.seniorId || !linker.inheritedId" @click="link">立</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { hierarchyApi, rolesApi } from '@/api';
import type { HierarchyEdge, RoleView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Modal from '@/components/Modal.vue';
import EmptyState from '@/components/EmptyState.vue';
import { toast } from '@/stores/toast';

const NW = 148; // 节点宽
const NH = 56; // 节点高
const GX = 60; // 横向间距
const GY = 84; // 纵向层距

const roles = ref<RoleView[]>([]);
const edges = ref<HierarchyEdge[]>([]);
const linker = reactive({ open: false, seniorId: 0, inheritedId: 0 });

/** 最长路径分层：被继承者往下放。 */
const layers = computed<Map<number, number>>(() => {
  const depth = new Map<number, number>();
  const childOf = new Map<number, number[]>();
  for (const e of edges.value) {
    const arr = childOf.get(e.seniorRoleId) ?? [];
    arr.push(e.inheritedRoleId);
    childOf.set(e.seniorRoleId, arr);
  }
  const visit = (id: number, seen: Set<number>): number => {
    if (depth.has(id)) return depth.get(id)!;
    if (seen.has(id)) return 0; // 环兜底
    seen.add(id);
    const children = childOf.get(id) ?? [];
    const d = children.length ? Math.max(...children.map((c) => visit(c, seen))) + 1 : 0;
    depth.set(id, d);
    return d;
  };
  for (const r of roles.value) visit(r.id, new Set());
  // 翻转：高级在上（层 0 = 最深依赖链顶端）
  const max = Math.max(0, ...depth.values());
  const layer = new Map<number, number>();
  for (const [id, d] of depth) layer.set(id, max - d);
  return layer;
});

/** 按层分组：先分层，再定画布，最后摆位（顺序计算，互不循环）。 */
const groups = computed<Map<number, RoleView[]>>(() => {
  const byLayer = new Map<number, RoleView[]>();
  for (const r of roles.value) {
    const l = layers.value.get(r.id) ?? 0;
    byLayer.set(l, [...(byLayer.get(l) ?? []), r]);
  }
  return byLayer;
});

const layerCount = computed(() => Math.max(1, groups.value.size));
const maxRowWidth = computed(() => Math.max(1, ...[...groups.value.values()].map((g) => g.length)));

const W = computed(() => Math.max(720, maxRowWidth.value * (NW + GX) + GX));
const H = computed(() => layerCount.value * (NH + GY) + 40);

interface LaidNode {
  role: RoleView;
  x: number;
  y: number;
}

const laidNodes = computed<LaidNode[]>(() => {
  const out: LaidNode[] = [];
  for (const [layer, group] of groups.value) {
    const width = group.length * NW + (group.length - 1) * GX;
    group.forEach((role, i) => {
      out.push({ role, x: (W.value - width) / 2 + i * (NW + GX), y: 30 + layer * (NH + GY) });
    });
  }
  return out;
});

interface LaidEdge {
  from: RoleView;
  to: RoleView;
  d: string;
  mx: number;
  my: number;
}

const laidEdges = computed<LaidEdge[]>(() => {
  const pos = new Map(laidNodes.value.map((n) => [n.role.id, n]));
  return edges.value.flatMap((e) => {
    const a = pos.get(e.seniorRoleId);
    const b = pos.get(e.inheritedRoleId);
    if (!a || !b) return [];
    const x1 = a.x + NW / 2;
    const y1 = a.y + NH;
    const x2 = b.x + NW / 2;
    const y2 = b.y;
    const my = (y1 + y2) / 2;
    return [
      {
        from: a.role,
        to: b.role,
        d: `M ${x1} ${y1} C ${x1} ${my}, ${x2} ${my}, ${x2} ${y2 - 4}`,
        mx: (x1 + x2) / 2,
        my,
      },
    ];
  });
});

function nameOf(id: number): string {
  return roles.value.find((r) => r.id === id)?.name ?? `#${id}`;
}

async function reload(): Promise<void> {
  try {
    [roles.value, edges.value] = await Promise.all([rolesApi.list(), hierarchyApi.edges()]);
  } catch (e) {
    toast.fail(e);
  }
}

async function link(): Promise<void> {
  try {
    await hierarchyApi.link(linker.seniorId, linker.inheritedId);
    toast.ok(`已立继承：${nameOf(linker.seniorId)} → ${nameOf(linker.inheritedId)}`);
    linker.open = false;
    linker.seniorId = 0;
    linker.inheritedId = 0;
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

async function askUnlink(e: LaidEdge): Promise<void> {
  if (!window.confirm(`解除继承「${e.from.name} → ${e.to.name}」？继承而来的权限随即失效。`)) return;
  try {
    await hierarchyApi.unlink(e.from.id, e.to.id);
    toast.ok('继承已解除');
    void reload();
  } catch (err) {
    toast.fail(err);
  }
}

onMounted(reload);
</script>

<style scoped>
.graph-panel {
  padding: 16px;
  overflow-x: auto;
}

.graph {
  width: 100%;
  min-width: 640px;
  display: block;
}

.edge {
  fill: none;
  stroke: var(--cinnabar);
  stroke-width: 1.6;
  marker-end: url(#arrow);
  opacity: 0.85;
}

.edge-hit {
  cursor: pointer;
}

.edge-hit-line {
  fill: none;
  stroke: transparent;
  stroke-width: 16;
}

.edge-knot {
  fill: var(--paper-raised);
  stroke: var(--line-strong);
  opacity: 0;
  transition: opacity 0.15s;
}

.edge-x {
  text-anchor: middle;
  font-size: 12px;
  fill: var(--cinnabar);
  opacity: 0;
  transition: opacity 0.15s;
  pointer-events: none;
}

.edge-hit:hover .edge-knot,
.edge-hit:hover .edge-x {
  opacity: 1;
}

.node-box {
  fill: var(--paper-raised);
  stroke: var(--line-strong);
  stroke-width: 1.2;
  filter: drop-shadow(0 2px 3px rgba(33, 29, 22, 0.08));
}

.node-box--off {
  stroke-dasharray: 4 3;
  opacity: 0.55;
}

.node-name {
  text-anchor: middle;
  font-size: 14.5px;
  font-weight: 600;
  fill: var(--ink);
  font-family: var(--font-title);
}

.node-code {
  text-anchor: middle;
  font-size: 11px;
  fill: var(--ink-faint);
  font-family: var(--font-mono);
}

.graph-note {
  margin: 8px 4px 0;
  border-top: 1px dashed var(--line);
  padding-top: 10px;
}
</style>
