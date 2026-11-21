<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { auditApi } from '@/api/audit';
import { usersApi } from '@/api/users';
import type { AuditEntry } from '@/api/types';
import Figures from '@/components/Figures.vue';
import PageHero from '@/components/PageHero.vue';
import { allowed } from '@/router';
import { catalog, load } from '@/stores/catalog';
import { SELF_SYSTEM } from '@/stores/session';
import { ui } from '@/stores/ui';
import { auditTime, auditTitle } from '@/utils/format';

const userTotal = ref<number | null>(null);
const recent = ref<AuditEntry[]>([]);

/** 各部分独立加载：当前身份没有某项授权时，该部分不显示，不影响其他部分。 */
onMounted(async () => {
  ui.pending++;
  try {
    await Promise.allSettled([
      ...(['systems', 'roles', 'permissions', 'resources', 'edges'] as const).map((key) =>
        load([key]),
      ),
      usersApi.list(0, 1).then((page) => (userTotal.value = page.total)),
      auditApi.list(SELF_SYSTEM, 1, 6).then((page) => (recent.value = page.items)),
    ]);
  } finally {
    ui.pending--;
  }
});

const figures = computed(() => {
  const items: { label: string; value: string | number; red?: boolean }[] = [];
  if (userTotal.value !== null)
    items.push({ label: '统一身份用户', value: userTotal.value.toLocaleString('zh-CN') });
  if (catalog.roles) items.push({ label: '角色', value: catalog.roles.length });
  if (catalog.edges) items.push({ label: '继承关系', value: catalog.edges.length });
  if (catalog.systems) items.push({ label: '接入应用', value: catalog.systems.length });
  if (catalog.resources) items.push({ label: 'API 资源', value: catalog.resources.length });
  return items;
});

const attention = computed(() => {
  const items: { text: string; to: string }[] = [];
  for (const role of catalog.roles ?? [])
    if (!role.enabled)
      items.push({
        text: `「${role.name}」角色已停用，经过它的继承已切断`,
        to: `/hierarchy?role=${role.id}`,
      });
  for (const system of catalog.systems ?? [])
    if (!system.enabled)
      items.push({ text: `应用「${system.name}」已停用，其下权限暂不生效`, to: '/systems' });
  const uncoded = (catalog.resources ?? []).filter((r) => !r.code).length;
  if (uncoded) items.push({ text: `${uncoded} 个接口还没有登记权限码`, to: '/resources' });
  return items;
});

const apps = computed(() =>
  (catalog.systems ?? []).map((system) => ({
    ...system,
    menus: (catalog.permissions ?? []).filter((p) => p.systemId === system.id).length,
    apis: (catalog.resources ?? []).filter((r) => r.systemId === system.id).length,
  })),
);
</script>

<template>
  <PageHero />
  <Figures v-if="figures.length" :items="figures" />
  <div class="cols section">
    <section class="c6">
      <div class="label">
        <h2>最近动态</h2>
        <RouterLink v-if="allowed('audit')" to="/audit">全部记录 →</RouterLink>
      </div>
      <ul v-if="recent.length" class="feed">
        <li v-for="entry in recent" :key="entry.id">
          <span class="when">{{ auditTime(entry.at) }}</span>
          <span
            ><b>{{ entry.actor?.name ?? '系统' }}</b> {{ auditTitle(entry) }}
            <span class="sub">{{ entry.target }}</span></span
          >
          <span></span>
        </li>
      </ul>
      <p v-else class="none">暂无记录</p>
    </section>
    <section class="c4">
      <div class="label">
        <h2>需要关注</h2>
        <span>{{ attention.length }}</span>
      </div>
      <ul v-if="attention.length" class="feed">
        <li v-for="item in attention" :key="item.text" class="plain">
          <span class="sq"></span><RouterLink :to="item.to">{{ item.text }}</RouterLink>
        </li>
      </ul>
      <p v-else class="none">目前没有需要处理的事项</p>
      <template v-if="apps.length">
        <div class="label" style="margin-top: 28px">
          <h2>接入应用</h2>
          <span>{{ apps.length }}</span>
        </div>
        <ul class="feed">
          <li v-for="app in apps" :key="app.id" class="plain">
            <span :class="['sq', { k: app.enabled }]"></span>
            <span
              ><b>{{ app.name }}</b
              ><span class="sub">菜单与按钮 {{ app.menus }} · 接口 {{ app.apis }}</span></span
            >
          </li>
        </ul>
      </template>
    </section>
  </div>
</template>
