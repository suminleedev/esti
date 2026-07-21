package com.example.esti.excel;

/**
 * 벤더 단가표 파싱용 <b>순수 문자열/코드 유틸</b> 모음(무상태 static).
 *
 * <p>{@link VendorBExcelParser}(및 일부는 {@link VendorAExcelParser})에 흩어져 있던 정규화·괄호 분리·
 * 냉/온 접미 등의 순수 함수를 한 곳에 모아 <b>중복 제거 + 독립 단위테스트</b>가 가능하게 한다(§13 유틸 공통화).
 * POI/시트 상태에 의존하는 셀 읽기(str/dec 등)는 각 파서에 남긴다(여기엔 순수 함수만).</p>
 */
final class ExcelParseUtils {

    private ExcelParseUtils() {}

    /** 코드(품번) 최대 길이(VARCHAR 한도). 초과분은 컷. */
    static final int CODE_MAX_LEN = 50;

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 앞뒤 공백 제거 + 연속 공백 1칸 정규화(비파괴 공백 포함). 빈 값이면 null. */
    static String stripSpace(String s) {
        if (s == null) return null;
        String x = s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        return x.isEmpty() ? null : x;
    }

    /** 헤더/라벨 비교용: 모든 공백 제거("품 번"→"품번", "긴 다 리"→"긴다리"). 빈 값이면 null. */
    static String noSpace(String s) {
        if (s == null) return null;
        String x = s.replaceAll("[\\s ]+", "");
        return x.isEmpty() ? null : x;
    }

    /** 슬롯/부속 라벨 정규화: 글자 사이 공백까지 제거(= {@link #noSpace}). */
    static String normLabel(String s) {
        return noSpace(s);
    }

    /** 코드 정규화: 공백 정리 + 괄호 앞부분만 + 길이 컷({@link #CODE_MAX_LEN}). 빈 값이면 null. */
    static String normalizeCode(String s) {
        String x = stripSpace(s);
        if (x == null) return null;
        int p = x.indexOf('(');
        if (p > 0) x = x.substring(0, p).trim();
        if (x.length() > CODE_MAX_LEN) x = x.substring(0, CODE_MAX_LEN);
        return x.isEmpty() ? null : x;
    }

    /**
     * "코드(설명)" 분리. 반환[0]=정규화 코드, [1]=괄호 안 설명.
     * 순수 "(설명)"이면 [null, 설명], 괄호 없으면 [코드, null], 빈 셀이면 [null, null].
     */
    static String[] splitParen(String raw) {
        String x = stripSpace(raw);
        if (x == null) return new String[]{null, null};
        int p = x.indexOf('(');
        if (p < 0) return new String[]{normalizeCode(x), null};
        String codePart = x.substring(0, p).trim();
        int q = x.indexOf(')', p + 1);
        String desc = (q > p ? x.substring(p + 1, q) : x.substring(p + 1)).trim();
        return new String[]{
                codePart.isEmpty() ? null : normalizeCode(codePart),
                desc.isEmpty() ? null : desc
        };
    }

    /**
     * 부속 품번 = 대표품번 + '_' + 부속코드 (A사 master-detail 방식 차용).
     * 같은 부속이 세트마다 고유 품번을 가지며, 서비스가 '_' 기준 masterCode/detailCode로 분리한다.
     */
    static String partCode(String masterCode, String detailCode) {
        if (isBlank(masterCode)) return detailCode;
        if (isBlank(detailCode)) return masterCode;
        return masterCode + "_" + detailCode;
    }

    /**
     * 동일 코드가 냉수용/온수용으로 중복될 때 코드에 c/h를 부여해 구분(관계 유실 방지).
     * 품명에 "냉수"면 c, "온수"면 h. (이미 해당 접미사/한글이면 그대로)
     */
    static String coldHot(String code, String name) {
        if (code == null || name == null) return code;
        String n = name.replaceAll("\\s", "");
        String cc = code.replaceAll("\\s", "");
        if (n.contains("냉수") && !cc.endsWith("c") && !cc.contains("냉수")) return code + "c";
        if (n.contains("온수") && !cc.endsWith("h") && !cc.contains("온수")) return code + "h";
        return code;
    }

    /**
     * 수전금구 부속 냉/온 구분: 라벨에 냉/온이 있으면 코드에 c/h 접미(같은 제품코드 냉·온수 공유 시 충돌 방지).
     * ({@link #coldHot}과 달리 "냉"/"온" 단일 글자 기준 — 수전금구 라벨 어휘에 맞춘 별도 변종.)
     */
    static String faucetDetail(String code, String label) {
        if (code == null || label == null) return code;
        String n = label.replaceAll("\\s", "");
        if (n.contains("냉") && !code.endsWith("c")) return code + "c";
        if (n.contains("온") && !code.endsWith("h")) return code + "h";
        return code;
    }

    /** 수전부속 품번 정규화(P9): 대문자 + 하이픈 제거(U-9110150a→U9110150A, U9013c→U9013C). */
    static String fittingPartNo(String token) {
        if (token == null) return null;
        String x = token.replace("-", "").toUpperCase();
        return x.isEmpty() ? null : x;
    }

    /** 첫 토큰(공백 전까지). 빈 값이면 null. */
    static String firstToken(String s) {
        if (s == null) return null;
        String t = s.replace(' ', ' ').trim();
        if (t.isEmpty()) return null;
        int i = 0;
        while (i < t.length() && !Character.isWhitespace(t.charAt(i))) i++;
        return t.substring(0, i);
    }

    /** 따옴표(ditto, 〃) 셀이면 직전 실품명으로 대체. lastName이 없으면 원본 유지. */
    static String resolveDitto(String name, String lastName) {
        if (isDitto(name) && lastName != null) return lastName;
        return name;
    }

    static boolean isDitto(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.equals("\"") || t.equals("“") || t.equals("”") || t.equals("″")
                || t.equals("〃") || t.equals("''") || t.equals("\"\"");
    }

    /** 두 조각을 공백 1칸으로 결합(한쪽이 비면 나머지). */
    static String join(String a, String b) {
        if (isBlank(a)) return b;
        if (isBlank(b)) return a;
        return a + " " + b;
    }

    static String orDefault(String v, String def) {
        return isBlank(v) ? def : v;
    }
}
