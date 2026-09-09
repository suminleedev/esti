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
     * 파서가 있는 공급사 코드 전부. 화면의 «공급사» 선택지가 여기서 온다.
     *
     * <p>예전에는 프론트에 코드와 이름이 함께 박혀 있었다. 공급사가 늘면 화면을 고쳐야 했고,
     * 무엇보다 <b>공급사 이름이 배포되는 파일 안에</b> 들어갔다.
     */
    public List<String> supportedVendorCodes() {
        return parsers.stream()
                .map(VendorExcelParser::getVendorCode)
                .sorted()
                .toList();
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

