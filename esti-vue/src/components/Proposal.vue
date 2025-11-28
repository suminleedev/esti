<template>
  <div class="container py-4">
    <h2 class="mb-4">제안서 작성</h2>

    <!-- 템플릿 영역 -->
    <div class="d-flex justify-content-between align-items-center mb-3">
      <div class="small text-muted">
        현장 정보와 위생기구 구성을 제안서/템플릿으로 재사용할 수 있습니다.
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

    <!-- STEP 1: 기본 정보 -->
    <div v-if="step === 0" class="card p-3">
      <h5 class="mb-3">현장 기본 정보</h5>
      <div class="row g-3">
        <div class="col-md-6">
          <label class="form-label">현장명 *</label>
          <input v-model.trim="form.projectName" class="form-control" placeholder="예) 대덕 XX아파트 위생기구 납품" />
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
    </div>

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
    </div>

    <!-- STEP 3: 카탈로그에서 제안 품목 채우기 -->
    <div v-if="step === 2" class="row g-3">
      <!-- 좌: 카탈로그 -->
      <div class="col-md-5">
        <div class="card h-100">
          <div class="card-header d-flex gap-2 align-items-center">
            <strong>제품 카탈로그</strong>
            <input v-model="search" class="form-control form-control-sm" placeholder="검색 (이름/모델/브랜드/규격)" />
          </div>
          <ul class="list-group list-group-flush overflow-auto" style="max-height: 560px">
            <li
              v-for="item in filteredItems"
              :key="item.id"
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
                <div class="fw-bold">{{ item.name }}</div>
                <small class="text-muted">{{ item.model }} · {{ item.brand }}</small>
                <div class="small text-muted">{{ item.specs }}</div>
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
                {{ candidate.name || '품목을 선택하세요' }}
                <small v-if="candidate.model" class="text-muted">({{ candidate.model }})</small>
              </h6>
              <dl class="row mb-0 small">
                <dt class="col-4">브랜드</dt><dd class="col-8">{{ candidate.brand || '-' }}</dd>
                <dt class="col-4">규격</dt><dd class="col-8">{{ candidate.specs || '-' }}</dd>
                <dt class="col-4">특징</dt><dd class="col-8">{{ candidate.description || '-' }}</dd>
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
              <button class="btn btn-primary btn-sm" :disabled="!candidate.id || !lineValid" @click="addLine">
                제안 항목 추가
              </button>
              <button class="btn btn-outline-secondary btn-sm" @click="resetLine">초기화</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 우: 제안 항목 리스트 -->
      <div class="col-md-4">
        <div class="card h-100">
          <div class="card-header d-flex justify-content-between align-items-center">
            <strong>제안 항목</strong>
            <small class="text-muted">총 {{ lines.length }}건</small>
          </div>
          <div class="card-body p-0">
            <div v-if="lines.length === 0" class="p-3 text-center text-muted small">아직 항목이 없습니다.</div>
            <div v-else class="table-responsive" style="max-height:560px;overflow:auto">
              <table class="table table-sm table-bordered mb-0 align-middle">
                <thead class="table-light">
                <tr>
                  <th>유형</th>
                  <th>품목</th>
                  <th>부위</th>
                  <th style="width:70px">수량</th>
                  <th style="width:60px"></th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="(r, idx) in lines" :key="r.uid">
                  <td>{{ r.category }}</td>
                  <td>
                    {{ r.name }}
                    <div class="small text-muted">{{ r.model }} · {{ r.brand }}</div>
                  </td>
                  <td>{{ r.area }}</td>
                  <td>
                    <input v-model.number="r.qty" type="number" min="1" class="form-control form-control-sm" />
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
              <button class="btn btn-success btn-sm" :disabled="!canSubmit" @click="submitProposal">
                제안서 저장
              </button>
            </div>
          </div>
        </div>
      </div>
    </div> <!-- /STEP 3 -->
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import axios from 'axios'
import noImg from '@/assets/no-image.png' // 없으면 임시로 주석 처리하고 onImgErr에서 대체

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

const form = reactive({
  projectName: '',
  manager: '',
  date: new Date().toISOString().slice(0, 10),
  apartmentType: '',
  households: null,
  note: '',
  areas: [],
  requiredCategories: []
})

/* ====== 카탈로그 ====== */
const search = ref('')
const items = ref([]) // /catalog/list 결과

const filteredItems = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return items.value
  return items.value.filter((i) =>
    [i.name, i.model, i.brand, i.specs, i.description]
      .filter(Boolean)
      .some((f) => f.toLowerCase().includes(q))
  )
})

/* ====== 상세 선택 + 입력 ====== */
const candidate = reactive({
  id: null, name: '', model: '', brand: '', specs: '',
  description: '', imageUrl: ''
})

const lineInput = reactive({
  area: '', category: '', qty: 1, note: ''
})

const lineValid = computed(() =>
  lineInput.area && lineInput.category && lineInput.qty > 0
)

/* ====== 제안 항목 리스트 ====== */
const lines = reactive([])

function addLine () {
  if (!candidate.id || !lineValid.value) return
  lines.push({
    uid: Date.now() + Math.random(),
    productId: candidate.id,
    name: candidate.name,
    model: candidate.model,
    brand: candidate.brand,
    specs: candidate.specs,
    description: candidate.description,
    imageUrl: candidate.imageUrl,
    area: lineInput.area,
    category: lineInput.category,
    qty: lineInput.qty,
    note: lineInput.note
  })
  resetLine()
}

