<template>
  <div>
    <PageHead title="员工同步" subtitle="按员工信息表入册：先预览变动，凭摘要一次性确认写入" seal="册" />

    <div class="panel mb16">
      <div class="panel__body">
        <div class="sync-grid">
          <!-- 选表 -->
          <label class="drop" :class="{ 'drop--on': !!file }">
            <input type="file" accept=".xlsx" hidden @change="onFile" />
            <span class="drop__glyph t-title">{{ file ? '已' : '选' }}</span>
            <span class="grow">
              <b>{{ file ? file.name : '选择员工信息表' }}</b>
              <span class="faint small" style="display: block">
                {{ file ? '换一张直接重选' : '.xlsx，表头为姓名、用户名、分公司、部门、岗位、电话号码' }}
              </span>
            </span>
          </label>

          <!-- 规则 -->
          <div>
            <div class="field">
              <label>总公司名称 <b>*</b></label>
              <input v-model.trim="rule.headOffice" class="input" placeholder="如 总公司" @input="reset" />
            </div>
            <div class="grid2">
              <div class="field">
                <label>总公司员工补授角色</label>
                <select v-model="rule.headOfficeRole" class="select" @change="reset">
                  <option value="">不补授</option>
                  <option v-for="r in roles" :key="r.id" :value="r.code">{{ r.name }}</option>
                </select>
              </div>
              <div class="field">
                <label>分公司员工补授角色</label>
                <select v-model="rule.branchRole" class="select" @change="reset">
                  <option value="">不补授</option>
                  <option v-for="r in roles" :key="r.id" :value="r.code">{{ r.name }}</option>
                </select>
              </div>
            </div>
          </div>
        </div>

        <p class="notice notice--tip" style="margin: 0 0 14px">
          按用户名（工号）入册，建立“分公司 / 部门”两级部门，总公司员工与分公司员工分别补授所选角色。新人员默认停用、不设密码；
          已在册的人只更新姓名、部门、岗位与电话，密码、启停状态和已有角色都不动。同一张表重复同步不会有变化。
        </p>
        <div class="row" style="justify-content: flex-end">
          <button class="btn btn--seal" :disabled="!file || !rule.headOffice || busy" @click="preview">
            {{ busy ? '清点中…' : '预览变动' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 预览结果 -->
    <div v-if="summary" class="panel rise">
      <div class="panel__head">
        <span class="panel__title">{{ summary.applied ? '写入完成' : '预览变动' }}</span>
        <span class="chip mono">摘要 {{ summary.digest.slice(0, 12) }}…</span>
      </div>
      <div class="panel__body">
        <div class="sum-grid">
          <div v-for="item in sumItems" :key="item.label" class="sum" :class="{ 'sum--hot': item.hot }">
            <span class="sum__num t-title">{{ item.value }}</span>
            <span class="sum__label">{{ item.label }}</span>
          </div>
        </div>
        <div class="row mt16" style="justify-content: flex-end">
          <button v-if="!summary.applied" class="btn btn--seal" :disabled="busy" @click="commit">
            {{ busy ? '写入中…' : '确认写入' }}
          </button>
          <span v-else class="stamp stamp--on">台账已更新，可以重复执行</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { directoryApi, rolesApi } from '@/api';
import type { RoleView, SyncSummary } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import { toast } from '@/stores/toast';

const file = ref<File | null>(null);
const roles = ref<RoleView[]>([]);
const rule = reactive({ headOffice: '总公司', headOfficeRole: '', branchRole: '' });
const summary = ref<SyncSummary | null>(null);
const busy = ref(false);

const sumItems = computed(() => {
  const s = summary.value;
  if (!s) return [];
  return [
    { label: '总行数', value: s.rows },
    { label: '公司数', value: s.companies },
    { label: '新建部门', value: s.departmentsCreated, hot: s.departmentsCreated > 0 },
    { label: '保留部门', value: s.departmentsKept },
    { label: '新建用户', value: s.usersCreated, hot: s.usersCreated > 0 },
    { label: '更新用户', value: s.usersUpdated, hot: s.usersUpdated > 0 },
    { label: '未变动', value: s.usersUnchanged },
    { label: '补授角色', value: s.roleGrants, hot: s.roleGrants > 0 },
  ];
});

function form(digest?: string): FormData {
  const data = new FormData();
  if (file.value) data.append('employees', file.value);
  data.append('headOffice', rule.headOffice);
  if (rule.headOfficeRole) data.append('headOfficeRole', rule.headOfficeRole);
  if (rule.branchRole) data.append('branchRole', rule.branchRole);
  if (digest) data.append('digest', digest);
  return data;
}

function onFile(e: Event): void {
  file.value = (e.target as HTMLInputElement).files?.[0] ?? null;
  reset();
}

function reset(): void {
  summary.value = null;
}

async function preview(): Promise<void> {
  busy.value = true;
  try {
    summary.value = await directoryApi.preview(form());
    toast.ok('清点完毕，核对后确认写入');
  } catch (e) {
    toast.fail(e);
  } finally {
    busy.value = false;
  }
}

async function commit(): Promise<void> {
  const digest = summary.value?.digest;
  if (!digest) return;
  busy.value = true;
  try {
    summary.value = await directoryApi.commit(form(digest));
    toast.ok('员工信息已入册');
  } catch (e) {
    toast.fail(e);
  } finally {
    busy.value = false;
  }
}

onMounted(async () => {
  try {
    roles.value = await rolesApi.list();
  } catch {
    /* 没有角色列表权限时只能“不补授” */
  }
});
</script>

<style scoped>
.sync-grid {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 20px;
  margin-bottom: 16px;
}

@media (max-width: 820px) {
  .sync-grid {
    grid-template-columns: 1fr;
  }
}

.drop {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border: 1.5px dashed var(--line-strong);
  border-radius: var(--radius);
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
  height: 100%;
}

.drop:hover {
  border-color: var(--ink-soft);
}

.drop--on {
  border-style: solid;
  border-color: var(--cinnabar);
  background: var(--cinnabar-wash);
}

.drop__glyph {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  border: 1.5px solid var(--line-strong);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--ink-soft);
  flex: none;
}

.drop--on .drop__glyph {
  background: var(--cinnabar);
  border-color: var(--cinnabar);
  color: #fdf7ef;
}

.sum-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 12px;
}

.sum {
  text-align: center;
  padding: 14px 6px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.sum--hot {
  border-color: var(--cinnabar);
  background: var(--cinnabar-wash);
}

.sum__num {
  display: block;
  font-size: 28px;
}

.sum__label {
  font-size: 12px;
  color: var(--ink-soft);
}
</style>
