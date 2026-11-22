<template>
  <div>
    <PageHead title="审计日志" subtitle="各接入应用报上来的操作记录" seal="察">
      <template #actions>
        <select v-model="system" class="select" style="width: 190px" @change="reload(1)">
          <option value="">全部来源</option>
          <option v-for="s in systems" :key="s.code" :value="s.code">{{ s.name }}</option>
        </select>
        <input v-model.trim="target" class="input" style="width: 200px" placeholder="搜操作目标…" @keyup.enter="reload(1)" />
        <button class="btn" @click="reload(1)">查</button>
      </template>
    </PageHead>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th>时间</th>
            <th>来源</th>
            <th>操作人</th>
            <th>类型</th>
            <th>对象</th>
            <th>目标</th>
            <th style="width: 80px; text-align: right">明细</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in page.items" :key="a.id">
            <td class="num muted">{{ fmtTime(a.at) }}</td>
            <td><span class="chip mono">{{ a.systemCode }}</span></td>
            <td>
              <b>{{ a.actor.name }}</b>
              <span v-if="a.actor.deleted" class="faint small">（已注销）</span>
              <div class="faint small">{{ a.actor.roles.join('、') || '—' }}</div>
            </td>
            <td><span class="chip">{{ a.type }}</span></td>
            <td class="muted small">{{ a.objectType }} #{{ a.objectId }}</td>
            <td class="small">{{ a.target }}</td>
            <td style="text-align: right">
              <button class="btn btn--ghost btn--sm" @click="detail = a">看</button>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!page.items.length" glyph="察" text="没有审计记录" hint="接入应用报上来的操作会记在这里" />
      <Pager :page="page.page" :page-size="page.pageSize" :total="page.total" @change="reload" />
    </div>

    <Modal :open="!!detail" title="操作明细" wide @close="detail = null">
      <template v-if="detail">
        <div class="detail-meta small muted mb16">
          <span class="chip mono">{{ detail.systemCode }}</span>
          {{ fmtTime(detail.at) }} · {{ detail.actor.name }} · {{ detail.type }}
        </div>
        <div class="diff-grid">
          <div>
            <div class="diff-label">改动前</div>
            <pre class="diff mono">{{ pretty(detail.before) }}</pre>
          </div>
          <div>
            <div class="diff-label">改动后</div>
            <pre class="diff mono">{{ pretty(detail.after) }}</pre>
          </div>
        </div>
      </template>
      <template #foot>
        <button class="btn" @click="detail = null">合上</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { auditApi, systemsApi } from '@/api';
import type { AuditEntry, AuditPage, SystemView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Modal from '@/components/Modal.vue';
import Pager from '@/components/Pager.vue';
import EmptyState from '@/components/EmptyState.vue';
import { toast } from '@/stores/toast';

const page = ref<AuditPage>({ page: 1, pageSize: 20, total: 0, items: [] });
const systems = ref<SystemView[]>([]);
const system = ref('');
const target = ref('');
const detail = ref<AuditEntry | null>(null);

function fmtTime(at: string): string {
  return at ? at.replace('T', ' ').slice(0, 19) : '—';
}

function pretty(obj: Record<string, unknown> | null): string {
  return obj ? JSON.stringify(obj, null, 2) : '（无）';
}

async function reload(toPage = page.value.page): Promise<void> {
  try {
    page.value = await auditApi.page({
      system: system.value || undefined,
      target: target.value || undefined,
      page: toPage,
      pageSize: page.value.pageSize,
    });
  } catch (e) {
    toast.fail(e);
  }
}

onMounted(async () => {
  void reload(1);
  try {
    systems.value = await systemsApi.list();
  } catch (e) {
    toast.fail(e);
  }
});
</script>

<style scoped>
.diff-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

@media (max-width: 700px) {
  .diff-grid {
    grid-template-columns: 1fr;
  }
}

.diff-label {
  font-size: 12.5px;
  color: var(--ink-faint);
  margin-bottom: 6px;
}

.diff {
  margin: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--paper-sunken);
  font-size: 12px;
  line-height: 1.5;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
