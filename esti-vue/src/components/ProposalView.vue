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
        <StatusBadge v-else :status="proposalStatus" class="ms-2" />
      </h2>

      <div class="d-flex gap-2">
        <!-- 상세 보기 → 수정 -->
        <button
          v-if="!isNew && !isEditMode && isDraft"
          class="btn btn-outline-primary btn-sm"
          @click="isEditMode = true">수정</button>
        <!-- 임시저장/작성 완료/발송 확정 -->
        <button
          v-if="isEditMode && (isNew || isDraft)"
          class="btn btn-outline-secondary btn-sm"
          @click="saveDraft">임시저장</button>
        <button
          v-if="(isNew || isDraft) && isEditMode"
          class="btn btn-primary btn-sm"
          @click="submit">작성 완료</button>
        <button
          v-if="!isNew && isSubmitted"
          class="btn btn-dark btn-sm"
          @click="sendFinal">발송 확정</button>
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
        <button class="btn btn-outline-secondary btn-sm" @click="goList">
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
          class="btn btn-outline-secondary btn-sm"
          :disabled="!selectedTemplateId"
          @click="onRenameTemplate"
          title="선택한 템플릿 이름 변경"
        >
          이름 변경
        </button>

        <button
          class="btn btn-outline-danger btn-sm"
          :disabled="!selectedTemplateId"
          @click="onDeleteTemplate"
          title="선택한 템플릿 삭제"
        >
          삭제
        </button>

        <button
          class="btn btn-outline-primary btn-sm"
          @click="onSaveTemplate"
        >
          현재 구성 템플릿 저장
        </button>
      </div>
    </div>

    <!-- Step Nav (편집/신규 모드에서만 노출 — 읽기 모드는 요약 뷰) -->
    <ul v-if="isNew || isEditMode" class="nav nav-pills mb-3">
      <li class="nav-item" v-for="(s, i) in steps" :key="i">
        <button class="nav-link" :class="{ active: step === i }" @click="go(i)">
          <i
            v-if="stepStatus[i]"
            class="bi bi-check-circle-fill me-1"
            :class="step === i ? '' : 'text-success'"
          ></i>
          <i
            v-else
            class="bi bi-circle me-1"
            :class="step === i ? '' : 'text-danger'"
          ></i>
          {{ s }}
        </button>
      </li>
    </ul>

    <!-- 읽기 전용 안내 배너 -->
    <div
      v-if="!isNew && !isEditMode"
      class="alert alert-secondary d-flex align-items-center py-2 small mb-3"
      role="status"
    >
      <i class="bi bi-lock-fill me-2"></i>
      <span v-if="isDraft">읽기 전용입니다. 수정하려면 상단 <strong>[수정]</strong> 버튼을 누르세요.</span>
      <span v-else>읽기 전용 제안서입니다. 수정하려면 상단 <strong>[복사]</strong>로 새 초안을 만드세요.</span>
    </div>

    <!-- 읽기 모드: 요약 뷰 (현장 정보 + 제안 품목 + 합계). 상세 확인 시 STEP 이동 불필요 -->
    <div v-if="!isNew && !isEditMode" class="proposal-summary">
      <!-- 현장 정보 요약 -->
      <div class="card mb-3">
        <div class="card-header"><strong>현장 정보</strong></div>
        <div class="card-body">
          <dl class="row mb-0">
            <dt class="col-sm-2">현장명</dt><dd class="col-sm-4">{{ form.projectName || '-' }}</dd>
            <dt class="col-sm-2">담당자</dt><dd class="col-sm-4">{{ form.manager || '-' }}</dd>
            <dt class="col-sm-2">평형</dt><dd class="col-sm-4">{{ form.apartmentType || '-' }}</dd>
            <dt class="col-sm-2">세대수</dt><dd class="col-sm-4">{{ form.households ?? '-' }}</dd>
            <dt class="col-sm-2">작성일</dt><dd class="col-sm-4">{{ date(form.date) }}</dd>
            <dt class="col-sm-2">적용 부위</dt><dd class="col-sm-4">{{ form.areas.length ? form.areas.join(', ') : '-' }}</dd>
            <dt class="col-sm-2">제출처</dt><dd class="col-sm-4">{{ form.clientName || '-' }}</dd>
            <dt class="col-sm-2">비고</dt><dd class="col-sm-4">{{ form.note || '-' }}</dd>
          </dl>
        </div>
      </div>

      <!-- 제안 품목 + 합계 -->
      <div class="card">
        <div class="card-header d-flex justify-content-between align-items-center">
          <strong>제안 품목</strong>
          <small class="text-muted">총 {{ lines.length }}건</small>
        </div>
        <div class="card-body p-0">
          <EmptyState
            v-if="lines.length === 0"
            icon="bi-box-seam"
            message="담긴 품목이 없습니다."
          />
          <div v-else class="table-responsive">
            <table class="table table-sm mb-0 align-middle">
              <thead class="table-light">
                <tr>
                  <th>유형</th>
                  <th>품목</th>
                  <th>부위</th>
                  <th class="text-end">수량</th>
                  <th class="text-end">마진</th>
                  <th class="text-end">금액</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in lines" :key="r.uid">
                  <td>{{ r.category }}</td>
                  <td>
                    {{ r.vendorItemName }}
                    <div class="small text-muted">{{ r.mainItemCode }} · {{ r.vendorName }}</div>
                  </td>
                  <td>
                    {{ r.area }}
                    <div v-if="r.buildingType" class="small text-muted">{{ r.buildingType }}</div>
                  </td>
                  <td class="text-end">{{ number(r.qty) }}<span class="text-muted small ms-1">{{ r.unit }}</span></td>
                  <td class="text-end">{{ getAppliedMarginRate(r) }}%</td>
                  <td class="text-end">{{ won(r.finalAmount) }}</td>
                </tr>
              </tbody>
              <tfoot>
                <tr class="table-light fw-bold">
                  <td colspan="5" class="text-end">합계</td>
                  <td class="text-end">{{ won(grandTotal) }}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- 하단 상세 영역: 읽기 모드에서는 fieldset disabled로 실제 비활성화(키보드 우회 불가) -->
    <div v-if="isNew || isEditMode" class="detail-content-wrapper">
      <fieldset class="detail-content border-0 p-0 m-0" :disabled="!isEditMode">
        <!-- STEP 1: 기본 정보 -->
        <div v-if="step === 0" class="card p-3">
          <h5 class="mb-3">현장 기본 정보</h5>
          <div class="row g-3">
            <div class="col-md-6">
              <label class="form-label">현장명 *</label>
              <input ref="projectNameRef" v-model.trim="form.projectName" class="form-control" placeholder="예) OO 아파트 위생기구 납품" />
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
              <select ref="apartmentTypeRef" v-model="form.apartmentType" class="form-select">
                <option value="">선택하세요</option>
                <option v-for="t in apartmentTypeChoices" :key="t" :value="t">{{ t }}</option>
              </select>
            </div>
            <div class="col-md-3">
              <label class="form-label">세대수 *</label>
              <input ref="householdsRef" v-model.number="form.households" type="number" min="1" class="form-control" placeholder="예) 240" />
            </div>
            <div class="col-md-6">
              <label class="form-label">비고</label>
              <input v-model.trim="form.note" class="form-control" placeholder="현장 특이사항, 일정 등" />
            </div>
            <div class="col-md-6">
              <label class="form-label">제출처</label>
              <input v-model.trim="form.clientName" class="form-control" placeholder="예) OO건설" />
              <div class="form-text">견적서 머리글의 <code>貴下</code> 앞에 들어갑니다.</div>
            </div>
            <div class="col-md-12">
              <label class="form-label">견적서 조건 문구</label>
              <textarea v-model="form.quoteTerms" class="form-control" rows="4"
                        placeholder="비워 두면 기본 문구 4줄이 나갑니다. 한 줄에 하나씩 적어주세요."></textarea>
            </div>
          </div>

          <div class="text-end mt-3">
            <button class="btn btn-primary" @click="goNext">다음</button>
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
                  <div class="d-flex align-items-center gap-2">
                    <span class="fs-5 fw-semibold">{{ form.apartmentType || '-' }}</span>
                    <i class="bi bi-lock-fill text-muted small" aria-hidden="true" title="읽기 전용"></i>
                  </div>
                  <div class="form-text mt-2">STEP 1에서 선택한 값입니다. 변경하려면 STEP 1로 이동하세요.</div>
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
                    <div class="col-6" v-for="area in areaChoices" :key="area">
                      <div class="form-check">
                        <input class="form-check-input" type="checkbox" :id="`chk-area-${area}`" :value="area" v-model="form.areas" />
                        <label class="form-check-label" :for="`chk-area-${area}`">{{ area }}</label>
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
                    <div class="col-12" v-for="cat in categoryChoices" :key="cat">
                      <div class="form-check">
                        <input class="form-check-input" type="checkbox" :id="`chk-cat-${cat}`" :value="cat" v-model="form.requiredCategories" />
                        <label class="form-check-label" :for="`chk-cat-${cat}`">{{ cat }}</label>
                      </div>
                    </div>
                  </div>
                  <div class="form-text mt-2">예: 양변기, 비데, 세면기, 수전류, 악세사리 등</div>
                </div>
              </div>
            </div>
          </div>

          <div class="text-end mt-3">
            <button class="btn btn-outline-secondary me-2" @click="prev">이전</button>
            <button class="btn btn-primary" @click="goNext">다음</button>
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
              <ul class="list-group list-group-flush overflow-auto" style="max-height: var(--esti-proposal-scroll-height)">
                <li v-if="items.length === 0">
                  <EmptyState icon="bi-box-seam" message="등록된 제품이 없습니다.">
                    <template #cta>
                      <button
                        class="btn btn-outline-primary btn-sm"
                        type="button"
                        @click="goExcelUpload">
                        엑셀 업로드
                      </button>
                    </template>
                  </EmptyState>
                </li>
                <li
                  v-for="item in filteredItems"
                  :key="item.vendorItemPriceId"
                  class="list-group-item d-flex align-items-center"
                  @click="selectCandidate(item)"
                  @keydown.enter="selectCandidate(item)"
                  @keydown.space.prevent="selectCandidate(item)"
                  role="button"
                  tabindex="0"
                  :aria-label="`${item.productName} 선택`"
                  style="cursor:pointer"
                >
                  <img
                    :src="productImage(item.imageUrl)"
                    class="me-3 rounded"
                    style="width:50px;height:50px;object-fit:contain"
                    :alt="`${item.productName} 제품 이미지`"
                    loading="lazy"
                    decoding="async"
                    @error="onImgErr($event)"
                  />
                  <div class="flex-grow-1">
                    <div class="fw-bold">{{ item.productName }}</div>
                    <small class="text-muted">{{ item.mainItemCode }} · {{ item.vendorName}}</small>
                    <!--
                      구성 요약 (G-1) — 같은 품번의 여러 세트가 각각 행으로 오므로 이걸로 가른다.
                      제안서에 담을 때 세트가가 스냅샷되니, 여기서 맞는 구성을 골라야 금액이 맞는다.
                    -->
                    <div v-if="item.setSummary" class="small text-muted">{{ item.setSummary }}</div>
                    <div class="small text-muted">{{ item.specs }}</div> <!-- 규격 -->
                  </div>
                  <div class="text-end small flex-shrink-0">
                    <div class="text-muted">참고가</div>
                    <div class="fw-semibold">{{ item.unitPrice != null ? won(item.unitPrice) : '-' }}</div>
                  </div>
                </li>
              </ul>
            </div>
          </div>

          <!-- 중: 선택/입력 상세 -->
          <div class="col-md-3">
            <div class="card h-100">
              <div class="card-body d-flex flex-column">
                <div class="text-center mb-3 img-box">
                  <img
                    :src="productImage(candidate.imageUrl)"
                    class="rounded candidate-img"
                    :alt="candidate.productName ? `${candidate.productName} 제품 이미지` : '제품 이미지 없음'"
                    @error="onImgErr($event)"
                  />
                </div>
                <div class="mb-3">
                  <h6 class="mb-1 text-center">
                    {{ candidate.productName || '품목을 선택하세요' }}
                    <small v-if="candidate.mainItemCode" class="d-block text-muted">{{ candidate.mainItemCode }}</small>
                  </h6>
                  <dl class="row mb-0 small">
                    <dt class="col-4">브랜드</dt><dd class="col-8">{{ candidate.vendorName || '-' }}</dd>
                    <dt class="col-4">규격</dt><dd class="col-8">{{ candidate.specs || '-' }}</dd>
                    <dt class="col-4">원가</dt><dd class="col-8">{{ candidate.vendorProductId ? number(candidate.unitPrice) : '-' }}</dd>
                    <dt class="col-4">설명</dt><dd class="col-8">{{ candidate.description || '-' }}</dd>
                    <dt class="col-4">비고</dt><dd class="col-8">{{ candidate.remark || '-' }}</dd>
                    <dt class="col-4">단위</dt><dd class="col-8">{{ candidate.unit || '-' }}</dd>
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
                <div class="row g-2 mb-2">
                  <!-- 평형은 라인에서 고르지 않는다 — 한 제안서 = 한 평형이라 STEP 1 값이 서버에서 자동으로 들어간다 -->
                  <div class="col-12">
                    <label class="form-label">건물 구분</label>
                    <input
                      v-model.trim="lineInput.buildingType"
                      class="form-control"
                      list="building-type-options"
                      placeholder="선택 또는 직접 입력"
                    />
                    <datalist id="building-type-options">
                      <option v-for="b in buildingTypes" :key="b" :value="b" />
                    </datalist>
                  </div>
                </div>
                <div class="mb-2">
                  <label class="form-label">수량</label>
                  <input v-model.number="lineInput.qty" type="number" min="1" class="form-control" />
                </div>
                <div class="mb-2">
                  <label class="form-label">비고</label>
                  <input v-model.trim="lineInput.note" class="form-control" placeholder="색상/사양 등" />
                </div>
                <div class="form-check mb-2">
                  <!-- 제안서 엑셀에서 옵션 열(3열)로 모이고, 세대당 계약금액 합계에서는 빠진다 -->
                  <input class="form-check-input" type="checkbox" id="chk-line-optional" v-model="lineInput.optional" />
                  <label class="form-check-label" for="chk-line-optional">
                    선택사항(유상옵션)
                    <span class="text-muted small d-block">체크하면 계약금액 합계에서 제외됩니다</span>
                  </label>
                </div>

                <div class="mt-auto d-flex gap-2">
                  <button class="btn btn-primary btn-sm" :disabled="!candidate.vendorProductId || !lineValid" @click="addLine">
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

                <div v-else class="flex-grow-1 overflow-auto" style="max-height: var(--esti-proposal-scroll-height);">
                  <table class="table table-sm table-bordered mb-0 align-middle">
                    <thead class="table-light">
                    <tr>
                      <th>품목</th>
                      <th style="width:64px">수량</th>
                      <th style="width:116px">마진</th>
                      <th style="width:96px" class="text-end">금액</th>
                      <th style="width:40px"></th>
                      <th style="width:48px"></th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="(r, idx) in lines" :key="r.uid">
                      <td>
                        {{ r.vendorItemName }}
                        <div class="small text-muted">{{ r.mainItemCode }} · {{ r.vendorName }}</div>
                        <div class="small text-muted">{{ r.category }} · {{ r.area }}</div>
                        <div v-if="r.buildingType" class="small text-muted">{{ r.buildingType }}</div>
                        <div class="small text-muted">원가 {{ won(r.catalogUnitPrice) }} · 단위 {{ r.unit }}</div>
                        <span v-if="r.optional" class="badge bg-warning-subtle text-warning-emphasis">유상옵션</span>
                      </td>

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

                      <td class="text-end fw-semibold text-primary text-nowrap">{{ won(r.finalAmount) }}</td>

                      <td>
                        <div class="btn-group-vertical btn-group-sm w-100" role="group" aria-label="항목 순서 이동">
                          <button
                            class="btn btn-outline-secondary py-0"
                            type="button"
                            :disabled="idx === 0"
                            @click="moveLine(idx, -1)"
                            aria-label="위로 이동"
                            title="위로"
                          >
                            <i class="bi bi-chevron-up"></i>
                          </button>
                          <button
                            class="btn btn-outline-secondary py-0"
                            type="button"
                            :disabled="idx === lines.length - 1"
                            @click="moveLine(idx, 1)"
                            aria-label="아래로 이동"
                            title="아래로"
                          >
                            <i class="bi bi-chevron-down"></i>
                          </button>
                        </div>
                      </td>

                      <td>
                        <button class="btn btn-sm btn-outline-danger" @click="removeLine(idx)" aria-label="항목 삭제" title="삭제">
                          <i class="bi bi-trash"></i>
                        </button>
                      </td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- 합계 고정 표시 (스크롤 영역 밖 — 항상 노출) -->
              <div class="d-flex justify-content-between align-items-center px-3 py-2 border-top bg-body-tertiary">
                <span class="fw-semibold">합계 <small class="text-muted">({{ lines.length }}건)</small></span>
                <span class="fw-bold fs-6">{{ won(grandTotal) }}</span>
              </div>

              <div class="card-footer d-flex justify-content-between align-items-center">
                <div class="small text-muted">
                  필수유형 충족:
                  <span :class="missingRequired.length ? 'text-danger' : 'text-success'">
                    {{ missingRequired.length ? `미충족: ${missingRequired.join(', ')}` : '완료' }}
                  </span>
                </div>
                <div>
                  <button class="btn btn-outline-secondary btn-sm me-2" @click="prev">이전</button>
                  <button
                    class="btn btn-primary btn-sm"
                    v-if="isEditMode && (isNew || isDraft)"
                    @click="submit"
                  >
                    작성 완료
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div> <!-- /STEP 3 -->
      </fieldset>
    </div><!-- 하단 상세 영역 끝 -->
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import noImg from '@/assets/no-image.svg'
import { productImage } from '@/utils/image'
import { won, number, date } from '@/utils/format'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { usePrompt } from '@/composables/usePrompt'
// UNITS는 여기서 쓰지 않는다 — 제안서의 단위는 사용자가 고르는 값이 아니라 카탈로그에서 스냅샷된다.
import { UNIT_DEFAULT } from '@/constants/labels'
import { useMasterCodes, withSaved } from '@/composables/useMasterCodes'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const { confirm } = useConfirm()
const { promptInput } = usePrompt()

