<template>
  <div class="container py-4">
    <!-- 제목 + 전역 버튼 영역 -->
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h2 class="mb-1">
        <span v-if="isNew">새 제안서 작성</span>
        <span v-else>제안서 #{{ proposalId }}</span>
        <!-- 제목 옆 상태 뱃지 : 읽기 / 편집 모드 -->
<!--        <span v-if="!isNew && !isEditMode" class="badge bg-dark-subtle ms-2">읽기</span>-->
<!--        <span v-if="isNew && isEditMode" class="badge bg-success ms-2">신규작성</span>-->
<!--        <span v-if="!isNew && isEditMode" class="badge bg-primary ms-2">편집 중</span>-->
        <span v-if="isNew" class="badge bg-success ms-2">신규작성</span>
        <span v-else-if="isDraft" class="badge bg-secondary ms-2">임시저장</span>
        <span v-else-if="isSubmitted" class="badge bg-warning text-dark ms-2">저장완료</span>
        <span v-else class="badge bg-dark ms-2">발송완료</span>
      </h2>

      <div class="d-flex gap-2">
        <!-- 상세 보기 → 수정 -->
        <button
          v-if="!isNew && !isEditMode && isDraft"
          class="btn btn-outline-primary btn-sm"
          @click="isEditMode = true">수정</button>
        <!-- 임시저장/저장/전송확정 -->
        <button
          v-if="isEditMode && (isNew || isDraft)"
          class="btn btn-success btn-sm"
          :disabled="!canDraft"
          @click="saveDraft">임시저장</button>
        <button
          v-if="(isNew || isDraft) && isEditMode"
          class="btn btn-primary btn-sm"
          :disabled="!canSubmit"
          @click="submit">제출(저장)</button>
        <button
          v-if="!isNew && isSubmitted"
          class="btn btn-dark btn-sm"
          @click="sendFinal">전송확정</button>
        <!-- 삭제 -->
        <button
          v-if="!isNew"
          class="btn btn-outline-danger btn-sm"
          :disabled="!canDelete"
          @click="deleteProposal">삭제</button>
        <!-- 복사 -->
        <button
          v-if="!isNew && (isSubmitted || isSent)"
          class="btn btn-outline-secondary btn-sm"
          @click="copyToDraft">복사</button>
        <!-- 목록 -->
        <button class="btn btn-outline-success btn-sm" @click="goList">
          목록
        </button>
      </div>
    </div>

    <!-- 템플릿 영역 -->
    <div class="d-flex justify-content-between align-items-center mb-3">
      <div class="small text-muted">
        현장 정보와 위생기구 구성을 제안서로 저장하거나 템플릿으로 재사용할 수 있습니다.<br>
        템플릿을 활용하면 평형/적용부위/기본 구성을 자동으로 불러올 수 있습니다.
      </div>
      <div class="d-flex gap-2">
        <!-- 템플릿 선택 -->
        <select
          v-model="selectedTemplateId"
          class="form-select form-select-sm"
          style="width: 220px"
        >
          <option value="">템플릿 선택...</option>
          <option
            v-for="t in templates"
            :key="t.id"
            :value="t.id"
          >
            {{ t.templateName }} <span v-if="t.apartmentType">({{ t.apartmentType }})</span>
          </option>
        </select>

        <button
          class="btn btn-outline-secondary btn-sm"
          :disabled="!selectedTemplateId"
          @click="onLoadTemplate"
        >
          불러오기
        </button>

        <button
          class="btn btn-outline-primary btn-sm"
          @click="onSaveTemplate"
        >
          현재 구성 템플릿 저장
        </button>
      </div>
    </div>

    <!-- Step Nav -->
    <ul class="nav nav-pills mb-3">
      <li class="nav-item" v-for="(s, i) in steps" :key="i">
        <button class="nav-link" :class="{ active: step === i }" @click="go(i)">
          {{ s }}
        </button>
      </li>
    </ul>

    <!-- 하단 상세 영역 읽기 모드 반투명 오버레이 적용-->
    <div class="detail-content-wrapper">
      <div class="detail-content" :class="{ 'view-only': !isEditMode }">
        <!-- STEP 1: 기본 정보 -->
        <div v-if="step === 0" class="card p-3">
          <h5 class="mb-3">현장 기본 정보</h5>
          <div class="row g-3">
            <div class="col-md-6">
              <label class="form-label">현장명 *</label>
              <input v-model.trim="form.projectName" class="form-control" placeholder="예) 신안 XX아파트 위생기구 납품" />
            </div>
            <div class="col-md-3">
              <label class="form-label">담당자</label>
              <input v-model.trim="form.manager" class="form-control" placeholder="예) 홍길동" />
            </div>
            <div class="col-md-3">
              <label class="form-label">작성일</label>
              <input v-model="form.date" type="date" class="form-control" />
            </div>

            <div class="col-md-3">
              <label class="form-label">아파트 평형 *</label>
              <select v-model="form.apartmentType" class="form-select">
                <option value="">선택하세요</option>
                <option v-for="t in apartmentTypes" :key="t" :value="t">{{ t }}</option>
              </select>
            </div>
            <div class="col-md-3">
              <label class="form-label">세대수 *</label>
              <input v-model.number="form.households" type="number" min="1" class="form-control" placeholder="예) 240" />
            </div>
            <div class="col-md-6">
              <label class="form-label">비고</label>
              <input v-model.trim="form.note" class="form-control" placeholder="현장 특이사항, 일정 등" />
            </div>
          </div>

          <div class="text-end mt-3">
            <button class="btn btn-primary" :disabled="!validStep1" @click="next">다음</button>
          </div>
        </div><!-- /STEP 1 -->

        <!-- STEP 2: 평형/적용부위/필수 유형 -->
        <div v-if="step === 1" class="card p-3">
          <h5 class="mb-3">평형·적용부위·필수 위생기구 유형</h5>

          <div class="row g-3">
            <div class="col-md-4">
              <div class="card h-100">
                <div class="card-header"><strong>평형 확인</strong></div>
                <div class="card-body">
                  <select v-model="form.apartmentType" class="form-select">
                    <option value="">선택하세요</option>
                    <option v-for="t in apartmentTypes" :key="t" :value="t">{{ t }}</option>
                  </select>
                  <div class="form-text mt-2">STEP 1에서 선택한 값과 동일하게 유지됩니다.</div>
                </div>
              </div>
            </div>

            <div class="col-md-4">
              <div class="card h-100">
                <div class="card-header d-flex justify-content-between align-items-center">
                  <strong>적용 부위</strong>
                  <small class="text-muted">중복 선택 가능</small>
                </div>
                <div class="card-body">
                  <div class="row">
                    <div class="col-6" v-for="area in areas" :key="area">
                      <div class="form-check">
                        <input class="form-check-input" type="checkbox" :value="area" v-model="form.areas" />
                        <label class="form-check-label">{{ area }}</label>
                      </div>
                    </div>
                  </div>
                  <div class="form-text mt-2">예: 욕실1/욕실2/주방/세탁실 등</div>
                </div>
              </div>
            </div>

            <div class="col-md-4">
              <div class="card h-100">
                <div class="card-header d-flex justify-content-between align-items-center">
                  <strong>필수 위생기구 유형</strong>
                  <small class="text-muted">체크된 항목은 채워야 저장</small>
                </div>
                <div class="card-body">
                  <div class="row">
                    <div class="col-12" v-for="cat in categories" :key="cat">
                      <div class="form-check">
                        <input class="form-check-input" type="checkbox" :value="cat" v-model="form.requiredCategories" />
                        <label class="form-check-label">{{ cat }}</label>
                      </div>
                    </div>
                  </div>
                  <div class="form-text mt-2">예: 양변기, 비데, 세면기, 수전류, 악세사리 등</div>
                </div>
              </div>
            </div>
          </div>

          <div class="text-end mt-3">
            <button class="btn btn-secondary me-2" @click="prev">이전</button>
            <button class="btn btn-primary" :disabled="!validStep2" @click="next">다음</button>
          </div>
        </div><!-- /STEP 2 -->

        <!-- STEP 3: 카탈로그에서 제안 품목 채우기 -->
        <div v-if="step === 2" class="row g-3">
          <!-- 좌: 카탈로그 -->
          <div class="col-md-5">
            <div class="card h-100">
              <div class="card-header d-flex gap-2 align-items-center">
                <strong>제품 카탈로그</strong>
                <input v-model="search" class="form-control form-control-sm" placeholder="검색 (이름/모델/브랜드/규격)" />
              </div>
              <ul class="list-group list-group-flush overflow-auto" style="max-height:670px">
                <li v-if="items.length === 0" class="p-3 text-center text-muted small">
                  <div>등록된 제품이 없습니다.</div>
                  <button
                    class="btn btn-outline-primary btn-sm mt-3"
                    type="button"
                    @click="goExcelUpload">
                    엑셀 업로드
                  </button>
                </li>
                <li
                  v-for="item in filteredItems"
                  :key="item.catalogId"
                  class="list-group-item d-flex align-items-center"
                  @click="selectCandidate(item)"
                  style="cursor:pointer"
                >
                  <img
                    :src="item.imageUrl || noImg"
                    class="me-3 rounded"
                    style="width:50px;height:50px;object-fit:cover"
                    @error="onImgErr($event)"
                  />
                  <div class="flex-grow-1">
                    <div class="fw-bold">{{ item.productName }}</div>
                    <small class="text-muted">{{ item.mainItemCode }} · {{ item.vendorName}}</small>
                    <div class="small text-muted">{{ item.specs }}</div> <!-- 규격 -->
                  </div>
                  <div class="text-end small text-muted">
                    <!-- 제안서는 가격 표시 안 하거나 옵션으로 -->
                    <span class="badge bg-light text-dark">참고가</span>
                  </div>
                </li>
              </ul>
            </div>
          </div>

          <!-- 중: 선택/입력 상세 -->
          <div class="col-md-3">
            <div class="card h-100">
              <div class="card-body d-flex flex-column">
                <div class="text-center mb-3">
                  <img
                    :src="candidate.imageUrl || noImg"
                    class="rounded"
                    style="max-width:100%;height:auto;object-fit:cover"
                    @error="onImgErr($event)"
                  />
                </div>
                <div class="mb-3">
                  <h6 class="mb-1 text-center">
                    {{ candidate.productName || '품목을 선택하세요' }}
                    <small v-if="candidate.mainItemCode" class="text-muted">({{ candidate.mainItemCode }})</small>
                  </h6>
                  <dl class="row mb-0 small">
                    <dt class="col-4">브랜드</dt><dd class="col-8">{{ candidate.vendorName || '-' }}</dd>
                    <dt class="col-4">규격</dt><dd class="col-8">{{ candidate.specs || '-' }}</dd>
                    <dt class="col-4">원가</dt><dd class="col-8">{{ candidate.catalogId ? toNumber(candidate.unitPrice).toLocaleString() : '-' }}</dd>
