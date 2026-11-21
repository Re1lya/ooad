<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { importsApi } from '@/api/imports';
import type { ImportSummary } from '@/api/types';
import PageHero from '@/components/PageHero.vue';
import { act } from '@/stores/ui';

/** 员工信息同步：三份来源表先预览校验，再凭校验摘要确认写入；可重复执行，已有账号的密码、状态与授权保留。 */
const slots = [
  { key: 'employees', label: '员工信息表', detail: '姓名、用户名、分公司、部门、岗位、电话号码' },
  { key: 'departments', label: '部门信息表', detail: '部门名称、部门经理、上级部门' },
  { key: 'permissions', label: '功能权限清单', detail: '软件系统、功能模块、功能点及各岗位授权' },
] as const;

const files = reactive<Record<string, File | null>>({
  employees: null,
  departments: null,
  permissions: null,
});
const preview = ref<ImportSummary | null>(null);
const result = ref<ImportSummary | null>(null);
const ready = computed(() => Object.values(files).every(Boolean));
const shown = computed(() => result.value ?? preview.value);

function choose(key: string, event: Event) {
  files[key] = (event.target as HTMLInputElement).files?.[0] ?? null;
  preview.value = null;
  result.value = null;
}

function form() {
  const data = new FormData();
  for (const [key, file] of Object.entries(files)) if (file) data.append(key, file);
  return data;
}

async function inspect() {
  result.value = null;
  await act(async () => (preview.value = await importsApi.preview(form())));
}

async function commit() {
  const digest = preview.value?.digest;
  if (!digest) return;
  const data = form();
  data.append('digest', digest);
  await act(async () => {
    result.value = await importsApi.commit(data);
    preview.value = null;
  }, '员工信息已同步');
}

const counts = [
  ['employees', '员工'],
  ['departments', '部门'],
  ['systems', '应用'],
  ['modules', '模块'],
  ['functions', '功能点'],
  ['roleTemplates', '角色模板'],
] as const;
</script>

<template>
  <PageHero
    title="导入员工信息表"
    caption="先校验，再确认写入。同一批数据可以重复导入：已有账号的密码、启用状态和授权都会保留，新账号默认停用。"
  />
  <div class="slots">
    <label
      v-for="(slot, index) in slots"
      :key="slot.key"
      :class="['slot', { chosen: files[slot.key] }]"
    >
      <span class="no">{{ String(index + 1).padStart(2, '0') }}</span>
      <strong>{{ slot.label }}</strong>
      <small>{{ slot.detail }}</small>
      <input
        type="file"
        accept=".xlsx"
        :aria-label="slot.label"
        @change="choose(slot.key, $event)"
      />
    </label>
  </div>
  <div class="tools">
    <span class="grow mute">只支持指定模板的 .xlsx 文件，每份不超过 8 MB。</span>
    <button class="btn ghost" :disabled="!ready" @click="inspect">预览并校验</button>
    <button class="btn" :disabled="!preview" @click="commit">确认导入</button>
  </div>

  <section class="section">
    <div class="label">
      <h2>{{ result ? '导入已完成' : preview ? '校验通过，可以导入' : '等待校验' }}</h2>
      <RouterLink v-if="result" to="/users">查看员工 →</RouterLink>
    </div>
    <template v-if="shown">
      <div class="figures">
        <div v-for="[key, text] in counts" :key="key">
          <b>{{ shown[key].toLocaleString('zh-CN') }}</b><span>{{ text }}</span>
        </div>
      </div>
      <p class="hint">
        已按分公司补全 {{ shown.qualifiedDepartments.toLocaleString('zh-CN') }} 条员工部门简称。
      </p>
      <ul class="notes">
        <li v-for="note in shown.notes" :key="note">{{ note }}</li>
      </ul>
    </template>
    <p v-else class="none">选择三份文件并点击「预览并校验」。校验通过前不会写入任何数据。</p>
  </section>
</template>
