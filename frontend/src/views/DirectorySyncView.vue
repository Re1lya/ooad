<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { directoryApi } from '@/api/directory';
import type { SyncSummary } from '@/api/types';
import PageHero from '@/components/PageHero.vue';
import { catalog, load } from '@/stores/catalog';
import { act, track } from '@/stores/ui';

/**
 * 员工信息同步：上传员工信息表，选择总公司与两类员工补授的角色，先预览变化，再凭预览摘要确认写入。
 * 可以重复执行；已有账号的密码、启用状态与已有角色都会保留，新账号默认停用。
 */
const file = ref<File | null>(null);
const rule = reactive({ headOffice: '总公司', headOfficeRole: '', branchRole: '' });
const preview = ref<SyncSummary | null>(null);
const result = ref<SyncSummary | null>(null);
const shown = computed(() => result.value ?? preview.value);
const roles = computed(() => (catalog.roles ?? []).filter((r) => r.enabled));

onMounted(() => void track(load(['roles'])));

/** 更换文件或修改规则后必须重新预览。 */
function reset() {
  preview.value = null;
  result.value = null;
}

function choose(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  reset();
}

function form(digest?: string) {
  const data = new FormData();
  if (file.value) data.append('employees', file.value);
  data.append('headOffice', rule.headOffice.trim());
  if (rule.headOfficeRole) data.append('headOfficeRole', rule.headOfficeRole);
  if (rule.branchRole) data.append('branchRole', rule.branchRole);
  if (digest) data.append('digest', digest);
  return data;
}

async function inspect() {
  result.value = null;
  await act(async () => (preview.value = await directoryApi.preview(form())));
}

async function commit() {
  const digest = preview.value?.digest;
  if (!digest) return;
  await act(async () => {
    result.value = await directoryApi.commit(form(digest));
    preview.value = null;
  }, '员工信息已同步');
}

const counts = [
  ['rows', '员工'],
  ['companies', '分公司'],
  ['departmentsCreated', '新建部门'],
  ['usersCreated', '新建人员'],
  ['usersUpdated', '更新人员'],
  ['roleGrants', '补授角色'],
] as const;
</script>

<template>
  <PageHero
    title="同步员工信息"
    caption="先预览，再确认写入，可以重复执行。分公司与部门建成两级部门树；已有人员的密码、启用状态和角色都会保留，新人员默认停用。"
  />
  <div class="slots">
    <label :class="['slot', { chosen: file }]">
      <span class="no">01</span>
      <strong>员工信息表</strong>
      <small>表头：姓名、用户名、分公司、部门、岗位、电话号码</small>
      <input type="file" accept=".xlsx" aria-label="员工信息表" @change="choose" />
    </label>
    <label :class="['slot', { chosen: rule.headOffice.trim() }]">
      <span class="no">02</span>
      <strong>总公司</strong>
      <small>与员工表“分公司”一列中的总公司名称一致</small>
      <input
        v-model="rule.headOffice"
        class="line-input"
        maxlength="128"
        aria-label="总公司名称"
        @input="reset"
      />
    </label>
    <div :class="['slot', { chosen: rule.headOfficeRole || rule.branchRole }]">
      <span class="no">03</span>
      <strong>补授角色</strong>
      <small>只增不减；不选则不授予</small>
      <label class="rule"
        >总公司员工
        <select v-model="rule.headOfficeRole" aria-label="总公司员工补授角色" @change="reset">
          <option value="">不授予</option>
          <option v-for="r in roles" :key="r.id" :value="r.code">{{ r.name }}</option>
        </select>
      </label>
      <label class="rule"
        >其他分公司员工
        <select v-model="rule.branchRole" aria-label="分公司员工补授角色" @change="reset">
          <option value="">不授予</option>
          <option v-for="r in roles" :key="r.id" :value="r.code">{{ r.name }}</option>
        </select>
      </label>
    </div>
  </div>
  <div class="tools">
    <span class="grow mute">只支持 .xlsx 文件，不超过 8 MB、20000 行。</span>
    <button class="btn ghost" :disabled="!file || !rule.headOffice.trim()" @click="inspect">
      预览变化
    </button>
    <button class="btn" :disabled="!preview" @click="commit">确认同步</button>
  </div>

  <section class="section">
    <div class="label">
      <h2>{{ result ? '同步已完成' : preview ? '预览完成，可以确认同步' : '等待预览' }}</h2>
      <RouterLink v-if="result" to="/users">查看员工 →</RouterLink>
    </div>
    <template v-if="shown">
      <div class="figures">
        <div v-for="[key, text] in counts" :key="key">
          <b>{{ shown[key].toLocaleString('zh-CN') }}</b><span>{{ text }}</span>
        </div>
      </div>
      <p class="hint">
        已有部门 {{ shown.departmentsKept.toLocaleString('zh-CN') }} 个保持不变，
        {{ shown.usersUnchanged.toLocaleString('zh-CN') }} 名人员没有变化。
      </p>
      <ul class="notes">
        <li>新人员没有可用密码；管理员设置密码并启用后才能登录。</li>
        <li>不在表中的人员不受影响；已经授予的角色不会因为调动而撤销。</li>
      </ul>
    </template>
    <p v-else class="none">选择员工信息表并点击「预览变化」。确认之前不会写入任何数据。</p>
  </section>
</template>

<style scoped>
.line-input {
  width: 100%;
  border: 0;
  border-bottom: 2px solid var(--ink);
  padding: 6px 0;
  font: inherit;
  font-size: 15px;
  background: transparent;
  outline: none;
}
.line-input:focus {
  border-bottom-color: var(--red);
}
.rule {
  display: grid;
  grid-template-columns: 7em 1fr;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  margin-top: 6px;
}
.rule select {
  width: 100%;
}
</style>
