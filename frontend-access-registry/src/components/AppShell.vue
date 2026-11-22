<template>
  <div class="shell">
    <!-- 档案脊：侧边导航 -->
    <aside class="rail" :class="{ 'rail--open': railOpen }">
      <div class="rail__brand">
        <span class="seal">权</span>
        <div class="rail__brand-text">
          <div class="rail__name t-title">权限台账</div>
          <div class="rail__sub">统一权限管理</div>
        </div>
      </div>

      <nav class="rail__nav">
        <div v-for="group in nav" :key="group.label" class="rail__group">
          <div class="rail__group-label">{{ group.label }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="rail__item"
            :class="{ 'rail__item--on': isOn(item.to) }"
            @click="railOpen = false"
          >
            <span class="rail__dot" />
            {{ item.label }}
          </router-link>
        </div>
      </nav>

      <div class="rail__foot">
        <div class="rail__strategy mono" v-if="info">
          <span class="stamp" :class="info.backendStrategy === 'ddd' ? 'stamp--hot' : 'stamp--on'">
            {{ info.backendStrategy === 'ddd' ? '面向对象' : '结构化' }}
          </span>
        </div>
        <button class="rail__user" @click="router.push('/mine')">
          <span class="rail__avatar">{{ userInitial }}</span>
          <span class="grow ellipsis" style="text-align: left">
            {{ session.state.user?.displayName ?? session.state.user?.username ?? '未登录' }}
          </span>
          <span class="faint small">我的</span>
        </button>
        <button class="btn btn--ghost btn--sm" style="width: 100%; justify-content: center" @click="logout">
          退出登录
        </button>
      </div>
    </aside>

    <div v-if="railOpen" class="rail-mask" @click="railOpen = false" />

    <!-- 主区 -->
    <div class="main">
      <header class="topbar">
        <button class="btn btn--ghost btn--sm topbar__burger" aria-label="打开导航" @click="railOpen = true">
          ☰
        </button>
        <span class="topbar__crumb mono">{{ crumb }}</span>
        <span class="grow" />
        <span class="stamp stamp--on">已连接</span>
      </header>
      <main class="content">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.fullPath" class="rise" />
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { session } from '@/stores/session';

const route = useRoute();
const router = useRouter();
const railOpen = ref(false);

interface NavItem {
  to: string;
  label: string;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const nav: NavGroup[] = [
  { label: '台', items: [{ to: '/overview', label: '总览' }] },
  {
    label: '档案',
    items: [
      { to: '/users', label: '用户' },
      { to: '/departments', label: '部门' },
      { to: '/systems', label: '接入应用' },
    ],
  },
  {
    label: '授权',
    items: [
      { to: '/roles', label: '角色' },
      { to: '/hierarchy', label: '继承图谱' },
      { to: '/permissions', label: '前端权限' },
      { to: '/resources', label: 'API 资源' },
      { to: '/grants', label: '用户授权' },
      { to: '/effective', label: '权限来源' },
    ],
  },
  {
    label: '运维',
    items: [
      { to: '/directory', label: '员工同步' },
      { to: '/audit', label: '审计日志' },
    ],
  },
];

const info = computed(() => session.state.info);
const crumb = computed(() => `/ ${(route.meta.menu as string) ?? ''}`);
const userInitial = computed(() => {
  const name = session.state.user?.displayName || session.state.user?.username || '·';
  return name.slice(0, 1).toUpperCase();
});

function isOn(to: string): boolean {
  return route.path === to || route.path.startsWith(`${to}/`);
}

function logout(): void {
  session.logout();
  router.replace('/login');
}
</script>

<style scoped>
.shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ---- 档案脊 ---- */
.rail {
  width: var(--rail-w);
  flex: none;
  display: flex;
  flex-direction: column;
  background: var(--paper-sunken);
  border-right: 1px solid var(--line-strong);
}

.rail__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid var(--line);
}

.rail__name {
  font-size: 18px;
  letter-spacing: 0.06em;
}

.rail__sub {
  font-size: 11.5px;
  color: var(--ink-faint);
  letter-spacing: 0.14em;
}

.rail__nav {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
}

.rail__group {
  margin-bottom: 14px;
}

.rail__group-label {
  font-family: var(--font-title);
  font-size: 11.5px;
  color: var(--ink-faint);
  padding: 4px 8px;
  letter-spacing: 0.3em;
  border-bottom: 1px dashed var(--line);
  margin-bottom: 4px;
}

.rail__item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 10px;
  border-radius: var(--radius);
  color: var(--ink-soft);
  text-decoration: none;
  font-size: 13.5px;
  transition:
    background 0.14s,
    color 0.14s;
}

.rail__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--line-strong);
  flex: none;
  transition: background 0.14s;
}

.rail__item:hover {
  background: rgba(33, 29, 22, 0.04);
  color: var(--ink);
}

.rail__item--on {
  background: var(--paper-raised);
  color: var(--ink);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px var(--line);
}

.rail__item--on .rail__dot {
  background: var(--cinnabar);
}

.rail__foot {
  padding: 12px;
  border-top: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rail__strategy {
  font-size: 12px;
  padding: 0 4px;
}

.rail__user {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--paper-raised);
  cursor: pointer;
  font-size: 13px;
  color: var(--ink);
  font-family: var(--font-body);
}

.rail__user:hover {
  border-color: var(--line-strong);
}

.rail__avatar {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  background: var(--ink);
  color: var(--paper);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-family: var(--font-title);
  flex: none;
}

.rail-mask {
  display: none;
}

/* ---- 主区 ---- */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: var(--head-h);
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  border-bottom: 1px solid var(--line);
  background: var(--paper);
}

.topbar__burger {
  display: none;
}

.topbar__crumb {
  color: var(--ink-faint);
  font-size: 12.5px;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 48px;
}

/* ---- 窄屏：侧栏收成抽屉 ---- */
@media (max-width: 900px) {
  .rail {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 50;
    transform: translateX(-100%);
    transition: transform 0.22s ease;
    box-shadow: var(--shadow-pop);
  }

  .rail--open {
    transform: translateX(0);
  }

  .rail-mask {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 40;
    background: rgba(33, 29, 22, 0.3);
  }

  .topbar__burger {
    display: inline-flex;
  }

  .content {
    padding: 18px 16px 40px;
  }
}
</style>
