import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 构建产物由 Spring Boot 在 /ui/ 下直接提供；开发时把 /api 代理到本机后端。
export default defineConfig({
  base: '/ui/',
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: { outDir: '../src/main/resources/static/ui', emptyOutDir: true },
  server: { proxy: { '/api': 'http://127.0.0.1:18081' } },
});