/* ====== STEP1 필드 refs (검증 실패 시 포커스 이동용) ====== */
const projectNameRef = ref(null)
const apartmentTypeRef = ref(null)
const householdsRef = ref(null)

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
// 평형·부위·카테고리·건물구분은 모두 마스터(/settings/master)에서 받는다
const {
  apartmentTypes: masterApartmentTypes,
  areas: masterAreas,
  categories: masterCategories,
  buildingTypes,
  load: loadMasterCodes,
} = useMasterCodes()
const marginOptions = [10, 15, 20, 25, 30] // 마진율

// 새 제안서의 초기값. 이 화면은 신규·상세를 겸하므로(`/proposal` ↔ `/proposal/:id`)
// 기본값을 한 곳에 두고 resetForm()이 같은 값을 다시 쓰게 한다 — 두 벌로 나뉘면 어긋난다.
function blankForm () {
  return {
    projectName: '',
    manager: '',
    date: new Date().toISOString().slice(0, 10),
    apartmentType: '',
    households: null,
    note: '',
    clientName: '',   // 제출처(건설사) — 견적서 머리글
    quoteTerms: '',   // 견적서 조건 문구(줄바꿈 구분). 비면 기본 문구가 나간다
    areas: [],
    requiredCategories: [],
    globalMarginRate: 10
  }
}

