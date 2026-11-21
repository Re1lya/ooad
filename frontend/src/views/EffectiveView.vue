<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { usersApi } from '@/api/users';
import type { PermissionOrigin, Role, User } from '@/api/types';
import OriginTable from '@/components/OriginTable.vue';
import PageHero from '@/components/PageHero.vue';
import { departmentName, load } from '@/stores/catalog';
import { track } from '@/stores/ui';
import { personLabel } from '@/utils/format';

const route = useRoute();
const router = useRouter();
const user = ref<User | null>(null);
const roles = ref<Role[]>([]);
const origins = ref<PermissionOrigin[]>([]);
const keyword = ref('');
const results = ref<User[]>([]);

const direct = computed(
  () => origins.value.filter((o) => o.sources.some((s) => !s.inherited)).length,
);

onMounted(() => {
  void load(['roles', 'systems', 'departments']).catch(() => undefined);
});

watch(
  () => route.query.user,
  async (value) => {
    const id = Number(value);
    user.value = null;
    roles.value = [];
    origins.value = [];
    if (!id) return;
    const data = await track(
      Promise.all([usersApi.get(id), usersApi.roles(id), usersApi.origins(id)]),
    );
    if (!data || Number(route.query.user) !== id) return;
    [user.value, roles.value, origins.value] = data;
  },
  { immediate: true },
);

let timer: ReturnType<typeof setTimeout> | undefined;
watch(keyword, (text) => {
  clearTimeout(timer);
  timer = setTimeout(async () => {
    const value = text.trim();
    if (!value) {
      results.value = [];
      return;
    }
    const page = await track(usersApi.list(1, 8, value));
    if (keyword.value.trim() !== value || !page) return;
    const lower = value.toLowerCase();
    results.value = page.items.filter((u) =>
      `${u.username} ${u.displayName}`.toLowerCase().includes(lower),
    );
  }, 300);
});

function choose(u: User) {
  keyword.value = '';
  results.value = [];
  void router.replace({ query: { user: u.id } });
}
</script>

<template>
  <PageHero />
  <div class="tools picker">
    <input
      v-model="keyword"
      class="grow"
      type="search"
      placeholder="输入工号或姓名查找用户"
      aria-label="搜索当前列表"
    />
  </div>
  <ul v-if="results.length" class="picker-results" aria-label="查找结果">
    <li v-for="u in results" :key="u.id">
      <button @click="choose(u)">
        <code style="color: var(--ink)">{{ u.username }}</code><b>{{ u.displayName }}</b>
      </button>
    </li>
  </ul>
  <template v-if="user">
    <div class="person">
      <h2>{{ user.displayName }}</h2>
      <div class="pid">
        <b>{{ user.username }}</b>{{ user.companyName ?? departmentName(user.departmentId) }}
        <br />{{ personLabel(user.personType)
        }}<span v-if="!user.enabled" class="off-t"> · 账号已停用</span>
      </div>
      <div class="proles">
        <span class="sub">已分配角色</span>
        <div class="inl">
          <span v-for="r in roles" :key="r.id" :class="{ off: !r.enabled }">{{ r.name }}</span>
          <span v-if="!roles.length" class="off">无</span>
        </div>
      </div>
    </div>
    <div class="perm-head" style="margin-top: 28px">
      <h2 class="big-title">有效权限<span> · 标明每项来源</span></h2>
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
      <h3>没有有效权限</h3>
      <p>该用户没有启用的角色，或角色还没有授权。</p>
    </div>
  </template>
  <div v-else-if="!results.length" class="empty">
    <h3>选择一位用户</h3>
    <p>输入工号或姓名，或在「用户管理」中点「有效权限」。</p>
  </div>
</template>
