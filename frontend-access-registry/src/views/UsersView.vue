<template>
  <div>
    <PageHead title="用户" subtitle="在册人员台账：登记、改资料、启停与角色授权" seal="员">
      <template #actions>
        <input v-model.trim="keyword" class="input" style="width: 220px" placeholder="搜账号或姓名…" @keyup.enter="search" />
        <button class="btn" @click="reload(1)">查</button>
        <button v-if="writable" class="btn btn--seal" @click="openCreate">登记用户</button>
      </template>
    </PageHead>

    <div class="panel">
      <table class="ledger">
        <thead>
          <tr>
            <th style="width: 56px">编号</th>
            <th>账号</th>
            <th>姓名</th>
            <th>部门</th>
            <th>身份</th>
            <th>有效期至</th>
            <th>状态</th>
            <th style="width: 220px; text-align: right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in page.items" :key="u.id">
            <td class="num">{{ u.id }}</td>
            <td class="mono">{{ u.username }}</td>
            <td>
              <b>{{ u.displayName }}</b>
              <span v-if="u.administrator" class="chip chip--hot" style="margin-left: 6px">管理员</span>
              <span v-if="isSelf(u)" class="chip" style="margin-left: 6px">本人</span>
            </td>
            <td class="muted">{{ departmentLabel(u.departmentId) }}</td>
            <td class="muted small">{{ personTypeText(u.personType) }}</td>
            <td class="num muted">{{ u.expiresAt ? u.expiresAt.slice(0, 10) : '长期' }}</td>
            <td>
              <Toggle
                v-if="writable && !isSelf(u)"
                :model-value="u.enabled"
                :disabled="busy"
                @change="(v) => toggle(u, v)"
              />
              <span v-else class="stamp" :class="u.enabled ? 'stamp--on' : ''">{{ u.enabled ? '启用' : '停用' }}</span>
            </td>
            <td style="text-align: right; white-space: nowrap">
              <template v-if="writable">
                <button class="btn btn--ghost btn--sm" @click="openEdit(u)">改</button>
                <button class="btn btn--ghost btn--sm" @click="openPassword(u)">密</button>
              </template>
              <router-link v-if="canGrant" class="btn btn--ghost btn--sm" :to="`/grants?userId=${u.id}`">授权</router-link>
              <button v-if="writable && !isSelf(u)" class="btn btn--danger-ghost btn--sm" @click="remove(u)">销</button>
            </td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-if="!loading && !page.items.length" glyph="无" text="没有查到用户" hint="换个关键词，或登记新用户" />
      <Pager :page="page.page" :page-size="page.pageSize" :total="page.total" @change="reload" />
    </div>

    <!-- 登记 / 编辑 -->
    <Modal :open="editor.open" :title="editor.id ? '修改资料' : '登记用户'" @close="editor.open = false">
      <div class="grid2">
        <div class="field">
          <label>账号 <b v-if="!editor.id">*</b></label>
          <input v-model.trim="editor.form.username" class="input mono" :disabled="!!editor.id" placeholder="工号或学号" />
        </div>
        <div class="field">
          <label>姓名 <b>*</b></label>
          <input v-model.trim="editor.form.displayName" class="input" placeholder="显示姓名" />
        </div>
        <div v-if="!editor.id" class="field">
          <label>初始密码 <b>*</b></label>
          <input v-model="editor.form.password" class="input" type="password" placeholder="符合密码策略" />
        </div>
        <div class="field">
          <label>身份类型</label>
          <select v-model="editor.form.personType" class="select" @change="onTypeChange">
            <option v-for="t in PERSON_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
        <div class="field" :style="editor.id ? '' : 'grid-column: 1 / -1'">
          <label>所属部门</label>
          <select v-model="editor.form.departmentId" class="select">
            <option :value="null">暂不分配</option>
            <option v-for="d in deptOptions" :key="d.id" :value="d.id">{{ d.label }}</option>
          </select>
        </div>
        <div v-if="editor.form.personType !== 'EMPLOYEE'" class="field">
          <label>有效期至 <b>*</b></label>
          <input v-model="editor.form.expiresOn" class="input" type="date" :min="today" />
        </div>
      </div>
      <p class="notice notice--tip" style="margin: 4px 0 0">
        正式员工长期有效；外部、临时人员必须设有效期，到期后不能登录，最长天数以后端策略为准。
      </p>
      <template #foot>
        <button class="btn" @click="editor.open = false">取消</button>
        <button class="btn btn--seal" :disabled="busy || !canSave" @click="save">落账</button>
      </template>
    </Modal>

    <!-- 设密码 -->
    <Modal :open="pwd.open" :title="`为 ${pwd.name} 重设密码`" @close="pwd.open = false">
      <div class="field">
        <label>新密码 <b>*</b></label>
        <input v-model="pwd.password" class="input" type="password" placeholder="符合密码策略" />
      </div>
      <p class="notice notice--tip" style="margin: 0">重设后，此人已登录的会话随即失效，需要用新密码重新登录。</p>
      <template #foot>
        <button class="btn" @click="pwd.open = false">取消</button>
        <button class="btn btn--seal" :disabled="busy || !pwd.password" @click="savePassword">重设</button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { departmentsApi, usersApi } from '@/api';