const form = reactive(blankForm())

// 선택지는 마스터 + 이 제안서가 이미 든 값의 합집합이다(withSaved 주석 참조)
const apartmentTypeChoices = computed(() => withSaved(masterApartmentTypes.value, form.apartmentType))
const areaChoices = computed(() => withSaved(masterAreas.value, form.areas))
const categoryChoices = computed(() => withSaved(masterCategories.value, form.requiredCategories))

/* ====== 카탈로그 ====== */
const search = ref('')
const items = ref([]) // /catalog/list 결과

/* 카테고리 연관검색어 사전 (A-3)
   검색어(키)를 카탈로그 categoryLarge/categorySmall 의 표제어(값)로 확장한다.
   값은 부분일치로 비교하므로 '수전'처럼 접미어만 적어도
   세면수전·주방수전·샤워수전·수전금구·수전부속이 모두 걸린다.

   표제어는 DB의 실제 categoryLarge 값 기준이다:
     양변기 · 소변기 · 세면기 · 비데 · 수채 · 욕조 · 샤워수전 · 세면수전 ·
     주방수전 · 수전금구 · 수전부속 · 악세사리 · 갈라시아 · 기타

   ⚠ 이 사전은 constants/labels.js 로 옮기지 않는다(Track C 소유).
     공용화는 Phase 5 머지 후 별도로 판단한다. */