<!--                    <dt class="col-4">특징</dt><dd class="col-8">{{ candidate.description || '-' }}</dd>-->
                  </dl>
                </div>

                <div class="mb-2">
                  <label class="form-label">적용 부위 *</label>
                  <select v-model="lineInput.area" class="form-select">
                    <option value="">선택하세요</option>
                    <option v-for="a in form.areas" :key="a" :value="a">{{ a }}</option>
                  </select>
                </div>
                <div class="mb-2">
                  <label class="form-label">유형(카테고리) *</label>
                  <select v-model="lineInput.category" class="form-select">
                    <option value="">선택하세요</option>
                    <option v-for="c in form.requiredCategories" :key="c" :value="c">{{ c }}</option>
                  </select>
                </div>
                <div class="mb-2">
                  <label class="form-label">수량</label>
                  <input v-model.number="lineInput.qty" type="number" min="1" class="form-control" />
                </div>
                <div class="mb-2">
                  <label class="form-label">비고</label>
                  <input v-model.trim="lineInput.note" class="form-control" placeholder="색상/사양 등" />
                </div>

                <div class="mt-auto d-flex gap-2">
                  <button class="btn btn-primary btn-sm" :disabled="!candidate.catalogId || !lineValid" @click="addLine">
                    제안 항목 추가
                  </button>
                  <button class="btn btn-outline-secondary btn-sm" @click="resetLine">초기화</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 우: 제안 항목 리스트 -->
          <div class="col-md-4 d-flex flex-column">
            <!-- 상단: 일괄 마진율 설정 -->
            <div class="card mb-2">
              <div class="card-body py-2 px-3">
                <div class="d-flex justify-content-between align-items-center flex-wrap gap-2">
                  <div class="small fw-semibold text-secondary">일괄 마진율</div>
                  <!-- marginOptions -->
                  <div class="btn-group btn-group-sm" role="group" aria-label="global margin">
                    <template v-for="rate in marginOptions" :key="rate">
                      <input
                        :id="`gm-${rate}`"
                        v-model="form.globalMarginRate"
                        class="btn-check"
                        type="radio"
                        name="globalMarginRate"
                        :value="rate"
                      />
                      <label class="btn btn-outline-secondary" :for="`gm-${rate}`">
                        {{ rate }}%
                      </label>
                    </template>
                  </div>

                  <span class="badge text-bg-primary">{{ form.globalMarginRate }}%</span>
                </div>
              </div>
            </div>

            <!-- 하단: 제안 항목 카드 -->
            <div class="card flex-grow-1 d-flex flex-column">
              <div class="card-header d-flex justify-content-between align-items-center">
                <strong>제안 항목</strong>
                <small class="text-muted">총 {{ lines.length }}건</small>
              </div>

              <div class="card-body p-0 d-flex flex-column">
                <div v-if="lines.length === 0" class="p-3 text-center text-muted small">
                  아직 항목이 없습니다.
                </div>

                <div v-else class="flex-grow-1 overflow-auto" style="max-height:670px;">
                  <table class="table table-sm table-bordered mb-0 align-middle">
                    <thead class="table-light">
                    <tr>
                      <th>유형</th>
                      <th>품목</th>
                      <th>부위</th>
                      <th style="width:70px">수량</th>
                      <th style="width:120px">마진</th>
                      <th style="width:55px"></th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="(r, idx) in lines" :key="r.uid">
                      <td>{{ r.category }}</td>

                      <td>
                        {{ r.vendorItemName }}
                        <div class="small text-muted">{{ r.mainItemCode }} · {{ r.vendorName }}</div>
                        <div class="small text-muted">
                          원가 {{ toNumber(r.catalogUnitPrice).toLocaleString() }}원
                        </div>
                        <div class="small fw-semibold text-primary">
                          최종 {{ toNumber(r.finalAmount).toLocaleString() }}원
                        </div>
                      </td>

                      <td>{{ r.area }}</td>

                      <td>
                        <input
                          v-model.number="r.qty"
                          type="number"
                          min="1"
                          max="10000"
                          class="form-control form-control-sm"
                          @input="recalculateLine(r)"
                        />
                      </td>

                      <td>
                        <div v-if="r.useManualMargin" class="input-group input-group-sm">
                          <input
                            v-model.number="r.marginRate"
                            type="number"
                            min="0"
                            max="100"
                            class="form-control text-end"
                            @input="recalculateLine(r)"
                          />
                          <span class="input-group-text px-2">%</span>
                          <button
                            class="btn btn-outline-secondary px-2"
                            type="button"
                            @click="disableManualMargin(r)"
                            title="일괄 마진으로 복귀"
                          >
                            ↺
                          </button>
                        </div>

                        <button
                          v-else
                          type="button"
                          class="btn btn-sm btn-light border w-100 text-nowrap"
                          @click="enableManualMargin(r)"
                          title="클릭하면 개별 마진 설정"
                        >
                          {{ form.globalMarginRate }}%
                        </button>
                      </td>

                      <td>
                        <button class="btn btn-sm btn-outline-danger" @click="removeLine(idx)">삭제</button>
                      </td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div class="card-footer d-flex justify-content-between align-items-center">
                <div class="small text-muted">
                  필수유형 충족:
                  <span :class="missingRequired.length ? 'text-danger' : 'text-success'">
          {{ missingRequired.length ? '미완료' : '완료' }}
        </span>
                </div>
                <div>
                  <button class="btn btn-secondary btn-sm me-2" @click="prev">이전</button>
                  <button
                    class="btn btn-success btn-sm"
                    v-if="isEditMode && (isNew || isDraft)"
                    :disabled="!canSubmit"
                    @click="submit"
                  >
                    제안서 저장
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div> <!-- /STEP 3 -->

        <!-- 클릭 이벤트 차단 레이어 설정 -->
        <div v-if="!isEditMode" class="detail-overlay"></div>
      </div>
    </div><!-- 하단 상세 영역 읽기 모드 반투명 오버레이 적용 끝 -->
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import noImg from '@/assets/no-image.png' // 없으면 임시로 주석 처리하고 onImgErr에서 대체

