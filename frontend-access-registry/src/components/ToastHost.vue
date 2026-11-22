<template>
  <teleport to="body">
    <div class="toast-rail" aria-live="polite">
      <transition-group name="toast">
        <div v-for="t in toasts" :key="t.id" class="toast" :class="`toast--${t.kind}`">
          <span class="toast__seal">{{ t.kind === 'ok' ? '讫' : '误' }}</span>
          <span>{{ t.text }}</span>
        </div>
      </transition-group>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { toasts } from '@/stores/toast';
</script>

<style scoped>
.toast-rail {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 14px 9px 9px;
  background: var(--paper-raised);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow-pop);
  font-size: 13px;
  max-width: 360px;
}

.toast__seal {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  font-family: var(--font-title);
  font-size: 12px;
  color: #fdf7ef;
  flex: none;
}

.toast--ok .toast__seal {
  background: var(--pine);
}

.toast--err .toast__seal {
  background: var(--cinnabar);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>
