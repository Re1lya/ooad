/** 二次确认：危险操作先盖一次“确认”印，替代浏览器自带的 confirm 弹窗。 */
import { reactive } from 'vue';

interface ConfirmState {
  open: boolean;
  title: string;
  text: string;
  ok: string;
  resolve: ((value: boolean) => void) | null;
}

export const confirmState = reactive<ConfirmState>({
  open: false,
  title: '',
  text: '',
  ok: '确认',
  resolve: null,
});

/** 弹出确认框，确认返回 true，取消或关闭返回 false。 */
export function confirmDanger(title: string, text: string, ok = '确认'): Promise<boolean> {
  confirmState.resolve?.(false);
  return new Promise((resolve) => {
    Object.assign(confirmState, { open: true, title, text, ok, resolve });
  });
}

export function settleConfirm(value: boolean): void {
  const resolve = confirmState.resolve;
  Object.assign(confirmState, { open: false, resolve: null });
  resolve?.(value);
}
