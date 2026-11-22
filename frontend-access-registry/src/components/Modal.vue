<template>
  <teleport to="body">
    <div v-if="open" class="veil" @mousedown.self="emit('close')">
      <div class="modal" :class="{ 'modal--wide': wide }" role="dialog" :aria-label="title">
        <div class="modal__head">
          <span class="seal seal--sm">{{ sealText }}</span>
          <div class="modal__title">{{ title }}</div>
          <button class="btn btn--ghost btn--sm" aria-label="关闭" @click="emit('close')">✕</button>
        </div>
        <div class="modal__body">
          <slot />
        </div>
        <div v-if="$slots.foot" class="modal__foot">
          <slot name="foot" />
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{ open: boolean; title: string; wide?: boolean; seal?: string }>(),
  { wide: false, seal: '' },
);
const emit = defineEmits<{ close: [] }>();

const sealText = computed(() => props.seal || props.title.slice(0, 1) || '印');
</script>
