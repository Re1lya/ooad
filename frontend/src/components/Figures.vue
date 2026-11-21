<script setup lang="ts">
defineProps<{ items: { label: string; value: string | number; red?: boolean }[] }>();
</script>

<template>
  <div class="figures">
    <div v-for="item in items" :key="item.label">
      <b :class="{ r: item.red }">{{ item.value }}</b><span>{{ item.label }}</span>
    </div>
  </div>
</template>
