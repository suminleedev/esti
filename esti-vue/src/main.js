import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// bootstrap CSS
import 'bootstrap/dist/css/bootstrap.min.css';
// bootstrap JS  (include Popper)
import 'bootstrap/dist/js/bootstrap.bundle.min.js';
// bootstrap icons
import 'bootstrap-icons/font/bootstrap-icons.css';

// esti design tokens
import './assets/tokens.css';
// 색 테마(청록 기본 + 쪽빛·클레이, 다크 대응). tokens.css 다음에 둬야 색 값이 이긴다
import './assets/esti-theme.css';


const app = createApp(App)

app.use(router)

app.mount('#app')
