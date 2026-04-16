# Esti

> 🚧 개발 진행 중인 프로젝트입니다.
> 현재 핵심 기능(크롤링, 엑셀 업로드, 제안서, 엑셀 출력)은 구현되어 있으며,
> 추가 기능은 지속적으로 확장 중입니다.

욕실 제품 견적서 관리 및 제조사 데이터 수집(크롤링 + 엑셀 업로드) 시스템입니다.

Spring Boot 백엔드와 Vue 3 프론트엔드로 구성되어 있으며, **제품 데이터 수집 → 제안서 생성 → 엑셀 출력** 흐름을 중심으로 동작합니다.

---

## 프로젝트 소개

이 프로젝트는 욕실/위생도기 제품 데이터를 기반으로 제안서와 견적서를 효율적으로 생성하고 관리하기 위해 만들어졌습니다.

특히 다음 문제를 해결하는 것을 목표로 합니다.

* 제조사 제품 정보가 여러 사이트에 분산되어 있어 흩어진 제품 정보를 수집하기 어려움
* 엑셀 기반 견적 작업의 반복성과 비효율성
* 제품 이미지 및 상세 정보 관리의 불편함

이를 해결하기 위해

* 제조사 제품 정보 크롤링
* 엑셀 기반 제품 데이터 업로드
* 제안서 생성 및 상태 관리
* 제안서 엑셀 출력

기능을 중심으로 구현되었습니다.

> 본 시스템은 공개된 웹 페이지의 데이터를 수집하며,  
> 각 사이트의 이용약관 및 정책을 준수하는 범위 내에서 동작하도록 설계되었습니다.

---

## 구현 상태

### ✅ 완료된 기능

* 제조사 제품 카탈로그 엑셀 업로드
* 엑셀 비동기 업로드 및 진행률 조회
* 제조사 사이트 크롤링을 통한 제품 이미지 수집
* 제안서 CRUD (생성 / 수정 / 삭제)
* 제안서 상태 관리 (임시저장 / 제출 / 발송 / 복사)
* 제안서 목록 페이징 조회
* 제안서 템플릿 관리
* 제안서 엑셀 출력

### 🚧 개발 예정

* 제조사 데이터 자동 매칭 로직 고도화
* 이미지 포함 엑셀 출력
* 크롤링 자동화 (배치/스케줄링)
* 제안서 기반 견적서 생성 자동화
* 제조사 확장

---

## 주요 기능

### 1. 제조사 제품 데이터 수집

#### 엑셀 업로드

* 제조사 제품 정보를 엑셀로 업로드
* 내부 데이터로 저장 및 관리

#### 크롤링

* 제조사 사이트 기반으로 제품 이미지 수집 및 동기화
* 제조사별 확장 가능한 구조로 설계

---

### 2. 제안서 관리

* 제안서 생성 / 조회 / 수정 / 삭제
* 템플릿 관리를 통해 제안서 기본 유닛 자동 생성
* 마진율 반영시 일괄 마진율(`globalMarginRate`) 및 품목별 개별 마진율 병행

---

### 3. 엑셀 출력

* 제안서 데이터를 엑셀로 출력
* 추후 이미지 포함 출력 확장 가능

---

### 4. 이미지 처리

* 크롤링 이미지 저장 경로 관리
* 제품 이미지 URL 또는 파일 저장 구조 지원

---

## 저장소 구성

```text
esti
├── src                # Spring Boot 백엔드
├── esti-vue           # Vue 3 + Vite 프론트엔드
├── pom.xml            # Maven 설정
└── .gitignore
```

---

## 기술 스택

### Backend

* Java 17
* Spring Boot 3.5.6
* Spring Web
* Spring Data JPA
* Apache Derby (파일 기반 DB)
* Lombok
* Apache POI (엑셀 처리)
* Jsoup (크롤링)

### Frontend

* Vue 3
* Vite
* Vue Router
* Axios
* Bootstrap 5

---

## 백엔드 구조

```text
src/main/java/com/example/esti
├── controller   # API
├── service      # 비즈니스 로직
├── repository   # DB 접근
├── entity       # 엔티티
├── crawler      # 제조사 크롤링
├── excel        # 엑셀 업로드/출력
├── dto          # 요청/응답
├── config/util  # 설정 및 유틸
```

---

