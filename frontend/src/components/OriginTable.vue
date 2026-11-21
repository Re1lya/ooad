<script setup lang="ts">
import type { PermissionOrigin } from '@/api/types';
import { roleName, systemName } from '@/stores/catalog';

/** 有效权限表：按权限去重，区分直接拥有（●）与继承获得（○），列出每一条来源。 */
const props = defineProps<{ items: PermissionOrigin[]; showAll?: boolean }>();

const direct = (item: PermissionOrigin) => item.sources.some((s) => !s.inherited);
const sources = (item: PermissionOrigin) =>
  [...item.sources].sort(
    (a, b) => Number(a.inherited) - Number(b.inherited) || a.path.length - b.path.length,
  );
const sorted = () => [...props.items].sort((a, b) => Number(direct(b)) - Number(direct(a)));
const pad = (n: number) => String(n).padStart(2, '0');
</script>

<template>
  <div class="table-wrap">
    <table class="t">
      <thead>
        <tr>
          <th>№</th>
          <th>权限</th>
          <th class="hide-sm">编码</th>
          <th>类型</th>
          <th class="hide-sm">应用</th>
          <th>来源</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(item, index) in sorted()" :key="item.permission.id" class="origin-row">
          <td class="n">{{ pad(index + 1) }}</td>
          <td class="nm">
            <span :class="['mark', { d: direct(item) }]"></span>{{ item.permission.name }}
          </td>
          <td class="hide-sm">
            <code>{{ item.permission.code }}</code>
          </td>
          <td>{{ item.permission.type === 'MENU' ? '菜单' : '按钮' }}</td>
          <td class="hide-sm">{{ systemName(item.permission.systemId) }}</td>
          <td>
            <span
              v-for="(source, i) in showAll ? sources(item) : sources(item).slice(0, 1)"
              :key="i"
              class="src-line"
            >
              <template v-if="!source.inherited">
                <span class="k">直接</span><small>{{ source.assignedRoleName }}</small>
              </template>
              <template v-else>
                <span class="k inh">继承 {{ source.permissionRoleName }}</span>
                <small>{{ source.path.map(roleName).join(' → ') }}</small>
              </template>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
