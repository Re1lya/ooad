<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { ui } from '@/stores/ui';

const dialog = ref<HTMLDialogElement | null>(null);

watch(
  () => ui.confirm,
  async (value) => {
    await nextTick();
    if (value && !dialog.value?.open) dialog.value?.showModal();
    if (!value && dialog.value?.open) dialog.value.close();
  },
);

function answer(ok: boolean) {
  ui.confirm?.resolve(ok);
}
</script>

<template>
  <dialog ref="dialog" class="dlg" aria-labelledby="confirm-title" @cancel.prevent="answer(false)">
    <div v-if="ui.confirm" class="dlg-body">
      <h2 id="confirm-title">{{ ui.confirm.title }}</h2>
      <p class="dlg-desc">{{ ui.confirm.description }}</p>
      <div class="dlg-actions">
        <button class="btn ghost" type="button" @click="answer(false)">取消</button>
        <button :class="['btn', { danger: ui.confirm.danger }]" type="button" @click="answer(true)">
          {{ ui.confirm.confirmLabel }}
        </button>
      </div>
    </div>
  </dialog>
</template>
