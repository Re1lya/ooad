<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { ApiError, StaleSessionError } from '@/api/http';
import { notify, ui } from '@/stores/ui';

/**
 * 表单对话框：提交期间禁止重复提交；失败时把错误留在对话框内，成功后关闭并提示。
 */
const props = defineProps<{
  open: boolean;
  title: string;
  description?: string;
  submitLabel: string;
  submit: () => Promise<unknown>;
  success?: string;
  /** true：提交成功后保持打开（例如逐个添加关联）。 */
  keepOpen?: boolean;
  /** true：只读查看，不显示提交按钮。 */
  readonly?: boolean;
}>();
const emit = defineEmits<{ close: []; done: [] }>();

const dialog = ref<HTMLDialogElement | null>(null);
const error = ref('');

watch(
  () => props.open,
  async (open) => {
    await nextTick();
    error.value = '';
    if (open && !dialog.value?.open) dialog.value?.showModal();
    if (!open && dialog.value?.open) dialog.value.close();
  },
  { immediate: true },
);

async function handle() {
  if (ui.writing) return;
  error.value = '';
  ui.writing = true;
  ui.pending++;
  try {
    await props.submit();
    emit('done');
    if (!props.keepOpen) emit('close');
    if (props.success) notify(props.success);
  } catch (e) {
    if (!(e instanceof StaleSessionError))
      error.value = e instanceof ApiError || e instanceof Error ? e.message : String(e);
  } finally {
    ui.writing = false;
    ui.pending--;
  }
}
</script>

<template>
  <dialog ref="dialog" class="dlg" aria-labelledby="form-title" @cancel.prevent="emit('close')">
    <form v-if="open" @submit.prevent="handle">
      <h2 id="form-title">{{ title }}</h2>
      <p v-if="description" class="dlg-desc">{{ description }}</p>
      <slot />
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <div class="dlg-actions">
        <button class="btn ghost" type="button" :disabled="ui.writing" @click="emit('close')">
          {{ readonly ? '关闭' : '取消' }}
        </button>
        <button v-if="!readonly" class="btn" :disabled="ui.writing">
          {{ ui.writing ? '正在保存…' : submitLabel }}
        </button>
      </div>
    </form>
  </dialog>
</template>
