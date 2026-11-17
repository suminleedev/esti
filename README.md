# Esti

> 🚧 개발 진행 중인 프로젝트입니다.
> 현재 핵심 기능(크롤링, 엑셀 업로드, 제안서, 엑셀 출력)은 구현되어 있으며,
> 추가 기능은 지속적으로 확장 중입니다.

욕실 제품 견적서 관리 및 제조사 데이터 수집(크롤링 + 엑셀 업로드) 시스템입니다.

Spring Boot 백엔드와 Vue 3 프론트엔드로 구성되어 있으며, **제품 데이터 수집 → 제안서 생성 → 엑셀 출력** 흐름을 중심으로 동작합니다.

## 데모

**https://estiapp.dev**

* 합성 데이터(가상 공급사 A사·B사, 플레이스홀더 이미지)로 동작합니다. 실제 단가·제품 정보는 들어 있지 않습니다.
* 누구나 제안서를 만들고 고칠 수 있으며, 데이터는 예고 없이 초기 상태로 되돌려질 수 있습니다.
* 크롤러는 데모에서 꺼져 있고 관리자 기능은 잠겨 있습니다. 업로드 한도는 2MB입니다.
* 구성: AWS EC2 한 대(Docker Compose — Caddy · Spring Boot · PostgreSQL) + Cloudflare(HTTPS 프록시). 매일 백업합니다.

---

## 프로젝트 소개

이 프로젝트는 욕실/위생도기 제품 데이터를 기반으로 제안서와 견적서를 효율적으로 생성하고 관리하기 위해 만들어졌습니다.

특히 다음 문제를 해결하는 것을 목표로 합니다.

* 제조사 제품 정보가 여러 사이트에 분산되어 있어 흩어진 제품 정보를 수집하기 어려움
* 엑셀 기반 견적 작업의 반복성과 비효율성
* 제품 이미지 및 상세 정보 관리의 불편함

이를 해결하기 위해

* 공급사 단가표 엑셀 업로드 (세트·부속 구성까지 적재)
* 제조사 사이트 크롤링을 통한 제품 이미지 수집
* 제안서 작성(템플릿·마진율) 및 상태 관리
* 제안서(고객 제출용)·견적서(내부 검토용) 엑셀 출력

기능을 중심으로 구현되었습니다.

> 크롤러는 카탈로그에 이미 있는 제품에 **이미지를 붙이는 용도**로만 쓰며, 관리자가 수동으로 실행합니다.
> 요청 사이에 간격을 두고, 제조사별 동시 실행 잠금과 재실행 쿨다운으로 같은 사이트에 요청이 겹치지 않게 합니다.
> 공개 데모에서는 크롤러가 동작하지 않습니다.

---

## 구현 상태

### ✅ 완료된 기능

**카탈로그(제품 데이터)**

* 공급사 단가표 엑셀(.xlsx/.xls) 업로드 — 비동기 처리, 진행률 조회, 완료 시 신규·갱신 건수 표시
* 업로드 전 검증 — 공급사 코드·빈 파일·확장자, 파일이 선택한 공급사의 양식인지까지 확인해 즉시 거절
* 세트(대표품목)–부속 구성 적재, 부속 합계와 세트가 대조
* 엑셀에 들어 있는 제품 이미지 추출 (EMF/WMF 벡터 그림은 PNG로 변환)
* 재업로드 시 코드 기준 갱신 + 최신본에서 사라진 행 정리
* 카탈로그 서버 검색·공급사 필터·페이징, 행 수정·삭제, 부속 구성 펼쳐 보기
* 공급사별 샘플 엑셀 내려받기

**크롤러(제품 이미지)**

* 제조사 사이트 크롤링으로 카탈로그 제품에 이미지 연결 (2개 제조사)
* 품번이 정확히 일치하는 제품에만 반영 — 색상·사양 변형 제품에 엉뚱한 사진이 붙지 않도록
* dry-run(반영 없이 매칭 결과만 확인)과 실행 리포트(수집·매칭·반영 건수)
* 제조사별 동시 실행 잠금, 재실행 쿨다운, 실행 상태 조회

