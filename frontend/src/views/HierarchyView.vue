<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError, StaleSessionError } from '@/api/http';
import { rolesApi } from '@/api/roles';
import type { PermissionOrigin } from '@/api/types';
import OriginTable from '@/components/OriginTable.vue';
import PageHero from '@/components/PageHero.vue';
import RoleGraph from '@/components/RoleGraph.vue';
import { catalog, load, roleName } from '@/stores/catalog';
import { can } from '@/stores/session';
import { act, ask, notify, track, ui } from '@/stores/ui';
import { cyclePath, juniorsOf, seniorsOf } from '@/utils/graph';

const route = useRoute();
const router = useRouter();
const selected = ref<number | null>(null);
const pick = ref('');
const origins = ref<PermissionOrigin[]>([]);
const failure = ref<{ title: string; path: number[] | null } | null>(null);
const done = ref('');
const writable = computed(() => can('rbac.hierarchy.write'));

const roles = computed(() => catalog.roles ?? []);
const edges = computed(() => catalog.edges ?? []);
const role = computed(() => roles.value.find((r) => r.id === selected.value) ?? null);
const juniors = computed(() =>
  selected.value == null ? [] : juniorsOf(edges.value, selected.value),
);
const seniors = computed(() =>
  selected.value == null ? [] : seniorsOf(edges.value, selected.value),
);
const loose = computed(() =>
  roles.value.filter(
    (r) => !edges.value.some((e) => e.seniorRoleId === r.id || e.inheritedRoleId === r.id),
  ),
);
const candidates = computed(() =>
  roles.value.filter((r) => r.id !== selected.value && !juniors.value.includes(r.id)),
);
const directCount = computed(
  () => origins.value.filter((o) => o.sources.some((s) => !s.inherited)).length,
);

// 地址栏中的 role 参数变化（例如从其他页面跳转、浏览器后退）时同步选中角色。
watch(
  () => route.query.role,
  (value) => {
    const id = Number(value);
    if (id && id !== selected.value && catalog.roles?.some((r) => r.id === id)) selected.value = id;
  },
);

onMounted(async () => {
  await track(Promise.all([load(['roles', 'edges'], true), load(['systems'])]));
  const wanted = Number(route.query.role);
  selected.value = roles.value.some((r) => r.id === wanted) ? wanted : (roles.value[0]?.id ?? null);
});

watch(selected, async (id) => {
  pick.value = '';
  failure.value = null;
  done.value = '';
  origins.value = [];
  if (id == null) return;
  if (Number(route.query.role) !== id) void router.replace({ query: { role: id } });
  const result = await track(rolesApi.effective(id));
  if (result && selected.value === id) origins.value = result;
});

async function refreshRole() {
  await load(['edges'], true);
  if (selected.value != null) {
    const id = selected.value;
    const result = await track(rolesApi.effective(id));
    if (result && selected.value === id) origins.value = result;
  }
}

async function link() {
  const senior = selected.value;
  const junior = Number(pick.value);
  if (senior == null || !junior) return;
  failure.value = null;
  done.value = '';
  const ok = await ask(
    '建立角色继承',
    `「${roleName(senior)}」将获得「${roleName(junior)}」的全部有效权限，包括后者继续继承的权限。`,
    { confirmLabel: '确认建立', danger: false },
  );
  if (!ok || ui.writing) return;
  ui.writing = true;
  ui.pending++;
  try {
    await rolesApi.inherit(senior, junior);
    done.value = `已建立：${roleName(senior)} 继承 ${roleName(junior)}`;
    pick.value = '';
    await refreshRole();
  } catch (error) {
    if (error instanceof StaleSessionError) return;
    const message = error instanceof ApiError ? error.message : String(error);
    const path = cyclePath(edges.value, senior, junior);
    failure.value = {
      title: path ? '会形成循环继承' : message,
      path: path && path.length > 2 ? path : null,
    };
  } finally {
    ui.writing = false;
    ui.pending--;
  }
}

