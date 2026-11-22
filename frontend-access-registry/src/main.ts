import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { session } from './stores/session';
import './styles/main.css';

async function boot(): Promise<void> {
  await session.restore();
  createApp(App).use(router).mount('#app');
}

void boot();
