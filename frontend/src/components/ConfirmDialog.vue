<script setup lang="ts">
import {ref,watch} from 'vue';
import type {Console} from '../console';
import Icon from './Icon.vue';
const props=defineProps<{c:Console}>();const dialog=ref<HTMLDialogElement|null>(null);
watch(()=>props.c.s.confirm,value=>{if(value)dialog.value?.showModal();else dialog.value?.close();},{flush:'post'});
</script>
<template><dialog ref="dialog" class="confirm-dialog" aria-labelledby="confirm-title" @cancel.prevent="c.s.confirm?.cancel()"><template v-if="c.s.confirm"><div :class="['confirm-symbol',c.s.confirm.danger?'danger-tone':'green-tone']"><Icon name="info" :size="27"/></div><h2 id="confirm-title">{{c.s.confirm.title}}</h2><p class="muted">{{c.s.confirm.description}}</p><footer class="dialog-actions"><button autofocus @click="c.s.confirm?.cancel()">取消</button><button :class="c.s.confirm.danger?'danger-solid':'primary'" @click="c.s.confirm?.accept()">确认{{c.s.confirm.danger?'操作':''}}</button></footer></template></dialog></template>
