import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

/** 后端地址：默认本地演示端口 18081，可用 VITE_API_TARGET 覆盖。 */
const target = process.env.VITE_API_TARGET ?? 'http://127.0.0.1:18081';

export default defineConfig({
  // 相对路径：既能被本地静态服务器托管在根路径，也能放进后端 /access-registry/ 子路径
  base: './',
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: { '/api': { target, changeOrigin: true } },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 900,
  },
});
