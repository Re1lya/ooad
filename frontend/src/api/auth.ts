import { request } from './http';
import type { Token, User } from './types';

export const authApi = {
  login: (username: string, password: string) =>
    request<Token>('/api/auth/login', { method: 'POST', body: { username, password } }),
  me: () => request<User>('/api/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/api/me/password', { method: 'PUT', body: { currentPassword, newPassword } }),
};
