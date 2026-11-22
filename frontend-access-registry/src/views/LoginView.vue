<template>
  <div class="login">
    <div class="login__card rise">
      <div class="login__masthead">
        <span class="seal" style="width: 52px; height: 52px; font-size: 24px">权</span>
        <div>
          <h1 class="login__name t-title">权限台账</h1>
          <p class="login__tag">谁 · 经由什么角色 · 获得哪些权限</p>
        </div>
      </div>

      <form @submit.prevent="submit">
        <div class="field">
          <label for="username">账号</label>
          <input
            id="username"
            v-model.trim="username"
            class="input mono"
            placeholder="工号或账号名"
            autocomplete="username"
            autofocus
          />
        </div>
        <div class="field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            class="input"
            type="password"
            placeholder="登录密码"
            autocomplete="current-password"
          />
        </div>

        <p v-if="error" class="notice notice--err" style="margin: 0 0 14px">{{ error }}</p>

        <button class="btn btn--seal login__submit" type="submit" :disabled="busy || !canSubmit">
          {{ busy ? '核验中…' : '盖章进入' }}
        </button>
      </form>

      <p class="login__foot faint small">RBAC1 · 直接权限与继承权限取并集 · 授权链可解释可撤回</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { session } from '@/stores/session';

const route = useRoute();
const router = useRouter();
const username = ref('');
const password = ref('');
const error = ref('');
const busy = ref(false);

const canSubmit = computed(() => username.value.length > 0 && password.value.length > 0);

async function submit(): Promise<void> {
  if (!canSubmit.value || busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await session.login(username.value, password.value);
    router.replace((route.query.to as string) || '/overview');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败，请检查账号密码';
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login__card {
  width: 400px;
  max-width: 100%;
  background: var(--paper-raised);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow-pop);
  padding: 36px 34px 24px;
}

.login__masthead {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.login__name {
  margin: 0;
  font-size: 26px;
  letter-spacing: 0.08em;
}

.login__tag {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--ink-faint);
  letter-spacing: 0.05em;
}

.login__submit {
  width: 100%;
  justify-content: center;
  padding: 10px;
  font-size: 14px;
}

.login__foot {
  margin: 22px 0 0;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
  text-align: center;
}
</style>
