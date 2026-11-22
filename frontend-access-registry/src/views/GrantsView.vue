<template>
  <div>
    <PageHead title="用户授权" subtitle="把角色盖到具体的人身上：左边挑人，右边盖印" seal="授" />

    <div class="grants-grid">
      <!-- 挑人 -->
      <div class="panel">
        <div class="panel__head">
          <input v-model.trim="keyword" class="input" placeholder="搜账号或姓名…" @keyup.enter="search" />
        </div>
        <div class="user-list">
          <button
            v-for="u in page.items"
            :key="u.id"
            class="user-item"
            :class="{ 'user-item--on': current?.id === u.id }"
            @click="pick(u)"
          >
            <span class="user-item__avatar">{{ u.displayName.slice(0, 1) }}</span>
            <span class="grow" style="text-align: left">
              <b>{{ u.displayName }}</b>
              <span class="faint small" style="margin-left: 6px">{{ u.username }}</span>
            </span>
            <span v-if="!u.enabled" class="stamp">停用</span>
          </button>
          <EmptyState v-if="!page.items.length" glyph="人" text="没有查到用户" />
        </div>
        <Pager :page="page.page" :page-size="page.pageSize" :total="page.total" @change="loadUsers" />
      </div>

      <!-- 盖印 -->
      <div class="panel">
        <div class="panel__head">
          <span class="panel__title">
            {{ current ? `「${current.displayName}」的角色` : '先在左边挑一个人' }}
          </span>
          <span v-if="current" class="chip mono">{{ current.username }}</span>
        </div>
        <div class="panel__body">
          <template v-if="current">
            <p class="notice notice--tip" style="margin: 0 0 14px">
              朱砂印为已授予；点击即改，立即生效。继承而来的权限到「权限来源」页核对。
            </p>
            <div class="role-grid">
              <button
                v-for="r in roles"
                :key="r.id"
                class="role-seal"
                :class="{ 'role-seal--on': grantedIds.has(r.id), 'role-seal--off': !r.enabled }"
                :disabled="busy"
                @click="flip(r)"
              >
                <span class="role-seal__mark">{{ grantedIds.has(r.id) ? '授' : '候' }}</span>
                <span class="role-seal__name">{{ r.name }}</span>
                <span class="role-seal__code mono">{{ r.code }}</span>
              </button>
            </div>
            <EmptyState v-if="!roles.length" glyph="角" text="还没有角色可授" hint="先去「角色」页建角色" />
          </template>
          <EmptyState v-else glyph="挑" text="左边挑一个人" hint="挑好后，这里就是他的角色印台" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { rolesApi, usersApi } from '@/api';
import type { RoleView, UserPage, UserView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Pager from '@/components/Pager.vue';
import EmptyState from '@/components/EmptyState.vue';
import { toast } from '@/stores/toast';

const route = useRoute();
const page = ref<UserPage>({ page: 1, pageSize: 12, total: 0, items: [] });
const keyword = ref('');
const roles = ref<RoleView[]>([]);
const current = ref<UserView | null>(null);
const granted = ref<RoleView[]>([]);
const busy = ref(false);

const grantedIds = computed(() => new Set(granted.value.map((r) => r.id)));

async function loadUsers(toPage = 1): Promise<void> {
  try {
    page.value = await usersApi.page(toPage, page.value.pageSize, keyword.value);
  } catch (e) {
    toast.fail(e);
  }
}

function search(): void {
  void loadUsers(1);
}

async function pick(u: UserView): Promise<void> {
  current.value = u;
  try {
    granted.value = await usersApi.roles(u.id);
  } catch (e) {
    toast.fail(e);
  }
}

async function flip(r: RoleView): Promise<void> {
  if (!current.value || busy.value) return;
  busy.value = true;
  try {
    if (grantedIds.value.has(r.id)) {
      await usersApi.revokeRole(current.value.id, r.id);
      toast.ok(`已收回 ${current.value.displayName} 的「${r.name}」`);
    } else {
      await usersApi.grantRole(current.value.id, r.id);
      toast.ok(`已授予 ${current.value.displayName}「${r.name}」`);
    }
    granted.value = await usersApi.roles(current.value.id);
  } catch (e) {
    toast.fail(e);
  } finally {
    busy.value = false;
  }
}

onMounted(async () => {
  void loadUsers(1);
  try {
    roles.value = await rolesApi.list();
  } catch (e) {
    toast.fail(e);
  }
  const preset = Number(route.query.userId);
  if (preset) {
    try {
      await pick(await usersApi.one(preset));
    } catch {
      /* 预填用户不存在就空着 */
    }
  }
});
</script>

<style scoped>
.grants-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .grants-grid {
    grid-template-columns: 1fr;
  }
}

.user-list {
  max-height: 60vh;
  overflow-y: auto;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 14px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  font-family: var(--font-body);
  font-size: 13.5px;
  color: var(--ink);
  cursor: pointer;
  transition: background 0.12s;
}

.user-item:hover {
  background: rgba(33, 29, 22, 0.03);
}

.user-item--on {
  background: var(--cinnabar-wash);
  box-shadow: inset 3px 0 0 var(--cinnabar);
}

.user-item__avatar {
  width: 26px;
  height: 26px;
  border-radius: 4px;
  background: var(--ink);
  color: var(--paper);
  font-family: var(--font-title);
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.role-seal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 10px 12px;
  border: 1.5px dashed var(--line-strong);
  border-radius: var(--radius);
  background: var(--paper-raised);
  cursor: pointer;
  font-family: var(--font-body);
  transition:
    border-color 0.15s,
    background 0.15s,
    transform 0.1s;
}

.role-seal:hover {
  border-color: var(--ink-soft);
}

.role-seal:active {
  transform: translateY(1px);
}

.role-seal__mark {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-title);
  font-size: 16px;
  color: var(--ink-faint);
  border: 1.5px dashed var(--line-strong);
  transition: all 0.15s;
}

.role-seal--on {
  border-style: solid;
  border-color: var(--cinnabar);
  background: var(--cinnabar-wash);
}

.role-seal--on .role-seal__mark {
  background: var(--cinnabar);
  border-color: var(--cinnabar);
  color: #fdf7ef;
  transform: rotate(-3deg);
  box-shadow: inset 0 0 0 2px rgba(253, 247, 239, 0.35);
}

.role-seal--off {
  opacity: 0.5;
}

.role-seal__name {
  font-weight: 600;
  font-size: 13.5px;
  color: var(--ink);
}

.role-seal__code {
  font-size: 11.5px;
  color: var(--ink-faint);
}
</style>
