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
}
