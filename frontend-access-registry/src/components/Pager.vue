<template>
  <div class="pager">
    <span class="num">共 {{ total }} 条 · 第 {{ page }}/{{ pages }} 页</span>
    <button class="btn btn--sm" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
    <button class="btn btn--sm" :disabled="page >= pages" @click="go(page + 1)">下一页</button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{ page: number; pageSize: number; total: number }>();
const emit = defineEmits<{ change: [page: number] }>();

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));

function go(page: number): void {
  if (page >= 1 && page <= pages.value && page !== props.page) emit('change', page);
}
</script>