const route = useRoute()
const router = useRouter()

/* ====== 라우트 / 모드 (URL 파라미터) ====== */
const proposalId = computed(() => route.params.id)
const isNew = computed(() => !proposalId.value)
const isEditMode = ref(false)

/* ====== 서버 status ====== */
const proposalStatus = ref('DRAFT') // 기본값

const isDraft = computed(() => proposalStatus.value === 'DRAFT')
const isSubmitted = computed(() => proposalStatus.value === 'SUBMITTED')
const isSent = computed(() => proposalStatus.value === 'SENT')

/* ====== 템플릿 목록, 선택된 템플릿 ====== */
const templates = ref([])           // GET /proposal-templates 결과
const selectedTemplateId = ref('')  // 선택된 템플릿 id (문자/숫자 모두 허용)

/* ====== 상단 상태 ====== */
const steps = ['기본 정보', '평형/적용부위/유형', '품목 채우기']
const step = ref(0)

/* ====== 폼/선택 데이터 ====== */
const apartmentTypes = ['24평', '32평', '40평', '48평', '59㎡', '74㎡', '84㎡']
const areas = ['욕실1', '욕실2', '주방', '세탁실', '다용도실']

const categories = [
  '양변기', '비데', '세면기',
  '세면기 수전', '욕조 수전/슬라이드바',
  '해바라기샤워수전', '씽크수전', '악세사리'
]

