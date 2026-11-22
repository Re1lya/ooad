/** 轻提示：成功盖绿印，失败盖朱印。 */
import { reactive } from 'vue';

export interface ToastItem {
  id: number;
  kind: 'ok' | 'err';
  text: string;
}

let seq = 0;
export const toasts = reactive<ToastItem[]>([]);

function push(kind: ToastItem['kind'], text: string): void {
  const id = ++seq;
  toasts.push({ id, kind, text });
  setTimeout(() => {
    const i = toasts.findIndex((t) => t.id === id);
    if (i >= 0) toasts.splice(i, 1);
  }, 3200);
}

export const toast = {
  ok: (text: string) => push('ok', text),
  err: (text: string) => push('err', text),
  /** 统一吞接口错误。 */
  fail: (error: unknown, fallback = '操作失败，请重试') => {
    const message = error instanceof Error && error.message ? error.message : fallback;
    push('err', message);
  },
};
