package com.example.esti.excel;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VendorExcelParserFactory {

    private final List<VendorExcelParser> parsers;

    public VendorExcelParser getParser(String vendorCode) {
        return parsers.stream()
                .filter(p -> p.getVendorCode().equalsIgnoreCase(vendorCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 공급사 코드: " + vendorCode));
    }

    /**
     * 이 코드를 받아 줄 파서가 있는지.
     *
     * <p>업로드를 받기 <b>전에</b> 물어보려고 열어 뒀다(F-009). 예전에는 파서가 없는 코드도
     * 200으로 받아 임시파일까지 쓴 뒤 비동기 단계에서 실패했다 — 요청한 쪽은 한참 뒤에야 알았다.
     */
    public boolean supports(String vendorCode) {
        return vendorCode != null
                && parsers.stream().anyMatch(p -> p.getVendorCode().equalsIgnoreCase(vendorCode));
    }
}

