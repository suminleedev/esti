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
              placeholder="예) 아메리칸스탠다드, 이누스, 홍길동"
            />
          </div>
          <div class="col-md-3">
            <label class="form-label small mb-1">평형</label>
            <select v-model="filters.apartmentType" class="form-select form-select-sm">
              <option value="">전체</option>
              <option v-for="t in apartmentTypes" :key="t" :value="t">{{ t }}</option>
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
              <option value="DRAFT">임시저장</option>
              <option value="SUBMITTED">제출완료</option>
              <option value="SENT">전송확정</option>
            </select>
          </div>
          <div class="col-md-2 text-end">
            <button class="btn btn-outline-secondary btn-sm me-2" @click="resetFilters">
              초기화
            </button>
            <button class="btn btn-outline-primary btn-sm" @click="reload">
              새로고침
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- List -->
    <div class="card list-card">
      <div class="card-header d-flex justify-content-between align-items-center">
        <span>제안서 {{ filteredProposals.length }}건</span>
        <small class="text-muted">클릭하면 상세 페이지로 이동합니다.</small>
      </div>
      <div class="card-body p-0">
        <div v-if="loading" class="p-3 text-center text-muted small">
          로딩 중...
        </div>
        <div v-else-if="filteredProposals.length === 0" class="p-3 text-center text-muted small">
          검색 조건에 해당하는 제안서가 없습니다.
        </div>
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
              v-for="p in filteredProposals"
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
                <span
                  class="badge"
                  :class="{
                    'bg-secondary': p.status === 'DRAFT',
                    'bg-warning text-dark': p.status === 'SUBMITTED',
                    'bg-dark': p.status === 'SENT'
                  }"
                >
                  {{
                    p.status === 'DRAFT' ? '임시저장' :
                    p.status === 'SUBMITTED' ? '저장완료' : '발송완료'
                  }}
                </span>
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
import {ref, computed, onMounted, watch} from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

import { usePagination } from "@/composables/usePagination"
import Pagination from "@/components/Pagination.vue";

const router = useRouter()

const loading = ref(false)
const proposals = ref([])

const apartmentTypes = ['24평', '32평', '40평', '48평', '59㎡', '74㎡', '84㎡']

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
    alert('제안서 목록 조회 중 오류가 발생했습니다.')
    proposals.value = []
    totalPages.value = 0
    totalElements.value = 0
  } finally {
    loading.value = false
  }
}

function reload () {
  loadProposals()
}

function resetFilters () {
  filters.value = {
    keyword: '',
    apartmentType: '',
    templateFilter: ''
  }
  resetToFirst()
  loadProposals()
}

// 필터 바뀌면 0페이지로 리셋하고 재조회
watch(filters, () => {
  resetToFirst()
  loadProposals()
}, { deep: true })


// 필터링 로직
const filteredProposals = computed(() => {
  const kw = filters.value.keyword.trim().toLowerCase()
  const apt = filters.value.apartmentType
  const tf = filters.value.templateFilter

  return proposals.value.filter(p => {
    // 키워드 필터: 현장명 + 담당자
    if (kw) {
      const target =
        ((p.projectName || '') + ' ' + (p.manager || '')).toLowerCase()
      if (!target.includes(kw)) return false
    }

    // 평형 필터
    if (apt && p.apartmentType !== apt) return false

    // 템플릿 기반 여부 필터
    if (tf === 'templated' && !p.templateId) return false
    if (tf === 'manual' && p.templateId) return false

    return true
  })
})

// 상세 페이지 이동
function goDetail (p) {
  router.push({ name: 'proposal-detail', params: { id: p.id } })
}

// 삭제
async function onDelete (p) {
  if (!confirm(`제안서 #${p.id} [${p.projectName}] 를 삭제하시겠습니까?`)) return

  try {
    await axios.delete(`/api/proposals/${p.id}`)
    await loadProposals() // 현재 페이지 재조회
    proposals.value = proposals.value.filter(x => x.id !== p.id)
  } catch (e) {
    console.error('제안서 삭제 실패', e)
    alert('제안서 삭제 중 오류가 발생했습니다.')
  }
}

// 출력
function printProposal(id) {
  window.open(`/api/proposals/${id}/print`, "_blank")
}


onMounted(() => {
  loadProposals()
})
</script>

<style scoped>
/* 화면에 맞게 높이 조절 */
.list-card {
  height: 64vh;           /* 또는 600px 같은 고정값 */
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

