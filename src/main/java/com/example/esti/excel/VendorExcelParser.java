package com.example.esti.excel;

import java.nio.file.Path;
import java.util.List;

/**
 * 공급사 단가표 엑셀 파서.
 *
 * <p>출력은 {@link VendorProductSet}(대표품목 + 부속 묶음)으로 통일한다.
 * 저장 단계({@code CatalogImportAsyncService})가 이 구조를 풀어 VendorProduct/Relation/ItemPrice로 적재한다.
 */
public interface VendorExcelParser {

    String getVendorCode(); // 'A', 'B' 등

    List<VendorProductSet> parseSets(Path path);

    /**
     * 이 파일이 «이 공급사 양식으로 보이는가».
     *
     * <p>업로드 직후(비동기 파싱 전)에 선택한 공급사와 파일 양식이 모순되는지 가리는 데 쓴다.
     * 판정 근거를 가진 파서만 재정의한다 — 근거 없이 YES/NO를 내면 실파일 양식이 바뀔 때
     * 멀쩡한 파일을 거부하게 되므로, 모르면 {@link Recognition#UNKNOWN}으로 둔다.
     */
    default Recognition recognize(Path path) {
        return Recognition.UNKNOWN;
    }

    /** {@link #recognize(Path)}의 판정. UNKNOWN은 «없는 값»이 아니라 «판정 근거 없음»이라는 상태다. */
    enum Recognition { YES, NO, UNKNOWN }
}