const CATEGORY_SYNONYMS = {
  변기: ['양변기', '소변기'],
  좌변기: ['양변기'],
  세면대: ['세면기'],
  세면볼: ['세면기'],
  샤워기: ['샤워수전'],
  해바라기: ['샤워수전'],
  수도: ['수전'],
  수전: ['수전'],
  싱크: ['주방수전'],
  씽크: ['주방수전'],
  악세서리: ['악세사리'],
  액세사리: ['악세사리'],
  액세서리: ['악세사리'],
  소품: ['악세사리'],
}

const filteredItems = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return items.value

  // 검색어가 사전에 걸리면 대응 카테고리 표제어도 후보로 삼는다
  const categoryTerms = CATEGORY_SYNONYMS[q] ?? []

  return items.value.filter((i) => {
    const hit = [
      i.productName,
      i.vendorName,
      i.vendorItemName,
      i.mainItemCode,
      i.oldItemCode,
      i.remark,
      i.specs,
      i.categoryLarge, // 카테고리 자체도 검색 대상 ('변기' → 양변기·소변기가 직접 걸린다)
      i.categorySmall
    ]
      .filter(Boolean)
      .some((f) => String(f).toLowerCase().includes(q))

    if (hit) return true
    if (categoryTerms.length === 0) return false

    // 연관검색어 확장: 이름에 그 글자가 없어도 카테고리가 맞으면 포함
    const categories = [i.categoryLarge, i.categorySmall]
      .filter(Boolean)
      .map((c) => String(c).toLowerCase())

    return categoryTerms.some((term) => categories.some((c) => c.includes(term)))
  })
})

