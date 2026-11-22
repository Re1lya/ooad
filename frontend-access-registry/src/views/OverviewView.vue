<template>
  <div>
    <PageHead title="总览" subtitle="系统当前格局：档案数量、运行模式与接入应用" seal="览" />

    <!-- 数字台账 -->
    <div class="stats">
      <div v-for="s in stats" :key="s.label" class="stat panel">
        <div class="stat__num t-title">{{ s.value }}</div>
        <div class="stat__label">{{ s.label }}</div>
      </div>
    </div>

    <div class="overview-grid">
      <!-- 运行模式 -->
      <section class="panel">
        <div class="panel__head">
          <span class="panel__title">运行模式</span>
          <span v-if="info" class="chip" :class="info.backendStrategy === 'ddd' ? 'chip--hot' : 'chip--pine'">
            {{ info.backendStrategy === 'ddd' ? 'DDD 面向对象' : 'STRUCTURED 结构化' }}
          </span>
        </div>
        <div class="panel__body">
          <template v-if="info">
            <p class="muted small" style="margin-top: 0">
              授权模型 <span class="mono">{{ info.authorizationModel }}</span> —— 直接权限与继承权限取并集。
            </p>
            <div class="modules">
              <div v-for="(mode, name) in info.modules" :key="name" class="module">
                <span class="module__name">{{ moduleText(String(name)) }}</span>
                <span class="chip" :class="mode === 'ddd' ? 'chip--hot' : 'chip--pine'">{{ strategyText(mode) }}</span>
              </div>
            </div>
          </template>
          <p v-else class="muted">暂时取不到运行模式，稍后刷新再看。</p>
        </div>
      </section>

      <!-- 接入应用 -->
      <section class="panel">
        <div class="panel__head">
          <span class="panel__title">接入应用</span>
          <router-link v-if="canVisit('/systems')" to="/systems" class="btn btn--ghost btn--sm">管理 →</router-link>
        </div>
        <div class="panel__body" style="padding-top: 8px">
          <EmptyState v-if="!systems.length" glyph="未" text="还没有接入应用" hint="到「接入应用」页登记第一个系统" />
          <div v-for="sys in systems" :key="sys.id" class="sys-row">
            <span class="stamp" :class="sys.enabled ? 'stamp--on' : ''">{{ sys.enabled ? '在册' : '停用' }}</span>
            <span class="grow">
              <b>{{ sys.name }}</b>
              <span class="chip mono" style="margin-left: 8px">{{ sys.code }}</span>
            </span>
            <span v-if="sys.baseUrl" class="mono faint small ellipsis" style="max-width: 200px">{{ sys.baseUrl }}</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { rolesApi, systemsApi, usersApi } from '@/api';
import type { RoleView, SystemView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import EmptyState from '@/components/EmptyState.vue';
import { canVisit, session } from '@/stores/session';
import { moduleText, strategyText } from '@/utils/labels';

const systems = ref<SystemView[]>([]);
const roles = ref<RoleView[]>([]);
const userTotal = ref(0);
const info = computed(() => session.state.info);

const stats = computed(() => [
  { label: '在册用户', value: userTotal.value },
  { label: '角色', value: roles.value.length },
  { label: '启用角色', value: roles.value.filter((r) => r.enabled).length },
  { label: '接入应用', value: systems.value.length },
]);

onMounted(async () => {
  const [sys, rol, usr] = await Promise.allSettled([
    systemsApi.list(),
    rolesApi.list(),
    usersApi.page(1, 1, ''),
  ]);
  if (sys.status === 'fulfilled') systems.value = sys.value;
  if (rol.status === 'fulfilled') roles.value = rol.value;
  if (usr.status === 'fulfilled') userTotal.value = usr.value.total;
  if (!session.state.info) void session.loadInfo();
});
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.stat {
  padding: 16px 18px 12px;
  border-top: 3px solid var(--ink);
}

.stat__num {
  font-size: 34px;
  line-height: 1.1;
}

.stat__label {
  font-size: 12.5px;
  color: var(--ink-soft);
  margin-top: 2px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 16px;
  align-items: start;
}

.modules {
  display: flex;
  flex-direction: column;
}

.module {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}

.module:last-child {
  border-bottom: 0;
}

.sys-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
}

.sys-row:last-child {
  border-bottom: 0;
}
</style>