import type { DepartmentView, PersonType, UserPage, UserView } from '@/api/types';
import PageHead from '@/components/PageHead.vue';
import Modal from '@/components/Modal.vue';
import Toggle from '@/components/Toggle.vue';
import Pager from '@/components/Pager.vue';
import EmptyState from '@/components/EmptyState.vue';
import { can, canVisit, session } from '@/stores/session';
import { confirmDanger } from '@/stores/confirm';
import { toast } from '@/stores/toast';
import { departmentOptions } from '@/utils/departments';
import { PERSON_TYPES, personTypeText } from '@/utils/labels';

const page = ref<UserPage>({ page: 1, pageSize: 20, total: 0, items: [] });
const keyword = ref('');
const loading = ref(false);
const busy = ref(false);
const departments = ref<DepartmentView[]>([]);

/** 登记、改资料、启停、重设密码、注销都要“维护用户”按钮；给人分配角色也是。 */
const writable = computed(() => can('rbac.users.write'));
const canGrant = computed(() => canVisit('/grants'));
const deptOptions = computed(() => departmentOptions(departments.value));
const today = new Date().toISOString().slice(0, 10);

const editor = reactive({
  open: false,
  id: 0,
  form: {
    username: '',
    displayName: '',
    password: '',
    personType: 'EMPLOYEE' as PersonType,
    departmentId: null as number | null,
    expiresOn: '',
  },
});

const pwd = reactive({ open: false, id: 0, name: '', password: '' });

const canSave = computed(() => {
  const f = editor.form;
  if (!f.displayName) return false;
  if (!editor.id && (!f.username || !f.password)) return false;
  return f.personType === 'EMPLOYEE' || !!f.expiresOn;
});

function isSelf(u: UserView): boolean {
  return u.id === session.state.user?.id;
}

function departmentLabel(id: number | null): string {
  return deptOptions.value.find((d) => d.id === id)?.label ?? '—';
}

function onTypeChange(): void {
  // 正式员工不设有效期，后端会拒绝带期限的正式员工
  if (editor.form.personType === 'EMPLOYEE') editor.form.expiresOn = '';
}

async function reload(toPage = page.value.page): Promise<void> {
  loading.value = true;
  try {
    page.value = await usersApi.page(toPage, page.value.pageSize, keyword.value);
  } catch (e) {
    toast.fail(e);
  } finally {
    loading.value = false;
  }
}

function search(): void {
  void reload(1);
}

function openCreate(): void {
  editor.id = 0;
  editor.form = { username: '', displayName: '', password: '', personType: 'EMPLOYEE', departmentId: null, expiresOn: '' };
  editor.open = true;
}

function openEdit(u: UserView): void {
  editor.id = u.id;
  editor.form = {
    username: u.username,
    displayName: u.displayName,
    password: '',
    personType: u.personType ?? 'EMPLOYEE',
    departmentId: u.departmentId,
    expiresOn: u.expiresAt ? u.expiresAt.slice(0, 10) : '',
  };
  editor.open = true;
}

async function save(): Promise<void> {
  busy.value = true;
  try {
    const expiresOn = editor.form.personType === 'EMPLOYEE' ? null : editor.form.expiresOn || null;
    if (editor.id) {
      await usersApi.update(editor.id, {
        displayName: editor.form.displayName,
        departmentId: editor.form.departmentId,
        personType: editor.form.personType,
        expiresOn,
      });
      toast.ok('资料已更新');
    } else {
      await usersApi.create({ ...editor.form, expiresOn });
      toast.ok('用户已落账');
    }
    editor.open = false;
    void reload();
  } catch (e) {
    toast.fail(e);
  } finally {
    busy.value = false;
  }
}

async function toggle(u: UserView, enabled: boolean): Promise<void> {
  busy.value = true;
  try {
    await usersApi.status(u.id, enabled);
    toast.ok(enabled ? `已启用 ${u.displayName}` : `已停用 ${u.displayName}`);
    void reload();
  } catch (e) {
    toast.fail(e);
  } finally {
    busy.value = false;
  }
}

function openPassword(u: UserView): void {
  pwd.id = u.id;
  pwd.name = u.displayName;
  pwd.password = '';
  pwd.open = true;
}

async function savePassword(): Promise<void> {
  busy.value = true;
  try {
    await usersApi.setPassword(pwd.id, pwd.password);
    toast.ok('密码已重设');
    pwd.open = false;
  } catch (e) {
    toast.fail(e);
  } finally {
    busy.value = false;
  }
}

async function remove(u: UserView): Promise<void> {
  if (!(await confirmDanger('注销用户', `注销「${u.displayName}（${u.username}）」？此操作不可撤回。`, '注销'))) return;
  try {
    await usersApi.remove(u.id);
    toast.ok('用户已注销');
    void reload();
  } catch (e) {
    toast.fail(e);
  }
}

onMounted(async () => {
  void reload(1);
  try {
    departments.value = await departmentsApi.list();
  } catch (e) {
    toast.fail(e);
  }
});
</script>
