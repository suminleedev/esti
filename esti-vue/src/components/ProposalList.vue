<template>
  <div class="container py-4">
    <!-- Topbar -->
    <div class="d-flex justify-content-between align-items-center mb-3">
      <div>
        <h2 class="mb-1">제안서 목록</h2>
        <div class="text-muted small">
          저장된 제안서를 조회·관리합니다.
        </div>
      </div>
      <div class="d-flex gap-2">
        <!-- 제안서 신규 작성 (기존 proposal.vue 라우트로 이동) -->
        <router-link class="btn btn-primary btn-sm" :to="{ name: 'proposal-new' }">
          + 새 제안서 작성
        </router-link>
      </div>
    </div>

    <!-- Filters -->
    <div class="card mb-3">
      <div class="card-body">
        <div class="row g-2 align-items-end">
          <div class="col-md-4">
            <label class="form-label small mb-1">검색어 (현장명/담당자)</label>
            <input
              v-model="filters.keyword"
              type="text"
              class="form-control form-control-sm"
              placeholder="예) 신안 XX아파트, 홍길동"
            />
          </div>
          <div class="col-md-3">
            <label class="form-label small mb-1">평형</label>
            <select v-model="filters.apartmentType" class="form-select form-select-sm">
              <option value="">전체</option>
              <option v-for="t in APARTMENT_TYPES" :key="t" :value="t">{{ t }}</option>
            </select>
          </div>
          <div class="col-md-3">
            <label class="form-label small mb-1">템플릿 기반 여부</label>
            <select v-model="filters.templateFilter" class="form-select form-select-sm">
              <option value="">전체</option>
              <option value="templated">템플릿 기반</option>
              <option value="manual">직접 작성</option>
            </select>
          </div>
          <div class="col-md-3">
            <label class="form-label small mb-1">상태</label>
            <select v-model="filters.status" class="form-select form-select-sm">
              <option value="">전체</option>
              <option v-for="(v, k) in PROPOSAL_STATUS" :key="k" :value="k">{{ v.label }}</option>
            </select>
          </div>
          <div class="col-md-2 text-end">
            <button class="btn btn-outline-secondary btn-sm" @click="resetFilters">
              초기화
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- List -->
    <div class="card list-card">
      <div class="card-header d-flex justify-content-between align-items-center">
        <span>제안서 {{ totalElements }}건</span>
        <small class="text-muted">클릭하면 상세 페이지로 이동합니다.</small>
      </div>
      <div class="card-body p-0">
        <EmptyState
          v-if="loading || proposals.length === 0"
          :loading="loading"
          icon="bi-clipboard-x"
          message="검색 조건에 해당하는 제안서가 없습니다."
        />
        <div v-else class="table-responsive">
          <div class="table-scroll">
          <table class="table table-sm table-hover mb-0 align-middle">
            <thead class="table-light">
            <tr>
              <th style="width: 5%">ID</th>
              <th style="width: 25%">현장명</th>
              <th style="width: 5%">평형</th>
              <th style="width: 5%">세대수</th>
              <th style="width: 5%">담당자</th>
              <th style="width: 10%">작성일</th>
              <th style="width: 10%">템플릿</th>
              <th style="width: 10%">상태</th>
              <th style="width: 10%">액션</th>
            </tr>
            </thead>
            <tbody>
            <tr
              v-for="p in proposals"
              :key="p.id"
              @click="goDetail(p)"
              style="cursor: pointer"
            >
              <td class="text-muted small">#{{ p.id }}</td>
              <td>
                <div class="fw-semibold" style="text-align: left">{{ p.projectName }}</div>
                <div class="small text-muted" style="text-align: left" v-if="p.note">
                  {{ p.note }}
                </div>
              </td>
              <td>{{ p.apartmentType || '-' }}</td>
              <td>{{ p.households ?? '-' }}</td>
              <td>{{ p.manager || '-' }}</td>
              <td>{{ p.date || '-' }}</td>
              <td>
                  <span
                    v-if="p.templateId"
                    class="badge bg-light text-success border"
                  >
                    템플릿 기반
                  </span>
                <span
                  v-else
                  class="badge bg-light text-secondary border"
                >
                    직접 작성
                  </span>
              </td>
              <td>
                <StatusBadge :status="p.status" />
              </td>
              <td class="text-end" @click.stop>
                <button
                  class="btn btn-outline-secondary btn-sm me-1"
                  @click="goDetail(p)"
                >
                  보기
                </button>
                <button
                  class="btn btn-outline-danger btn-sm"
                  v-if="p.status !== 'SENT'"
                  @click="onDelete(p)"
                >
                  삭제
                </button>
                <button
                  v-if="p.status === 'SENT'"
                  class="btn btn-outline-primary btn-sm"
                  @click="printProposal(p.id)"
                >
                  출력
                </button>
              </td>
            </tr>
            </tbody>
          </table>
          </div>
        </div>
      </div>
    </div>

    <div class="card-footer">
      <!-- Pagination -->
      <Pagination
        :page="page"
        :size="size"
        :totalPages="totalPages"
        :pageNumbers="pageNumbers"
        :blockSize="blockSize"
        @go="goToPage"
        @first="firstPage"
        @last="lastPage"
        @prevBlock="prevBlock"
        @nextBlock="nextBlock"
      />
      </div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch} from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

