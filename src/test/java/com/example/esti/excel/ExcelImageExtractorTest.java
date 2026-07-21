package com.example.esti.excel;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static com.example.esti.support.TestSamples.requireSample;

/**
 * P5 검증: 엑셀 임베디드 이미지(DrawingML)를 시트별 앵커 행 기준으로 추출하는지(D15).
 * 샘플(docs/samples/...)은 git 추적 제외이므로 파일이 없으면 스킵한다.
 */
class ExcelImageExtractorTest {

    private static final Path SAMPLE = Path.of("docs/samples/B사 단가표_sample.xlsx");

    private final ExcelImageExtractor extractor = new ExcelImageExtractor();

    @Test
    void 시트별로_이미지를_앵커행_기준으로_추출한다() {
        requireSample(SAMPLE);

        Map<String, Map<Integer, ExcelImageExtractor.ExtractedImage>> images = extractor.extract(SAMPLE);

        assertFalse(images.isEmpty(), "이미지가 추출돼야 함");
        assertTrue(images.containsKey("양변기"), "양변기 시트 이미지 존재");

        // MC921 = 엑셀 r5(0-based row 4)에 도기 이미지가 앵커돼 있음
        ExcelImageExtractor.ExtractedImage mc921Img = images.get("양변기").get(4);
        assertNotNull(mc921Img, "양변기 row4(MC921)에 이미지");
        assertTrue(mc921Img.data().length > 0, "이미지 바이트 존재");
        assertNotNull(mc921Img.ext(), "확장자 추출");
    }
}