**제안서**

* 제안서 CRUD (생성 / 조회 / 수정 / 삭제)
* 제안서 상태 관리 (임시저장 → 작성완료 → 발송완료, 복사)
* 단계별 작성 화면 — 기본 정보 → 평형·적용 부위·필수 유형 → 카탈로그에서 품목 채우기
* 일괄 마진율 + 품목별 개별 마진율, 단가·금액은 서버에서 다시 계산
* 제안서 템플릿 관리 (현재 구성 저장·불러오기·이름 변경·삭제)
* 제안서 목록 페이징 조회 + 서버 필터 (현장명/담당자·평형·템플릿 기반 여부·상태)
* 동시 수정 충돌 감지 — 화면이 가진 버전과 다르면 저장 거절
* 입력값 길이·범위 검증 (금액이 음수가 되는 입력 차단)

**엑셀 출력**

* 제안서(고객 제출용) — 제품 이미지가 들어간 카드 그리드, 사입가·마진 미포함
* 견적서(내부 검토용) — 평형별 본세대 / 부속동·상가 합본, 집계 수식, 사입가·마진 포함
* 견적번호 자동 부여, 제출처·조건 문구·합계 금액 한글 표기

**설정·화면**

* 마스터 관리 — 아파트 평형·건물 구분·적용 부위·적용 카테고리를 화면에서 추가·수정·숨김·순서 변경
* 화면 색상 테마 3종 선택
* 공통 알림(토스트)·확인 창, 빈 상태 안내, 없는 주소 안내 화면

**운영·배포**

* 실행 프로파일 분리 — `local`(실사용, Derby) / `demo`(공개 데모, PostgreSQL)
* 관리자 API(`/api/admin/**`) HTTP Basic 인증 (`local`만 해제)
* CI — 백엔드 테스트를 Derby·PostgreSQL 두 DB로 실행
* 공개 데모 배포 — 단일 jar 이미지, EC2 + Docker Compose, Cloudflare HTTPS, 매일 백업

### 🚧 개발 예정

* 제조사 데이터 자동 매칭 로직 고도화 — 현재는 품번이 정확히 일치할 때만 이미지 연결
* 크롤링 자동화 (배치/스케줄링) — 현재는 관리자가 수동 실행
* 제조사 확장 — 현재 2개 제조사
* 수량산출서에서 구성 품목을 템플릿으로 자동 추가 (원본 양식 확보 후)

---

## 주요 기능

### 1. 제조사 제품 데이터 수집

#### 엑셀 업로드

공급사 단가표 엑셀을 올리면 비동기로 파싱·적재하고, 화면은 `jobId`로 진행률을 조회합니다.

1. 요청 검증 — 공급사 코드, 빈 파일, 확장자(.xlsx/.xls), 선택한 공급사 양식과의 일치 여부 (실패하면 400, 작업을 만들지 않음)
2. 업로드 파일을 임시 저장하고 `jobId` 반환
3. 공급사별 파서(`VendorExcelParserFactory`)로 대표품목·부속 묶음과 엑셀 속 이미지 추출
4. 코드 기준 upsert — 다시 올려도 중복 없이 갱신, 최신본에서 사라진 행은 정리
5. 완료되면 진행률에 신규·갱신 건수 표시

#### 크롤링

* 제조사 사이트에서 품번과 이미지를 읽어, 카탈로그에 **이미 있는** 세트 제품에 이미지 연결
* 카탈로그의 정본은 단가표 — 크롤러는 제품을 새로 만들거나 제품명·가격을 바꾸지 않음
* 제조사별 크롤러(`ProductImageCrawler`)와 저장 핸들러(`ManufacturerProductSyncHandler`)를 `@Component`로 추가하면 `ProductImageSyncService`가 제조사 식별자로 골라 쓰는 구조

---

### 2. 제안서 관리

