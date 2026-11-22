<template>
  <Modal :open="confirmState.open" :title="confirmState.title" seal="慎" @close="settleConfirm(false)">
    <p class="confirm-text">{{ confirmState.text }}</p>
    <template #foot>
      <button class="btn" @click="settleConfirm(false)">取消</button>
      <button class="btn btn--seal" @click="settleConfirm(true)">{{ confirmState.ok }}</button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import Modal from '@/components/Modal.vue';
import { confirmState, settleConfirm } from '@/stores/confirm';
</script>

<style scoped>
.confirm-text {
  margin: 0;
  line-height: 1.7;
  color: var(--ink);
}
</style>
