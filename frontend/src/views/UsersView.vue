<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { usersApi } from '@/api/users';
import type { PersonType, Role, User } from '@/api/types';
import Figures from '@/components/Figures.vue';
import FormDialog from '@/components/FormDialog.vue';
import PageHero from '@/components/PageHero.vue';
import Pager from '@/components/Pager.vue';
import { allowed } from '@/router';
import { catalog, departmentName, load } from '@/stores/catalog';
import { can } from '@/stores/session';
import { act, ask, track } from '@/stores/ui';
import { expiresLabel, personLabel, personTypes } from '@/utils/format';

const SIZE = 20;
const router = useRouter();
const page = ref(0);
const total = ref(0);
const rows = ref<User[]>([]);
const keyword = ref('');
const writable = computed(() => can('rbac.users.write'));

async function refresh() {
  const text = keyword.value.trim();
  const result = await track(usersApi.list(page.value + 1, SIZE, text || undefined));
  if (!result) return;
  rows.value = result.items;
  total.value = result.total;
}

onMounted(() => {
  void track(load(['departments']));
  void refresh();
});

let timer: ReturnType<typeof setTimeout> | undefined;
watch(keyword, () => {
  clearTimeout(timer);
  timer = setTimeout(() => {
    page.value = 0;
    void refresh();
  }, 300);
});

/** 当前页内再按关键字过滤一次，与数据库检索结果保持一致的显示。 */
const visible = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (!text) return rows.value;
  return rows.value.filter((u) =>
    [
      u.username,
      u.displayName,
      departmentName(u.departmentId),
      u.companyName ?? '',
      u.sourceDepartment ?? '',
    ]
      .join(' ')
      .toLowerCase()
      .includes(text),
  );
});

function go(next: number) {
  page.value = next;
  void refresh();
}

// ── 新建 / 编辑 ──────────────────────────────
const editor = reactive({
  open: false,
  id: null as number | null,
  username: '',
  displayName: '',
  password: '',
  departmentId: '' as string | number,
  personType: 'EMPLOYEE' as PersonType,
  expiresOn: '',
});

function openEditor(user?: User) {
  Object.assign(editor, {
    open: true,
    id: user?.id ?? null,
    username: user?.username ?? '',
    displayName: user?.displayName ?? '',
    password: '',
    departmentId: user?.departmentId ?? '',
    personType: user?.personType ?? 'EMPLOYEE',
    expiresOn: user?.expiresAt ? expiresLabel(user.expiresAt) : '',
  });
}

async function saveUser() {
  const departmentId = editor.departmentId === '' ? null : Number(editor.departmentId);
  const expiresOn = editor.personType === 'EMPLOYEE' || !editor.expiresOn ? null : editor.expiresOn;
  if (editor.id === null)
    await usersApi.create({
      username: editor.username.trim(),
      displayName: editor.displayName.trim(),
      password: editor.password,
      departmentId,
      personType: editor.personType,
      expiresOn,
    });
  else
    await usersApi.update(editor.id, {
      displayName: editor.displayName.trim(),
      departmentId,
      personType: editor.personType,
      expiresOn,
    });
  await refresh();
}

// ── 状态、删除 ───────────────────────────────
async function toggle(user: User) {
  const verb = user.enabled ? '停用' : '启用';
  const ok = await ask(
    `${verb}${user.displayName}`,
    user.enabled
      ? '停用后该账号不能登录任何接入应用，已签发的令牌立即失效。角色分配会保留。'
      : '启用后该账号可以登录，保留的角色分配重新生效。',
    { confirmLabel: `确认${verb}`, danger: user.enabled },
  );
  if (ok && (await act(() => usersApi.status(user.id, !user.enabled), `已${verb}`)))
    await refresh();
}

async function remove(user: User) {
  const ok = await ask(
    `删除${user.displayName}`,
    '删除后无法恢复。账号仍有角色分配时，系统会拒绝删除。',
    {
      confirmLabel: '确认删除',
    },
  );
  if (ok && (await act(() => usersApi.remove(user.id), '已删除'))) {
    if (rows.value.length === 1 && page.value > 0) page.value--;
    await refresh();
  }
}

