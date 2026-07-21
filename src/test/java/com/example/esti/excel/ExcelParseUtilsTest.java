package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import static com.example.esti.excel.ExcelParseUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExcelParseUtils} 순수 유틸 단위 테스트(§13 유틸 공통화로 추출된 함수의 동작 고정).
 * 파서에서 옮겨온 정규화·괄호 분리·냉/온 접미 로직이 종전과 동일함을 보장한다.
 */
class ExcelParseUtilsTest {

    @Test
    void isBlank_널_공백_빈문자() {
        assertTrue(isBlank(null));
        assertTrue(isBlank("   "));
        assertFalse(isBlank("x"));
    }

    @Test
    void stripSpace_연속공백정리_빈값은널() {
        assertEquals("a b", stripSpace("  a   b  "));
        assertNull(stripSpace("   "));
        assertNull(stripSpace(null));
    }

    @Test
    void noSpace_글자사이공백까지제거() {
        assertEquals("품번", noSpace("품 번"));
        assertEquals("긴다리", noSpace("긴 다 리"));
        assertNull(noSpace("  "));
    }

    @Test
    void normalizeCode_괄호앞만_길이컷() {
        assertEquals("IC552EF", normalizeCode("IC552EF(길마위욕)"));
        assertEquals("MC921", normalizeCode("  MC921 "));
        // 길이 컷: CODE_MAX_LEN 초과분 잘림
        assertEquals(CODE_MAX_LEN, normalizeCode("A".repeat(60)).length());
        assertNull(normalizeCode(null));
    }

    @Test
    void splitParen_코드와설명분리() {
        assertArrayEquals(new String[]{"IC552EF", "길마위욕"}, splitParen("IC552EF(길마위욕)"));
        assertArrayEquals(new String[]{"X", null}, splitParen("X"));
        assertArrayEquals(new String[]{null, "설명"}, splitParen("(설명)"));
        assertArrayEquals(new String[]{null, null}, splitParen("   "));
    }

    @Test
    void partCode_마스터_디테일_결합() {
        assertEquals("MC921_4mc921wt", partCode("MC921", "4mc921wt"));
        assertEquals("MC921", partCode("MC921", null));
        assertEquals("4mc921wt", partCode(null, "4mc921wt"));
    }

    @Test
    void coldHot_냉수온수_접미_중복방지() {
        assertEquals("U9420c", coldHot("U9420", "앵글밸브 냉수"));
        assertEquals("U9420h", coldHot("U9420", "앵글밸브 온수"));
        assertEquals("U9420c", coldHot("U9420c", "냉수")); // 이미 c면 그대로
        assertEquals("U9420", coldHot("U9420", "일반")); // 냉/온 없으면 그대로
    }

    @Test
    void faucetDetail_냉온_단일글자기준() {
        assertEquals("43sw1c", faucetDetail("43sw1", "냉"));
        assertEquals("43sw1h", faucetDetail("43sw1", "온수"));
        assertEquals("43sw1", faucetDetail("43sw1", "메탈호스"));
    }

    @Test
    void fittingPartNo_대문자_하이픈제거() {
        assertEquals("U9110150A", fittingPartNo("U-9110150a"));
        assertEquals("U9013C", fittingPartNo("U9013c"));
    }

    @Test
    void firstToken_공백전까지() {
        assertEquals("U9013", firstToken("U9013 냉수용"));
        assertEquals("X", firstToken("X"));
        assertNull(firstToken("   "));
    }

    @Test
    void resolveDitto_따옴표는_직전품명() {
        assertEquals("노출형 휴지걸이", resolveDitto("\"", "노출형 휴지걸이"));
        assertEquals("〃대체안됨", resolveDitto("〃대체안됨", "이전")); // 순수 ditto 아님
        assertEquals("이전", resolveDitto("〃", "이전"));
    }

    @Test
    void join_orDefault() {
        assertEquals("a b", join("a", "b"));
        assertEquals("a", join("a", null));
        assertEquals("def", orDefault("  ", "def"));
        assertEquals("v", orDefault("v", "def"));
    }
}
