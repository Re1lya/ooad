<template>
  <div class="empty">
    <span class="empty__glyph">{{ glyph }}</span>
    <div>{{ text }}</div>
    <div v-if="hint" class="small mt8">{{ hint }}</div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ glyph?: string; text: string; hint?: string }>(), {
  glyph: '空',
  hint: '',
});
</script>