* 제안서 생성 / 조회 / 수정 / 삭제
* 템플릿 관리를 통해 자주 쓰는 품목 구성을 저장하고, 불러와서 한 번에 채움
* 마진율 반영 시 일괄 마진율(`globalMarginRate`) 및 품목별 개별 마진율(`manualMargin=true`인 품목의 `marginRate`) 병행
  * 단가 = 원가 × (100 + 마진율) / 100 (원 단위 반올림), 금액 = 단가 × 수량
  * 클라이언트가 보낸 단가·금액은 쓰지 않고 서버가 다시 계산
* 저장할 때 화면이 받아 둔 `version`을 함께 보내고, 그사이 다른 곳에서 바뀌었으면 409로 거절

상태 흐름

| 상태 | 화면 표시 | 화면에서 수정 | 삭제 | 엑셀 출력 |
|---|---|---|---|---|
| `DRAFT` | 임시저장 | 가능 | 하드 삭제 | 불가 |
| `SUBMITTED` | 작성완료 | 불가 (복사해서 새 초안) | 소프트 삭제 | 불가 |
| `SENT` | 발송완료 | 불가 (복사해서 새 초안) | 불가 | 가능 |

---

### 3. 엑셀 출력

발송완료(`SENT`) 제안서만 출력할 수 있습니다. 받는 사람이 달라서 양식과 엔드포인트를 나눴습니다.

| | 제안서 | 견적서 |
|---|---|---|
| 용도 | 고객 제출용 | 내부 검토용 |
| 형태 | 4열 카드 그리드, 제품 이미지 포함 | 표 + 집계 (항목 → 소분류 소계 → 섹션 소계 → 합계) |
| 사입가·마진 | 없음 | 있음 — 수식이라 사입가를 고치면 마진이 따라 바뀜 |
| 출력 단위 | 제안서 1부 | 평형별 본세대 각 1부, 부속동·상가 합본 1부 |
| 파일명 | `제안서_{현장명}_{yyMMdd}.xlsx` | `견적서_{현장명}_{대상}_{yyMMdd}.xlsx` |

* 견적번호는 첫 견적서 출력 때 부여하고, 이후에는 같은 번호를 씀
* 견적서에는 제출처, 조건 문구(비워 두면 기본 문구), 합계 금액 한글 표기, 서명란 대표이사명(`COMPANY_CEO`)이 들어감
* 선택사항(유상옵션) 품목은 제안서에서 별도 열로 모이고 세대당 금액 합계에서 빠짐

---

### 4. 이미지 처리

* 이미지 출처는 두 가지 — 업로드한 엑셀 속 그림, 크롤러가 내려받은 제조사 이미지
* `./uploads/product-images/`에 저장하고 `/uploads/**` 경로로 서빙 (DB에는 `/uploads/product-images/...` 경로를 저장)
* 프론트는 `BASE_URL`(`VITE_API_BASE_URL`, 비우면 같은 오리진) + 저장된 경로로 이미지 주소를 조합
* EMF/WMF 벡터 그림은 저장할 때 PNG로 변환
* 제안서 엑셀의 카드마다 제품 이미지를 비율을 유지해 칸에 맞춰 삽입

---

### 5. 마스터 관리

* 제안서 화면의 선택지(아파트 평형·건물 구분·적용 부위·적용 카테고리)를 `/settings/master` 화면에서 관리
* 삭제는 숨김(소프트 삭제) — 그 값을 이미 쓴 제안서는 그대로
* 기동할 때 종류별로 값이 하나도 없으면 기본값을 넣음
* 같은 화면에서 화면 색(청록·쪽빛·클레이) 선택 — 브라우저에 저장

---

### 6. 화면 구성

| 경로 | 화면 |
|---|---|
| `/` | 홈 — 바로가기, 최근 제안서 |
| `/upload` | 카탈로그 관리 — 단가표 업로드, 카탈로그 조회·수정 |
| `/proposal` | 새 제안서 작성 |
| `/proposal/:id` | 제안서 상세·수정 |
| `/proposal/list` | 제안서 목록 — 검색, 엑셀 출력 |
| `/settings/master` | 마스터 관리 — 선택지, 화면 색 |

---

## 저장소 구성

