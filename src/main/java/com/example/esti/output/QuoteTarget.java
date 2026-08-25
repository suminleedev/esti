package com.example.esti.output;

import com.example.esti.entity.ProposalLine;

/**
 * 견적서 1부의 <b>출력 대상</b> (O-7 ⓑ 확정 — 평형별 별도 견적서).
 *
 * <ul>
 *   <li>{@link Kind#MAIN} — 본세대. 평형 하나만 담는다. 그룹 헤더는 {@code 아파트 59형(523)} 꼴이다.</li>
 *   <li>{@link Kind#ANNEX} — 부속동·상가 합본. 평형 개념이 없어 둘을 한 파일에 섹션으로 나눠 담는다.</li>
 * </ul>
 *
 * <p>건물 구분은 현장마다 값이 늘 수 있는 문자열이다(O-5). 그래서 "본세대인가 아닌가"로만 가른다 —
 * 나중에 `관리동` 같은 값이 생겨도 자동으로 부속동 파일에 실린다.
 */
public record QuoteTarget(Kind kind, String apartmentType) {

    public enum Kind { MAIN, ANNEX }

    /** 본세대를 뜻하는 건물 구분 값. 비어 있는 라인(구 데이터)도 본세대로 본다. */
    public static final String MAIN_BUILDING_TYPE = "본세대";

    /** 본세대 + 평형. 평형이 {@code null}이면 평형을 가리지 않고 본세대 전부를 담는다. */
    public static QuoteTarget main(String apartmentType) {
        return new QuoteTarget(Kind.MAIN, apartmentType);
    }

    /** 부속동·상가 합본. */
    public static QuoteTarget annex() {
        return new QuoteTarget(Kind.ANNEX, null);
    }

    public boolean matches(ProposalLine line) {
        boolean mainLine = isMainBuilding(line.getBuildingType());
        if (kind == Kind.ANNEX) return !mainLine;
        if (!mainLine) return false;

        // 평형을 지정하지 않았으면 본세대 전부를 담는다(라인 평형이 없는 구 데이터 대응)
        if (apartmentType == null || apartmentType.isBlank()) return true;
        return apartmentType.equals(line.getApartmentType());
    }

    private static boolean isMainBuilding(String buildingType) {
        return buildingType == null || buildingType.isBlank() || MAIN_BUILDING_TYPE.equals(buildingType);
    }
}