/* ====== 상세 선택 + 입력 ====== */
const candidate = reactive({
  vendorProductId: null,
  productName: '',
  vendorName: '',
  vendorItemName: '',
  mainItemCode: '',
  oldItemCode: '',
  unitPrice: 0,
  remark: '',
  specs: '',
  description: '',
  imageUrl: '',
  unit: UNIT_DEFAULT,
  categorySmall: ''
})

// 평형·건물구분은 라인마다 다를 수 있어(O-5, O-7) 담을 때 함께 고른다.
// 부위·카테고리와 달리 필수는 아니다 — 기존 제안서에 없던 값이라 비워 둔 채로도 저장된다.
const lineInput = reactive({
  area: '', category: '', qty: 1, note: '', buildingType: '', optional: false
})

const lineValid = computed(() =>
  lineInput.area && lineInput.category && lineInput.qty > 0
)

/* ====== 제안 항목 리스트 ====== */
const lines = reactive([])

/* ====== 공통 유틸 ====== */
function toNumber(value) { return Number(value ?? 0) }

/**
 * 서버가 준 사유를 그대로 보여준다. 없을 때만 fallback을 쓴다.
 *
 * 백엔드가 «현장명은 200자까지 입력할 수 있습니다.»처럼 어느 필드가 왜 걸렸는지 짚어 주는데,
 * 여기서 뭉뚱그려 "저장 중 오류가 발생했습니다"로 덮으면 그 정보가 사용자에게 닿지 않는다(F-024).
 * MasterSettingsView의 같은 이름 함수와 동작을 맞췄다.
 */
function errorMessage(e, fallback) {
  return e?.response?.data?.message || fallback
}

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
    vendorProductId: item.vendorProductId,
    productName: item.productName ?? '',
    vendorName: item.vendorName ?? '',
    vendorItemName: item.vendorItemName ?? '',
    mainItemCode: item.mainItemCode ?? '',
    oldItemCode: item.oldItemCode ?? '',
    unitPrice: toNumber(item.unitPrice),
    remark: item.remark ?? '',
    specs: item.specs ?? '',
    description: item.description ?? '',
    imageUrl: item.imageUrl ?? '',
    unit: item.unit ?? UNIT_DEFAULT,
    categorySmall: item.categorySmall ?? ''
    // area/category/평형/건물구분/옵션여부는 사용자가 선택
  })
}

function resetLine () {
  Object.assign(candidate, {
    vendorProductId: null,
    productName: '',
    vendorName: '',
    vendorItemName: '',
    mainItemCode: '',
    oldItemCode: '',
    unitPrice: 0,
    remark: '',
    specs: '',
    description: '',
    imageUrl: '',
    unit: UNIT_DEFAULT,
    categorySmall: ''
  })
  Object.assign(lineInput, { area: '', category: '', qty: 1, note: '', buildingType: '', optional: false })
}

/**
 * 새 제안서 진입 시 화면을 처음 상태로 되돌린다 (F-018).
 *
 * 이 컴포넌트는 `/proposal`(신규)과 `/proposal/:id`(상세)가 같이 쓴다. 라우트가 바뀌어도
 * 같은 컴포넌트라 다시 마운트되지 않으므로, 상세를 보다가 [+ 새 제안서]로 오면
 * 직전 제안서의 값이 그대로 남아 있었다 — 그대로 저장하면 복제본이 만들어졌다.
 * 이전에는 lines만 비웠다. form·step·검색어·템플릿 선택까지 함께 되돌린다.
 */
function resetForm () {
  Object.assign(form, blankForm())
  step.value = 0                 // 항상 STEP 1(기본 정보)에서 시작한다
  search.value = ''              // 카탈로그 검색어
  selectedTemplateId.value = ''  // 템플릿 선택
  resetLine()                    // 담기 전 후보/입력칸
}

