import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// bootstrap CSS
import 'bootstrap/dist/css/bootstrap.min.css';
// bootstrap JS  (include Popper)
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

// global apply
//import './assets/css/estimatePage.css';


const app = createApp(App)

app.use(router)

app.mount('#app')
