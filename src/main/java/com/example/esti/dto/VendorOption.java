package com.example.esti.dto;

/**
 * 화면의 «공급사» 선택지 한 건.
 *
 * <p>이름은 DB의 공급사 행에서 온다. 아직 그 행이 없으면 {@code A사}처럼 코드로 부른다 —
 * 데모 배포본은 이름을 가칭으로 두므로, 실제로 그 이름이 그대로 화면에 나간다.
 */
public record VendorOption(String vendorCode, String vendorName) {}
