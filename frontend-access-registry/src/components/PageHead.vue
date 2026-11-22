<template>
  <header class="page-head">
    <div class="page-head__lead">
      <span class="seal">{{ sealText }}</span>
      <div>
        <h1 class="page-head__title t-title">{{ title }}</h1>
        <p v-if="subtitle" class="page-head__sub">{{ subtitle }}</p>
      </div>
    </div>
    <div class="row">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{ title: string; subtitle?: string; seal?: string }>();
const sealText = computed(() => props.seal || props.title.slice(0, 1));
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.page-head__lead {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.page-head__title {
  margin: 0;
  font-size: 24px;
  line-height: 1.3;
}

.page-head__sub {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--ink-soft);
}
</style>
