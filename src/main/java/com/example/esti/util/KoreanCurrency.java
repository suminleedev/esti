package com.example.esti.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 금액을 한글 표기로 바꾼다 — 견적서 `合計金` 줄에 쓴다.
 *
 * <p>예: {@code 806513400} → {@code 팔억육백오십일만삼천사백원정}
 *
 * <p>표기 규칙
 * <ul>
 *   <li>만·억·조 단위로 4자리씩 끊는다. 값이 0인 자리는 통째로 건너뛴다
 *       ({@code 100000000} → {@code 일억}, {@code 일억영만영천} 아님).</li>
 *   <li>4자리 묶음 안에서 <b>십·백·천 앞의 '일'은 생략</b>한다 ({@code 15} → {@code 십오}).
 *       단 묶음 값이 정확히 1이면 '일'을 남긴다 ({@code 10000} → {@code 일만}).</li>
 *   <li>원 단위 미만은 반올림한다 — 견적서에 소수점 금액을 쓰지 않는다.</li>
 * </ul>
 */
public final class KoreanCurrency {

    private static final String[] DIGITS = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] SMALL_UNITS = {"", "십", "백", "천"};
    /** 4자리 묶음의 단위. 조 위로는 견적서에서 쓸 일이 없다. */
    private static final String[] BIG_UNITS = {"", "만", "억", "조", "경"};

    private KoreanCurrency() {
    }

    /** 금액 → {@code 팔억육백오십일만삼천사백원정}. 0이면 {@code 영원정}. */
    public static String toKoreanAmount(BigDecimal amount) {
        return toKorean(amount) + "원정";
    }

    /** 금액 → 한글 수사(단위 접미 없음). */
    public static String toKorean(BigDecimal amount) {
        if (amount == null) return "영";

        BigDecimal rounded = amount.setScale(0, RoundingMode.HALF_UP);
        boolean negative = rounded.signum() < 0;
        java.math.BigInteger value = rounded.abs().toBigInteger();

        if (value.signum() == 0) return "영";

        StringBuilder sb = new StringBuilder();
        java.math.BigInteger tenThousand = java.math.BigInteger.valueOf(10_000);
        int groupIndex = 0;
        StringBuilder head = new StringBuilder();

        while (value.signum() > 0) {
            int group = value.mod(tenThousand).intValue();
            if (group > 0) {
                if (groupIndex >= BIG_UNITS.length) {
                    throw new IllegalArgumentException("표기 범위를 넘는 금액입니다: " + amount);
                }
                head.insert(0, groupToKorean(group) + BIG_UNITS[groupIndex]);
            }
            value = value.divide(tenThousand);
            groupIndex++;
        }

        if (negative) sb.append("마이너스 ");
        return sb.append(head).toString();
    }

    /** 4자리 이하 묶음을 한글로. 십·백·천 앞의 '일'은 생략하되, 묶음이 1이면 '일'을 남긴다. */
    private static String groupToKorean(int group) {
        if (group == 1) return "일";

        StringBuilder sb = new StringBuilder();
        for (int pos = 3; pos >= 0; pos--) {
            int digit = (group / (int) Math.pow(10, pos)) % 10;
            if (digit == 0) continue;
            // 십·백·천 자리의 1은 '일십'이 아니라 '십'으로 쓴다
            sb.append(digit == 1 && pos > 0 ? "" : DIGITS[digit]).append(SMALL_UNITS[pos]);
        }
        return sb.toString();
    }
}