```text
esti
├── src/                 # Spring Boot 백엔드 (main / test)
├── esti-vue/            # Vue 3 + Vite 프론트엔드
├── deploy/              # 데모 배포 — Dockerfile, Docker Compose(Caddy·앱·PostgreSQL), EC2·백업·초기화 스크립트
├── .github/workflows/   # CI — 백엔드 테스트(Derby·PostgreSQL), 데모 이미지 빌드·푸시
├── pom.xml              # Maven 설정 (-Pfrontend: 프론트까지 담은 단일 jar)
└── mvnw, mvnw.cmd       # Maven Wrapper
```

---

## 기술 스택

### Backend

* Java 17
* Spring Boot 3.5.6
* Spring Web
* Spring Data JPA
* Bean Validation (요청 값 검증)
* Spring Security (관리자 API HTTP Basic)
* Apache Derby (파일 기반 DB, `local`)
* PostgreSQL (`demo`)
* Lombok
* Apache POI (엑셀 처리, EMF/WMF 이미지 변환)
* Jsoup (크롤링)
* JUnit 5 · Testcontainers (테스트, PostgreSQL 컨테이너)

### Frontend

* Vue 3
* Vite
* Vue Router
* Axios
* Bootstrap 5 · Bootstrap Icons
* Vitest (단위 테스트) · ESLint · Prettier

### 배포 (데모)

* Docker Compose (Caddy · Spring Boot · PostgreSQL)
* AWS — EC2, ECR(이미지), S3(백업), SSM Parameter Store(비밀값)
* Cloudflare (DNS · HTTPS 프록시)
* GitHub Actions (테스트, 이미지 빌드·푸시 — AWS 인증은 OIDC)

---

## 백엔드 구조

```text
src/main/java/com/example/esti
├── controller   # API
├── service      # 비즈니스 로직 (제안서·템플릿·카탈로그 적재/조회·마스터 코드·견적번호)
├── repository   # DB 접근 (Spring Data JPA, 목록 검색 조건)
├── entity       # 엔티티
├── dto          # 요청/응답
├── excel        # 공급사 단가표 엑셀 파싱 (공급사별 파서, 엑셀 속 이미지 추출)
├── output       # 제안서·견적서 엑셀 출력
├── crawler      # 제조사 이미지 크롤링 (공통 / 제조사별 / 동기화 서비스)
├── progress     # 업로드 진행률 (인메모리)
├── exception    # 예외 → HTTP 상태·메시지
├── config       # 보안·CORS·비동기, 마스터 코드 기본값, 데모 시드
└── util         # 이미지 경로, 금액 한글 표기, 벡터 이미지 변환
```

---

## 도메인 구조

### 1. 제안서 도메인

Proposal (제안서)

* 견적서의 메인 정보 — 현장명, 평형, 세대수, 제출처, 견적서 조건 문구
* 상태, 일괄 마진율(globalMarginRate), 견적번호 포함
* 버전(`version`)으로 동시 수정 충돌 감지

ProposalLine (제안서 상세)

* 제안서에 포함된 제품 — 담는 시점의 카탈로그 값(품명·품번·원가)을 복사해 저장
* 수량, 마진율, 단가, 금액 관리
* 적용 부위·유형·건물 구분·선택사항(유상옵션) — 엑셀 출력의 배치 기준

ProposalTemplate / ProposalTemplateLine (제안서 템플릿)

* 자주 쓰는 품목 구성

관계:
Proposal 1 : N ProposalLine
ProposalTemplate 1 : N ProposalTemplateLine
Proposal N : 1 ProposalTemplate (템플릿으로 만든 경우)

---

### 2. 제조사 제품 도메인

Vendor (공급사)

* 공급사 코드(`A`/`B`)와 이름

VendorProduct (공급사 제품)

* 공급사 단가표 기준 제품 — 품번(대표·상세), 제품명, 대·소분류, 시리즈명
* 이미지 경로, 제조사 상세 페이지 URL

VendorItemPrice (가격 정보)

