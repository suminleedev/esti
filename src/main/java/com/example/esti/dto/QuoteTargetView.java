package com.example.esti.dto;

/**
 * 견적서 출력 대상 1건 — 화면의 대상 선택 목록에 쓴다 (O-7 ⓑ).
 *
 * @param kind          {@code MAIN}(본세대) / {@code ANNEX}(부속동·상가 합본)
 * @param apartmentType 본세대일 때의 평형. 합본이면 {@code null}
 * @param label         화면 표시용 ("59㎡" / "부속동·상가")
 * @param lineCount     이 대상에 담기는 품목 수 — 0건인 대상은 목록에 넣지 않는다
 */
public record QuoteTargetView(
        String kind,
        String apartmentType,
        String label,
        int lineCount
) {}