const marginOptions = [10, 15, 20, 25, 30] // 마진율

const form = reactive({
  projectName: '',
  manager: '',
  date: new Date().toISOString().slice(0, 10),
  apartmentType: '',
  households: null,
  note: '',
  areas: [],
  requiredCategories: [],
  globalMarginRate: 10
})

/* ====== 카탈로그 ====== */
const search = ref('')
const items = ref([]) // /catalog/list 결과

const filteredItems = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return items.value
  return items.value.filter((i) =>
    [
      i.productName,
      i.vendorName,
      i.vendorItemName,
      i.mainItemCode,
      i.oldItemCode,
      i.remark,
      i.specs
    ]
      .filter(Boolean)
      .some((f) => String(f).toLowerCase().includes(q))
  )
})

/* ====== 상세 선택 + 입력 ====== */
const candidate = reactive({
  catalogId: null,
  productName: '',
  vendorName: '',
  vendorItemName: '',
  mainItemCode: '',
  oldItemCode: '',
  unitPrice: 0,
  remark: '',
  specs: '',
  description: '',
  imageUrl: ''
})

const lineInput = reactive({
  area: '', category: '', qty: 1, note: ''
})

const lineValid = computed(() =>
  lineInput.area && lineInput.category && lineInput.qty > 0
)

