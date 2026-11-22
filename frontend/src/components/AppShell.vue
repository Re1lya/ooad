<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { allowed } from '@/router';
import { PAGES, pageNumber } from '@/router/pages';
import { logout, session } from '@/stores/session';
import { ui } from '@/stores/ui';

const route = useRoute();
const open = ref(false);

/** 导航只列出当前身份被授权的菜单，按固定分组和页码排列。 */
const groups = computed(() => {
  const result: { section: string; items: typeof PAGES }[] = [];
  for (const page of PAGES) {
    if (!allowed(page.name)) continue;
    let group = result.find((g) => g.section === page.section);
    if (!group) result.push((group = { section: page.section, items: [] }));
    group.items.push(page);
  }
  return result;
});

const current = computed(() => String(route.meta.page ?? route.name ?? ''));

watch(
  () => route.fullPath,
  () => (open.value = false),
);
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <header class="mast grid12">
    <div class="wordmark">Cdwy<small>统一权限管理系统</small></div>
    <div class="muser">
      <button
        class="textbtn nav-toggle"
        :aria-expanded="open"
        aria-controls="primary-nav"
        :aria-label="open ? '关闭导航' : '打开导航'"
        @click="open = !open"
      >
        菜单
      </button>
      <span class="who"
        ><b>{{ session.me?.displayName }}</b
        ><span>{{ session.me?.username }}</span></span
      >
      <button class="textbtn on" aria-label="退出登录" @click="logout()">退出</button>
    </div>
  </header>
  <div class="page grid12">
    <nav id="primary-nav" :class="['index', { open }]" aria-label="主导航">
      <div v-for="group in groups" :key="group.section" class="ngroup">
        <h3>{{ group.section }}</h3>
        <RouterLink
          v-for="page in group.items"
          :key="page.name"
          :to="page.path"
          :class="['nitem', { on: current === page.name }]"
          :aria-current="current === page.name ? 'page' : undefined"
        >
          <span class="no">{{ pageNumber(page.name) }}</span>
          <span>{{ page.title }}</span>
        </RouterLink>
      </div>
    </nav>
    <main id="main-content" tabindex="-1" :aria-busy="ui.pending > 0">
      <RouterView :key="route.path" />
    </main>
  </div>
</template>