async function unlink(junior: number) {
  const senior = selected.value;
  if (senior == null) return;
  const ok = await ask(
    '解除继承',
    `「${roleName(senior)}」将不再通过这条关系获得「${roleName(junior)}」的权限；其他路径和直接授权保留。`,
    { confirmLabel: '确认解除' },
  );
  if (ok && (await act(() => rolesApi.uninherit(senior, junior)))) {
    done.value = `已解除：${roleName(senior)} 不再继承 ${roleName(junior)}`;
    notify('继承关系已解除');
    await refreshRole();
  }
}
</script>

<template>
  <PageHero />
  <div class="cols section" style="margin-top: 0">
    <section class="c6">
      <div class="label">
        <h2>角色层级</h2>
        <span>上层继承下层</span>
      </div>
      <RoleGraph :roles="roles" :edges="edges" :selected="selected" @select="selected = $event" />
      <div class="key">
        <span><i></i>继承</span>
        <span><i class="hot"></i>{{ role?.name ?? '选中角色' }}的继承链</span>
        <span><i class="dim"></i>经过停用角色</span>
      </div>
      <div v-if="loose.length" class="loose" aria-label="未参与继承的角色">
        <span class="mute">未参与继承</span>
        <button
          v-for="r in loose"
          :key="r.id"
          :class="{ on: r.id === selected }"
          :aria-pressed="r.id === selected"
          @click="selected = r.id"
        >
          {{ r.name }}
        </button>
      </div>
    </section>
    <section v-if="role" class="c4">
      <div class="label">
        <h2>角色详情</h2>
        <span>{{ role.code }}</span>
      </div>
      <div class="role-name">{{ role.name }}</div>
      <div class="role-code">
        <span v-if="role.enabled">已启用</span><span v-else class="off-t">已停用</span>
        <span v-if="role.description">{{ role.description }}</span>
      </div>
      <div class="facts">
        <div class="fact">
          <span>继承自</span><b>{{ juniors.length }}</b>
        </div>
        <div class="fact">
          <span>被继承</span><b>{{ seniors.length }}</b>
        </div>
        <div class="fact">
          <span>直接权限</span><b>{{ directCount }}</b>
        </div>
        <div class="fact">
          <span>有效权限</span><b>{{ origins.length }}</b>
        </div>
      </div>
      <p class="h-sub">继承自</p>
      <ul v-if="juniors.length" class="rows inherited-list">
        <li v-for="j in juniors" :key="j">
          {{ roleName(j) }}
          <span v-if="!roles.find((r) => r.id === j)?.enabled" class="off-t" style="font-size: 12px"
            >已停用</span
          >
          <button
            v-if="writable"
            class="act"
            :aria-label="`解除继承${roleName(j)}`"
            @click="unlink(j)"
          >
            解除
          </button>
        </li>
      </ul>
      <p v-else class="none">没有继承任何角色</p>
      <div v-if="writable" class="add">
        <label class="field" style="margin-top: 0"
          >被继承角色
          <select v-model="pick" aria-label="被继承角色">
            <option value="">选择角色</option>
            <option v-for="r in candidates" :key="r.id" :value="r.id">{{ r.name }}</option>
          </select>
        </label>
        <button class="btn" :disabled="!pick" @click="link">建立继承</button>
      </div>
      <div v-if="failure" class="alert" role="alert">
        <button class="close" aria-label="关闭提示" @click="failure = null">×</button>
        <small>无法建立继承</small>
        <strong>{{ failure.title }}</strong>
        <div v-if="failure.path" class="path">
          <template v-for="(id, i) in failure.path" :key="i"
            ><i v-if="i">→</i>{{ roleName(id) }}</template
          >
        </div>
      </div>
      <p v-if="done" class="ok" role="status">{{ done }}</p>
    </section>
  </div>
  <template v-if="role">
    <div class="perm-head">
      <h2 class="big-title">{{ role.name }}<span> 的有效权限</span></h2>
      <div class="big2">
        <div>
          <b>{{ directCount }}</b><span>● 直接拥有</span>
        </div>
        <div>
          <b class="r">{{ origins.length - directCount }}</b><span>○ 继承获得</span>
        </div>
      </div>
    </div>
    <OriginTable v-if="origins.length" :items="origins" />
    <div v-else class="empty">
      <h3>暂无有效权限</h3>
      <p>在「授权工作台」为它勾选菜单与按钮，或让它继承其他角色。</p>
    </div>
  </template>
</template>
