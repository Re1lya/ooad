<template>
  <div>
    <PageHead title="个人中心" subtitle="我的档案、有效权限与改密" seal="我" />

    <div class="mine-grid">
      <!-- 档案 -->
      <section class="panel">
        <div class="panel__head"><span class="panel__title">我的档案</span></div>
        <div class="panel__body">
          <div class="me-head">
            <span class="me-avatar t-title">{{ me?.displayName?.slice(0, 1) ?? '·' }}</span>
            <div>
              <div style="font-size: 17px; font-weight: 600">{{ me?.displayName }}</div>
              <div class="mono muted small">{{ me?.username }}</div>
            </div>
          </div>
          <div class="me-rows small">
            <div class="me-row"><span class="muted">身份</span><span>{{ me?.administrator ? '管理员' : '普通用户' }}</span></div>
            <div class="me-row"><span class="muted">单位</span><span>{{ me?.companyName ?? '—' }}</span></div>
            <div class="me-row"><span class="muted">部门</span><span>{{ me?.sourceDepartment ?? '—' }}</span></div>
            <div class="me-row"><span class="muted">岗位</span><span>{{ me?.jobTitle ?? '—' }}</span></div>
            <div class="me-row"><span class="muted">电话</span><span class="mono">{{ me?.phone ?? '—' }}</span></div>
            <div class="me-row">
              <span class="muted">有效期</span>
              <span class="mono">{{ me?.expiresAt ? me.expiresAt.slice(0, 10) : '长期' }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 改密 -->
      <section class="panel">
        <div class="panel__head"><span class="panel__title">修改密码</span></div>
        <div class="panel__body">
          <div class="field">
            <label>当前密码 <b>*</b></label>
            <input v-model="pwd.current" class="input" type="password" autocomplete="current-password" />
          </div>
          <div class="field">
            <label>新密码 <b>*</b></label>
            <input v-model="pwd.next" class="input" type="password" autocomplete="new-password" />
          </div>
          <div class="field">
            <label>再输一遍 <b>*</b></label>
            <input v-model="pwd.confirm" class="input" type="password" autocomplete="new-password" />
          </div>
          <p v-if="pwd.next && pwd.confirm && pwd.next !== pwd.confirm" class="notice notice--err" style="margin: 0 0 12px">
            两遍新密码不一致
          </p>
          <button class="btn btn--seal" :disabled="!canChange || busy" @click="changePassword">
            {{ busy ? '提交中…' : '改密' }}
          </button>
        </div>
      </section>

      <!-- 有效权限 -->
      <section class="panel" style="grid-column: 1 / -1">
        <div class="panel__head">
          <span class="panel__title">我的有效权限</span>
          <span class="chip">{{ origins.length }} 项</span>
        </div>
        <div class="panel__body">
          <EmptyState v-if="!origins.length" glyph="权" text="你目前没有任何权限" hint="找管理员在「用户授权」页给你授角色" />
          <div v-else class="origin-list">
            <div v-for="o in origins" :key="o.permission.id" class="origin">
              <div class="origin__head">
                <span class="chip mono">{{ o.permission.code }}</span>
                <b>{{ o.permission.name }}</b>
                <span class="faint small">{{ o.permission.type === 'MENU' ? '菜单' : '按钮' }}</span>
              </div>
              <div class="origin__sources">
                <div v-for="(s, i) in o.sources" :key="i" class="source">
                  <span class="stamp" :class="s.inherited ? 'stamp--hot' : 'stamp--on'">
                    {{ s.inherited ? '继承' : '直接' }}
                  </span>
                  <span class="small muted">
                    角色「{{ s.assignedRoleName }}」{{ s.inherited ? `经「${s.permissionRoleName}」继承得来` : '直接持有' }}
                  </span>
                  <span v-if="s.path.length" class="path mono">{{ s.path.join(' → ') }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { meApi } from '@/api';
import type { PermissionOrigin } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import EmptyState from '@/components/EmptyState.vue';
import { session } from '@/stores/session';
import { toast } from '@/stores/toast';

const me = computed(() => session.state.user);
const origins = ref<PermissionOrigin[]>([]);
const busy = ref(false);
const pwd = reactive({ current: '', next: '', confirm: '' });

const canChange = computed(
  () => pwd.current && pwd.next && pwd.confirm && pwd.next === pwd.confirm,
);

async function changePassword(): Promise<void> {
  if (!canChange.value || busy.value) return;
  busy.value = true;
  try {
    await meApi.changePassword(pwd.current, pwd.next);
    toast.ok('密码已改，下次登录用新密码');
    pwd.current = pwd.next = pwd.confirm = '';
  } catch (e) {
    toast.fail(e);
  } finally {
    busy.value = false;
  }
}

onMounted(async () => {
  try {
    origins.value = await meApi.origins();
  } catch (e) {
    toast.fail(e);
  }
});
</script>

<style scoped>
.mine-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  align-items: start;
}

.me-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.me-avatar {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: var(--ink);
  color: var(--paper);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.me-rows {
  display: flex;
  flex-direction: column;
}

.me-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-bottom: 1px solid var(--line);
}

.me-row:last-child {
  border-bottom: 0;
}

.origin-list {
  display: flex;
  flex-direction: column;
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
}

.path {
  font-size: 12px;
  color: var(--cinnabar);
  background: var(--cinnabar-wash);
  border-radius: 999px;
  padding: 1px 10px;
}
</style>