function removeLine (idx) {
  lines.splice(idx, 1)
}

function resetLine () {
  Object.assign(candidate, { id: null, name: '', model: '', brand: '', specs: '', description: '', imageUrl: '' })
  Object.assign(lineInput, { area: '', category: '', qty: 1, note: '' })
}

/* ====== 유틸 ====== */
function onImgErr (e) {
  e.target.src = noImg /* 만약 import 불가하면: e.target.src = 'data:image/svg+xml;utf8,<svg .../>' */
}

function selectCandidate (item) {
  Object.assign(candidate, {
    id: item.id,
    name: item.name,
    model: item.model,
    brand: item.brand,
    specs: item.specs,
    description: item.description,
    imageUrl: item.imageUrl
  })
  // area/category는 사용자가 선택
}

/* ====== Step 제어 & 검증 ====== */
const validStep1 = computed(() =>
  !!form.projectName && !!form.apartmentType && Number(form.households) > 0
)

const validStep2 = computed(() =>
  !!form.apartmentType && form.areas.length > 0 && form.requiredCategories.length > 0
)

function go (i) { step.value = i }
function next () { step.value++ }
function prev () { step.value-- }

/* 필수유형 충족 검사 */
const missingRequired = computed(() => {
  const usedCats = new Set(lines.map(l => l.category))
  return form.requiredCategories.filter(c => !usedCats.has(c))
})

const canSubmit = computed(() =>
  validStep1.value && validStep2.value && missingRequired.value.length === 0 && lines.length > 0
)

/* ====== 템플릿 목록 불러오기  ====== */
async function fetchTemplates () {
  try {
    const res = await axios.get('/proposal-templates')
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
    const res = await axios.get(`/proposal-templates/${selectedTemplateId.value}`)
    const t = res.data

    // Step 1, 2 폼 값 매핑
    form.apartmentType = t.apartmentType || ''
    form.areas = t.areas || []
    form.requiredCategories = t.requiredCategories || []

    // 제안 항목(lines) 초기화 후 다시 채우기
    lines.splice(0, lines.length)

    ;(t.lines || []).forEach(line => {
      lines.push({
        uid: Date.now() + Math.random(),
        productId: line.productId,
        name: line.name,
        model: line.model,
        brand: line.brand,
        specs: line.specs,
        description: line.description,
        imageUrl: line.imageUrl,
        area: line.area,
        category: line.category,
        qty: line.defaultQty || 1,
        note: line.note || ''
      })
    })

    // UX: 바로 Step 2 또는 3으로 이동해도 좋음
    step.value = 2
    alert('템플릿을 불러왔습니다.')
  } catch (e) {
    console.error('템플릿 불러오기 실패', e)
    alert('템플릿을 불러오지 못했습니다.')
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

  const payload = {
    templateName,
    apartmentType: form.apartmentType,
    areas: form.areas,
    requiredCategories: form.requiredCategories,
    lines: lines.map(l => ({
      productId: l.productId,
      area: l.area,
      category: l.category,
      defaultQty: l.qty,
      note: l.note
    }))
  }

  try {
    await axios.post('/proposal-templates', payload)
    await fetchTemplates()
    alert('템플릿이 저장되었습니다.')
  } catch (e) {
    console.error('템플릿 저장 실패', e)
    alert('템플릿 저장 중 오류가 발생했습니다.')
  }
}


/* ====== 저장 (백엔드 연동 위치) ====== */
async function submitProposal () {
  if (!validStep1.value || !validStep2.value) {
    alert('기본 정보와 적용 부위/필수 유형을 먼저 완성하세요.')
    return
  }
  if (lines.length === 0) {
    alert('제안 항목이 없습니다.')
    return
  }

  const payload = {
    templateId: selectedTemplateId.value || null,   // 템플릿 기반이면 전달, 아니면 null
    projectName: form.projectName,
    manager: form.manager,
    date: form.date,
    apartmentType: form.apartmentType,
    households: form.households,
    note: form.note,
    areas: form.areas,
    requiredCategories: form.requiredCategories,
    lines: lines.map(l => ({
      productId: l.productId,
      area: l.area,
      category: l.category,
      qty: l.qty,
      note: l.note
    }))
  }

  try {
    const res = await axios.post('/proposals', payload)
    console.log('제안서 저장 결과:', res.data)
    alert(`제안서가 저장되었습니다. (ID: ${res.data.id})`)
  } catch (e) {
    console.error('제안서 저장 실패', e)
    alert('제안서 저장 중 오류가 발생했습니다.')
  }
}


/* ====== 카탈로그 로드 ====== */
async function loadCatalog () {
  try {
    const res = await axios.get('/catalog/list') // Vite 프록시로 백엔드 8080
    items.value = res.data
  } catch (e) {
    console.error('카탈로그 조회 실패', e)
  }
}

// onMounted(loadCatalog)
onMounted(() => {
  loadCatalog()
  fetchTemplates()
})
</script>

<style scoped>
/* 필요시 소소한 보정만 사용 (Bootstrap 위주) */
</style>
