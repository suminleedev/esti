package com.example.esti.service;

import com.example.esti.entity.ProductCatalog;
import com.example.esti.repository.ProductCatalogRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProductCatalogService {

    private final ProductCatalogRepository repository;

    public ProductCatalogService(ProductCatalogRepository repository) {
        this.repository = repository;
    }

    // ===== 엑셀 업로드 =====
    public void importFromExcel(MultipartFile file) throws Exception {
        List<ProductCatalog> products = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 0행은 헤더니까 1행부터
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = getCellValue(row.getCell(0));
                String model = getCellValue(row.getCell(1));
                String brand = getCellValue(row.getCell(2));
                String specs = getCellValue(row.getCell(3));
                String basePriceStr = getCellValue(row.getCell(4));
                String description = getCellValue(row.getCell(5));
                String imageUrl = getCellValue(row.getCell(6));

                // 필수값 검증
                if (name == null || name.isBlank()) continue;
                if (basePriceStr == null || basePriceStr.isBlank()) continue;

                BigDecimal basePrice;
                try {
                    basePrice = new BigDecimal(basePriceStr);
                } catch (NumberFormatException e) {
                    // 숫자 변환 실패 → 건너뛰기
                    continue;
                }

                ProductCatalog product = new ProductCatalog();
                product.setName(name);
                product.setModel(model);
                product.setBrand(brand);
                product.setSpecs(specs);
                product.setBasePrice(basePrice);
                product.setDescription(description);
                product.setImageUrl(imageUrl);

                products.add(product);
            }
        }

        if (!products.isEmpty()) {
            repository.saveAll(products);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    // ===== CRUD =====
    public List<ProductCatalog> findAll() {
        return repository.findAll();
    }

    public Optional<ProductCatalog> findById(Long id) {
        return repository.findById(id);
    }

    public ProductCatalog save(ProductCatalog product) {
        return repository.save(product);
    }

    public boolean existsById(Long id) {
        return repository.existsById(id);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
