import { createRouter, createWebHistory } from 'vue-router'

import HomeView from '@/components/HomeView.vue';
import EstimateView from '../components/EstimateView.vue'
import UploadCatalog from '../components/UploadCatalog.vue'
import ProposalView from "@/components/ProposalView.vue";
import ProposalList from "@/components/ProposalList.vue";

const routes = [
  { path: "/", name: "home", component: HomeView, meta: { title: '홈' } },
  { path: '/estimate', name: 'estimate', component: EstimateView, meta: { title: '견적서' } }, // 견적서 페이지
  { path: '/upload', name: 'uploadCatalog', component: UploadCatalog, meta: { title: '카탈로그 관리' } },
  { path: '/proposal', name: 'proposal-new', component: ProposalView, meta: { title: '새 제안서' } },
  { path: '/proposal/list', name: 'proposal-list', component: ProposalList, meta: { title: '제안서 목록' } },
  { path: '/proposal/:id', name: 'proposal-detail', component: ProposalView, props: true, meta: { title: '제안서 상세' } }, // 같은 화면 재사용 (id 있으면 조회 모드)
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: routes, // ✅ 선언한 routes 배열을 넣어야 함
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} — esti` : 'esti'
})

export default router
