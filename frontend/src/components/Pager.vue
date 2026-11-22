<script setup lang="ts">
import { computed } from 'vue';

/** page 从 0 开始。 */
const props = defineProps<{ page: number; size: number; total: number; unit?: string }>();
const emit = defineEmits<{ go: [page: number] }>();
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)));
</script>

<template>
  <div class="pager">
    <span>共 {{ total.toLocaleString('zh-CN') }} {{ unit ?? '条' }}，每页 {{ size }}</span>
    <span>
      <b>{{ page + 1 }}</b> / {{ pages }}
      <button class="textbtn" :disabled="page === 0" @click="emit('go', page - 1)">上一页</button>
      <button class="textbtn" :disabled="page + 1 >= pages" @click="emit('go', page + 1)">
        下一页
      </button>
    </span>
  </div>
</template>
