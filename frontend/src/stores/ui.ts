import { reactive } from 'vue';
import { ApiError, StaleSessionError } from '@/api/http';

/** 全局界面状态：进行中的请求数、提示条与确认框。 */
export const ui = reactive({
  pending: 0,
  writing: false,
  notice: null as { text: string; error: boolean } | null,
  confirm: null as null | {
    title: string;
    description: string;
    confirmLabel: string;
    danger: boolean;
    resolve: (ok: boolean) => void;
  },
});

let timer: ReturnType<typeof setTimeout> | undefined;

/** 成功提示 6 秒后自动消失；错误保留到用户关闭。 */
export function notify(text: string, error = false): void {
  clearTimeout(timer);
  ui.notice = { text, error };
  if (!error) timer = setTimeout(() => (ui.notice = null), 6000);
}

export function clearNotice(): void {
  clearTimeout(timer);
  ui.notice = null;
}

/** 读取类请求：计入忙碌状态（#main-content 的 aria-busy），错误转成提示。 */
export async function track<T>(work: Promise<T>): Promise<T | undefined> {
  ui.pending++;
  try {
    return await work;
  } catch (error) {
    report(error);
    return undefined;
  } finally {
    ui.pending--;
  }
}

/** 写操作：同一时间只允许一个，防止重复提交；成功返回 true。 */
export async function act(work: () => Promise<unknown>, success?: string): Promise<boolean> {
  if (ui.writing) return false;
  ui.writing = true;
  ui.pending++;
  try {
    await work();
    if (success) notify(success);
    return true;
  } catch (error) {
    report(error);
    return false;
  } finally {
    ui.writing = false;
    ui.pending--;
  }
}

export function report(error: unknown): void {
  if (error instanceof StaleSessionError) return;
  notify(error instanceof ApiError || error instanceof Error ? error.message : String(error), true);
}

/** 统一确认框：取消与 Escape 都不发起写入。 */
export function ask(
  title: string,
  description: string,
  options: { confirmLabel?: string; danger?: boolean } = {},
): Promise<boolean> {
  return new Promise((resolve) => {
    ui.confirm = {
      title,
      description,
      confirmLabel: options.confirmLabel ?? '确认',
      danger: options.danger ?? true,
      resolve: (ok) => {
        ui.confirm = null;
        resolve(ok);
      },
    };
  });
}