/* ====== 마진 계산 ====== */
function getAppliedMarginRate(line) {
  if (line.useManualMargin && line.marginRate != null && line.marginRate !== '') {
    return toNumber(line.marginRate)
  }
  return toNumber(form.globalMarginRate)
}

/* 제안 품목 합계 금액 (요약 뷰 + 편집 STEP3에서 공유) */
const grandTotal = computed(() =>
  lines.reduce((sum, l) => sum + toNumber(l.finalAmount), 0)
)

function recalculateLine(line) {
  const base = toNumber(line.catalogUnitPrice)

  const rate = line.useManualMargin
    ? toNumber(line.marginRate)
    : toNumber(form.globalMarginRate)

  // 서버(ProposalService.calculateUnitPrice)와 동일한 결과가 나오도록
  // base * (1 + rate/100) 대신 (base * (100 + rate)) / 100 로 계산한다.
  // 전자는 1.15 같은 값이 2진 부동소수로 정확히 표현되지 않아 .5 경계에서 1원 어긋난다.
  const calculatedUnitPrice = Math.round((base * (100 + rate)) / 100)

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

    unit: data.unit ?? UNIT_DEFAULT,
    apartmentType: data.apartmentType ?? '',
    buildingType: data.buildingType ?? '',
    categorySmall: data.categorySmall ?? '',
    optional: data.optional ?? false,
  }
}

/* ====== 행 조작 ====== */
function addLine() {
  if (!candidate.vendorProductId || !lineValid.value) return

  const newLine = createLine({
    productId: candidate.vendorProductId,
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
    note: lineInput.note,

    unit: candidate.unit,
    // 서버가 제안서 평형으로 덮어쓴다. 여기서도 같은 값을 넣어 저장 전후 표시가 흔들리지 않게 한다.
    apartmentType: form.apartmentType,
    buildingType: lineInput.buildingType,
    categorySmall: candidate.categorySmall,
    optional: lineInput.optional
  })

  recalculateLine(newLine)
  lines.push(newLine)
  resetLine()
}

function removeLine(idx) {
  lines.splice(idx, 1)
}

/* 표시 순서 변경. buildPayload()가 lines 배열 순서를 그대로 보내고
   서버가 그 인덱스를 sortOrder로 저장하므로 배열만 재정렬하면 된다. */
function moveLine(idx, delta) {
  const target = idx + delta
  if (target < 0 || target >= lines.length) return
  const [moved] = lines.splice(idx, 1)
  lines.splice(target, 0, moved)
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

/* STEP3(품목) 표시용 유효성 — 규칙은 canSubmit과 동일, 아이콘 표시에만 사용 */
const validStep3 = computed(() => missingRequired.value.length === 0 && lines.length > 0)

/* 스텝 탭 완료 상태(✓/미완료) — 표시 전용 */
const stepStatus = computed(() => [validStep1.value, validStep2.value, validStep3.value])

/* ====== 검증 안내(조용한 disable 대신 클릭 시 부족 항목 안내) ====== */
// STEP1 부족 항목 안내 + 첫 미충족 필드 포커스
function warnStep1() {
  const missing = []
  if (!form.projectName) missing.push('현장명')
  if (!form.apartmentType) missing.push('평형')
  if (!(Number(form.households) > 0)) missing.push('세대수')
  toast.error(`다음 항목을 입력하세요: ${missing.join(', ')}`)
  step.value = 0
  nextTick(() => {
    if (!form.projectName) projectNameRef.value?.focus()
    else if (!form.apartmentType) apartmentTypeRef.value?.focus()
    else householdsRef.value?.focus()
  })
}

// STEP2 부족 항목 안내
function warnStep2() {
  const missing = []
  if (!form.apartmentType) missing.push('평형')
  if (form.areas.length === 0) missing.push('적용 부위')
  if (form.requiredCategories.length === 0) missing.push('필수 위생기구 유형')
  step.value = 1
  toast.error(`다음 항목을 선택하세요: ${missing.join(', ')}`)
}

// '다음' 버튼: 활성 유지, 클릭 시 현재 스텝 검증 → 통과 시에만 진행
function goNext() {
  if (step.value === 0 && !validStep1.value) return warnStep1()
  if (step.value === 1 && !validStep2.value) return warnStep2()
  next()
}

// 저장/제출 실패 시 부족한 스텝으로 이동 + 안내
function warnSubmit() {
  if (!validStep1.value) return warnStep1()
  if (!validStep2.value) return warnStep2()
  if (lines.length === 0) {
    step.value = 2
    return toast.error('제안 항목을 최소 1개 이상 추가하세요.')
  }
  if (missingRequired.value.length > 0) {
    step.value = 2
    return toast.error(`미충족 유형: ${missingRequired.value.join(', ')}`)
  }
}

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
        // 평형·건물구분은 현장별 값이라 템플릿에 없다. 나머지는 이어받는다.
        unit: line.unit,
        categorySmall: line.categorySmall,
        optional: line.optional,
      }))
      recalculateLine(lines[lines.length - 1])
    })

    // UX: 바로 Step 2 또는 3으로 이동해도 좋음
    step.value = 2
    toast.success('템플릿을 불러왔습니다.')
  } catch (e) {
    console.error('템플릿 불러오기 실패', e)
    toast.error('템플릿을 불러오지 못했습니다.')
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
      unit: l.unit,
      categorySmall: l.categorySmall,
      optional: l.optional,
    }))
  }
}

