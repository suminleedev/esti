import { createRouter, createWebHistory } from 'vue-router'

import Estimate from '../components/Estimate.vue' // 컴포넌트 경로 확인
import UploadCatalog from '../components/UploadCatalog.vue'
import ProposalPage from "@/components/Proposal.vue";
import ProposalList from "@/components/ProposalList.vue";

const routes = [
  { path: '/estimate', name: 'estimate', component: Estimate }, // 견적서 페이지
  { path: '/upload', name: 'uploadCatalog', component: UploadCatalog },
  { path: '/proposal', name: 'proposal-new', component: ProposalPage },
  { path: '/proposal/:id', name: 'proposal-detail', component: ProposalPage, props: true }, // 같은 화면 재사용 (id 있으면 조회 모드)
  { path: '/proposal-list', name: 'proposal-list', component: ProposalList },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: routes, // ✅ 선언한 routes 배열을 넣어야 함
})


export default router