import { usePagination } from "@/composables/usePagination"
import { useToast } from "@/composables/useToast"
import { useConfirm } from "@/composables/useConfirm"
import Pagination from "@/components/Pagination.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import { PROPOSAL_STATUS, APARTMENT_TYPES } from "@/constants/labels";

const router = useRouter()
const toast = useToast()
const { confirm } = useConfirm()

const loading = ref(false)
const proposals = ref([])

// 필터 상태
const filters = ref({
  keyword: '',
  apartmentType: '',
  templateFilter: '', // '', 'templated', 'manual'
  status: ''
})

// 페이징
const {
  page, size, totalPages, totalElements, blockSize, pageNumbers,
  goToPage, firstPage, lastPage, prevBlock, nextBlock, resetToFirst
} = usePagination(loadProposals)

// 제안서 목록 로드
async function loadProposals () {
  loading.value = true
  try {
    const res = await axios.get('/api/proposals/page', {
      params: {
        page: page.value,
        size: size.value,
        keyword: filters.value.keyword?.trim() || "",
        apartmentType: filters.value.apartmentType || "",
        templateFilter: filters.value.templateFilter || "",
        status: filters.value.status || "",
      }
    })

    proposals.value = res.data?.content ?? []
    totalPages.value = res.data?.totalPages ?? 0
    totalElements.value = res.data?.totalElements ?? 0
    page.value = res.data?.number ?? page.value // 서버 보정 반영
  } catch (e) {
    console.error('제안서 목록 조회 실패', e)
    toast.error('제안서 목록 조회 중 오류가 발생했습니다.')
    proposals.value = []
    totalPages.value = 0
    totalElements.value = 0
  } finally {
    loading.value = false
  }
}

function resetFilters () {
  filters.value = {
    keyword: '',
    apartmentType: '',
    templateFilter: '',
    status: ''
  }
  resetToFirst()
  loadProposals()
}

// 셀렉트 필터(평형·템플릿·상태)는 즉시 서버 재조회
watch(
  () => [filters.value.apartmentType, filters.value.templateFilter, filters.value.status],
  () => {
    resetToFirst()
    loadProposals()
  }
)

// 검색어는 300ms 디바운스 후 서버 재조회
let keywordTimer = null
watch(
  () => filters.value.keyword,
  () => {
    if (keywordTimer) clearTimeout(keywordTimer)
    keywordTimer = setTimeout(() => {
      resetToFirst()
      loadProposals()
    }, 300)
  }
)

// 상세 페이지 이동
function goDetail (p) {
  router.push({ name: 'proposal-detail', params: { id: p.id } })
}

// 삭제
async function onDelete (p) {
  const ok = await confirm({
    title: '제안서 삭제',
    message: `제안서 #${p.id} [${p.projectName}] 를 삭제할까요?`,
    confirmLabel: '삭제',
  })
  if (!ok) return

  try {
    await axios.delete(`/api/proposals/${p.id}`)
    await loadProposals() // 현재 페이지 재조회
    toast.success('삭제되었습니다')
  } catch (e) {
    console.error('제안서 삭제 실패', e)
    toast.error('제안서 삭제 중 오류가 발생했습니다.')
  }
}

// 출력
async function printProposal(id) {
  try {
    const res = await axios.get(`/api/proposals/${id}/export-excel`, {
      responseType: 'blob'
    })

    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })

    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `proposal_${id}.xlsx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    console.error(e)
    toast.error('엑셀 출력 중 오류가 발생했습니다.')
  }

}

onMounted(() => {
  loadProposals()
})
</script>

<style scoped>
/* 화면에 맞게 높이 조절 */
.list-card {
  height: var(--esti-list-card-height);
  display: flex;
  flex-direction: column;
}

.table-scroll {
  flex: 1;                /* 남는 공간 전부 */
  overflow-y: auto;       /* 세로 스크롤 */
  overflow-x: auto;       /* 가로 스크롤(필요시) */
}

/* 테이블 헤더 고정 */
.table-scroll thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--bs-table-bg); /* 부트스트랩 테이블 배경 */
}

.table th, td{
  text-align: center;
}
</style>

