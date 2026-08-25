package com.example.esti.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanCurrencyTest {

    @Test
    @DisplayName("샘플 견적서의 合計金 표기를 그대로 재현한다")
    void 샘플_합계금() {
        assertThat(KoreanCurrency.toKoreanAmount(new BigDecimal("<PRICE>")))
                .isEqualTo("팔억육백오십일만삼천사백원정");
    }

    @ParameterizedTest
    @CsvSource({
            "0,             영",
            "1,             일",
            "10,            십",
            "11,            십일",
            "15,            십오",
            "100,           백",
            "1000,          천",
            "1001,          천일",
            "3400,          삼천사백",
            "10000,         일만",          // 묶음이 1이면 '일'을 남긴다
            "10001,         일만일",
            "100000000,     일억",          // 0인 묶음은 통째로 건너뛴다
            "100010000,     일억일만",
            "651,           육백오십일",
            "6510000,       육백오십일만",
            "1234567890,    십이억삼천사백오십육만칠천팔백구십",
    })
    @DisplayName("자리·묶음 규칙")
    void 표기_규칙(String amount, String expected) {
        assertThat(KoreanCurrency.toKorean(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    @DisplayName("원 단위 미만은 반올림한다")
    void 반올림() {
        assertThat(KoreanCurrency.toKorean(new BigDecimal("1000.4"))).isEqualTo("천");
        assertThat(KoreanCurrency.toKorean(new BigDecimal("1000.5"))).isEqualTo("천일");
    }

    @Test
    @DisplayName("null과 음수도 터지지 않는다")
    void 예외_입력() {
        assertThat(KoreanCurrency.toKoreanAmount(null)).isEqualTo("영원정");
        assertThat(KoreanCurrency.toKorean(new BigDecimal("-1500"))).isEqualTo("마이너스 천오백");
    }
}
