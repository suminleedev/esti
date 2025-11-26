import { createRouter, createWebHistory } from 'vue-router'

import Estimate from '../components/Estimate.vue' // 컴포넌트 경로 확인
import UploadCatalog from '../components/UploadCatalog.vue'
import Proposal from "@/components/Proposal.vue";

const routes = [
  { path: '/estimate', name: 'Estimate', component: Estimate }, // 견적서 페이지
  { path: '/upload', name: 'UploadCatalog', component: UploadCatalog },
  { path: '/proposal', name: 'Proposal', component: Proposal },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: routes, // ✅ 선언한 routes 배열을 넣어야 함
})


export default router