* 제품 가격을 별도로 관리 — 세트(SET)·부속(PART) 가격행
* 같은 품번이라도 부속 구성이 다르면 가격행이 갈림 (구성 요약 포함)

VendorProductRelation (세트 구성)

* 세트(대표품목) → 부속 연결, 수량과 세트별 부속명

관계:
Vendor 1 : N VendorProduct
VendorProduct 1 : N VendorItemPrice
VendorProduct 1 : N VendorProductRelation (세트 → 부속)

---

### 3. 설정·기록

MasterCode (마스터 코드)

* 아파트 평형·건물 구분·적용 부위·적용 카테고리를 한 테이블에서 종류로 구분

SyncRun (실행 기록)

* 마지막 카탈로그 업로드·크롤링 시각 — 크롤러 쿨다운과 상태 판단에 사용

---

### 4. 특징

* 제안서와 제조사 데이터는 분리된 구조 — 제안서 라인은 카탈로그 값을 복사해 두므로, 단가표가 바뀌어도 이미 만든 제안서의 금액은 변하지 않음
* 카탈로그의 정본은 공급사 단가표 — 크롤러는 이미지만 붙이고 제품명·가격은 바꾸지 않음
* 가격 정보는 별도 엔티티로 관리

---

## API

### 엔드포인트 요약

| 메서드 | 경로 | 설명 |
|---|---|---|
| **제안서** | | |
| POST | `/api/proposals/drafts` | 임시저장 (신규) |
| PUT | `/api/proposals/{id}/draft` | 임시저장 수정 (임시저장 상태만) |
| POST | `/api/proposals/submit` | 신규 작성 후 바로 작성 완료 |
| POST | `/api/proposals/{id}/submit` | 작성 완료 (발송완료 건 제외) |
| POST | `/api/proposals/{id}/send` | 발송 확정 (작성완료 상태만) |
| POST | `/api/proposals/{id}/copy` | 복사해서 새 임시저장 만들기 |
| GET | `/api/proposals` | 전체 목록 |
| GET | `/api/proposals/page` | 페이징 목록 (`keyword`, `apartmentType`, `templateFilter`, `status`) |
| GET | `/api/proposals/{id}` | 단건 조회 |
| DELETE | `/api/proposals/{id}` | 삭제 (발송완료 건은 불가) |
| GET | `/api/proposals/{id}/export/proposal` | 제안서 엑셀 |
| GET | `/api/proposals/{id}/export/quote-targets` | 견적서로 뽑을 수 있는 대상 목록 |
| GET | `/api/proposals/{id}/export/quote` | 견적서 엑셀 (`kind=MAIN`/`ANNEX`, `apartmentType`) |
| **제안서 템플릿** | | |
| GET · POST | `/api/proposal-templates` | 목록 · 생성 |
| GET · PUT · DELETE | `/api/proposal-templates/{id}` | 조회 · 수정 · 삭제 |
| **카탈로그** | | |
| POST | `/api/vendor-catalog/upload-excel/{vendorCode}` | 단가표 엑셀 업로드 |
| GET | `/api/vendor-catalog/upload-progress/{jobId}` | 업로드 진행률 |
| GET | `/api/vendor-catalog/vendors` | 공급사 선택지 |
| GET | `/api/vendor-catalog/page/{vendorCode}` · `/api/vendor-catalog/page/` | 공급사별 · 전체 페이징 (`keyword` 검색) |
| GET | `/api/vendor-catalog/list` | 전체 목록 (제안서 작성 화면용) |
| GET | `/api/vendor-catalog/{vendorItemPriceId}/parts` | 세트의 부속 구성 |
| PUT · DELETE | `/api/vendor-catalog/{vendorItemPriceId}` | 가격행 수정(전체 교체) · 삭제 |
| **마스터 코드** | | |
| GET | `/api/master-codes/options` | 종류별 선택지 (숨김 제외) |
| GET · POST | `/api/master-codes` | 종류별 목록 (`type`) · 추가 |
| PUT | `/api/master-codes/{id}` · `/api/master-codes/reorder` | 수정 · 순서 변경 |
| DELETE | `/api/master-codes/{id}` | 숨김 (소프트 삭제) |
| **관리자** | | |
| POST | `/api/admin/crawler/{maker}/images` | 이미지 동기화 (`dryRun`, `force`) |
| GET | `/api/admin/crawler/{maker}/status` | 실행 상태 (실행 중·쿨다운·업로드 이후 반영 여부) |