// ── 角色分配 ─────────────────────────────────
const roleDialog = reactive({
  open: false,
  user: null as User | null,
  assigned: [] as Role[],
  pick: '',
});

async function openRoles(user: User) {
  await track(load(['roles']));
  const assigned = await track(usersApi.roles(user.id));
  Object.assign(roleDialog, { open: true, user, assigned: assigned ?? [], pick: '' });
}
const candidates = computed(() =>
  (catalog.roles ?? []).filter((r) => r.enabled && !roleDialog.assigned.some((a) => a.id === r.id)),
);
async function assignRole() {
  if (!roleDialog.user || !roleDialog.pick) return;
  await usersApi.assign(roleDialog.user.id, Number(roleDialog.pick));
  roleDialog.assigned = await usersApi.roles(roleDialog.user.id);
  roleDialog.pick = '';
}
async function revokeRole(role: Role) {
  const user = roleDialog.user;
  if (!user) return;
  if (await act(() => usersApi.revoke(user.id, role.id), `已撤销「${role.name}」`))
    roleDialog.assigned = roleDialog.assigned.filter((r) => r.id !== role.id);
}

// ── 设置密码 ─────────────────────────────────
const pwd = reactive({ open: false, user: null as User | null, value: '' });
async function savePassword() {
  if (pwd.user) await usersApi.password(pwd.user.id, pwd.value);
  pwd.value = '';
  await refresh();
}
</script>

