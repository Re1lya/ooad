<template>
  <router-view />
  <ToastHost />
  <ConfirmHost />
</template>

<script setup lang="ts">
import ConfirmHost from '@/components/ConfirmHost.vue';
import ToastHost from '@/components/ToastHost.vue';
</script>
