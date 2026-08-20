<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { BASE_URL } from '@/config/api'
import axios from 'axios'

import { usePagination } from "@/composables/usePagination"
import Pagination from "@/components/Pagination.vue";
import EmptyState from "@/components/common/EmptyState.vue"
import { useToast } from "@/composables/useToast"
import { useConfirm } from "@/composables/useConfirm"

const toast = useToast()
const { confirm } = useConfirm()

// 페이징
const {
  page, size, totalPages, totalElements, blockSize, pageNumbers,
  goToPage, firstPage, lastPage, prevBlock, nextBlock, resetToFirst
} = usePagination(loadVendorCatalog)

const editingProduct = ref(null) // 수정 중인 제품
/* ===== 공급사 단가표 엑셀 업로드 상태 ===== */
const uploadVendorCode = ref('A')   // 업로드 영역 전용 : 기본값 A사
const filterVendorCode = ref('') // 목록 필터 전용 : ALL/A/B
const vendorFile = ref(null)
const vendorUploading = ref(false)
const vendorProgress = ref(0)
const vendorMessage = ref('')
const vendorError = ref('')
const vendorSummary = ref(null) // 업로드 완료 요약 { total, raw } — G-최소(총계 배지)

/** 파일 유효성 검사 */
function onVendorFileChange(e) {
  vendorError.value = ''
  const f = e.target.files?.[0]
  if (!f) {
    vendorFile.value = null
    return
  }

  // 간단한 확장자 체크
  const ext = f.name.split('.').pop()?.toLowerCase()
  if (ext !== 'xlsx' && ext !== 'xls') {
    vendorError.value = '엑셀(.xlsx, .xls) 파일만 업로드 가능합니다.'
    vendorFile.value = null
    return
  }

  vendorFile.value = f
}

// 서버 진행률 폴링용
const vendorJobId = ref(null)
let progressTimer = null

function stopProgressPolling() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

/**
 * 공급사 엑셀 업로드 진행률 api
 */
async function startProgressPolling(jobId) {
  stopProgressPolling()
  vendorJobId.value = jobId

  progressTimer = setInterval(async () => {
    try {
      const res = await axios.get(`/api/vendor-catalog/upload-progress/${jobId}`)
      const data = res.data || {}

      // 서버가 주는 percent(0~100)를 그대로 쓰되,
      // 업로드 전송을 0~30에서 이미 사용하므로,
      // 서버 percent는 서비스에서 30부터 시작하도록(백엔드 코드가 그렇게 업데이트함) 맞춰두는 게 깔끔함.
      if (typeof data.percent === 'number') {
        vendorProgress.value = Math.max(vendorProgress.value, data.percent)
      }

      // 서버 메시지 표시(선택)
      if (data.message) {
        vendorMessage.value = data.message
      }

      // 완료 처리
      if (data.done) {
        stopProgressPolling()
        // vendorUploading.value = false

        if (data.error) {
          vendorError.value = data.message || '서버 처리 중 오류가 발생했습니다.'
          return
        }

        // 완료 요약 배지: 총계(N건)는 메시지에서 파싱(G-최소), 신규/갱신은 구조화 필드(G-완전)
        const totalMatch = String(data.message || '').match(/(\d[\d,]*)\s*건/)
        vendorSummary.value = {
          total: totalMatch ? totalMatch[1] : null,
          created: typeof data.created === 'number' ? data.created : null,
          updated: typeof data.updated === 'number' ? data.updated : null,
          raw: data.message || '업로드/반영 완료',
        }
        vendorMessage.value = ''
        vendorProgress.value = 100

        // 1초 정도 완료 상태 보여주고 UI 종료
        setTimeout(() => {
          vendorUploading.value = false
          vendorJobId.value = null // 원하면 초기화
        }, 1000)

        // 업로드 후 1페이지로 리셋
        page.value = 0
        // 공급사 엑셀로 카탈로그 갱신 후 목록 재조회
        filterVendorCode.value = uploadVendorCode.value
        await loadVendorCatalog()
      }
    } catch (e) {
      // 네트워크 순간 오류 정도는 무시해도 됨
      console.error('진행률 조회 실패', e)
    }
  }, 700) // 0.7초마다 폴링
}

