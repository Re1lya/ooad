<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { meApi } from '@/api/me';
import type { PermissionOrigin, PermissionTree } from '@/api/types';
import OriginTable from '@/components/OriginTable.vue';
import PageHero from '@/components/PageHero.vue';
import { load } from '@/stores/catalog';
import { session } from '@/stores/session';
import { track } from '@/stores/ui';

const origins = ref<PermissionOrigin[]>([]);
const menu = ref<PermissionTree[]>([]);

async function refresh() {
  const data = await track(Promise.all([meApi.origins(), meApi.menu()]));
  if (data) [origins.value, menu.value] = data;
}
onMounted(() => {
  void load(['systems', 'roles']).catch(() => undefined);
  void refresh();
});

const direct = computed(
  () => origins.value.filter((o) => o.sources.some((s) => !s.inherited)).length,
);
const flat = computed(() => {
  const result: { name: string; level: number; granted: boolean; type: string }[] = [];
  const walk = (nodes: PermissionTree[], level: number) => {
    for (const n of nodes) {
      result.push({ name: n.permission.name, level, granted: n.granted, type: n.permission.type });
      walk(n.children ?? [], level + 1);
    }
  };
  walk(menu.value, 0);
  return result;
});
</script>

<template>
  <PageHero />
  <div class="person">
    <h2>{{ session.me?.displayName }}</h2>
    <div class="pid">
      <b>{{ session.me?.username }}</b>当前账号有效
    </div>
    <div class="proles">
      <span class="sub">权限变化会在下一次请求时生效</span>
      <button class="textbtn on" @click="refresh">刷新权限</button>
    </div>
  </div>
  <div class="perm-head" style="margin-top: 28px">
    <h2 class="big-title">权限与来源</h2>
    <div class="big2">
      <div>
        <b>{{ direct }}</b><span>● 直接拥有</span>
      </div>
      <div>
        <b class="r">{{ origins.length - direct }}</b><span>○ 继承获得</span>
      </div>
    </div>
  </div>
  <OriginTable v-if="origins.length" :items="origins" show-all />
  <div v-else class="empty">
    <h3>暂无有效权限</h3>
    <p>请联系管理员为你分配角色。</p>
  </div>
  <section class="section">
    <div class="label">
      <h2>我的菜单</h2>
      <span>{{ flat.length }}</span>
    </div>
    <ul v-if="flat.length" class="rows">
      <li
        v-for="(item, i) in flat"
        :key="i"
        :style="{ paddingLeft: `${item.level * 24}px`, fontWeight: item.level ? 500 : 600 }"
      >
        {{ item.name }}
        <span class="sub" style="margin-left: auto">{{
          item.type === 'BUTTON' ? '按钮' : item.granted ? '菜单' : '上级菜单'
        }}</span>
      </li>
    </ul>
    <p v-else class="none">暂无可用菜单</p>
  </section>
</template>
