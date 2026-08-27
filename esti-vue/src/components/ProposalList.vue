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
            <label class="form-label small mb-1" for="filter-keyword">검색어 (현장명/담당자)</label>
            <input
              id="filter-keyword"
              v-model="filters.keyword"
              type="text"
              class="form-control form-control-sm"
              placeholder="예) 신안 XX아파트, 홍길동"
            />
          </div>
          <div class="col-md-3">
            <label class="form-label small mb-1" for="filter-apartmentType">평형</label>
            <select id="filter-apartmentType" v-model="filters.apartmentType" class="form-select form-select-sm">
              <option value="">전체</option>
              <option v-for="t in apartmentTypeChoices" :key="t" :value="t">{{ t }}</option>
            </select>
          </div>
          <div class="col-md-3">
            <label class="form-label small mb-1" for="filter-templateFilter">템플릿 기반 여부</label>
            <select id="filter-templateFilter" v-model="filters.templateFilter" class="form-select form-select-sm">
              <option value="">전체</option>
              <option value="templated">템플릿 기반</option>
              <option value="manual">직접 작성</option>
            </select>
          </div>
          <div class="col-md-3">
            <label class="form-label small mb-1" for="filter-status">상태</label>
            <select id="filter-status" v-model="filters.status" class="form-select form-select-sm">
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
              @keydown.enter="goDetail(p)"
              @keydown.space.prevent="goDetail(p)"
              role="button"
              tabindex="0"
              :aria-label="`제안서 #${p.id} ${p.projectName} 상세 열기`"
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
              <td>{{ date(p.date) }}</td>
              <td>
                  <span
                    v-if="p.templateId"
                    class="badge bg-success-subtle text-success-emphasis border"
                  >
                    템플릿 기반
                  </span>
                <span
                  v-else
                  class="badge bg-secondary-subtle text-secondary-emphasis border"
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
                <div v-if="p.status === 'SENT'" class="btn-group">
                  <button
                    class="btn btn-outline-primary btn-sm dropdown-toggle"
                    type="button"
                    data-bs-toggle="dropdown"
                    aria-expanded="false"
                    @click="loadQuoteTargets(p.id)"
                  >
                    출력
                  </button>
                  <ul class="dropdown-menu dropdown-menu-end">
                    <li>
                      <button class="dropdown-item" @click="downloadProposal(p.id)">
                        제안서 <span class="text-muted small">(고객 제출용)</span>
                      </button>
                    </li>
                    <li><hr class="dropdown-divider" /></li>
                    <li>
                      <h6 class="dropdown-header">
                        견적서 <span class="text-danger">— 사입가·마진 포함</span>
                      </h6>
                    </li>
                    <li v-if="quoteTargetsLoadingId === p.id" class="dropdown-item-text small text-muted">
                      불러오는 중...
                    </li>
                    <li
                      v-else-if="!(quoteTargets[p.id] || []).length"
                      class="dropdown-item-text small text-muted"
                    >
                      낼 수 있는 대상이 없습니다
                    </li>
                    <li v-for="t in quoteTargets[p.id] || []" :key="`${t.kind}-${t.apartmentType}`">
                      <button class="dropdown-item" @click="downloadQuote(p.id, t)">
                        {{ t.label }}
                        <span class="text-muted small">{{ t.lineCount }}건</span>
                      </button>
                    </li>
                  </ul>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
          </div>
        </div>
      </div>

      <div class="card-footer">
        <!-- Pagination -->
        <AppPagination
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
  </div>
</template>

<script setup>
import {ref, reactive, computed, onMounted, watch} from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

import { date } from "@/utils/format"
import { usePagination } from "@/composables/usePagination"
import { useToast } from "@/composables/useToast"
import { useConfirm } from "@/composables/useConfirm"
import { saveBlob } from "@/utils/download"
import AppPagination from "@/components/AppPagination.vue";
import StatusBadge from "@/components/common/StatusBadge.vue";
import EmptyState from "@/components/common/EmptyState.vue";
import { PROPOSAL_STATUS } from "@/constants/labels";
import { useMasterCodes, withSaved } from "@/composables/useMasterCodes";

const router = useRouter()
const toast = useToast()
const { confirm } = useConfirm()

const loading = ref(false)
const proposals = ref([])

const { apartmentTypes: masterApartmentTypes, load: loadMasterCodes } = useMasterCodes()


// 필터 상태
const filters = ref({
  keyword: '',
  apartmentType: '',
  templateFilter: '', // '', 'templated', 'manual'
  status: ''
})

/*
 * 필터 선택지 = 마스터 + 지금 목록에 실제로 떠 있는 값 + 현재 선택값.
 * 마스터에서 숨긴 평형을 든 제안서가 남아 있어도 그 값으로 걸러볼 수 있어야 하고,
 * 마스터만 그리면 선택 중이던 값이 목록에서 사라져 필터가 저 혼자 풀린다.
 */
const apartmentTypeChoices = computed(() =>
  withSaved(masterApartmentTypes.value, [
    ...proposals.value.map((p) => p.apartmentType),
    filters.value.apartmentType,
  ]),
)

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

/* ====== 출력 (제안서 / 견적서) ======
   두 양식은 수신자가 다르다 — 제안서는 고객 제출용(사입가 없음), 견적서는 내부 검토용(사입가 포함).
   메뉴에서부터 갈라 두어 잘못 내려받는 일을 줄인다. */
const quoteTargets = reactive({})
const quoteTargetsLoadingId = ref(null)

/** 견적서 대상 목록은 메뉴를 열 때 한 번만 불러온다 (목록 행마다 미리 부르면 N+1). */
async function loadQuoteTargets(id) {
  if (quoteTargets[id]) return
  quoteTargetsLoadingId.value = id
  try {
    const res = await axios.get(`/api/proposals/${id}/export/quote-targets`)
    quoteTargets[id] = res.data
  } catch (e) {
    console.error('견적서 대상 조회 실패', e)
    quoteTargets[id] = []
    toast.error('견적서 대상을 불러오지 못했습니다.')
  } finally {
    quoteTargetsLoadingId.value = null
  }
}

async function downloadProposal(id) {
  await download(`/api/proposals/${id}/export/proposal`, `제안서_${id}.xlsx`)
}

async function downloadQuote(id, target) {
  const params = new URLSearchParams({ kind: target.kind })
  if (target.apartmentType) params.set('apartmentType', target.apartmentType)
  await download(`/api/proposals/${id}/export/quote?${params}`, `견적서_${id}.xlsx`)
}

/** 파일명은 서버가 Content-Disposition으로 정한다. fallback은 헤더를 못 읽을 때만 쓴다. */
async function download(url, fallbackName) {
  try {
    const res = await axios.get(url, { responseType: 'blob' })
    saveBlob(res, fallbackName)
  } catch (e) {
    console.error('엑셀 출력 실패', e)
    toast.error('엑셀 출력 중 오류가 발생했습니다.')
  }
}

onMounted(() => {
  loadMasterCodes()
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

/* 키보드 포커스 가시화 (H-9 접근성) */
tr[role="button"]:focus-visible {
  outline: 2px solid var(--bs-primary);
  outline-offset: -2px;
}
</style>

