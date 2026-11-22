<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { pageByName, pageNumber } from '@/router/pages';

const props = defineProps<{ title?: string; caption?: string }>();
const route = useRoute();
const page = computed(() => pageByName(String(route.meta.page ?? route.name)));
</script>

<template>
  <header class="hero">
    <div class="num" aria-hidden="true">{{ pageNumber(page?.name ?? '') }}</div>
    <h1>{{ props.title ?? page?.title }}</h1>
    <p class="lede">{{ props.caption ?? page?.caption }}</p>
  </header>
</template>
