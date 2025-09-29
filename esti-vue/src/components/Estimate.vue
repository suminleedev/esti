<template>
  <div class="container py-4">
    <!-- Topbar -->
    <div class="mb-4 text-center">
      <h1 class="h3">아파트 화장실 위생도기 견적서 작성</h1>
      <p class="text-muted small">
        품목 사진 · 규격 확인 → 수량·단가 입력 → 견적서에 추가
      </p>
    </div>

    <div class="row g-3">
      <!-- Left: Catalog -->
      <div class="col-md-4">
        <div class="card h-100">
          <div class="card-header d-flex justify-content-between align-items-center">
            <strong>제품 카탈로그</strong>
            <input
              v-model="search"
              type="text"
              class="form-control form-control-sm w-50"
              placeholder="검색 (예: 변기, 세면대)"
            />
          </div>
          <ul class="list-group list-group-flush overflow-auto" style="max-height: 520px">
            <li
              v-for="item in filteredItems"
              :key="item.id"
              class="list-group-item d-flex align-items-center"
              @click="selectItem(item)"
              style="cursor:pointer"
            >
              <img :src="item.imageUrl" class="me-3 rounded" style="width: 50px; height: 50px; object-fit: cover;" />
              <div class="flex-grow-1">
                <div class="fw-bold">{{ item.name }}</div>
                <small class="text-muted">{{ item.model }} · {{ item.brand }}</small>
              </div>
              <div class="text-end small text-muted">
                {{ currency(item.basePrice) }}
              </div>
            </li>
          </ul>
        </div>
      </div>

      <!-- Middle: Detail + Controls -->
      <div class="col-md-4">
        <div class="card h-100">
          <div class="card-body">
            <div class="d-flex mb-3">
              <img :src="selected.imageUrl" class="rounded me-3" style="width: 150px; height: 150px; object-fit: cover;" />
              <div style="width: 90%;">
                <h5 class="mb-1">
                  {{ selected.name }}
                  <small class="text-muted">({{ selected.model }})</small>
                </h5>
                <dl class="row mb-0 small">
                  <dt class="col-4">브랜드</dt><dd class="col-8">{{ selected.brand }}</dd>
                  <dt class="col-4">규격</dt><dd class="col-8">{{ selected.specs }}</dd>
                  <dt class="col-4">기본 단가</dt><dd class="col-8">{{ currency(selected.basePrice) }}</dd>
                  <dt class="col-4">설명</dt><dd class="col-8">{{ selected.description }}</dd>
                </dl>
              </div>
            </div>

            <h6 class="fw-bold">견적 항목 입력</h6>
            <p class="small text-muted">
              선택한 품목: <span v-if="selected.id">{{ selected.name }}</span>
              <span v-else>없음</span>
            </p>

            <div class="row g-2 mb-2">
              <div class="col">
                <input type="number" v-model.number="form.qty" min="1" class="form-control" placeholder="수량" />
              </div>
              <div class="col">
                <input type="number" v-model.number="form.unitPrice" min="0" class="form-control" placeholder="단가 (원)" />
              </div>
            </div>
            <input type="text" v-model="form.note" class="form-control mb-2" placeholder="비고 (선택)" />

            <div class="d-flex gap-2 mb-2">
              <button class="btn btn-primary btn-sm" @click="addToEstimate" :disabled="!selected.id || form.qty < 1">
                추가
              </button>
              <button class="btn btn-outline-secondary btn-sm" @click="clearForm">초기화</button>
            </div>
            <div class="text-end small text-muted">
              총액: <span class="fw-bold">{{ currency(computedLineTotal) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Estimate List -->
      <div class="col-md-4">
        <div class="card h-100">
          <div class="card-header d-flex justify-content-between align-items-center">
            <strong>견적 내역</strong>
            <span class="text-muted small">총 항목: {{ estimate.length }}</span>
          </div>
          <div class="card-body p-0">
            <div v-if="estimate.length === 0" class="p-3 text-center text-muted small">
              아직 추가된 항목이 없습니다.
            </div>
            <div v-else class="table-responsive" style="max-height: 520px; overflow:auto;">
              <table class="table table-sm table-bordered mb-0 align-middle">
                <thead class="table-light">
                <tr>
                  <th>품목</th>
                  <th>규격/모델</th>
                  <th style="width: 70px">수량</th>
                  <th class="text-end">단가</th>
                  <th class="text-end">금액</th>
                  <th style="width:60px"></th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="(row, idx) in estimate" :key="row.uid">
                  <td>
                    {{ row.name }}
                    <div class="small text-muted">{{ row.brand }}</div>
                  </td>
                  <td>{{ row.model }}</td>
                  <td>
                    <input type="number" v-model.number="row.qty" min="1" class="form-control form-control-sm" />
                  </td>
                  <td class="text-end">{{ currency(row.unitPrice) }}</td>
                  <td class="text-end">{{ currency(row.qty * row.unitPrice) }}</td>
                  <td>
                    <button class="btn btn-sm btn-outline-danger" @click="removeRow(idx)">삭제</button>
                  </td>
                </tr>
                </tbody>
                <tfoot>
                <tr>
                  <td colspan="4" class="text-end fw-bold">합계</td>
                  <td class="text-end fw-bold">{{ currency(subtotal) }}</td>
                  <td></td>
                </tr>
                </tfoot>
              </table>
            </div>
          </div>
          <div class="card-footer text-end">
            <div class="small text-muted">부가세 포함(10%)</div>
            <div class="fw-bold">{{ currency(taxIncluded) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from "vue";
import axios from "axios";
import noImage from "@/assets/no-image.png";  // ✅ 로컬 기본 이미지

const search = ref("");
const items = ref([]);   // ✅ DB에서 가져옴
// const items = reactive([
//   { id:1, name:'양변기 (일체형)', model:'TZ-101', brand:'CleanBath', specs:'W:360 x D:700 x H:780mm', price:220000, desc:'저수량 절수형 일체형 변기', image:'https://images.unsplash.com/photo-1598300059196-9b2f6f6f6f6f?auto=format&fit=crop&w=600&q=60' },
//   { id:2, name:'세면대 (탑볼)', model:'WB-202', brand:'AquaForm', specs:'W:500 x D:420 x H:185mm', price:95000, desc:'상판 설치형 탑볼 세면대', image:'https://images.unsplash.com/photo-1616596871742-3e3d2b1b1b1b?auto=format&fit=crop&w=600&q=60' },
//   { id:3, name:'비데 일체형 시트', model:'BD-300', brand:'HygienePro', specs:'표준형, 자동세척', price:180000, desc:'온수·좌욕·자동세척 기능', image:'https://images.unsplash.com/photo-1586201375761-83865001e86a?auto=format&fit=crop&w=600&q=60' },
//   { id:4, name:'샤워기 세트', model:'SH-77', brand:'RainFlow', specs:'스테인리스, 3단수압', price:85000, desc:'절수형 샤워헤드+호스 세트', image:'https://images.unsplash.com/photo-1556228720-1f5c1b7e3f3f?auto=format&fit=crop&w=600&q=60' },
//   { id:5, name:'양변기 뚜껑', model:'CT-55', brand:'SpareParts', specs:'폴리프로필렌, 백색', price:28000, desc:'표준형 변기용 뚜껑', image:'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=600&q=60' }
// ]);

const selected = reactive({
  id: null,
  name: "품목을 선택하세요",
  model: "",
  brand: "",
  specs: "",
  basePrice: 0,
  description: "좌측에서 품목 선택",
  imageUrl: noImage,
});

const form = reactive({ qty: 1, unitPrice: 0, note: "" });
const estimate = reactive([]);

const filteredItems = computed(() => {
  const q = search.value.trim().toLowerCase();
  if (!q) return items.value;

  return items.value.filter((i) =>
    [i.name, i.model, i.brand]
      .filter(Boolean) // null/undefined 대비
      .some((field) => field.toLowerCase().includes(q))
  );
});


function selectItem(item) {
  // Object.assign(selected, item);
  // form.unitPrice = item.price;
  // form.qty = 1;
  // DB 연동시
  Object.assign(selected, {
    id: item.id,
    name: item.name,
    model: item.model,
    brand: item.brand,
    specs: item.specs,
    basePrice: item.basePrice,
    description: item.description,
    imageUrl: item.imageUrl || noImage,
  });
  form.unitPrice = item.basePrice;
  form.qty = 1;
}

function addToEstimate() {
  if (!selected.id) return;
  estimate.push({
    uid: Date.now(),
    ...selected,
    qty: form.qty,
    unitPrice: form.unitPrice,
  });
}

function clearForm() {
  form.qty = 1;
  form.unitPrice = selected.basePrice || 0;
  form.note = "";
}

function removeRow(idx) {
  estimate.splice(idx, 1);
}

const subtotal = computed(() =>
  estimate.reduce((s, r) => s + r.qty * r.unitPrice, 0)
);
const taxIncluded = computed(() => Math.round(subtotal.value * 1.1));
const computedLineTotal = computed(() => form.qty * form.unitPrice);

function currency(v) {
  return v == null ? "-" : v.toLocaleString("ko-KR") + "원";
}

// ✅ DB 카탈로그 로드
async function loadCatalog() {
  try {
    const res = await axios.get("/catalog/list"); // Vite 프록시 통해 백엔드 호출
    console.log(res);
    console.log(res.data);
    console.log(res.data[0]);
    items.value = res.data;
  } catch (e) {
    console.error("카탈로그 조회 실패", e);
  }
}

onMounted(() => {
  loadCatalog();
});
</script>
