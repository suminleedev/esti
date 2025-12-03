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
}

