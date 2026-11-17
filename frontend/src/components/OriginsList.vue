<script setup lang="ts">
import type {Row} from '../console';
import Icon from './Icon.vue';
const props=defineProps<{items:Row[];roles?:Row[];empty?:string}>();
function roleName(id:number){return props.roles?.find(r=>r.id===id)?.name||`角色 #${id}`;}
</script>
<template><div v-if="!items.length" class="empty compact"><Icon name="key" :size="30"/><h3>{{empty||'暂无有效权限'}}</h3><p>有效权限来自直接授权，或有效角色的继承路径。</p></div><div v-else class="origin-list"><article v-for="item in items" :key="item.permission.id" class="origin-row"><div class="origin-name"><span class="resource-icon"><Icon :name="item.permission.type==='MENU'?'layers':'key'"/></span><div><strong>{{item.permission.name}}</strong><code>{{item.permission.code}}</code></div></div><div class="origin-sources"><div v-for="(source,index) in item.sources" :key="index" class="source-line"><span :class="['badge',source.inherited?'indigo':'green']">{{source.inherited?'继承权限':'直接权限'}}</span><span v-if="source.inherited&&roles?.length" class="inherit-path">{{source.path.map((id:number)=>roleName(id)).join(' → ')}}</span><span v-else-if="!source.inherited">{{source.assignedRoleName}}</span><span v-if="!roles?.length&&source.inherited" class="muted">{{source.assignedRoleName}} 经 {{source.path.length-1}} 级继承，获得 {{source.permissionRoleName}} 的权限</span></div></div></article></div></template>
