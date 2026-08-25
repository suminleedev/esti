package com.example.esti.output;

import com.example.esti.entity.ProposalLine;

import java.util.Set;

/**
 * 견적서 본문의 <b>소분류 소계 묶음</b>.
 *
 * <p>샘플은 항목을 세 덩어리로 끊고 각 덩어리 끝에 `소 계`를 넣는다 —
 * 도기류(R15~20) · 수전류(R22~32) · 악세사리(R34~40). 덩어리에 이름표는 없고 소계 위치로만 구분된다.
 * 그 세 덩어리를 라인의 유형(`category`)에서 되짚는 매핑이다.
 *
 * <p>목록에 없는 유형은 {@link #OTHER}로 모여 맨 뒤에 붙는다 — 유형이 늘어도 항목이 사라지지 않는다.
 */
public enum QuoteItemGroup {

    /** 도기류 — 양변기·비데·세면기. */
    SANITARY_WARE(Set.of("양변기", "비데", "세면기")),

    /** 수전류. */
    FAUCET(Set.of("세면기 수전", "욕조 수전/슬라이드바", "해바라기샤워수전", "씽크수전")),

    /** 악세사리. */
    ACCESSORY(Set.of("악세사리")),

    /** 위 어디에도 없는 유형. 맨 뒤에 모인다. */
    OTHER(Set.of());

    private final Set<String> categories;

    QuoteItemGroup(Set<String> categories) {
        this.categories = categories;
    }

    public static QuoteItemGroup of(ProposalLine line) {
        String category = line.getCategory();
        if (category != null) {
            for (QuoteItemGroup group : values()) {
                if (group.categories.contains(category)) return group;
            }
        }
        return OTHER;
    }
}
