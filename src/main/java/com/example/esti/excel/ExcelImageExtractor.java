package com.example.esti.excel;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 엑셀 임베디드 이미지(DrawingML 플로팅 그림) 추출기 (D15).
 *
 * <p>시트별로 그림의 앵커 행(top row, 0-based)을 키로 이미지 바이트를 모은다.
 * 한 행에 여러 그림이 앵커되면 마지막 것이 남는다(이미지 열은 보통 1개). B사 단가표는 D=이미지 열에
 * 제품코드 행과 같은 행으로 그림이 앵커돼 있어 행 기준 매칭이 신뢰 가능하다.
 *
 * <p>저장(파일 쓰기)은 서비스가 담당하고, 본 추출기는 메모리상의 바이트만 반환한다(파서/IO 분리).
 */
@Component
public class ExcelImageExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ExcelImageExtractor.class);

    /** 시트명 → (앵커 행(0-based) → 이미지). 그림이 없으면 빈 맵. */
    public Map<String, Map<Integer, ExtractedImage>> extract(Path path) {
        Map<String, Map<Integer, ExtractedImage>> result = new HashMap<>();
        try (InputStream is = Files.newInputStream(path);
             Workbook wb = WorkbookFactory.create(is)) {

            if (!(wb instanceof XSSFWorkbook)) {
                logger.warn("[이미지추출] XSSF(.xlsx) 아님 → 이미지 추출 스킵");
                return result;
            }

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                XSSFSheet sheet = (XSSFSheet) wb.getSheetAt(i);
                XSSFDrawing drawing = sheet.getDrawingPatriarch();
                if (drawing == null) continue;

                Map<Integer, ExtractedImage> byRow = new HashMap<>();
                for (XSSFShape shape : drawing.getShapes()) {
                    if (!(shape instanceof XSSFPicture pic)) continue;
                    XSSFClientAnchor anchor = pic.getClientAnchor(); // 실제 배치 앵커(top row)
                    if (anchor == null) continue;
                    int row = anchor.getRow1();
                    XSSFPictureData data = pic.getPictureData();
                    if (data == null) continue;
                    byRow.put(row, new ExtractedImage(data.getData(), data.suggestFileExtension()));
                }
                if (!byRow.isEmpty()) {
                    result.put(sheet.getSheetName(), byRow);
                    logger.debug("[이미지추출] {} 그림 {}건", sheet.getSheetName(), byRow.size());
                }
            }
        } catch (Exception e) {
            logger.warn("[이미지추출] 실패(무시하고 진행): {}", e.getMessage());
        }
        return result;
    }

    /** 추출된 이미지 1건. */
    public record ExtractedImage(byte[] data, String ext) {}
}