/**
 * 공급사 엑셀 업로드
 */
async function uploadVendorExcel() {
  vendorError.value = ''
  vendorMessage.value = ''
  vendorSummary.value = null

  if (!vendorFile.value) {
    vendorError.value = '업로드할 공급사 엑셀 파일을 선택하세요.'
    return
  }

  vendorUploading.value = true
  vendorProgress.value = 0

  try {
    const form = new FormData()
    form.append('file', vendorFile.value)

    const res = await axios.post(`/api/vendor-catalog/upload-excel/${uploadVendorCode.value}`, form, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress(e) {
        if (!e.total) return
        // 업로드 전송은 0~30% 까지만 사용
        const uploadPct = Math.round((e.loaded * 100) / e.total)
        vendorProgress.value = Math.min(30, Math.round(uploadPct * 0.3))
        vendorMessage.value = `업로드 중... (${uploadPct}%)`
      },
    })

    // 업로드 성공 → 서버 비동기 처리 진행률 폴링 시작
    const jobId = res.data?.jobId
    if (!jobId) {
      throw new Error('서버에서 jobId를 받지 못했습니다.')
    }

    vendorFile.value = null
    vendorMessage.value = '서버 처리 시작...'
    vendorProgress.value = Math.max(vendorProgress.value, 30)

    await startProgressPolling(jobId)

  } catch (err) {
    console.error(err)
    stopProgressPolling()
    vendorUploading.value = false

    vendorError.value =
      '공급사 엑셀 업로드/처리 중 오류가 발생했습니다: ' +
      (err?.response?.data || err?.message || '')
  } finally {
    vendorUploading.value = false
  }
}

/**
 * 공급사 카탈로그 조회
 */
const vendorCatalogs = ref([])
const loading = ref(false)

// 부속 구성 드릴다운 상태 (B-2) — 사용은 아래 toggleParts/fetchParts
const expandedRowId = ref(null)   // 펼쳐진 행의 vendorItemPriceId. 한 번에 한 행만 연다.
const partsCache = ref({})        // vendorItemPriceId → 부속 배열 (같은 행 재조회 방지)
const partsLoadingId = ref(null)
const partsErrorId = ref(null)

