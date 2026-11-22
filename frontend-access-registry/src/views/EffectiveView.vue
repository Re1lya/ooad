<template>
  <div>
    <PageHead title="权限来源" subtitle="拆开授权链：每项有效权限从哪个角色、经哪条继承路径得来" seal="溯">
      <template #actions>
        <select v-model="roleId" class="select" style="width: 220px" @change="loadRole">
          <option :value="0" disabled>选角色查有效权限…</option>
          <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}（{{ r.code }}）</option>
        </select>
      </template>
    </PageHead>

    <div class="panel">
      <EmptyState v-if="!roleId" glyph="溯" text="右上角选一个角色" hint="会列出它直接与继承得到的全部权限，以及每条授权链" />
      <EmptyState v-else-if="!origins.length" glyph="无" text="该角色暂无任何权限" hint="到「角色」页给它配直接权限，或到「继承图谱」立继承" />
      <div v-else class="origin-list">
        <div v-for="o in origins" :key="o.permission.id" class="origin">
          <div class="origin__head">
            <span class="chip mono">{{ o.permission.code }}</span>
            <b>{{ o.permission.name }}</b>
            <span class="faint small">{{ o.permission.type === 'MENU' ? '菜单' : '按钮' }}</span>
            <span class="grow" />
            <span class="stamp" :class="o.permission.active ? 'stamp--on' : ''">
              {{ o.permission.active ? '有效' : '停用' }}
            </span>
          </div>
          <div class="origin__sources">
            <div v-for="(s, i) in o.sources" :key="i" class="source">
              <span class="stamp" :class="s.inherited ? 'stamp--hot' : 'stamp--on'">
                {{ s.inherited ? '继承' : '直接' }}
              </span>
              <span class="mono small">
                授给用户/角色的是「{{ s.assignedRoleName }}」，权限挂在「{{ s.permissionRoleName }}」
              </span>
              <span v-if="s.path.length" class="path mono">
                {{ s.path.join(' → ') }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { rolesApi } from '@/api';
import type { PermissionOrigin, RoleView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import EmptyState from '@/components/EmptyState.vue';
import { toast } from '@/stores/toast';

const roles = ref<RoleView[]>([]);
const roleId = ref(0);
const origins = ref<PermissionOrigin[]>([]);

async function loadRole(): Promise<void> {
  if (!roleId.value) return;
  try {
    origins.value = await rolesApi.effective(roleId.value);
  } catch (e) {
    toast.fail(e);
  }
}

onMounted(async () => {
  try {
    roles.value = await rolesApi.list();
  } catch (e) {
    toast.fail(e);
  }
});
</script>

<style scoped>
.origin-list {
  padding: 8px 16px;
}

.origin {
  border-bottom: 1px solid var(--line);
  padding: 12px 0;
}

.origin:last-child {
  border-bottom: 0;
}

.origin__head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.origin__sources {
  margin: 8px 0 2px 16px;
  border-left: 2px solid var(--line);
  padding-left: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.source {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--ink-soft);
}

.path {
  font-size: 12px;
  color: var(--cinnabar);
  background: var(--cinnabar-wash);
  border-radius: 999px;
  padding: 1px 10px;
}
</style>
