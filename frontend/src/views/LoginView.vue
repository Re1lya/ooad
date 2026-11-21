<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ApiError } from '@/api/http';
import { firstAllowed } from '@/router';
import { login } from '@/stores/session';
import { ui } from '@/stores/ui';

const router = useRouter();
const username = ref('');
const password = ref('');
const error = ref('');

async function submit() {
  if (ui.writing) return;
  error.value = '';
  ui.writing = true;
  ui.pending++;
  try {
    await login(username.value.trim(), password.value);
    password.value = '';
    await router.replace(firstAllowed());
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败，请稍后重试';
  } finally {
    ui.writing = false;
    ui.pending--;
  }
}
</script>

<template>
  <div class="login grid12">
    <section class="brand">
      <b style="font-size: 22px; letter-spacing: -0.02em">Cdwy</b>
      <div class="num" aria-hidden="true">01</div>
      <h1>统一权限<br />管理系统</h1>
      <p>一处维护人员、角色与资源；各业务应用的菜单、按钮和接口都由这里授权。</p>
    </section>
    <section class="panel">
      <h2>登录工作区</h2>
      <form @submit.prevent="submit">
        <label class="field"
          >登录名
          <input v-model="username" autocomplete="username" required maxlength="64" />
        </label>
        <label class="field"
          >密码
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="72"
          />
        </label>
        <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
        <button class="btn" :disabled="ui.writing">{{ ui.writing ? '正在登录…' : '登录' }}</button>
      </form>
    </section>
  </div>
</template>
