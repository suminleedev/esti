package com.example.esti.entity;

import java.util.List;

/**
 * 마스터 코드 종류 — 한 테이블 + {@code code_type} 컬럼으로 세 종류를 함께 관리한다(M-7).
 * 스키마·CRUD·정렬 로직이 셋 다 같아서 종류가 늘어도 여기에 상수 하나만 추가하면 된다.
 *
 * <p>화면에 뜨는 종류 이름(탭 제목)은 프론트 {@code labels.js}가 갖는다 — 표시 용어의 단일 출처다.
 */
public enum MasterCodeType {

    /** 건물 구분 — 견적서의 본동·부속동 섹션 분리 기준(O-5). 현장마다 값이 달라진다(M-3). */
    BUILDING_TYPE(List.of("본세대", "부속동", "상가")),

    /** 적용 부위 — {@code ProposalLine.area} / {@code Proposal.areasJson}에 문자열로 저장된다. */
    AREA(List.of("욕실1", "욕실2", "욕실 공통", "주방", "세탁실", "다용도실")),

    /** 적용 카테고리(위생기구 유형) — {@code ProposalLine.category}에 문자열로 저장된다. */
    CATEGORY(List.of(
            "양변기", "비데", "세면기", "세면기 수전",
            "욕조 수전/슬라이드바", "해바라기샤워수전", "씽크수전", "악세사리"));

    private final List<String> defaults;

    MasterCodeType(List<String> defaults) {
        this.defaults = defaults;
    }

    /**
     * 최초 시딩 값 — Phase 6까지 프론트 {@code labels.js}에 하드코딩돼 있던 목록 그대로다.
     * 해당 종류의 row가 하나도 없을 때만 넣는다({@code MasterCodeSeeder}).
     */
    public List<String> getDefaults() {
        return defaults;
    }
}
