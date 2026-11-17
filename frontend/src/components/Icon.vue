<script setup lang="ts">
defineProps<{name:string;size?:number}>();
const paths:Record<string,string>={
 overview:'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
 users:'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
 organization:'M9 3h6v5H9z M2 16h6v5H2z M16 16h6v5h-6z M12 8v4 M5 16v-4h14v4',
 grid:'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
 shield:'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7z M8 12l3 3 5-6',
 layers:'m12 3 10 6-10 6L2 9z M2 13l10 6 10-6 M2 17l10 6 10-6',
 code:'m8 7-5 5 5 5 M16 7l5 5-5 5 M14 4l-4 16',
 hierarchy:'M3 3h7v6H3z M14 15h7v6h-7z M6 9v9h8 M14 3h7v6h-7z',
 link:'M10 13a5 5 0 0 0 7 .3l3-3a5 5 0 0 0-7-7l-2 2 M14 11a5 5 0 0 0-7-.3l-3 3a5 5 0 0 0 7 7l2-2',
 key:'M8 15a5 5 0 1 1 4-8l9 9-3 3-2-2-2 2-4-4z',lock:'M5 10h14v11H5z M8 10V7a4 4 0 0 1 8 0v3 M12 14v3',
 search:'M21 21l-5-5 M18 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0',plus:'M12 5v14 M5 12h14',
 arrow:'M5 12h14 M14 7l5 5-5 5',chevron:'m9 6 6 6-6 6',refresh:'M20 7a9 9 0 1 0 1 9 M20 3v5h-5',
 close:'m6 6 12 12 M6 18 18 6',logout:'M9 3H3v18h6 M8 12h13 M16 7l5 5-5 5',check:'m5 12 4 4L19 6',
 edit:'m16 3 5 5L8 21H3v-5z M14 5l5 5',trash:'M3 6h18 M9 6V3h6v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7',
 info:'M12 11v6 M12 7v.1 M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',menu:'M3 6h18 M3 12h18 M3 18h18',
 eye:'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0'
};
</script>
<template><svg :width="size||18" :height="size||18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name]||paths.grid"/></svg></template>
