<script setup lang="ts">
import { ref } from 'vue';
import { authApi } from '@/api/auth';
import PageHero from '@/components/PageHero.vue';
import { logout } from '@/stores/session';
import { act, notify } from '@/stores/ui';

const current = ref('');
const next = ref('');
const repeat = ref('');
const error = ref('');

async function submit() {
  error.value = '';
  if (next.value !== repeat.value) {
    error.value = '两次输入的新密码不一致';
    return;
  }
  const ok = await act(() => authApi.changePassword(current.value, next.value));
  if (ok) {
    current.value = next.value = repeat.value = '';
    logout();
    notify('密码已更新，请用新密码登录');
  }
}
</script>

<template>
  <PageHero />
  <div class="cols section">
    <form class="c6" @submit.prevent="submit">
      <label class="field"
        >当前密码 <span class="req">*</span>
        <input
          v-model="current"
          type="password"
          required
          autocomplete="current-password"
          maxlength="72"
        />
      </label>
      <label class="field"
        >新密码 <span class="req">*</span>
        <input
          v-model="next"
          type="password"
          required
          minlength="8"
          maxlength="72"
          autocomplete="new-password"
        />
        <small>至少 8 位，大小写字母、数字、符号中至少包含三类。</small>
      </label>
      <label class="field"
        >确认新密码 <span class="req">*</span>
        <input
          v-model="repeat"
          type="password"
          required
          minlength="8"
          maxlength="72"
          autocomplete="new-password"
        />
      </label>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <button class="btn" style="margin-top: 26px">更新密码</button>
    </form>
  </div>
</template>