오류는 `{"status": 409, "message": "..."}` 형태로 돌려줍니다.

| 상태 | 언제 |
|---|---|
| 400 | 입력 형식·길이·범위 오류, 필수 값 누락, 업로드 거절(공급사 코드·파일·양식) |
| 404 | 없는 제안서·카탈로그 행·경로 |
| 409 | 상태 규칙 위반(발송완료 건 수정·삭제 등), 동시 수정 충돌, 크롤러가 이미 실행 중 |
| 413 | 업로드 파일 크기 초과 |
| 429 | 크롤러 재실행 쿨다운 |

---

### 1. 제안서 임시저장

POST /api/proposals/drafts

Request

```json
{
  "projectName": "OO아파트 위생기구 납품",
  "apartmentType": "84㎡",
  "households": 240,
  "globalMarginRate": 10,
  "lines": [
    {
      "productId": 1,
      "productName": "양변기",
      "catalogUnitPrice": 100000,
      "qty": 2,
      "area": "욕실1",
      "category": "양변기"
    }
  ]
}
```

Response

* 저장된 제안서 (조회 응답과 같은 형태)

설명

- `lines[].productId`(카탈로그 제품 id)는 필수입니다.
- 단가(`unitPrice`)·금액(`amount`)은 보내도 쓰지 않고, 서버가 원가·마진율·수량으로 계산합니다.

---

### 2. 제안서 조회

GET /api/proposals/{id}

Response (발췌)

```json
{
  "id": 1,
  "version": 0,
  "projectName": "OO아파트 위생기구 납품",
  "apartmentType": "84㎡",
  "households": 240,
  "status": "DRAFT",
  "globalMarginRate": 10.00,
  "lines": [
    {
      "productId": 1,
      "productName": "양변기",
      "catalogUnitPrice": 100000.00,
      "manualMargin": false,
      "marginRate": 10.00,
      "unitPrice": 110000.00,
      "qty": 2,
      "amount": 220000.00,
      "unit": "SET"
    }
  ]
}
```

설명

- 수정·작성 완료 요청에는 받은 `version`을 그대로 돌려보냅니다. 그사이 다른 곳에서 바뀌었으면 409입니다.

---

### 3. 제조사 카탈로그 엑셀 업로드

POST /api/vendor-catalog/upload-excel/{vendorCode}

Request

* multipart/form-data
* file: Excel 파일 (.xlsx, .xls)

Response

```json
{
  "jobId": "uuid"
}
```

설명

- 업로드 요청 시 비동기 작업이 시작됩니다.
- jobId를 통해 진행 상태를 조회할 수 있습니다.
- 받을 수 없는 요청은 작업을 만들기 전에 400으로 거절합니다.

```json
{
  "status": 400,
  "message": "이 파일은 B사 양식으로 보입니다 (선택한 공급사: A사). 공급사를 바꿔 다시 올려 주세요."
}
```

동작 방식

- 업로드 요청 → 검증 → jobId 반환 → 진행률 조회

---

### 4. 업로드 진행률 조회

GET /api/vendor-catalog/upload-progress/{jobId}

Response (완료 — 합성 샘플 업로드 결과)

```json
{
  "percent": 100,
  "message": "완료! (총 104건, 신규 104 · 갱신 0)",
  "done": true,
  "error": false,
  "created": 104,
  "updated": 0
}
```

설명

- 진행 중에는 `message`가 단계(엑셀 파싱 중 → DB 저장 중 → 사라진 행 정리 중)를 보여 주고, `created`·`updated`는 `null`입니다.
- 실패하면 `done`·`error`가 모두 `true`이고 `message`에 사유가 담깁니다.

---

### 5. 크롤링 실행

