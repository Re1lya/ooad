<template>
  <div>
    <PageHead title="权限来源" subtitle="拆开授权链：每项有效权限从哪个角色、经哪条继承路径得来" seal="溯">
      <template #actions>
        <button class="btn btn--sm" :class="mode === 'user' ? 'btn--seal' : 'btn--ghost'" @click="mode = 'user'">按人员</button>
        <button
          v-if="roleMode"
          class="btn btn--sm"
          :class="mode === 'role' ? 'btn--seal' : 'btn--ghost'"
          @click="mode = 'role'"
        >
          按角色
        </button>
        <select v-if="mode === 'role'" v-model="roleId" class="select" style="width: 220px" @change="loadRole">
          <option :value="0" disabled>选角色查有效权限…</option>
          <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}（{{ r.code }}）</option>
        </select>
      </template>
    </PageHead>

    <!-- 按人员：左边挑人，右边看他的授权链 -->
    <div v-if="mode === 'user'" class="effective-grid">
      <div class="panel">
        <div class="panel__head">
          <input v-model.trim="keyword" class="input" placeholder="搜账号或姓名…" @keyup.enter="loadUsers(1)" />
        </div>
        <div class="user-list">
          <button
            v-for="u in page.items"
            :key="u.id"
            class="user-item"
            :class="{ 'user-item--on': person?.id === u.id }"
            @click="pickUser(u)"
          >
            <span class="user-item__avatar">{{ u.displayName.slice(0, 1) }}</span>
            <span class="grow" style="text-align: left">
              <b>{{ u.displayName }}</b>
              <span class="faint small" style="margin-left: 6px">{{ u.username }}</span>
            </span>
            <span v-if="!u.enabled" class="stamp">停用</span>
          </button>
          <EmptyState v-if="!page.items.length" glyph="人" text="没有查到人员" />
        </div>
        <Pager :page="page.page" :page-size="page.pageSize" :total="page.total" @change="loadUsers" />
      </div>

      <div class="panel">
        <div class="panel__head">
          <span class="panel__title">{{ person ? `「${person.displayName}」的有效权限` : '先在左边挑一个人' }}</span>
          <span v-if="person" class="chip mono">{{ person.username }}</span>
          <span v-if="person" class="grow" />
          <span v-if="person" class="chip">{{ origins.length }} 项</span>
        </div>
        <EmptyState v-if="!person" glyph="溯" text="左边挑一个人" hint="会列出他直接与经继承得到的全部权限，以及每条授权链" />
        <EmptyState
          v-else-if="!origins.length"
          glyph="无"
          text="此人暂无任何有效权限"
          hint="先在「用户授权」给他盖角色，或检查角色、权限与应用是否停用"
        />
        <OriginList v-else :origins="origins" :system-name="systemName" :names="names" />
      </div>
    </div>

    <!-- 按角色 -->
    <div v-else class="panel">
      <EmptyState v-if="!roleId" glyph="溯" text="右上角选一个角色" hint="会列出它直接与继承得到的全部权限，以及每条授权链" />
      <EmptyState v-else-if="!origins.length" glyph="无" text="该角色暂无任何权限" hint="到「角色」页给它配直接权限，或到「继承图谱」立继承" />
      <OriginList v-else :origins="origins" :system-name="systemName" :names="names" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch, type PropType } from 'vue';