<template>
  <PageHero />
  <Figures
    :items="[
      { label: keyword ? '匹配的用户' : '统一身份用户', value: total.toLocaleString('zh-CN') },
    ]"
  />
  <div class="tools">
    <input
      v-model="keyword"
      class="grow"
      type="search"
      placeholder="搜索工号、姓名或部门"
      aria-label="搜索当前列表"
    />
    <button v-if="can('rbac.directory.sync')" class="btn ghost" @click="router.push('/users/sync')">
      同步员工信息
    </button>
    <button v-if="writable" class="btn" @click="openEditor()">新增用户</button>
  </div>
  <div class="table-wrap">
    <table class="t">
      <thead>
        <tr>
          <th>工号</th>
          <th>姓名</th>
          <th class="hide-sm">部门</th>
          <th class="hide-sm">身份</th>
          <th>状态</th>
          <th class="right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in visible" :key="user.id">
          <td>
            <code style="color: var(--ink)">{{ user.username }}</code>
          </td>
          <td>
            <span class="nm">{{ user.displayName }}</span>
            <span v-if="user.jobTitle" class="sub">{{ user.jobTitle }}</span>
          </td>
          <td class="hide-sm">
            {{ user.companyName ?? departmentName(user.departmentId) }}
            <span v-if="user.sourceDepartment" class="sub">{{ user.sourceDepartment }}</span>
          </td>
          <td class="hide-sm">
            {{ personLabel(user.personType) }}
            <span v-if="user.personType !== 'EMPLOYEE'" class="sub"
              >至 {{ expiresLabel(user.expiresAt) }}</span
            >
          </td>
          <td><span v-if="user.enabled">已启用</span><span v-else class="off-t">已停用</span></td>
          <td>
            <div class="acts">
              <RouterLink v-if="allowed('effective')" class="act" :to="`/effective?user=${user.id}`"
                >有效权限</RouterLink
              >
              <template v-if="writable">
                <button class="act" @click="openRoles(user)">角色</button>
                <button class="act" @click="openEditor(user)">编辑</button>
                <button class="act" @click="Object.assign(pwd, { open: true, user, value: '' })">
                  设置密码
                </button>
                <button class="act" @click="toggle(user)">
                  {{ user.enabled ? '停用' : '启用' }}
                </button>
                <button
                  class="act warn"
                  :aria-label="`删除${user.displayName}`"
                  @click="remove(user)"
                >
                  删除
                </button>
              </template>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-if="!visible.length" class="empty">
    <h3>{{ keyword ? '没有匹配的结果' : '还没有用户' }}</h3>
    <p>{{ keyword ? '换一个工号、姓名或部门再试。' : '可以新增用户，或同步员工信息。' }}</p>
  </div>
  <Pager :page="page" :size="SIZE" :total="total" unit="人" @go="go" />

  <FormDialog
    :open="editor.open"
    :title="editor.id === null ? '新增用户' : '编辑用户'"
    :description="
      editor.id === null ? '登录名即工号，创建后不能修改。' : '登录名与密码在此不可修改。'
    "
    submit-label="保存用户"
    :submit="saveUser"
    success="已保存用户"
    @close="editor.open = false"
  >
    <template v-if="editor.id === null">
      <label class="field"
        >登录名 <span class="req">*</span>
        <input v-model="editor.username" required maxlength="64" pattern="[A-Za-z0-9_.\-]{1,64}" />
      </label>
      <label class="field"
        >初始密码 <span class="req">*</span>
        <input
          v-model="editor.password"
          type="password"
          required
          minlength="8"
          maxlength="72"
          autocomplete="new-password"
        />
        <small>至少 8 位，大小写字母、数字、符号中至少包含三类。</small>
      </label>
    </template>
    <label class="field"
      >姓名 <span class="req">*</span>
      <input v-model="editor.displayName" required maxlength="128" />
    </label>
    <label class="field"
      >所属部门
      <select v-model="editor.departmentId">
        <option value="">不设置</option>
        <option v-for="d in catalog.departments ?? []" :key="d.id" :value="d.id">
          {{ d.name }}
        </option>
      </select>
    </label>
    <label class="field"
      >人员身份 <span class="req">*</span>
      <select v-model="editor.personType" required>
        <option v-for="[value, text] in personTypes" :key="value" :value="value">{{ text }}</option>
      </select>
    </label>
    <label v-if="editor.personType !== 'EMPLOYEE'" class="field"
      >有效期至 <span class="req">*</span>
      <input v-model="editor.expiresOn" type="date" required />
      <small>外部人员和临时人员必须设置有效期。</small>
    </label>
  </FormDialog>

  <FormDialog
    :open="roleDialog.open"
    :title="`${roleDialog.user?.displayName ?? ''}的角色`"
    description="多个角色的权限取并集；撤销某个角色后，其他角色提供的权限保留。"
    submit-label="分配角色"
    :submit="assignRole"
    keep-open
    @close="roleDialog.open = false"
  >
    <p class="h-sub">已分配</p>
    <ul v-if="roleDialog.assigned.length" class="rows">
      <li v-for="role in roleDialog.assigned" :key="role.id">
        {{ role.name }} <code>{{ role.code }}</code>
        <button
          type="button"
          class="act"
          :aria-label="`撤销${role.name}`"
          @click="revokeRole(role)"
        >
          撤销
        </button>
      </li>
    </ul>
    <p v-else class="none">尚未分配角色</p>
    <label class="field"
      >添加角色
      <select v-model="roleDialog.pick" required>
        <option value="">选择角色</option>
        <option v-for="role in candidates" :key="role.id" :value="role.id">{{ role.name }}</option>
      </select>
    </label>
  </FormDialog>

  <FormDialog
    :open="pwd.open"
    title="设置密码"
    :description="`为 ${pwd.user?.displayName ?? ''}（${pwd.user?.username ?? ''}）设置新密码。已签发的令牌会失效；账号的启用状态保持不变。`"
    submit-label="保存密码"
    :submit="savePassword"
    success="密码已设置，账号启用状态保持不变"
    @close="pwd.open = false"
  >
    <label class="field"
      >员工新密码 <span class="req">*</span>
      <input
        v-model="pwd.value"
        type="password"
        required
        minlength="8"
        maxlength="72"
        autocomplete="new-password"
        aria-label="员工新密码"
      />
      <small>至少 8 位，大小写字母、数字、符号中至少包含三类。</small>
    </label>
  </FormDialog>
</template>
