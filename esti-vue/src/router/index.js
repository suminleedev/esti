import { createRouter, createWebHistory } from 'vue-router'

import Estimate from '../components/Estimate.vue' // 컴포넌트 경로 확인
import EstimatePage from '../components/EstimatePage.vue' // 컴포넌트 경로 확인

const routes = [
  { path: '/', name: 'Estimate', component: Estimate },
  { path: '/estimate', name: 'EstimatePage', component: EstimatePage }, // 견적서 페이지
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: routes, // ✅ 선언한 routes 배열을 넣어야 함
})


export default router
