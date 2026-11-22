<script setup lang="ts">
import { clearNotice, ui } from '@/stores/ui';
</script>

<template>
  <div
    v-if="ui.notice && !ui.confirm"
    :class="['toast', { error: ui.notice.error }]"
    :role="ui.notice.error ? 'alert' : 'status'"
  >
    <span>{{ ui.notice.text }}</span>
    <button type="button" aria-label="关闭提示" @click="clearNotice()">×</button>
  </div>
</template>