/* ====== 템플릿 저장  ====== */
async function onSaveTemplate () {
  // 최소한의 유효성 체크 (원하면 더 강화 가능)
  if (!validStep1.value || !validStep2.value) {
    toast.error('먼저 기본 정보와 적용 부위/필수 유형을 모두 입력하세요.')
    return
  }
  if (lines.length === 0) {
    toast.error('제안 항목이 없습니다. 최소 1개 이상 추가 후 저장하세요.')
    return
  }

  const nameDefault = form.projectName || `${form.apartmentType} 기본 구성`
  const templateName = await promptInput({
    title: '템플릿 저장',
    label: '템플릿 이름',
    placeholder: '예) 84㎡ 기본 구성',
    defaultValue: nameDefault,
  })
  if (!templateName) return

  const payload = buildTemplatePayload(templateName)

  try {
    await axios.post('/api/proposal-templates', payload)
    await fetchTemplates()
    toast.success('템플릿이 저장되었습니다.')
  } catch (e) {
    console.error('템플릿 저장 실패', e)
    toast.error('템플릿 저장 중 오류가 발생했습니다.')
  }
}

/* ====== 템플릿 이름 변경 (A-4) ======
   PUT /api/proposal-templates/{id} 는 본문 전체를 덮어쓰고 라인도 삭제 후 재생성한다.
   (ProposalTemplateService.update 참고) 따라서 이름만 바꾸려면
   상세를 먼저 읽어 그대로 되돌려주고 templateName만 교체해야 한다. */
async function onRenameTemplate() {
  if (!selectedTemplateId.value) return

  const current = templates.value.find((t) => String(t.id) === String(selectedTemplateId.value))

  const templateName = await promptInput({
    title: '템플릿 이름 변경',
    label: '템플릿 이름',
    placeholder: '예) 84㎡ 기본 구성',
    defaultValue: current?.templateName ?? '',
  })
  if (!templateName) return
  if (templateName === current?.templateName) return

  try {
    // 기존 본문을 그대로 유지하기 위해 상세를 먼저 읽는다
    const { data: t } = await axios.get(`/api/proposal-templates/${selectedTemplateId.value}`)

    await axios.put(`/api/proposal-templates/${selectedTemplateId.value}`, {
      templateName,
      apartmentType: t.apartmentType,
      areas: t.areas || [],
      requiredCategories: t.requiredCategories || [],
      lines: (t.lines || []).map((l) => ({
        id: l.id,
        productId: l.productId,
        specs: l.specs,
        description: l.description,
        imageUrl: l.imageUrl,
        vendorCode: l.vendorCode,
        vendorName: l.vendorName,
        vendorItemName: l.vendorItemName,
        mainItemCode: l.mainItemCode,
        oldItemCode: l.oldItemCode,
        unitPrice: l.unitPrice,
        remark: l.remark,
        area: l.area,
        category: l.category,
        defaultQty: l.defaultQty,
        note: l.note,
        unit: l.unit,
        categorySmall: l.categorySmall,
        optional: l.optional,
      })),
    })

    await fetchTemplates()
    toast.success('템플릿 이름을 변경했습니다.')
  } catch (e) {
    console.error('템플릿 이름 변경 실패', e)
    toast.error('템플릿 이름 변경 중 오류가 발생했습니다.')
  }
}

/* ====== 템플릿 삭제 (A-4) ====== */
async function onDeleteTemplate() {
  if (!selectedTemplateId.value) return

  const current = templates.value.find((t) => String(t.id) === String(selectedTemplateId.value))

  const ok = await confirm({
    title: '템플릿 삭제',
    message: `템플릿 [${current?.templateName ?? selectedTemplateId.value}] 를 삭제할까요? 되돌릴 수 없습니다.`,
    confirmLabel: '삭제',
  })
  if (!ok) return

  try {
    await axios.delete(`/api/proposal-templates/${selectedTemplateId.value}`)
    selectedTemplateId.value = ''
    await fetchTemplates()
    toast.success('템플릿이 삭제되었습니다.')
  } catch (e) {
    console.error('템플릿 삭제 실패', e)
    toast.error('템플릿 삭제 중 오류가 발생했습니다.')
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
    clientName: form.clientName,
    quoteTerms: form.quoteTerms,
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

      unit: l.unit,
      apartmentType: l.apartmentType,
      buildingType: l.buildingType,
      categorySmall: l.categorySmall,
      optional: l.optional,
    }))
  }
}

/* ====== 제안서 임시 저장 ====== */
async function saveDraft () {
  if (!canDraft.value) {
    toast.error('현장명을 입력해야 임시저장할 수 있어요.')
    step.value = 0
    nextTick(() => projectNameRef.value?.focus())
    return
  }

  const payload = buildPayload()

  try {
    if (isNew.value) {
      // 초안 신규 생성
      const res = await axios.post('/api/proposals/drafts', payload)
      toast.success(`임시저장되었습니다. (ID: ${res.data.id})`)

      // 같은 컴포넌트 재사용될 수 있어서 replace 추천
      await router.replace({ name: 'proposal-detail', params: { id: res.data.id } })

      // 초안 만든 직후에도 계속 편집 모드 유지
      isEditMode.value = true
      proposalStatus.value = 'DRAFT'
    } else {
      // 이미 id가 있으면 초안 업데이트(백엔드에 맞게 PUT/PATCH)
      await axios.put(`/api/proposals/${proposalId.value}/draft`, payload)
      toast.success('임시저장되었습니다.')
      proposalStatus.value = 'DRAFT'
    }
  } catch (e) {
    console.error('임시저장 실패', e)
    toast.error(errorMessage(e, '임시저장 중 오류가 발생했습니다.'))
  }
}


