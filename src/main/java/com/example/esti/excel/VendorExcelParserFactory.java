package com.example.esti.excel;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

    /**
     * 파일 양식이 선택한 공급사와 «명백히» 모순되는지 가린다. 모순이 없으면 빈 값.
     *
     * <ol>
     *   <li>다른 공급사 파서가 {@code YES} → 모순 (그 공급사 코드를 담는다)</li>
     *   <li>선택한 공급사 파서가 {@code NO} → 모순</li>
     *   <li>그 외 → 통과 — 판정 근거가 없으면(UNKNOWN) 사용자 선언을 따른다</li>
     * </ol>
     *
     * <p>감지 결과로 공급사를 바꿔 적재하지는 않는다. 양식이 조금 달라진 실파일이 엉뚱한 공급사로
     * 조용히 들어가는 것이, 거부당해 다시 고르는 것보다 훨씬 나쁘다. 이름은 여기서 모르므로
     * 안내 문구는 호출한 쪽이 코드로 공급사를 찾아 만든다.
     */
    public Optional<FormatMismatch> findFormatMismatch(String vendorCode, Path file) {
        VendorExcelParser selected = getParser(vendorCode);
        Optional<String> claimedByOther = parsers.stream()
                .filter(p -> p != selected)
                .sorted(Comparator.comparing(VendorExcelParser::getVendorCode))
                .filter(p -> p.recognize(file) == VendorExcelParser.Recognition.YES)
                .map(VendorExcelParser::getVendorCode)
                .findFirst();
        if (claimedByOther.isPresent()) {
            return Optional.of(new FormatMismatch(selected.getVendorCode(), claimedByOther.get()));
        }
        if (selected.recognize(file) == VendorExcelParser.Recognition.NO) {
            return Optional.of(new FormatMismatch(selected.getVendorCode(), null));
        }
        return Optional.empty();
    }

    /**
     * 양식 모순. {@code detectedVendorCode}는 파일을 자기 양식이라 판정한 다른 공급사 —
     * 선택한 공급사가 «아니다»라고만 했고 알아본 곳이 없으면 {@code null}.
     */
    public record FormatMismatch(String selectedVendorCode, String detectedVendorCode) {}
}
