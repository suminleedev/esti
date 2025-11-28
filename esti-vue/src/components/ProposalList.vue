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
              placeholder="예) 대덕, 이누스, 홍길동"
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
    <div class="card">
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
          <table class="table table-sm table-hover mb-0 align-middle">
            <thead class="table-light">
            <tr>
              <th style="width: 70px">ID</th>
              <th>현장명</th>
              <th style="width: 100px">평형</th>
              <th style="width: 80px">세대수</th>
              <th style="width: 100px">담당자</th>
              <th style="width: 110px">작성일</th>
              <th style="width: 120px">템플릿</th>
              <th style="width: 120px"></th>
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
                <div class="fw-semibold">{{ p.projectName }}</div>
                <div class="small text-muted" v-if="p.note">
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
              <td class="text-end" @click.stop>
                <button
                  class="btn btn-outline-secondary btn-sm me-1"
                  @click="goDetail(p)"
                >
                  보기
                </button>
                <button
                  class="btn btn-outline-danger btn-sm"
                  @click="onDelete(p)"
                >
                  삭제
                </button>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()

const loading = ref(false)
const proposals = ref([])

const apartmentTypes = ['24평', '32평', '40평', '48평', '59㎡', '74㎡', '84㎡']

// 필터 상태
const filters = ref({
  keyword: '',
  apartmentType: '',
  templateFilter: '' // '', 'templated', 'manual'
})

// 제안서 목록 로드
async function loadProposals () {
  loading.value = true
  try {
    const res = await axios.get('/api/proposals') // 백엔드 ProposalController.list()
    proposals.value = res.data
  } catch (e) {
    console.error('제안서 목록 조회 실패', e)
    alert('제안서 목록 조회 중 오류가 발생했습니다.')
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
}

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
  // 라우터 네이밍은 프로젝트에 맞게 수정
  // 예) /proposal/:id 상세보기 라우트가 있다 가정
  router.push({ name: 'proposal-detail', params: { id: p.id } })
}

// 삭제
async function onDelete (p) {
  if (!confirm(`제안서 #${p.id} [${p.projectName}] 를 삭제하시겠습니까?`)) return

  try {
    await axios.delete(`/api/proposals/${p.id}`)
    proposals.value = proposals.value.filter(x => x.id !== p.id)
  } catch (e) {
    console.error('제안서 삭제 실패', e)
    alert('제안서 삭제 중 오류가 발생했습니다.')
  }
}

onMounted(() => {
  loadProposals()
})
</script>

<style scoped>
/* 필요하면 약간의 보정만 */
</style>

