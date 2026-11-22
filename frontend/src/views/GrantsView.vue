<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { permissionsApi } from '@/api/permissions';
import { rolesApi } from '@/api/roles';
import type { Permission, PermissionOrigin, PermissionTree } from '@/api/types';
import Figures from '@/components/Figures.vue';
import PageHero from '@/components/PageHero.vue';
import { catalog, load } from '@/stores/catalog';
import { can, SELF_SYSTEM } from '@/stores/session';
import { act, track } from '@/stores/ui';

const route = useRoute();
const router = useRouter();
const roleId = ref<number | null>(null);
const app = ref<number | null>(null);
const tree = ref<PermissionTree[]>([]);
const direct = ref<Set<number>>(new Set());
const origins = ref<PermissionOrigin[]>([]);
const writable = computed(() => can('rbac.grants.write'));

// 地址栏中的 role 参数变化（例如从其他页面跳转、浏览器后退）时同步选中角色。
watch(
  () => route.query.role,
  (value) => {
    const id = Number(value);
    if (id && id !== roleId.value && catalog.roles?.some((r) => r.id === id)) roleId.value = id;
  },
);

onMounted(async () => {
  await track(load(['roles', 'systems']));
  const wanted = Number(route.query.role);
  roleId.value = catalog.roles?.some((r) => r.id === wanted)
    ? wanted
    : (catalog.roles?.[0]?.id ?? null);
  // 默认显示业务应用；只有权限系统自身时显示它。
  app.value =
    (catalog.systems?.find((s) => s.code !== SELF_SYSTEM) ?? catalog.systems?.[0])?.id ?? null;
});

watch(app, async (id) => {
  tree.value = [];
  if (id == null) return;
  const result = await track(permissionsApi.tree(id));
  if (result && app.value === id) tree.value = result;
});

async function loadRole(id: number) {
  const [mine, effective] = (await track(
    Promise.all([rolesApi.permissions(id), rolesApi.effective(id)]),
  )) ?? [[], []];
  if (roleId.value !== id) return;
  direct.value = new Set(mine.map((p) => p.id));
  origins.value = effective;
}

watch(roleId, (id) => {
  direct.value = new Set();
  origins.value = [];
  if (id == null) return;
  if (Number(route.query.role) !== id) void router.replace({ query: { role: id } });
  void loadRole(id);
});

/** 继承获得：有效权限中存在、但不是本角色直接授予的项。 */
const inheritedVia = computed(() => {
  const map = new Map<number, string>();
  for (const item of origins.value)
    if (!direct.value.has(item.permission.id)) {
      const source = item.sources.find((s) => s.inherited);
      if (source) map.set(item.permission.id, source.permissionRoleName);
    }
  return map;
});

const rows = computed(() => {
  const result: { p: Permission; level: number }[] = [];
  const walk = (nodes: PermissionTree[], level: number) => {
    for (const node of nodes) {
      result.push({ p: node.permission, level });
      walk(node.children ?? [], level + 1);
    }
  };
  walk(tree.value, 0);
  return result;
});

const state = (p: Permission) =>
  direct.value.has(p.id) ? 'direct' : inheritedVia.value.has(p.id) ? 'inherited' : 'none';

async function toggle(p: Permission) {
  const id = roleId.value;
  if (id == null) return;
  const granted = direct.value.has(p.id);
  const ok = await act(
    () => (granted ? rolesApi.revoke(id, p.id) : rolesApi.grant(id, p.id)),
    granted ? `已撤销「${p.name}」` : `已授予「${p.name}」`,
  );
  if (ok) await loadRole(id);
}
</script>

<template>
  <PageHero />
  <Figures
    :items="[
      { label: '直接授予', value: rows.filter((r) => state(r.p) === 'direct').length },
      {
        label: '继承获得',
        value: rows.filter((r) => state(r.p) === 'inherited').length,
        red: true,
      },
      { label: '未授予', value: rows.filter((r) => state(r.p) === 'none').length },
    ]"
  />
  <div class="cols section" style="margin-top: 28px">
    <section class="c3">
      <div class="label">
        <h2>角色</h2>
        <span>{{ catalog.roles?.length ?? 0 }}</span>
      </div>
      <ul class="rlist" aria-label="角色">
        <li v-for="r in catalog.roles ?? []" :key="r.id">
          <button
            :class="{ on: r.id === roleId }"
            :aria-pressed="r.id === roleId"
            @click="roleId = r.id"
          >
            {{ r.name }}<small>{{ r.enabled ? r.code : '已停用' }}</small>
          </button>
        </li>
      </ul>
    </section>
    <section class="c7">
      <div class="label">
        <h2>{{ catalog.roles?.find((r) => r.id === roleId)?.name ?? '选择角色' }}</h2>
        <span class="filters" role="group" aria-label="应用">
          <button
            v-for="s in catalog.systems ?? []"
            :key="s.id"
            :class="['textbtn', { on: app === s.id }]"
            :aria-pressed="app === s.id"
            @click="app = s.id"
          >
            {{ s.name }}
          </button>
        </span>
      </div>
      <div v-for="row in rows" :key="row.p.id" :class="['prow', { child: row.level > 0 }]">
        <span
          v-if="state(row.p) === 'inherited'"
          class="box inh"
          :title="`继承自 ${inheritedVia.get(row.p.id)}`"
        ></span>
        <button
          v-else
          :class="['box', { on: state(row.p) === 'direct' }]"
          role="checkbox"
          :aria-checked="state(row.p) === 'direct'"
          :aria-label="row.p.name"
          :disabled="!writable || !row.p.active"
          @click="toggle(row.p)"
        ></button>
        <span class="pn" :style="{ paddingLeft: `${row.level * 24}px` }"
          >{{ row.p.name
          }}<span v-if="!row.p.active" class="off-t" style="font-size: 12px"> 已停用</span></span
        >
        <span class="ty">{{ row.p.type === 'MENU' ? '菜单' : '按钮' }}</span>
        <span :class="['via', { r: state(row.p) === 'inherited' }]">
          {{
            state(row.p) === 'inherited'
              ? `继承自 ${inheritedVia.get(row.p.id)}`
              : state(row.p) === 'direct'
                ? '直接授予'
                : ''
          }}
        </span>
      </div>
      <p v-if="!rows.length" class="none">这个应用还没有菜单与按钮</p>
      <div class="key" style="border: 0">
        <span><span class="box on" style="width: 14px; height: 14px"></span>直接授予</span>
        <span
          ><span class="box inh" style="width: 14px; height: 14px"></span
          >继承获得（在「角色继承」中调整）</span
        >
        <span><span class="box" style="width: 14px; height: 14px"></span>未授予</span>
      </div>
    </section>
  </div>
</template>