POST /api/admin/crawler/{maker}/images

Request

* `dryRun=true` — 내려받거나 저장하지 않고 매칭 결과만 확인
* `force=true` — 재실행 쿨다운을 건너뜀 (동시 실행 잠금은 그대로)

Response

* 실행 리포트 — 수집 건수(`collected`), 처리·실패 건수(`processed`·`failed`), 매칭 상세(`match`), 요약(`message`)

설명

- 지정된 제조사 제품 이미지 동기화를 수행합니다.
- 같은 제조사가 이미 실행 중이면 409, 쿨다운 중이면 429를 돌려줍니다.
- `local` 외 프로파일에서는 HTTP Basic 인증이 필요하고, `demo`에는 이 API가 없습니다.

---

### 6. 엑셀 다운로드

GET /api/proposals/{id}/export/proposal

GET /api/proposals/{id}/export/quote-targets

GET /api/proposals/{id}/export/quote?kind=MAIN&apartmentType={평형}

Response

* Excel 파일 다운로드 (한글 파일명은 `Content-Disposition`에 UTF-8로 인코딩)
* 견적서 대상 목록 예: `[{"kind": "MAIN", "apartmentType": "84㎡", "label": "84㎡", "lineCount": 1}]`

설명

- `kind=ANNEX`는 부속동·상가 합본입니다.
- 발송완료가 아닌 제안서는 409 (`발송완료 상태의 제안서만 출력할 수 있습니다.`)를 돌려줍니다.

---

## 실행 프로파일과 데이터베이스

프로파일을 지정하지 않으면 `local`(실사용)로 뜹니다. 공개 데모는 `demo`입니다.
갈리는 값만 프로파일 파일(`application-local.properties` / `application-demo.properties`)에 둡니다.

| | `local` (기본) | `demo` |
|---|---|---|
| DB | Apache Derby 파일 (`./data/estimateDB`) | PostgreSQL (`ESTI_DB_URL` · `ESTI_DB_USER` · `ESTI_DB_PASSWORD`) |
| 데이터 | 업로드한 단가표 | 합성 샘플 — 제품이 없는 공급사에만 자동 적재 |
| 크롤러 API | 사용 | 없음 |
| 업로드 한도 | 100MB | 2MB |
| 바인딩 | `127.0.0.1` | 기본(모든 인터페이스) |
| 관리자 API 인증 | 끔 | 켬 |

```properties
# application-local.properties
spring.datasource.url=jdbc:derby:./data/estimateDB;create=true
spring.jpa.hibernate.ddl-auto=update

# application-demo.properties
spring.datasource.url=${ESTI_DB_URL:jdbc:postgresql://localhost:5432/esti}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
```

* 모든 테이블은 `APP` 스키마에 만들어집니다.
* 엔티티 매핑이 두 DB에서 같은 의미여야 해서, CI가 같은 테스트를 Derby와 PostgreSQL로 함께 돌립니다.

### 주요 환경변수

| 변수 | 용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `demo`로 데모 배포본 실행 (비우면 `local`) |
| `ESTI_DB_URL` · `ESTI_DB_USER` · `ESTI_DB_PASSWORD` | `demo` DB 접속 정보 |
| `ADMIN_USER` · `ADMIN_PASSWORD` | 관리자 API 계정 (비밀번호를 비우면 기동할 때마다 임시로 만들어 로그에 남김) |
| `APP_CORS_ALLOWED_ORIGINS` | CORS 허용 오리진 |
| `COMPANY_CEO` | 견적서 서명란 대표이사명 (설정 파일의 값은 예시 이름) |
| `VITE_API_BASE_URL` | (프론트, 선택) API 기준 주소. 비우면 같은 오리진 |

---

## 실행 방법

### 0. 준비

* JDK 17
* Node.js 20.19+(20.x) 또는 22.12+ (프론트엔드)
* Docker — `demo` 프로파일 실행과 PostgreSQL 테스트에만 필요

### 1. 저장소 클론

```bash
git clone https://github.com/suminleedev/esti.git
cd esti
```

