package com.example.esti.service;

import com.example.esti.entity.ProductCatalog;
import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorItemPrice;
import com.example.esti.excel.VendorExcelParser;
import com.example.esti.excel.VendorExcelParserFactory;
import com.example.esti.excel.VendorExcelRow;
import com.example.esti.repository.ProductCatalogRepository;
import com.example.esti.repository.VendorItemPriceRepository;
import com.example.esti.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogImportService {

    private final VendorExcelParserFactory parserFactory;
    private final VendorRepository vendorRepository;
    private final ProductCatalogRepository catalogRepository;
    private final VendorItemPriceRepository vendorItemPriceRepository;
    // private final ItemComponentRepository itemComponentRepository; // 필요시 주입

    public List<VendorItemPrice> getVendorCatalog(String vendorCode) {
        return vendorItemPriceRepository.findByVendor_VendorCode(vendorCode);
    }

    /** 브랜드명 매핑 */
    private static String resolveVendorName(String vendorCode) {
        return switch (vendorCode) {
            case "A" -> "아메리칸스탠다드";
            case "B" -> "이누스"; // 필요하면 채우기
            default  -> vendorCode + "사";
        };
    }

    /**
     * 공급사(vendorCode)별 카탈로그 엑셀 업로드
     * 예: vendorCode = "A" 또는 "B"
     */
    @Transactional
    public void importVendorCatalog(String vendorCode, MultipartFile file) {


        // 1. 공급사 조회 or 생성
        Vendor vendor = vendorRepository.findByVendorCode(vendorCode)
                .orElseGet(() -> {
                    Vendor v = new Vendor();
                    v.setVendorCode(vendorCode);
                    v.setVendorName(resolveVendorName(vendorCode));
                    return vendorRepository.save(v);
                });

        // 2. 파서 선택 & 엑셀 파싱
        VendorExcelParser parser = parserFactory.getParser(vendorCode);
        List<VendorExcelRow> rows = parser.parse(file);

        // 3. 각 행을 카탈로그 + 공급사 단가로 반영
        for (VendorExcelRow row : rows) {
            ProductCatalog catalog = upsertCatalog(row);
            upsertVendorItemPrice(vendor, catalog, row);

            // TODO: 필요하면 여기서 item_component upsert도 호출
        }
    }

    // ==== 카탈로그 upsert ====
    private ProductCatalog upsertCatalog(VendorExcelRow row) {

        // 1) masterCode 기준으로 우선 찾기
        ProductCatalog catalog = null;
        if (row.masterCodeHint() != null && !row.masterCodeHint().isBlank()) {
            catalog = catalogRepository.findByMasterCode(row.masterCodeHint())
                    .orElse(null);
        }

        // 2) 없으면 이름 + 대/소분류 조합으로 검색
        if (catalog == null) {
            catalog = catalogRepository.findByNameAndCategoryLargeAndCategorySmall(
                            row.productName(),
                            row.categoryLarge(),
                            row.categorySmall())
                    .orElse(null);
        }

        // 3) 그래도 없으면 새로 생성
        if (catalog == null) {
            catalog = new ProductCatalog();
            catalog.setMasterCode(row.masterCodeHint()); // 초기엔 공급사 품번 사용
        }

        catalog.setName(row.productName());
        catalog.setCategoryLarge(row.categoryLarge());
        catalog.setCategorySmall(row.categorySmall());
        catalog.setItemType(row.priceType()); // 'SET' 등

        // TODO: 필요하면 spec/description/basePrice도 엑셀 기반으로 채움

        return catalogRepository.save(catalog);
    }

    // ==== 공급사 단가 upsert ====
    private VendorItemPrice upsertVendorItemPrice(
            Vendor vendor, ProductCatalog catalog, VendorExcelRow row) {

        VendorItemPrice vip = vendorItemPriceRepository
                .findByVendorAndCatalogAndProposalItemCode(
                        vendor, catalog, row.proposalItemCode())
                .orElse(null);

        if (vip == null) {
            vip = new VendorItemPrice();
            vip.setVendor(vendor);
            vip.setCatalog(catalog);
            vip.setProposalItemCode(row.proposalItemCode());
        }

        vip.setMainItemCode(row.mainItemCode());
        vip.setSubItemCode(row.subItemCode());
        vip.setOldItemCode(row.oldItemCode());
        vip.setVendorItemName(row.vendorItemName());
        vip.setVendorSpec(row.vendorSpec());
        vip.setRemark(row.remark());
        vip.setUnitPrice(row.unitPrice());
        vip.setPriceType(row.priceType());
        vip.setCurrency("KRW"); // 필요시 변경

        return vendorItemPriceRepository.save(vip);
    }
}

