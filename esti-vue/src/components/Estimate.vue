<style scoped src="../assets/css/estimate.css"></style>

<template>
  <div class="wrap">
    <div class="topbar">
      <h1>아파트 화장실 위생도기 견적서 작성</h1>
      <div class="small muted">
        품목 사진 · 규격 확인 → 수량·단가 입력 → 견적서에 추가
      </div>
    </div>

    <div class="grid">
      <!-- Left: Catalog -->
      <div class="card">
        <div
          style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px"
        >
          <strong>제품 카탈로그</strong>
          <input
            v-model="search"
            placeholder="검색 (예: 변기, 세면대)"
            style="padding:6px;border-radius:8px;border:1px solid #e6e9ef"
          />
        </div>

        <div class="catalog">
          <div
            v-for="item in filteredItems"
            :key="item.id"
            class="item"
            @click="selectItem(item)"
          >
            <img :src="item.image" class="thumb" alt="thumb" />
            <div class="meta">
              <div class="title">{{ item.name }}</div>
              <div class="sub">{{ item.model }} · {{ item.brand }}</div>
            </div>
            <div class="small muted">{{ currency(item.price) }}</div>
          </div>
        </div>
      </div>

      <!-- Middle: Detail + Controls -->
      <div class="card">
        <div class="detail-preview">
          <img :src="selected.image" class="detail-img" alt="detail" />
          <div class="specs">
            <h3 style="margin:0">
              {{ selected.name }}
              <span class="small muted">({{ selected.model }})</span>
            </h3>
            <dl>
              <dt>브랜드</dt>
              <dd>{{ selected.brand }}</dd>
              <dt>규격</dt>
              <dd>{{ selected.specs }}</dd>
              <dt>기본 단가</dt>
              <dd>{{ currency(selected.price) }}</dd>
              <dt>설명</dt>
              <dd class="small muted">{{ selected.desc }}</dd>
            </dl>
          </div>
        </div>

        <div class="controls">
          <div
            style="display:flex;justify-content:space-between;align-items:center"
          >
            <strong>견적 항목 입력</strong>
            <div class="small muted">
              선택한 품목:
              <span v-if="selected.id">{{ selected.name }}</span>
              <span v-else>없음</span>
            </div>
          </div>
          <div class="row">
            <input
              type="number"
              v-model.number="form.qty"
              min="1"
              placeholder="수량"
            />
            <input
              type="number"
              v-model.number="form.unitPrice"
              min="0"
              placeholder="단가 (원)"
            />
          </div>
          <div class="row">
            <input type="text" v-model="form.note" placeholder="비고 (선택)" />
          </div>
          <div style="display:flex;gap:8px">
            <button @click="addToEstimate" :disabled="!selected.id || form.qty<1">추가</button>
            <button class="secondary" @click="clearForm">초기화</button>
          </div>
          <div class="small muted">
            총액: <span class="total">{{ currency(computedLineTotal) }}</span>
          </div>
        </div>
      </div>

      <!-- Right: Estimate List -->
      <div class="card">
        <div
          style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px"
        >
          <strong>견적 내역</strong>
          <div class="small muted">총 항목: {{ estimate.length }}</div>
        </div>

        <div v-if="estimate.length === 0" class="empty">
          아직 추가된 항목이 없습니다.
        </div>
        <div v-else style="overflow:auto;max-height:520px">
          <table>
            <thead>
            <tr>
              <th>품목</th>
              <th>규격/모델</th>
              <th>수량</th>
              <th class="right">단가</th>
              <th class="right">금액</th>
              <th></th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(row, idx) in estimate" :key="row.uid">
              <td>
                {{ row.name }}
                <div class="small muted">{{ row.brand }}</div>
              </td>
              <td>{{ row.model }}</td>
              <td>
                <input
                  class="qty-input"
                  type="number"
                  v-model.number="row.qty"
                  min="1"
                />
              </td>
              <td class="right">{{ currency(row.unitPrice) }}</td>
              <td class="right">{{ currency(row.qty * row.unitPrice) }}</td>
              <td><button class="ghost" @click="removeRow(idx)">삭제</button></td>
            </tr>
            </tbody>
            <tfoot>
            <tr>
              <td colspan="4" class="right">합계</td>
              <td class="right">{{ currency(subtotal) }}</td>
              <td></td>
            </tr>
            </tfoot>
          </table>
        </div>
        <div style="text-align:right;margin-top:12px">
          <div class="small muted">부가세 포함(10%):</div>
          <div class="total">{{ currency(taxIncluded) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from "vue";
//import "./estimate.css";

const search = ref("");
const items = reactive([
  {
    id: 1,
    name: "양변기 (일체형)",
    model: "TZ-101",
    brand: "CleanBath",
    specs: "W:360 x D:700 x H:780mm",
    price: 220000,
    desc: "저수량 절수형 일체형 변기",
    image: "https://via.placeholder.com/150",
  },
  {
    id: 2,
    name: "세면대 (탑볼)",
    model: "WB-202",
    brand: "AquaForm",
    specs: "W:500 x D:420 x H:185mm",
    price: 95000,
    desc: "상판 설치형 탑볼 세면대",
    image: "https://via.placeholder.com/150",
  },
]);

const selected = reactive({
  id: null,
  name: "품목을 선택하세요",
  model: "",
  brand: "",
  specs: "",
  price: 0,
  desc: "좌측에서 품목 선택",
  image: "https://via.placeholder.com/300x200?text=선택된+품목",
});

const form = reactive({ qty: 1, unitPrice: 0, note: "" });
const estimate = reactive([]);

const filteredItems = computed(() => {
  const q = search.value.toLowerCase();
  return q
    ? items.filter((i) =>
      (i.name + i.model + i.brand).toLowerCase().includes(q)
    )
    : items;
});

function selectItem(item) {
  Object.assign(selected, item);
  form.unitPrice = item.price;
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
  form.unitPrice = selected.price || 0;
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
</script>