import { useRoute } from 'vue-router';
import { rolesApi, systemsApi, usersApi } from '@/api';
import type { PermissionOrigin, RoleView, SystemView, UserPage, UserView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Pager from '@/components/Pager.vue';
import EmptyState from '@/components/EmptyState.vue';
import { canVisit } from '@/stores/session';
import { toast } from '@/stores/toast';
import { pathText, roleNames } from '@/utils/origins';

const route = useRoute();
/** 按人员查是“有效权限”菜单本身的授权；按角色查用的是继承图谱与授权工作台的接口。 */
const roleMode = computed(() => canVisit('/hierarchy') || canVisit('/grants'));
const mode = ref<'user' | 'role'>('user');

const page = ref<UserPage>({ page: 1, pageSize: 12, total: 0, items: [] });
const keyword = ref('');
const person = ref<UserView | null>(null);
const roles = ref<RoleView[]>([]);
const roleId = ref(0);
const origins = ref<PermissionOrigin[]>([]);
const systems = ref<SystemView[]>([]);
const names = computed(() => roleNames(origins.value, roles.value));

function systemName(id: number): string {
  return systems.value.find((s) => s.id === id)?.name ?? `应用 #${id}`;
}

/** 一项有效权限及其全部来源；按人员、按角色两种查法共用。 */
const OriginList = (props: {
  origins: PermissionOrigin[];
  systemName: (id: number) => string;
  names: Map<number, string>;
}) =>
  h(
    'div',
    { class: 'trace-list' },
    props.origins.map((o) =>
      h('div', { key: o.permission.id, class: 'trace' }, [
        h('div', { class: 'trace__head' }, [
          h('span', { class: 'chip mono' }, o.permission.code),
          h('b', o.permission.name),
          h('span', { class: 'faint small' }, o.permission.type === 'MENU' ? '菜单' : '按钮'),
          h('span', { class: 'grow' }),
          h('span', { class: 'faint small' }, props.systemName(o.permission.systemId)),
          h('span', { class: ['stamp', o.permission.active ? 'stamp--on' : ''] }, o.permission.active ? '有效' : '停用'),
        ]),
        h(
          'div',
          { class: 'trace__sources' },
          o.sources.map((s, i) =>
            h('div', { key: i, class: 'trace__source' }, [
              h('span', { class: ['stamp', s.inherited ? 'stamp--hot' : 'stamp--on'] }, s.inherited ? '继承' : '直接'),
              h(
                'span',
                { class: 'small' },
                s.inherited
                  ? `经角色「${s.assignedRoleName}」继承自「${s.permissionRoleName}」`
                  : `角色「${s.permissionRoleName}」直接持有`,
              ),
              s.path.length > 1 ? h('span', { class: 'trace__path' }, pathText(s.path, props.names)) : null,
            ]),
          ),
        ),
      ]),
    ),
  );
OriginList.props = {
  origins: { type: Array as PropType<PermissionOrigin[]>, required: true },
  systemName: { type: Function as PropType<(id: number) => string>, required: true },
  names: { type: Map as PropType<Map<number, string>>, required: true },
};

async function loadUsers(toPage = 1): Promise<void> {
  try {
    page.value = await usersApi.page(toPage, page.value.pageSize, keyword.value);
  } catch (e) {
    toast.fail(e);
  }
}

async function pickUser(u: UserView): Promise<void> {
  person.value = u;
  try {
    origins.value = await usersApi.origins(u.id);
  } catch (e) {
    origins.value = [];
    toast.fail(e);
  }
}

async function loadRole(): Promise<void> {
  if (!roleId.value) return;
  try {
    origins.value = await rolesApi.effective(roleId.value);
  } catch (e) {
    origins.value = [];
    toast.fail(e);
  }
}

watch(mode, async (next) => {
  origins.value = [];
  if (next === 'user' && person.value) await pickUser(person.value);
  if (next === 'role') {
    await loadRole();
  }
});

onMounted(async () => {
  void loadUsers(1);
  // 应用名、继承路径中间的角色名只用于标注；没有这两个列表的权限时显示编号
  rolesApi
    .list()
    .then((list) => (roles.value = list))
    .catch(() => undefined);
  systemsApi
    .list()
    .then((list) => (systems.value = list))
    .catch(() => undefined);
  const preset = Number(route.query.userId);
  if (preset) {
    try {
      await pickUser(await usersApi.one(preset));
    } catch {
      /* 预填的人不存在就空着 */
    }
  }
});
</script>

<style scoped>
.effective-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .effective-grid {
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
</style>

<style>
/* 授权链列表由渲染函数生成，scoped 样式作用不到，按同组其他页的做法写成全局样式 */
.trace-list {
  padding: 8px 16px;
}

.trace {
  border-bottom: 1px solid var(--line);
  padding: 12px 0;
}

.trace:last-child {
  border-bottom: 0;
}

.trace__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.trace__sources {
  margin: 8px 0 2px 16px;
  border-left: 2px solid var(--line);
  padding-left: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.trace__source {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--ink-soft);
}

.trace__path {
  font-size: 12px;
  color: var(--cinnabar);
  background: var(--cinnabar-wash);
  border-radius: 999px;
  padding: 1px 10px;
}
</style>
