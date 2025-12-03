package com.example.esti.excel;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VendorExcelParser {

    String getVendorCode(); // 'A', 'B' 등

    List<VendorExcelRow> parse(MultipartFile file);
}

