<template>
  <button
    class="switch"
    role="switch"
    :aria-checked="modelValue"
    :disabled="disabled"
    :title="modelValue ? onText : offText"
    @click.stop="emit('update:modelValue', !modelValue); emit('change', !modelValue)"
  />
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{ modelValue: boolean; disabled?: boolean; onText?: string; offText?: string }>(),
  { disabled: false, onText: '已启用', offText: '已停用' },
);
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; change: [value: boolean] }>();
</script>
