<script setup lang="ts">
import { watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from '@/components/AppShell.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import NoticeBar from '@/components/NoticeBar.vue';
import { session } from '@/stores/session';
import { ui } from '@/stores/ui';

const route = useRoute();
const router = useRouter();

// 任何原因退出（主动退出、令牌失效）都回到登录页。
watch(
  () => session.token,
  (token) => {
    if (!token && route.name !== 'login') void router.replace({ name: 'login' });
  },
);
</script>

<template>
  <div v-if="ui.pending > 0" class="progress" role="progressbar" aria-label="正在处理请求"></div>
  <RouterView v-if="route.meta.public" />
  <AppShell v-else-if="session.me" />
  <ConfirmDialog />
  <NoticeBar />
</template>
