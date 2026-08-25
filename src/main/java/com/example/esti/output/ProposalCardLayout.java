package com.example.esti.output;

import com.example.esti.entity.ProposalLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 제안서 카드 그리드의 <b>열 배치 규칙</b> (O-2·O-3).
 *
 * <p>샘플 양식의 4개 카드 열은 단순 순서가 아니라 의미로 나뉘어 있다. 샘플 20장을 대조해 얻은 규칙이다.
 *
 * <ol>
 *   <li><b>4열</b> — 악세사리</li>
 *   <li><b>3열</b> — 선택사항(유상옵션)</li>
 *   <li><b>1열</b> — 부위가 욕실이면서 메인 위생기구</li>
 *   <li><b>2열</b> — 나머지 (비욕실이거나, 욕실이지만 메인이 아닌 것)</li>
 * </ol>
 *
 * <p>2열의 존재가 규칙의 핵심 근거다 — 샘플의 `욕실청소건`은 부위가 욕실인데도 1열이 아니라 2열에 있다.
 * 즉 조건은 "욕실이면 1열"이 아니라 <b>"욕실 + 메인"</b>이다.
 *
 * <p><b>겹칠 때의 우선순위</b>: 악세사리를 옵션보다 먼저 본다. 악세사리는 제품 종류(고정)이고
 * 옵션은 상거래 표시(가변)라, 유상옵션인 악세사리도 4열에 모이는 편이 열의 성격이 흔들리지 않는다.
 * 반대로 두고 싶으면 {@link #columnOf} 의 두 분기 순서만 바꾸면 된다.
 */
public final class ProposalCardLayout {

    /** 카드 열 개수. 양식이 4열 고정이다. */
    public static final int COLUMNS = 4;

    /** 악세사리 유형명. 이 값이면 4열로 간다. */
    public static final String ACCESSORY_CATEGORY = "악세사리";

    /**
     * 선택사항(유상옵션) 열(0-based). 이 열은 <b>세대당 합계에 넣지 않는다.</b>
     *
     * <p>샘플이 그렇게 돼 있다 — R4의 열별 소계 4칸 중 3열 자리(I4)만 비어 있고,
     * 세대당 금액 수식 {@code K3=C4+F4+I4+L4}는 그 빈칸을 0으로 집계한다.
     * 유상옵션은 기본 계약 범위가 아니라 별도 청구 대상이라는 뜻이다.
     * 카드 자체의 금액은 그대로 보이고, 합계에만 빠진다.
     */
    public static final int OPTION_COLUMN = 2;

    /**
     * 1열(메인) 자격이 있는 유형들. `esti-vue/src/constants/labels.js`의 `CATEGORIES`에서
     * 욕실 메인 위생기구에 해당하는 것만 골랐다. Phase 7에서 마스터로 옮길 때 함께 이동한다.
     */
    public static final Set<String> MAIN_CATEGORIES = Set.of(
            "양변기",
            "세면기",
            "세면기 수전",
            "욕조 수전/슬라이드바",
            "해바라기샤워수전"
    );

    private ProposalCardLayout() {
    }

    /**
     * 라인이 들어갈 카드 열(0-based, 0=B·C … 3=K·L).
     *
     * <p>부위 판정은 "욕실" 글자 포함으로 한다 — 시스템 목록은 `욕실1`·`욕실 공통`처럼 앞에 오지만
     * 샘플은 `공용욕실`·`부부욕실`처럼 뒤에 붙는다. 양쪽 다 걸려야 한다.
     */
    public static int columnOf(ProposalLine line) {
        if (ACCESSORY_CATEGORY.equals(line.getCategory())) return 3;
        if (Boolean.TRUE.equals(line.getOptional())) return 2;

        boolean bathroom = line.getArea() != null && line.getArea().contains("욕실");
        boolean main = MAIN_CATEGORIES.contains(line.getCategory());
        return (bathroom && main) ? 0 : 1;
    }

    /**
     * 라인들을 4개 열로 나눈다. 열 안의 순서는 입력 순서(=sortOrder)를 그대로 지킨다.
     *
     * @return 길이 {@value #COLUMNS}의 리스트. 빈 열도 빈 리스트로 자리를 지킨다
     */
    public static List<List<ProposalLine>> distribute(List<ProposalLine> lines) {
        List<List<ProposalLine>> columns = new ArrayList<>(COLUMNS);
        for (int i = 0; i < COLUMNS; i++) columns.add(new ArrayList<>());
        if (lines == null) return columns;

        for (ProposalLine line : lines) {
            columns.get(columnOf(line)).add(line);
        }
        return columns;
    }

    /** 세로 블록 수 = 가장 긴 열의 카드 수. 상한 없이 아래로 늘어난다(O-2 ⓑ). */
    public static int blockCount(List<List<ProposalLine>> columns) {
        int max = 0;
        for (List<ProposalLine> col : columns) max = Math.max(max, col.size());
        return max;
    }
}