### 2. 백엔드 실행

프로파일을 지정하지 않으면 `local`로 `http://127.0.0.1:8080`에 뜨고, DB 파일(`./data/estimateDB`)이 없으면 새로 만듭니다.

#### 방법 1. 개발 모드 실행

##### Windows

```bash
mvnw.cmd spring-boot:run
```

##### Mac / Linux

```bash
./mvnw spring-boot:run
```

#### 방법 2. Jar 빌드 후 실행

##### 빌드

###### Windows

```bash
mvnw.cmd clean package
```

###### Mac / Linux

```bash
./mvnw clean package
```

##### 실행

```bash
java -jar target/esti-0.0.1-SNAPSHOT.jar
```

### 3. 프론트엔드 실행

```bash
cd esti-vue
npm install
npm run dev
```

`http://localhost:5173`에서 열립니다. `/api`·`/uploads` 등은 Vite 개발 서버가 `http://localhost:8080`으로 넘겨 주므로 `.env` 없이 동작합니다.
API를 다른 주소로 보내려면 `esti-vue/.env`에 `VITE_API_BASE_URL`을 적습니다.

### 4. 데모 배포본 실행 (단일 jar + PostgreSQL)

```bash
(cd esti-vue && npm install)        # 프론트 빌드에 필요
./mvnw -Pfrontend clean package     # 프론트까지 담은 단일 jar

docker run -d --name esti-demo-pg \
  -e POSTGRES_USER=esti -e POSTGRES_PASSWORD=demo-local -e POSTGRES_DB=esti \
  -p 15432:5432 postgres:16-alpine

SPRING_PROFILES_ACTIVE=demo \
ESTI_DB_URL=jdbc:postgresql://localhost:15432/esti ESTI_DB_PASSWORD=demo-local \
  java -jar target/esti-0.0.1-SNAPSHOT.jar
```

`http://localhost:8080`에서 화면까지 함께 열리고, 처음 뜰 때 합성 샘플 카탈로그가 적재됩니다.
Caddy까지 포함한 구성은 `deploy/compose.yml` 머리 주석을 참고하세요.

### 5. 테스트

```bash
./mvnw test                          # 백엔드 (Derby 인메모리)
ESTI_TEST_DB=postgres ./mvnw test    # 같은 테스트를 PostgreSQL 컨테이너로 (Docker 필요)

cd esti-vue
npm run test                         # 프론트 (Vitest)
npm run lint                         # ESLint (자동 수정 포함)
```

* 공급사 원본 단가표가 필요한 테스트는 파일이 없으면 건너뜁니다. 원본은 저장소에 두지 않습니다.
* CI(GitHub Actions)는 PR과 master 푸시마다 백엔드 테스트를 Derby·PostgreSQL 두 DB로 돌립니다.

---

## Git 관리

저장소에 올리지 않는 것 (`.gitignore`)

| 경로 | 내용 |
|---|---|
| `/data/`, `/derby.log` | 로컬 Derby DB 파일 |
| `uploads/` | 제품 이미지, 업로드 임시 파일 |
| `.env`, `deploy/certs/` | 환경변수·비밀값, 인증서 |
| `deploy/backups/` | 데모 백업 결과 |
| `docs/`, `work-logs/`, `scripts/` | 로컬 전용 작업 문서·작업일지·보조 스크립트 |
| `node_modules/`, `target/` | 의존성·빌드 산출물 |

공급사 단가·내부 코드는 영업 정보라 테스트 기대값까지 저장소에 두지 않고, gitignore된 로컬 파일에서 읽습니다.

---

## 정리

이 프로젝트는 단순 CRUD를 넘어서

> "제조사 데이터 수집 → 내부 데이터화 → 제안서 및 견적서 생성 → 엑셀 출력"

흐름을 자동화하는 실무형 데이터 처리 시스템을 목표로 하고 있습니다.

특히 크롤링 + 엑셀 + 템플릿 기능이 결합된 구조로, 실무에서 반복되는 견적 작업을 줄이는 데 초점을 맞춘 프로젝트입니다.