async function loadVendorCatalog() {
  loading.value = true
  try {
    const res = await axios.get(`/api/vendor-catalog/page/${filterVendorCode.value}`, {
      params: {
        page: page.value,
        size: size.value,
        sort: 'id,desc', // 서버 엔티티 필드 기준. (DTO의 catalogId로 sort하면 안 먹을 수 있음)
      }
    })
    // 목록이 바뀌면 펼친 행과 부속 캐시를 버린다(수정·삭제 후 낡은 구성이 남지 않도록)
    expandedRowId.value = null
    partsCache.value = {}
    partsErrorId.value = null

    vendorCatalogs.value = res.data?.content ?? []
    totalPages.value = res.data?.totalPages ?? 0
    totalElements.value = res.data?.totalElements ?? 0

    // 서버가 보정한 현재 페이지
    page.value = res.data?.number ?? page.value
  } catch (e) {
    console.error('공급사 카탈로그 목록 조회 실패', e)
    vendorCatalogs.value = []
    totalPages.value = 0
    totalElements.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 카탈로그 수정
 */
function startEdit(p) {
  editingProduct.value = { ...p } // 복사본
}

function cancelEdit() {
  editingProduct.value = null
}

async function saveEdit() {
  if (!editingProduct.value) return
  try {
    await axios.put(`/api/vendor-catalog/${editingProduct.value.vendorItemPriceId}`, editingProduct.value)
    editingProduct.value = null
    await loadVendorCatalog()
    toast.success('수정되었습니다')
  } catch (e) {
    toast.error('수정 실패: ' + (e?.response?.data?.message || e?.message || ''))
  }
}

/**
 * 카탈로그 삭제
 */
async function deleteProduct(p) {
  const ok = await confirm({
    title: '카탈로그 삭제',
    message: `『${p.productName}』(${p.mainItemCode ?? '-'}) 항목을 삭제할까요?`,
    confirmLabel: '삭제',
  })
  if (!ok) return
  try {
    await axios.delete(`/api/vendor-catalog/${p.vendorItemPriceId}`)
    await loadVendorCatalog()
    toast.success('삭제되었습니다')
  } catch (e) {
    toast.error('삭제 실패: ' + (e?.response?.data?.message || e?.message || ''))
  }
}

/**
 * 부속 구성 드릴다운 (B-2)
 * 단가 셀을 누르면 해당 행 아래에 부속 목록을 펼친다.
 * 목록을 그릴 때 미리 불러오면 행 수만큼 조회가 나가므로(N+1), 펼친 시점에만 요청한다.
 */
async function toggleParts(p) {
  const id = p.vendorItemPriceId
  if (expandedRowId.value === id) {
    expandedRowId.value = null
    return
  }
  expandedRowId.value = id
  if (partsCache.value[id]) return

  await fetchParts(id)
}

async function fetchParts(id) {
  partsLoadingId.value = id
  partsErrorId.value = null
  try {
    const res = await axios.get(`/api/vendor-catalog/${id}/parts`)
    // 빈 배열 = 부속 없음(정상). 조회 실패와 구분해야 하므로 캐시에 넣는다.
    partsCache.value = { ...partsCache.value, [id]: res.data ?? [] }
  } catch (e) {
    console.error('부속 구성 조회 실패', e)
    partsErrorId.value = id
  } finally {
    partsLoadingId.value = null
  }
}

function partsOf(id) {
  return partsCache.value[id] ?? []
}

/** 부속 단가 합 — 세트가와 맞는지 화면에서 바로 대조하기 위한 값. */
function partsSum(id) {
  return partsOf(id).reduce((sum, part) => sum + (Number(part.unitPrice) || 0), 0)
}

// 공급사 바꾸면 0페이지부터 다시 조회
watch(filterVendorCode, async () =>{
  page.value = 0
  resetToFirst()
  await loadVendorCatalog()
})

// 업로드 중 vendorCode 바꾸면 업로드 중지
watch(uploadVendorCode, async () => {
  stopProgressPolling()
  vendorUploading.value = false
  vendorProgress.value = 0
  vendorMessage.value = ''
  vendorError.value = ''
  vendorSummary.value = null
})

// 컴포넌트 unmount 시 타이머 정리
onBeforeUnmount(() => {
  stopProgressPolling()
})

onMounted(() => {
  loadVendorCatalog()
})
</script>

<template>
  <div class="container py-4">
    <!-- Topbar -->
    <div class="d-flex justify-content-between align-items-center mb-3">
      <div>
        <h2 class="mb-1">카탈로그 관리</h2>
        <div class="text-muted small">
          공급사 단가표를 업로드하고 제품 카탈로그를 조회·관리합니다.
        </div>
      </div>
    </div>

    <!-- 공급사 단가표 엑셀 업로드 -->
    <div class="card mb-3">
      <div class="card-header"><strong>공급사 단가표 엑셀 업로드</strong></div>
      <div class="card-body">
        <div class="row g-2 align-items-end">
          <div class="col-md-3">
            <label class="form-label small mb-1" for="upload-vendorCode">공급사 선택</label>
            <select id="upload-vendorCode" v-model="uploadVendorCode" class="form-select form-select-sm">
              <option value="A">아메리칸스탠다드</option>
              <option value="B">이누스</option>
            </select>
          </div>

          <div class="col-md-5">
            <label class="form-label small mb-1" for="upload-file">엑셀 파일 (.xlsx, .xls)</label>
            <input
              id="upload-file"
              type="file"
              class="form-control form-control-sm"
              accept=".xlsx,.xls,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              @change="onVendorFileChange"
            />
          </div>

          <div class="col-md-4 d-grid">
            <button
              class="btn btn-outline-primary btn-sm"
              :disabled="!vendorFile || vendorUploading"
              @click="uploadVendorExcel"
            >
              {{ vendorUploading ? '공급사 엑셀 업로드 중...' : '공급사 단가표 업로드' }}
            </button>
          </div>
        </div>

        <!-- 진행률 -->
        <div v-if="vendorJobId" class="mt-2">
          <div class="progress">
            <div
              class="progress-bar"
              role="progressbar"
              :style="{ width: vendorProgress + '%' }"
              :aria-valuenow="vendorProgress"
              aria-valuemin="0"
              aria-valuemax="100"
              aria-label="카탈로그 업로드 진행률"
            >
              {{ vendorProgress }}%
            </div>
          </div>
        </div>
        <!-- 메시지 -->
        <p v-if="vendorMessage" class="mt-2 mb-0 text-success small">
          {{ vendorMessage }}
        </p>
        <p v-if="vendorError" class="mt-2 mb-0 text-danger small">
          {{ vendorError }}
        </p>
        <!-- 업로드 완료 요약 (G-최소: 총계 배지) -->
        <div
          v-if="vendorSummary"
          class="mt-2 alert alert-success d-flex align-items-center gap-2 py-2 mb-0"
          role="status"
        >
          <i class="bi bi-check-circle-fill"></i>
          <span>카탈로그 반영 완료</span>
          <span v-if="vendorSummary.total" class="badge text-bg-success">
            총 {{ vendorSummary.total }}건
          </span>
          <span v-if="vendorSummary.created !== null" class="badge text-bg-primary">
            신규 {{ vendorSummary.created }}
          </span>
          <span v-if="vendorSummary.updated !== null" class="badge text-bg-secondary">
            갱신 {{ vendorSummary.updated }}
          </span>
          <span v-if="!vendorSummary.total" class="small text-muted">{{ vendorSummary.raw }}</span>
        </div>
      </div>
    </div>

    <!-- 카탈로그 목록 -->
    <div class="card list-card">
      <div class="card-header d-flex justify-content-between align-items-center">
        <span>카탈로그 {{ totalElements.toLocaleString() }}건</span>
        <div class="d-flex align-items-center gap-3">
          <!-- 공급사 필터 -->
          <div class="d-flex align-items-center gap-2">
            <label class="text-muted small mb-0" for="filter-vendorCode">공급사</label>
            <select id="filter-vendorCode" v-model="filterVendorCode" class="form-select form-select-sm w-auto">
              <option value="">전체</option>
              <option value="A">아메리칸스탠다드</option>
              <option value="B">이누스</option>
            </select>
          </div>
          <!-- 페이지 사이즈 -->
          <select v-model.number="size" class="form-select form-select-sm" style="width: 90px"
                  aria-label="페이지당 표시 건수"
                  @change="page = 0; loadVendorCatalog()">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
            <option :value="100">100</option>
          </select>
        </div>
      </div>
      <div class="card-body p-0">
        <EmptyState
          v-if="loading || vendorCatalogs.length === 0"
          :loading="loading"
          icon="bi-box-seam"
          message="등록된 제품이 없습니다"
        />
        <div v-else class="table-responsive">
          <div class="table-scroll">
            <table class="table table-sm table-striped table-bordered mb-0 align-middle">
              <thead class="table-light">
              <tr class="text-center">
                <th style="width:3%">#</th>
                <th style="width:8%">대분류</th>
                <th style="width:10%">소분류</th>
                <th style="width:12%">제품명</th>
                <th style="width:11%">모델명</th>
                <th style="width:10%">브랜드</th>
                <th style="width:13%">비고</th>
                <th style="width:7%">단가</th>
<!--                <th style="width:7%">구품번</th>-->
                <th style="width:9%">설명</th>
                <th style="width:6%">이미지</th>
                <th style="width:7%">액션</th>
              </tr>
              </thead>
              <tbody>
              <template v-for="(p, idx) in vendorCatalogs" :key="p.vendorItemPriceId">
              <tr>
                <template v-if="editingProduct && editingProduct.vendorItemPriceId === p.vendorItemPriceId">
                  <!-- 수정 모드 -->
                  <td>{{ idx + 1 }}</td>
                  <td><input v-model="editingProduct.categoryLarge" class="form-control form-control-sm" /></td>
                  <td><input v-model="editingProduct.categorySmall" class="form-control form-control-sm" /></td>
                  <td><input v-model="editingProduct.productName" class="form-control form-control-sm" /></td>
                  <td><input v-model="editingProduct.mainItemCode" class="form-control form-control-sm" /></td>
                  <td>{{ editingProduct.vendorName }}</td><!-- 브랜드는 공급사 공통 정보라 행 단위 수정 불가 -->
                  <td><input v-model="editingProduct.remark" class="form-control form-control-sm" /></td>
                  <td>
                    <input
                      v-model="editingProduct.unitPrice"
                      type="number"
                      class="form-control form-control-sm text-end"
                    />
                  </td>
                  <td><input v-model="editingProduct.description" class="form-control form-control-sm" /></td>
                  <td><input v-model="editingProduct.imageUrl" class="form-control form-control-sm" /></td>
                  <td class="d-flex justify-content-center align-items-center gap-1">
                    <button class="btn btn-success btn-sm" @click="saveEdit" title="저장" aria-label="저장"><i class="bi bi-check-lg"></i></button>
                    <button class="btn btn-secondary btn-sm" @click="cancelEdit" title="취소" aria-label="취소"><i class="bi bi-x-lg"></i></button>
                  </td>
                </template>
                <template v-else>
                  <!-- 조회 모드 -->
                  <td>{{ idx + 1 }}</td>
                  <td>{{ p.categoryLarge }}</td>
                  <td>{{ p.categorySmall }}</td>
                  <td>{{ p.productName }}</td>
                  <td>{{ p.mainItemCode }}</td>
                  <td>{{ p.vendorName }}</td>
                  <td>{{ p.remark }}</td>
                  <!-- 단가 셀 = 부속 구성 펼치기 트리거 (B-2) -->
                  <td class="p-0">
                    <button
                      type="button"
                      class="btn btn-link btn-sm w-100 d-flex justify-content-end align-items-center gap-1 px-2 parts-toggle"
                      :aria-expanded="expandedRowId === p.vendorItemPriceId ? 'true' : 'false'"
                      :title="`${p.productName} 부속 구성 보기`"
                      @click="toggleParts(p)"
                    >
                      <span>{{ p.unitPrice?.toLocaleString() }}</span>
                      <i
                        class="bi"
                        :class="expandedRowId === p.vendorItemPriceId ? 'bi-chevron-up' : 'bi-chevron-down'"
                        aria-hidden="true"
                      ></i>
                    </button>
                  </td>
<!--                  <td>{{ p.oldItemCode }}</td>-->
                  <td>{{ p.description }}</td>
                  <td style="padding:1px;">
                    <div class="img-cell">
                      <img
                        v-if="p.imageUrl"
                        :src="`${BASE_URL}${p.imageUrl}`"
                        :alt="`${p.productName} 제품 이미지`"
                        class="product-img"
                      />
                    </div>
                  </td>
                  <td class="text-center align-middle">
                    <div class="d-flex justify-content-center align-items-center gap-1">
                      <button class="btn btn-warning btn-sm" @click="startEdit(p)" title="수정" aria-label="수정"><i class="bi bi-pencil-square"></i></button>
                      <button class="btn btn-danger btn-sm" @click="deleteProduct(p)" title="삭제" aria-label="삭제"><i class="bi bi-trash"></i></button>
                    </div>
                  </td>
                </template>
              </tr>

              <!-- 부속 구성 (B-2) — 단가 셀을 누른 행 아래에만 펼친다 -->
              <tr v-if="expandedRowId === p.vendorItemPriceId" class="parts-row">
                <td colspan="11">
                  <div v-if="partsLoadingId === p.vendorItemPriceId" class="text-muted small py-2">
                    <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                    부속 구성을 불러오는 중...
                  </div>

                  <div v-else-if="partsErrorId === p.vendorItemPriceId" class="text-danger small py-2">
                    부속 구성을 불러오지 못했습니다.
                    <button class="btn btn-outline-danger btn-sm ms-2" @click="fetchParts(p.vendorItemPriceId)">
                      다시 시도
                    </button>
                  </div>

                  <div v-else-if="partsOf(p.vendorItemPriceId).length === 0" class="text-muted small py-2">
                    부속 없음 — 단일 품목입니다.
                  </div>

                  <div v-else class="parts-panel">
                    <table class="table table-sm table-borderless mb-0 parts-table">
                      <thead>
                      <tr class="text-muted small">
                        <th style="width:18%">구분</th>
                        <th style="width:22%">품번</th>
                        <th>부속명</th>
                        <th style="width:16%" class="text-end">단가</th>
                      </tr>
                      </thead>
                      <tbody>
                      <tr v-for="part in partsOf(p.vendorItemPriceId)" :key="part.vendorProductId">
                        <td><span class="badge text-bg-light">{{ part.relationType }}</span></td>
                        <td>{{ part.productCode ?? '-' }}</td>
                        <td>{{ part.productName }}</td>
                        <td class="text-end">
                          {{ part.unitPrice != null ? Number(part.unitPrice).toLocaleString() : '-' }}
                        </td>
                      </tr>
                      </tbody>
                      <tfoot>
                      <tr class="border-top">
                        <td colspan="2" class="fw-semibold">부속 합계</td>
                        <td class="text-muted small">
                          세트가 {{ p.unitPrice?.toLocaleString() }}
                          <span
                            v-if="Number(p.unitPrice) !== partsSum(p.vendorItemPriceId)"
                            class="badge text-bg-warning ms-1"
                          >차이 {{ (partsSum(p.vendorItemPriceId) - Number(p.unitPrice)).toLocaleString() }}</span>
                          <span v-else class="badge text-bg-success ms-1">일치</span>
                        </td>
                        <td class="text-end fw-semibold">{{ partsSum(p.vendorItemPriceId).toLocaleString() }}</td>
                      </tr>
                      </tfoot>
                    </table>
                  </div>
                </td>
              </tr>
              </template>
              </tbody>
            </table>
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
  </div>
</template>

<style scoped>
/* 화면에 맞게 높이 조절 (제안서 목록과 동일한 list-card 구조) */
.list-card {
  height: var(--esti-catalog-list-card-height);
  display: flex;
  flex-direction: column;
}

/* 카드 body가 남는 높이를 전부 차지해야 .table-scroll의 flex:1이 먹는다 */
.list-card .card-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.list-card .table-responsive {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/** 테이블 내 스크롤 영역 **/
.table-scroll {
  flex: 1;                /* 남는 공간 전부 */
  overflow-y: auto;       /* 세로 스크롤 */
  overflow-x: auto;       /* 가로 스크롤(필요시) */
  scrollbar-gutter: stable;
}

/* 테이블 헤더 고정 */
.table-scroll thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--bs-table-bg);
}

/* 컬럼 11개라 폭 고정이 필요 */
.table-scroll table{
  width: 100%;
  table-layout: fixed;
}

.table td,
.table th{
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 단가 셀 = 부속 펼치기 버튼. 셀 텍스트처럼 보이되 누를 수 있다는 것만 표시한다 */
.parts-toggle {
  text-decoration: none;
  color: inherit;
  font-variant-numeric: tabular-nums;
}

.parts-toggle:hover {
  text-decoration: underline;
}

/* 부속 구성 펼침 행 — 상위 테이블의 nowrap/ellipsis·고정 레이아웃을 이 행에서만 푼다 */
.parts-row > td {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
  background: var(--bs-tertiary-bg);
  padding: 0.25rem 0.75rem;
}

.parts-table {
  table-layout: auto;
  background: transparent;
}

.parts-table td,
.parts-table th {
  white-space: normal;
  overflow: visible;
  text-overflow: clip;
  background: transparent;
}

/* 이미지 영역 가운데 정렬 조정 */
.img-cell {
  width: 80px;
  height: 60px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.product-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
</style>
