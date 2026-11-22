<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { auditApi } from '@/api/audit';
import type { AuditEntry } from '@/api/types';
import Figures from '@/components/Figures.vue';
import PageHero from '@/components/PageHero.vue';
import Pager from '@/components/Pager.vue';
import { catalog, load } from '@/stores/catalog';
import { SELF_SYSTEM } from '@/stores/session';
import { track } from '@/stores/ui';
import { auditTime, auditTitle } from '@/utils/format';

const SIZE = 20;
const system = ref(SELF_SYSTEM);
const page = ref(0);
const total = ref(0);
const items = ref<AuditEntry[]>([]);
const target = ref('');

async function refresh() {
  const result = await track(
    auditApi.list(system.value, page.value + 1, SIZE, target.value.trim() || undefined),
  );
  if (!result) return;
  items.value = result.items;
  total.value = result.total;
}

onMounted(() => {
  void load(['systems']).catch(() => undefined);
  void refresh();
});

watch(system, () => {
  page.value = 0;
  void refresh();
});
let timer: ReturnType<typeof setTimeout> | undefined;
watch(target, () => {
  clearTimeout(timer);
  timer = setTimeout(() => {
    page.value = 0;
    void refresh();
  }, 300);
});

function go(next: number) {
  page.value = next;
  void refresh();
}

const actorRoles = (entry: AuditEntry) => entry.actor?.roles?.join('、') ?? '';
</script>

<template>
  <PageHero />
  <Figures :items="[{ label: '记录', value: total.toLocaleString('zh-CN') }]" />
  <div class="tools">
    <input
      v-model="target"
      class="grow"
      type="search"
      placeholder="按操作对象查找"
      aria-label="搜索当前列表"
    />
    <div class="filters" role="group" aria-label="应用">
      <button
        v-for="s in catalog.systems ?? [{ id: 0, code: SELF_SYSTEM, name: '权限管理系统' }]"
        :key="s.code"
        :class="['textbtn', { on: system === s.code }]"
        :aria-pressed="system === s.code"
        @click="system = s.code"
      >
        {{ s.name }}
      </button>
    </div>
  </div>
  <div class="table-wrap">
    <table class="t">
      <thead>
        <tr>
          <th>时间</th>
          <th>操作人</th>
          <th>操作</th>
          <th>对象</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="entry in items" :key="entry.id">
          <td class="mute" style="white-space: nowrap">{{ auditTime(entry.at) }}</td>
          <td>
            <span class="nm">{{ entry.actor?.name ?? '系统' }}</span>
            <span v-if="actorRoles(entry)" class="sub">{{ actorRoles(entry) }}</span>
          </td>
          <td>{{ auditTitle(entry) }}</td>
          <td>{{ entry.target ?? '—' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-if="!items.length" class="empty">
    <h3>{{ target ? '没有匹配的记录' : '还没有记录' }}</h3>
    <p>关键操作完成后会出现在这里。</p>
  </div>
  <Pager :page="page" :size="SIZE" :total="total" unit="条" @go="go" />
</template>