/* ====== 제안서 저장 ====== */
async function submit() {
  if (!canSubmit.value) {
    warnSubmit()
    return
  }

  const payload = buildPayload()

  try {
    if (isNew.value) {
      // 신규: 생성 + 저장
      const res = await axios.post('/api/proposals/submit', payload)
      toast.success(`제안서가 저장되었습니다. (ID: ${res.data.id})`)
      await router.replace({ name: 'proposal-detail', params: { id: res.data.id } })
      proposalStatus.value = res.data.status || 'SUBMITTED'
      isEditMode.value = false
      return
    }
    // 기존: id 기반 제출
    const res = await axios.post(`/api/proposals/${proposalId.value}/submit`, payload)

    toast.success('저장되었습니다.')
    proposalStatus.value = res.data?.status || 'SUBMITTED'
    isEditMode.value = false
  } catch (e) {
    console.error('제안서 저장 실패', e)
    toast.error(errorMessage(e, '제안서 저장 중 오류가 발생했습니다.'))
  }
}

/* ====== 제안서 발송 확정  ====== */
async function sendFinal() {
  const ok = await confirm({
    title: '발송 확정',
    message: `제안서 #${proposalId.value} [${form.projectName}] 를 발송 확정할까요? 확정하면 최종본이 되어 수정할 수 없습니다.`,
    confirmLabel: '발송 확정',
  })
  if (!ok) return

  try {
    await axios.post(`/api/proposals/${proposalId.value}/send`)
    toast.success('발송 확정되었습니다.')
    proposalStatus.value = 'SENT'
  } catch (e) {
    console.error('전송 확정 실패', e)
    toast.error('발송 확정에 실패했습니다.')
  }
}

/* ====== 복사하여 수정 (새 DRAFT 생성) ====== */
async function copyToDraft() {
  try {
    const res = await axios.post(`/api/proposals/${proposalId.value}/copy`)
    const newId = res.data.id
    toast.success(`복사본이 생성되었습니다. (ID: ${newId})`)
    await router.push({ name: 'proposal-detail', params: { id: newId } })
    proposalStatus.value = 'DRAFT'
    isEditMode.value = true
  } catch (e) {
    console.error('복사 실패', e)
    toast.error('복사에 실패했습니다.')
  }
}

/* ====== 제안서 삭제 ====== */
async function deleteProposal() {
  if (!canDelete.value) {
    toast.error('전송 완료된 최종 제안서는 삭제할 수 없습니다.')
    return
  }
  const ok = await confirm({
    title: '제안서 삭제',
    message: `제안서 #${proposalId.value} [${form.projectName}] 를 삭제할까요?`,
    confirmLabel: '삭제',
  })
  if (!ok) return

  try {
    await axios.delete(`/api/proposals/${proposalId.value}`)
    toast.success('삭제되었습니다.')
    router.push({ name: 'proposal-list' })
  } catch (e) {
    console.error('제안서 삭제 실패', e)
    toast.error('제안서 삭제 중 문제가 발생했습니다.')
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
    form.clientName = p.clientName ?? ''
    form.quoteTerms = p.quoteTerms ?? ''

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
        unit: l.unit,
        apartmentType: l.apartmentType,
        buildingType: l.buildingType,
        categorySmall: l.categorySmall,
        optional: l.optional,
      }))
    })

    // 상세 보기 모드로 시작
    isEditMode.value = false
    step.value = 0
    proposalStatus.value = p.status || 'DRAFT'
  } catch (e) {
    console.error('제안서 불러오기 실패', e)
    toast.error('제안서를 불러오지 못했습니다.')
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
      resetForm()
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
  loadMasterCodes()
  if (isNew.value) isEditMode.value = true // 신규 작성과 상세/수정 모드 구분
})
</script>

<style scoped>
/* fieldset을 레이아웃 컨테이너로만 사용(기본 테두리/여백 제거) */
.detail-content {
  border: 0;
  padding: 0;
  margin: 0;
  min-inline-size: auto; /* fieldset 기본 min-width 제거로 flex/grid 레이아웃 유지 */
}

/* 키보드 포커스 가시화 (H-9 접근성) */
.list-group-item[role="button"]:focus-visible {
  outline: 2px solid var(--bs-primary);
  outline-offset: -2px;
  z-index: 1;
}

/* 제품 상세 이미지 표시 */
.img-box {                 /* 이미지 표시 영역 */
  height: 250px;           /* 모든 카드의 이미지 영역 높이를 동일하게 유지 */
  display: flex;
  justify-content: center; /* 이미지 가로 중앙 정렬 */
  align-items: center;     /* 이미지 세로 중앙 정렬 */
  overflow: hidden;        /* 영역을 벗어나는 이미지 숨김 */
}
.candidate-img {           /* 이미지 */
  max-width: 100%;         /* 가로 크기가 영역을 초과하지 않도록 제한 */
  max-height: 100%;        /* 세로 크기가 영역을 초과하지 않도록 제한 */
  object-fit: contain;     /* 이미지 비율을 유지하면서 전체가 보이도록 축소 */
}
</style>