/* ====== 제안 항목 리스트 ====== */
const lines = reactive([])

/* ====== 공통 유틸 ====== */
function toNumber(value) { return Number(value ?? 0) }

function newUid() {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random()}`
}

function go (i) { step.value = i }
function next () { step.value++ }
function prev () { step.value-- }

function onImgErr (e) {
  e.target.src = noImg /* 만약 import 불가하면: e.target.src = 'data:image/svg+xml;utf8,<svg .../>' */
}

function goList() {
  router.push({ name: 'proposal-list' })
}

function goExcelUpload() {
  router.push("/upload");
}

/* ====== 후보 선택 / 초기화 ====== */
function selectCandidate(item) {
  Object.assign(candidate, {
    catalogId: item.catalogId,
    productName: item.productName ?? '',
    vendorName: item.vendorName ?? '',
    vendorItemName: item.vendorItemName ?? '',
    mainItemCode: item.mainItemCode ?? '',
    oldItemCode: item.oldItemCode ?? '',
    unitPrice: toNumber(item.unitPrice),
    remark: item.remark ?? '',
    specs: item.specs ?? '',
    description: item.description ?? '',
    imageUrl: item.imageUrl ?? ''
    // area/category는 사용자가 선택
  })
}

function resetLine () {
  Object.assign(candidate, {
    catalogId: null,
    productName: '',
    vendorName: '',
    vendorItemName: '',
    mainItemCode: '',
    oldItemCode: '',
    unitPrice: 0,
    remark: '',
    specs: '',
    description: '',
    imageUrl: ''
  })
  Object.assign(lineInput, { area: '', category: '', qty: 1, note: '' })
}

/* ====== 마진 계산 ====== */
function getAppliedMarginRate(line) {
  if (line.useManualMargin && line.marginRate != null && line.marginRate !== '') {
    return toNumber(line.marginRate)
  }
  return toNumber(form.globalMarginRate)
}

function recalculateLine(line) {
  const base = toNumber(line.catalogUnitPrice)

  const rate = line.useManualMargin
    ? toNumber(line.marginRate)
    : toNumber(form.globalMarginRate)

  const calculatedUnitPrice = Math.round(base * (1 + rate / 100))

  line.unitPrice = calculatedUnitPrice
  line.finalAmount = calculatedUnitPrice * toNumber(line.qty)
}
/* ====== line 생성 ====== */
function createLine(data = {}) {
  return {
    uid: data.uid ?? newUid(),

    id: data.id ?? null,
    productId: data.productId ?? null,
    productName: data.productName ?? '',
    vendorCode: data.vendorCode ?? '',
    vendorName: data.vendorName ?? '',
    vendorItemName: data.vendorItemName ?? '',
    mainItemCode: data.mainItemCode ?? '',
    oldItemCode: data.oldItemCode ?? '',
    catalogUnitPrice: toNumber(data.catalogUnitPrice ?? 0), // 원가
    useManualMargin: data.manualMargin ?? data.useManualMargin ?? false,
    marginRate: data.marginRate ?? null,
    unitPrice: toNumber(data.unitPrice ?? 0),               // 제안 단가
    finalAmount: toNumber(data.amount ?? data.finalAmount ?? 0), // 총금액

    remark: data.remark ?? '',
    specs: data.specs ?? '',
    description: data.description ?? '',
    imageUrl: data.imageUrl ?? '',

    area: data.area ?? '',
    category: data.category ?? '',
    qty: toNumber(data.qty ?? data.defaultQty ?? 1),
    note: data.note ?? '',
  }
}

/* ====== 행 조작 ====== */
function addLine() {
  if (!candidate.catalogId || !lineValid.value) return

  const newLine = createLine({
    productId: candidate.catalogId,
    productName: candidate.productName,
    vendorName: candidate.vendorName,
    vendorItemName: candidate.vendorItemName,
    mainItemCode: candidate.mainItemCode,
    oldItemCode: candidate.oldItemCode,

    // 카탈로그 unitPrice -> 제안서 원가
    catalogUnitPrice: candidate.unitPrice,
    // 초기 상태
    useManualMargin: false,
    marginRate: null,
    // 초기값은 우선 원가로 넣고, 아래 recalculateLine에서 다시 계산
    unitPrice: candidate.unitPrice,
    finalAmount: 0,

    remark: candidate.remark,
    specs: candidate.specs,
    description: candidate.description,
    imageUrl: candidate.imageUrl,

    area: lineInput.area,
    category: lineInput.category,
    qty: lineInput.qty,
    note: lineInput.note
  })

  recalculateLine(newLine)
  lines.push(newLine)
  resetLine()
}

function removeLine(idx) {
  lines.splice(idx, 1)
}

function enableManualMargin(row) {
  row.useManualMargin = true
  row.marginRate = row.marginRate ?? form.globalMarginRate
  recalculateLine(row)
}

function disableManualMargin(row) {
  row.useManualMargin = false
  row.marginRate = null
  recalculateLine(row)
}


/* ====== Step 제어 & 검증 ====== */
const validStep1 = computed(() =>
  !!form.projectName && !!form.apartmentType && Number(form.households) > 0
)

const validStep2 = computed(() =>
  !!form.apartmentType && form.areas.length > 0 && form.requiredCategories.length > 0
)

/* 필수유형 충족 검사 */
const missingRequired = computed(() => {
  const usedCats = new Set(lines.map(l => l.category))
  return form.requiredCategories.filter(c => !usedCats.has(c))
})

/* 저장 검증 */
const canSubmit = computed(() =>
  validStep1.value && validStep2.value && missingRequired.value.length === 0 && lines.length > 0
)

/* 임시저장 최소 충족 검증 */
const canDraft = computed(() => {
  // 임시저장은 정말 최소만: 예) 현장명만 있으면 OK
  return !!form.projectName
})

/* 삭제 검증 */
const canDelete = computed(() => {
  // 정책 예시:
  // - 신규(아직 저장 전): 삭제 의미 없음 → false
  // - DRAFT: 삭제 가능
  // - SUBMITTED: (선택) 삭제 가능하게 하려면 true
  // - SENT: 절대 불가
  if (isNew.value) return false
  if (isSent.value) return false
  //return proposalStatus.value === 'DRAFT' // 가장 보수적인 정책
  return ['DRAFT', 'SUBMITTED'].includes(proposalStatus.value)
})

/* ====== 템플릿 목록 불러오기  ====== */
async function fetchTemplates () {
  try {
    const res = await axios.get('/api/proposal-templates')
    // 서비스에서 list() 를 간단하게 돌려주고 있으니:
    // [{id, templateName, apartmentType}, ...] 형태라고 가정
    templates.value = res.data
  } catch (e) {
    console.error('템플릿 목록 조회 실패', e)
  }
}

/* ====== 템플릿 상세 불러오기 + 폼/라인에 반영 ====== */
async function onLoadTemplate () {
  if (!selectedTemplateId.value) return
  try {
    const res = await axios.get(`/api/proposal-templates/${selectedTemplateId.value}`)
    const t = res.data

    // Step 1, 2 폼 값 매핑
    form.apartmentType = t.apartmentType || ''
    form.areas = t.areas || []
    form.requiredCategories = t.requiredCategories || []
    form.globalMarginRate = t.globalMarginRate ?? 10

    // 제안 항목(lines) 초기화 후 다시 채우기
    lines.splice(0, lines.length)
    ;(t.lines || []).forEach((line) => {
      lines.push(createLine({
        productId: line.productId,
        productName: line.productName ?? line.name,
        vendorCode: line.vendorCode,
        vendorName: line.vendorName,
        vendorItemName: line.vendorItemName,
        mainItemCode: line.mainItemCode,
        oldItemCode: line.oldItemCode,

        catalogUnitPrice: line.unitPrice, // 템플릿의 상품 단가 = 원가로 사용
        useManualMargin: false,
        marginRate: null,
        unitPrice: line.unitPrice,
        finalAmount: 0,

        remark: line.remark,
        specs: line.specs,
        description: line.description,
        imageUrl: line.imageUrl,

        area: line.area,
        category: line.category,
        qty: line.defaultQty ?? line.qty,
        note: line.note,
      }))
      recalculateLine(lines[lines.length - 1])
    })

    // UX: 바로 Step 2 또는 3으로 이동해도 좋음
    step.value = 2
    alert('템플릿을 불러왔습니다.')
  } catch (e) {
    console.error('템플릿 불러오기 실패', e)
    alert('템플릿을 불러오지 못했습니다.')
  }
}

/* Template payload */
function buildTemplatePayload(templateName) {
  return {
    templateName,
    apartmentType: form.apartmentType,
    areas: form.areas || [],
    requiredCategories: form.requiredCategories || [],
    globalMarginRate: form.globalMarginRate,
    lines: lines.map((l) => ({
      id: l.id,
      productId: l.productId,
      productName: l.productName,
      vendorCode: l.vendorCode,
      vendorName: l.vendorName,
      vendorItemName: l.vendorItemName,
      mainItemCode: l.mainItemCode,
      oldItemCode: l.oldItemCode,
      unitPrice: l.unitPrice,
      remark: l.remark,
      specs: l.specs,
      description: l.description,
      imageUrl: l.imageUrl,
      area: l.area,
      category: l.category,
      defaultQty: l.qty,
      note: l.note || '',
    }))
  }
}

/* ====== 템플릿 저장  ====== */
async function onSaveTemplate () {
  // 최소한의 유효성 체크 (원하면 더 강화 가능)
  if (!validStep1.value || !validStep2.value) {
    alert('먼저 기본 정보와 적용 부위/필수 유형을 모두 입력하세요.')
    return
  }
  if (lines.length === 0) {
    alert('제안 항목이 없습니다. 최소 1개 이상 추가 후 저장하세요.')
    return
  }

  const nameDefault = form.projectName || `${form.apartmentType} 기본 구성`
  const templateName = window.prompt('템플릿 이름을 입력하세요.', nameDefault)
  if (!templateName) return

  const payload = buildTemplatePayload(templateName)

  try {
    await axios.post('/api/proposal-templates', payload)
    await fetchTemplates()
    alert('템플릿이 저장되었습니다.')
  } catch (e) {
    console.error('템플릿 저장 실패', e)
    alert('템플릿 저장 중 오류가 발생했습니다.')
  }
}

/* ====== Proposal payload ====== */
function buildPayload() {
  return {
    templateId: selectedTemplateId.value || null,
    projectName: form.projectName,
    manager: form.manager,
    date: form.date,
    apartmentType: form.apartmentType,
    households: form.households,
    note: form.note,
    areas: form.areas,
    requiredCategories: form.requiredCategories,
    globalMarginRate: form.globalMarginRate,

    lines: lines.map((l) => ({
      productId: l.productId,
      productName: l.productName,
      vendorCode: l.vendorCode,
      vendorName: l.vendorName,
      vendorItemName: l.vendorItemName,
      mainItemCode: l.mainItemCode,
      oldItemCode: l.oldItemCode,

      catalogUnitPrice: l.catalogUnitPrice,     // 원가
      manualMargin: l.useManualMargin,          // 수동 여부
      marginRate: getAppliedMarginRate(l),      // 적용 마진율
      unitPrice: l.unitPrice,                   // 제안 단가
      amount: l.finalAmount,                    // 총금액

      remark: l.remark,
      specs: l.specs,
      description: l.description,
      imageUrl: l.imageUrl,

      area: l.area,
      category: l.category,
      qty: l.qty,
      note: l.note,
    }))
  }
}

/* ====== 제안서 임시 저장 ====== */
async function saveDraft () {
  if (!canDraft.value) {
    alert('현장명은 최소로 입력해야 임시저장할 수 있어요.')
    return
  }

  const payload = buildPayload()

  try {
    if (isNew.value) {
      // 초안 신규 생성
      const res = await axios.post('/api/proposals/drafts', payload)
      alert(`임시저장되었습니다. (ID: ${res.data.id})`)

      // 같은 컴포넌트 재사용될 수 있어서 replace 추천
      await router.replace({ name: 'proposal-detail', params: { id: res.data.id } })

      // 초안 만든 직후에도 계속 편집 모드 유지
      isEditMode.value = true
      proposalStatus.value = 'DRAFT'
    } else {
      // 이미 id가 있으면 초안 업데이트(백엔드에 맞게 PUT/PATCH)
      await axios.put(`/api/proposals/${proposalId.value}/draft`, payload)
      alert('임시저장되었습니다.')
      proposalStatus.value = 'DRAFT'
    }
  } catch (e) {
    console.error('임시저장 실패', e)
    alert('임시저장 중 오류가 발생했습니다.')
  }
}


/* ====== 제안서 저장 ====== */
async function submit() {
  if (!canSubmit.value) {
    alert('필수 항목/필수 유형 충족 후 제출할 수 있어요.')
    return
  }

  const payload = buildPayload()

  try {
    if (isNew.value) {
      // 신규: 생성 + 저장
      const res = await axios.post('/api/proposals/submit', payload)
      alert(`제안서가 저장되었습니다. (ID: ${res.data.id})`)
      await router.replace({ name: 'proposal-detail', params: { id: res.data.id } })
      proposalStatus.value = res.data.status || 'SUBMITTED'
      isEditMode.value = false
      return
    }
    // 기존: id 기반 제출
    const res = await axios.post(`/api/proposals/${proposalId.value}/submit`, payload)

    alert('저장되었습니다.')
    proposalStatus.value = res.data?.status || 'SUBMITTED'
    isEditMode.value = false
  } catch (e) {
    console.error('제안서 저장 실패', e)
    alert('제안서 저장 중 오류가 발생했습니다.')
  }
}

/* ====== 제안서 전송확정  ====== */
async function sendFinal() {
  if (!confirm('전송 확정하면 최종본이 되어 수정할 수 없습니다. 진행할까요?')) return

  try {
    await axios.post(`/api/proposals/${proposalId.value}/send`)
    alert('전송 확정되었습니다.')
    proposalStatus.value = 'SENT'
  } catch (e) {
    console.error('전송 확정 실패', e)
    alert('전송 확정 실패')
  }
}

/* ====== 복사하여 수정 (새 DRAFT 생성) ====== */
async function copyToDraft() {
  try {
    const res = await axios.post(`/api/proposals/${proposalId.value}/copy`)
    const newId = res.data.id
    alert(`복사본이 생성되었습니다. (ID: ${newId})`)
    await router.push({ name: 'proposal-detail', params: { id: newId } })
    proposalStatus.value = 'DRAFT'
    isEditMode.value = true
  } catch (e) {
    console.error('복사 실패', e)
    alert('복사 실패')
  }
}

/* ====== 제안서 삭제 ====== */
async function deleteProposal() {
  if (!canDelete.value) {
    alert('전송 완료된 최종 제안서는 삭제할 수 없습니다.')
    return
  }
  if (!confirm('정말 삭제하시겠습니까?')) return

  try {
    await axios.delete(`/api/proposals/${proposalId.value}`)
    alert('삭제되었습니다.')
    router.push({ name: 'proposal-list' })
  } catch (e) {
    console.error('제안서 삭제 실패', e)
    alert('제안서 삭제 중 문제가 발생했습니다.')
  }
}

/* ====== 기존 제안서 데이터 로드 ====== */
async function loadProposal(id) {
  try {
    const res = await axios.get(`/api/proposals/${id}`)
    const p = res.data

    // Step1
    form.projectName = p.projectName
    form.manager = p.manager
    form.date = p.date
    form.apartmentType = p.apartmentType
    form.households = p.households
    form.note = p.note

    // Step2
    form.areas = p.areas || []
    form.requiredCategories = p.requiredCategories || []

    // Step3 (제안 항목들)
    form.globalMarginRate = p.globalMarginRate ?? 10

    lines.splice(0, lines.length)
    ;(p.lines || []).forEach((l) => {
      lines.push(createLine({
        id: l.id,
        productId: l.productId,
        productName: l.productName,
        vendorCode: l.vendorCode,
        vendorName: l.vendorName,
        vendorItemName: l.vendorItemName,
        mainItemCode: l.mainItemCode,
        oldItemCode: l.oldItemCode,
        catalogUnitPrice: l.catalogUnitPrice,
        manualMargin: l.manualMargin,
        marginRate: l.marginRate,
        unitPrice: l.unitPrice,
        amount: l.amount,
        remark: l.remark,
        specs: l.specs,
        description: l.description,
        imageUrl: l.imageUrl,
        area: l.area,
        category: l.category,
        qty: l.qty,
        note: l.note,
      }))
    })

    // 상세 보기 모드로 시작
    isEditMode.value = false
    step.value = 0
    proposalStatus.value = p.status || 'DRAFT'
  } catch (e) {
    console.error('제안서 불러오기 실패', e)
    alert('제안서를 불러오지 못했습니다.')
  }
}


/* ====== 카탈로그 로드 ====== */
async function loadCatalog () {
  try {
    // const res = await axios.get('/api/catalog/list') // Vite 프록시로 백엔드 8080
    const res = await axios.get('/api/vendor-catalog/list') // Vite 프록시로 백엔드 8080
    items.value = res.data
  } catch (e) {
    console.error('카탈로그 조회 실패', e)
  }
}


/* ====== watch ====== */
watch(
  () => form.globalMarginRate,
  () => {
    lines.forEach((line) => {
      if (!line.useManualMargin) {
        recalculateLine(line)
      }
    })
  }
)

watch(
  () => proposalId.value,
  (id) => {
    if (id) loadProposal(id)
    else {
      proposalStatus.value = 'DRAFT'
      isEditMode.value = true
      lines.splice(0, lines.length)
    }
  },
  { immediate: true }
)

/* ====== mounted ====== */
onMounted(() => {
  loadCatalog()
  fetchTemplates()
  if (isNew.value) isEditMode.value = true // 신규 작성과 상세/수정 모드 구분
})
</script>

<style scoped>
/* 읽기 모드 반투명 오버레이 */
.detail-content-wrapper {   /* 오버레이 기준 위치 */
  position: relative;
}
.detail-content.view-only { /* 화면 불투명도 */
  opacity: 0.85;
}
.detail-overlay {           /* 클릭 이벤트 막기 */
  position: absolute; /* 오버레이를 위에 */
  inset: 0; /* 부모 영역 전체 덮기 */
  background: rgba(255, 255, 255, 0.15);
  z-index: 10; /* overlay를 위에 배치 */
  /*cursor: not-allowed; // 클릭 불가 표시 */
}
</style>
