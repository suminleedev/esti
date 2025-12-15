<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const message = ref('')
const error = ref('')
const editingProduct = ref(null) // 수정 중인 제품
/* ===== 공급사 단가표 엑셀 업로드 상태 ===== */
const vendorCode = ref('A')          // 기본값 A사
const vendorFile = ref(null)
const vendorUploading = ref(false)
const vendorProgress = ref(0)
const vendorMessage = ref('')
const vendorError = ref('')

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

/**
 * 공급사 엑셀 업로드
 */
async function uploadVendorExcel() {
  vendorError.value = ''
  vendorMessage.value = ''

  if (!vendorFile.value) {
    vendorError.value = '업로드할 공급사 엑셀 파일을 선택하세요.'
    return
  }

  vendorUploading.value = true
  vendorProgress.value = 0

  try {
    const form = new FormData()
    form.append('file', vendorFile.value)

    const res = await axios.post(`/api/vendor-catalog/upload-excel/${vendorCode.value}`, form, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress(e) {
        if (!e.total) return
        vendorProgress.value = Math.round((e.loaded * 100) / e.total)
      },
    })

    vendorMessage.value = `[${vendorCode.value}] 공급사 카탈로그 업로드가 완료되었습니다.`
    vendorFile.value = null
    vendorProgress.value = 0

    // 공급사 엑셀로 카탈로그 갱신 후 목록 재조회
    if (typeof loadVendorCatalog === 'function') {
      await loadVendorCatalog()
    }
  } catch (err) {
    console.error(err)
    vendorError.value =
      '공급사 엑셀 업로드 중 오류가 발생했습니다: ' +
      (err?.response?.data || err?.message || '')
  } finally {
    vendorUploading.value = false
  }
}

/**
 * 공급사 카탈로그 조회
 */
const vendorCatalogs = ref([])

async function loadVendorCatalog() {
  try {
    const res = await axios.get(`/api/vendor-catalog/list/${vendorCode.value}`)
    console.log("type:", typeof res.data);
    console.log("isArray:", Array.isArray(res.data));
    console.log("keys:", Object.keys(res.data));
    console.log("data:", res.data);
    vendorCatalogs.value = res.data;
  } catch (e) {
    console.error('공급사 카탈로그 목록 조회 실패', e)
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
    const res = await axios.put(`/api/catalog/${editingProduct.value.id}`, editingProduct.value)
    message.value = '수정 성공'
    editingProduct.value = null
    await loadVendorCatalog()
  } catch (e) {
    error.value = '수정 실패: ' + (e?.response?.data || e?.message)
  }
}

/**
 * 카탈로그 삭제
 */
async function deleteProduct(id) {
  if (!confirm('정말 삭제하시겠습니까?')) return
  try {
    await axios.delete(`/api/catalog/${id}`)
    message.value = '삭제 성공'
    await loadVendorCatalog()
  } catch (e) {
    error.value = '삭제 실패: ' + (e?.response?.data || e?.message)
  }
}


onMounted(() => {
  loadVendorCatalog()
})
</script>

<template>
  <div class="container my-5">
    <div class="card shadow-sm">
      <div class="card-body">
        <h2 class="card-title h4 mb-4">제품 카탈로그 엑셀 업로드</h2>

        <!-- 공급사 단가표 엑셀 업로드 -->
        <div class="mt-4 p-3 border rounded bg-light">
          <h5 class="mb-3">공급사 단가표 엑셀 업로드</h5>

          <div class="row g-2 align-items-end">
            <div class="col-md-3">
              <label class="form-label">공급사 선택</label>
              <select v-model="vendorCode" class="form-select">
                <option value="A">아메리칸스탠다드</option>
                <option value="B">이누스</option>
              </select>
            </div>

            <div class="col-md-5">
              <label class="form-label">엑셀 파일 (.xlsx, .xls)</label>
              <input
                type="file"
                class="form-control"
                accept=".xlsx,.xls,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                @change="onVendorFileChange"
              />
            </div>

            <div class="col-md-4 d-grid">
              <button
                class="btn btn-outline-primary"
                :disabled="!vendorFile || vendorUploading"
                @click="uploadVendorExcel"
              >
                {{ vendorUploading ? '공급사 엑셀 업로드 중...' : '공급사 단가표 업로드' }}
              </button>
            </div>
          </div>

          <!-- 진행률 -->
          <div v-if="vendorUploading" class="mt-2">
            <div class="progress">
              <div
                class="progress-bar"
                role="progressbar"
                :style="{ width: vendorProgress + '%' }"
              >
                {{ vendorProgress }}%
              </div>
            </div>
          </div>
          <!-- 메시지 -->
          <p v-if="vendorMessage" class="mt-2 text-success small">
            {{ vendorMessage }}
          </p>
          <p v-if="vendorError" class="mt-2 text-danger small">
            {{ vendorError }}
          </p>
        </div>



        <!-- 카탈로그 목록 -->
        <div class="mt-4">
          <h5>카탈로그 목록</h5>
          <table class="table table-striped table-bordered mt-2 align-middle">
            <thead class="table-light">
            <tr class="text-center">
              <th>#</th>
              <th>대분류</th>
              <th>소분류</th>
              <th>제품명</th>
              <th>모델명</th>
              <th>브랜드</th>
              <th>규격</th>
              <th>단가</th>
              <th>설명</th>
              <th>이미지</th>
              <th>액션</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(p, idx) in vendorCatalogs" :key="p.catalogId">
              <template v-if="editingProduct && editingProduct.catalogId === p.catalogId">
                <!-- 수정 모드 -->
                <td>{{ idx + 1 }}</td>
                <td><input v-model="editingProduct.categoryLarge" class="form-control" /></td>
                <td><input v-model="editingProduct.categorySmall" class="form-control" /></td>
                <td><input v-model="editingProduct.productName" class="form-control" /></td>
                <td><input v-model="editingProduct.mainItemCode" class="form-control" /></td>
                <td><input v-model="editingProduct.vendorName" class="form-control" /></td>
                <td><input v-model="editingProduct.remark" class="form-control" /></td>
                <td>
                  <input
                    v-model="editingProduct.unitPrice"
                    type="number"
                    class="form-control text-end"
                  />
                </td>
                <td><input v-model="editingProduct.oldItemCode" class="form-control" /></td>
                <td><input v-model="editingProduct.imageUrl" class="form-control" /></td>
                <td class="d-flex gap-1">
                  <button class="btn btn-success btn-sm" @click="saveEdit">저장</button>
                  <button class="btn btn-secondary btn-sm" @click="cancelEdit">취소</button>
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
                <td class="text-end">{{ p.unitPrice?.toLocaleString() }}</td>
                <td>{{ p.oldItemCode }}</td>
                <td>
                  <img
                    v-if="p.imageUrl"
                    :src="p.imageUrl"
                    alt="상품 이미지"
                    style="max-width: 80px; max-height: 60px"
                  />
                </td>
                <td class="d-flex gap-1">
                  <button class="btn btn-warning btn-sm" @click="startEdit(p)">수정</button>
                  <button class="btn btn-danger btn-sm" @click="deleteProduct(p.catalogId)">삭제</button>
                </td>
              </template>
            </tr>
            <tr v-if="vendorCatalogs.length === 0">
              <td colspan="11" class="text-center text-muted">등록된 제품이 없습니다</td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
