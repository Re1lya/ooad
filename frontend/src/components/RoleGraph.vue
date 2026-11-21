<script setup lang="ts">
import { computed } from 'vue';
import type { Edge, Role } from '@/api/types';
import { chainEdges, layout } from '@/utils/graph';

/** 角色层级图：上层继承下层；选中角色的整条继承链标红；停用角色划线、经过它的边为虚线。 */
const props = defineProps<{ roles: Role[]; edges: Edge[]; selected: number | null }>();
const emit = defineEmits<{ select: [id: number] }>();

const SLOT = 150;
const ROW = 96;
const TOP = 34;
const LEFT = 44;

const placed = computed(() => layout(props.roles, props.edges, SLOT));
const byId = computed(() => new Map(props.roles.map((r) => [r.id, r])));
const hot = computed(() =>
  props.selected == null ? new Set<string>() : chainEdges(props.edges, props.roles, props.selected),
);
const inChain = computed(() => new Set([...hot.value].map((k) => Number(k.split('>')[1]))));
const width = computed(() => Math.max(560, LEFT + placed.value.width + 20));
const height = computed(() => TOP + Math.max(0, placed.value.layers - 1) * ROW + 52);
const point = (id: number) => {
  const node = placed.value.nodes.find((n) => n.id === id)!;
  return { x: LEFT + node.x, y: TOP + (placed.value.layers - 1 - node.layer) * ROW };
};
const lines = computed(() =>
  props.edges
    .filter((e) => byId.value.has(e.seniorRoleId) && byId.value.has(e.inheritedRoleId))
    .map((e) => {
      const a = point(e.seniorRoleId);
      const b = point(e.inheritedRoleId);
      const key = `${e.seniorRoleId}>${e.inheritedRoleId}`;
      const dim =
        !byId.value.get(e.seniorRoleId)!.enabled || !byId.value.get(e.inheritedRoleId)!.enabled;
      return {
        key,
        x1: a.x + 5,
        y1: a.y + 26,
        x2: b.x + 5,
        y2: b.y - 8,
        cls: hot.value.has(key) ? 'hot' : dim ? 'dim' : '',
      };
    }),
);
const rules = computed(() =>
  Array.from({ length: placed.value.layers }, (_, i) => ({
    y: TOP + i * ROW,
    label: placed.value.layers - i,
  })),
);
const label = (text: string) => (text.length > 8 ? `${text.slice(0, 7)}…` : text);
</script>

<template>
  <div class="graph-box">
    <svg
      class="graph"
      :viewBox="`0 0 ${width} ${height}`"
      :width="width"
      :height="height"
      role="group"
      aria-label="角色层级图"
    >
      <g v-for="rule in rules" :key="rule.y">
        <line class="rule" x1="0" :x2="width" :y1="rule.y - 16" :y2="rule.y - 16" />
        <text class="lnum" x="0" :y="rule.y + 6">{{ rule.label }}</text>
      </g>
      <line
        v-for="l in lines"
        :key="l.key"
        :class="['edge', l.cls]"
        :x1="l.x1"
        :y1="l.y1"
        :x2="l.x2"
        :y2="l.y2"
      />
      <g
        v-for="node in placed.nodes"
        :key="node.id"
        :class="[
          'node',
          {
            sel: node.id === selected,
            chain: inChain.has(node.id) && node.id !== selected,
            off: !byId.get(node.id)?.enabled,
          },
        ]"
        role="button"
        tabindex="0"
        :aria-label="`${byId.get(node.id)?.name}${byId.get(node.id)?.enabled ? '' : '（已停用）'}`"
        :aria-pressed="node.id === selected"
        @click="emit('select', node.id)"
        @keydown.enter.prevent="emit('select', node.id)"
        @keydown.space.prevent="emit('select', node.id)"
      >
        <title>{{ byId.get(node.id)?.name }}</title>
        <rect
          class="hit"
          :x="point(node.id).x - 8"
          :y="point(node.id).y - 14"
          width="140"
          height="46"
        />
        <rect class="dot" :x="point(node.id).x" :y="point(node.id).y - 6" width="10" height="10" />
        <text class="name" :x="point(node.id).x + 18" :y="point(node.id).y + 4">
          {{ label(byId.get(node.id)?.name ?? '') }}
        </text>
        <text class="code" :x="point(node.id).x + 18" :y="point(node.id).y + 20">
          {{ byId.get(node.id)?.code }}{{ byId.get(node.id)?.enabled ? '' : ' · 停用' }}
        </text>
      </g>
    </svg>
  </div>
</template>