## 도메인 구조

### 1. 제안서 도메인

Proposal (제안서)

* 견적서의 메인 정보
* 상태, 일괄 마진율(globalMarginRate) 포함

ProposalLine (제안서 상세)

* 제안서에 포함된 제품
* 수량, 단가, 최종 금액 관리

관계:
Proposal 1 : N ProposalLine

---

### 2. 제조사 제품 도메인

Vendor (공급사)

* 제조사 정보

VendorProduct (공급사 제품)

* 제조사 사이트 기준 원본 데이터
* 제품명, 품번, 이미지, 상세 URL 포함

VendorItemPrice (가격 정보)

* 제품 가격을 별도로 관리

관계:
Vendor 1 : N VendorProduct
VendorProduct 1 : N VendorItemPrice

---

### 3. 특징

* 제안서와 제조사 데이터는 분리된 구조
* 크롤링 데이터는 원본 형태로 유지
* 가격 정보는 별도 엔티티로 관리

---

## API 예시

### 1. 제안서 생성

POST /api/proposals/drafts

Request

```json
{
  "title": "욕실 견적서",
  "globalMarginRate": 10
}
```

Response

```json
{
  "id": 1,
  "title": "욕실 견적서",
  "globalMarginRate": 10,
  "status": "DRAFT"
}
```

---

### 2. 제안서 조회

GET /api/proposals/{id}

Response

```json
{
  "id": 1,
  "title": "욕실 견적서",
  "globalMarginRate": 10,
  "lines": [
    {
      "productName": "양변기",
      "quantity": 2,
      "unitPrice": 100000,
      "finalPrice": 110000
    }
  ]
}
```

---

### 3. 제조사 카탈로그 엑셀 업로드

POST /api/vendor-catalog/upload-excel/{vendorCode}

Request

* multipart/form-data
* file: Excel 파일

Response

```json
{
  "jobId": "uuid"
}
```


설명

- 업로드 요청 시 비동기 작업이 시작됩니다.
- jobId를 통해 진행 상태를 조회할 수 있습니다.

동작 방식

- 업로드 요청 → jobId 반환 → 진행률 조회

---

### 4. 업로드 진행률 조회

GET /api/vendor-catalog/upload-progress/{jobId}

Response

```json
{
  "progress": 45,
  "message": "엑셀 데이터 처리 중",
  "done": false,
  "fail": false
}
```

---

### 5. 크롤링 실행

POST /api/admin/crawler/{maker}/images

Response

```json
{
  "message": "{maker} 이미지 동기화 완료"
}
```

설명
- 지정된 제조사 제품 이미지 동기화를 수행합니다.

---

### 6. 엑셀 다운로드

GET /api/proposals/{id}/excel

Response

* Excel 파일 다운로드

---

## 데이터베이스 설정

```properties
spring.datasource.url=jdbc:derby:./data/estimateDB;create=true
spring.datasource.driver-class-name=org.apache.derby.jdbc.EmbeddedDriver
spring.jpa.hibernate.ddl-auto=update
```

---

## 실행 방법

### 1. 저장소 클론

```bash
git clone https://github.com/suminleedev/esti.git
cd esti
```

### 2. 백엔드 실행

```bash
./mvnw spring-boot:run
```

또는

```bash
./mvnw clean package
java -jar target/esti-0.0.1-SNAPSHOT.jar
```

### 3. 프론트엔드 실행

```bash
cd esti-vue
npm install
npm run dev
```

---

## Git 관리

```gitignore
/data/
/derby.log
node_modules/
*.log
```

---

## 향후 계획

* 제조사 데이터 자동 매칭 로직 고도화
* 이미지 포함 엑셀 출력
* 크롤링 자동화 (배치/스케줄링)
* 제안서 기반 견적서 생성 자동화
* 제조사 확장

---

## 정리

이 프로젝트는 단순 CRUD를 넘어서

> "제조사 데이터 수집 → 내부 데이터화 → 제안서 및 견적서 생성 → 엑셀 출력"

흐름을 자동화하는 실무형 데이터 처리 시스템을 목표로 하고 있습니다.

특히 크롤링 + 엑셀 + 템플릿 기능이 결합된 구조로, 실무에서 반복되는 견적 작업을 줄이는 데 초점을 맞춘 프로젝트입니다.
