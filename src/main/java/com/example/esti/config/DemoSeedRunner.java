package com.example.esti.config;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorProduct;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import com.example.esti.service.CatalogImportAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 데모 배포본을 기동할 때 합성 카탈로그를 적재한다 (D-3).
 *
 * <p>데모는 인메모리 DB라 <b>뜰 때마다 빈 상태</b>다. 카탈로그가 비면 방문자가 볼 수 있는 게
 * 업로드 화면뿐이라, 기동 시 합성 단가표(D-2)를 한 벌 넣어 둔다. 방문자가 카탈로그를 망쳐도
 * 재시작하면 이 상태로 돌아온다.
 *
 * <p><b>적재 로직을 새로 만들지 않는다.</b> 사람이 업로드했을 때와 같은 경로
 * ({@link CatalogImportAsyncService#importVendorCatalog})를 그대로 부른다 — 데모에서만 도는
 * 두 번째 적재 경로가 생기면 그쪽만 조용히 낡는다.
 *
 * <p>이 클래스가 {@code demo} 프로파일에 묶인 <b>유일한 자리</b>다. 서비스 로직에
 * {@code if (demo)}를 흩뿌리지 않기 위해 데모에만 필요한 일을 여기 모은다.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoSeedRunner implements ApplicationRunner {

    /** 방문자가 내려받는 샘플과 <b>같은 파일</b>이다(D-9) — 두 벌로 갈라지면 한쪽이 낡는다. */
    private static final Map<String, String> SEEDS = Map.of(
            "A", "static/samples/vendor-a-sample.xlsx",
            "B", "static/samples/vendor-b-sample.xlsx");

    /**
     * 대분류 → 플레이스홀더 이미지 (G-3).
     *
     * <p>실제 제품 사진은 제조사 사이트에서 수집한 것이라 배포본에 얹지 않는다. 대신 직접 그린 것을
     * 대분류별로 붙인다.
     *
     * <p><b>실제 제품 이미지와 같은 자리에 둔다</b>({@code uploads/product-images/}) — 화면만이면
     * jar 안에서 그냥 서빙해도 되지만, <b>제안서 카드 엑셀</b>이 이미지를 파일 경로로 읽기 때문이다.
     * 데모에서 카드 출력의 사진 칸이 비면 보여줄 것의 절반이 빈다.
     */
    private static final Map<String, String> IMAGE_BY_CATEGORY = Map.ofEntries(
            Map.entry("양변기", "toilet"),
            Map.entry("비데", "bidet"),
            Map.entry("세면기", "basin"),
            Map.entry("상업용제품", "basin"),
            Map.entry("욕조", "bath"),
            Map.entry("수전", "faucet"),
            Map.entry("주방수전", "faucet"),
            Map.entry("발코니수전", "faucet"),
            Map.entry("수전금구", "faucet"),
            Map.entry("샤워수전", "shower"),
            Map.entry("액세서리", "accessory"),
            Map.entry("악세사리", "accessory"),
            Map.entry("부속", "fitting"));

    private static final String FALLBACK_IMAGE = "product";

    /** jar 안의 원본 위치와, 내려놓을 자리(실제 제품 이미지와 같은 폴더). */
    private static final String IMAGE_RESOURCE_DIR = "static/demo-images/";
    private static final Path IMAGE_TARGET_DIR = Path.of("uploads", "product-images");
    private static final String IMAGE_URL_PREFIX = "/uploads/product-images/";
    private static final String IMAGE_FILE_PREFIX = "demo-";

    private final CatalogImportAsyncService importService;
    private final VendorProductRepository productRepository;
    private final VendorRepository vendorRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            log.info("[데모시드] 이미 카탈로그가 있어 건너뛴다 ({}건)", productRepository.count());
            return;
        }

        SEEDS.forEach(this::seed);
        renameVendors();
        assignPlaceholderImages(copyPlaceholderImages());
    }

    /**
     * 시드 한 벌을 적재한다.
     *
     * <p><b>실패해도 기동을 막지 않는다.</b> 카탈로그가 빈 데모는 반쪽이지만, 아예 안 뜨는 것보다는
     * 낫다 — 방문자가 같은 파일을 직접 내려받아 올려 볼 수 있는 길이 남아 있다(D-9).
     */
    private void seed(String vendorCode, String resourcePath) {
        Path temp = null;
        try {
            temp = Files.createTempFile("demo-seed-" + vendorCode + "-", ".xlsx");
            try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
                Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            int total = importService.importVendorCatalog(vendorCode, temp);
            log.info("[데모시드] {} 적재 완료 — 세트 {}건", vendorCode, total);
        } catch (Exception e) {
            log.error("[데모시드] {} 적재 실패 — 카탈로그가 빈 채로 뜬다: {}", vendorCode, e.toString());
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (Exception ignore) { /* 임시파일이다 */ }
            }
        }
    }

    /**
     * 공급사 이름을 가칭으로 바꾼다.
     *
     * <p>적재가 공급사 행을 만들 때 실제 상호를 넣는다. 그 이름이 데모 API 응답과 화면에
     * 그대로 실려 나가는데, <b>공급사 이름은 저장소 밖으로 내보내지 않는다</b>는 것이
     * 이 저장소의 규칙이다({@code CLAUDE.md}). 공개 배포본이 그 규칙의 가장 바깥이다.
     *
     * <p>이름은 <b>행을 만들 때만</b> 정해지므로, 여기서 한 번 바꿔 두면 방문자가 나중에
     * 직접 단가표를 올려도 되돌아가지 않는다.
     */
    private void renameVendors() {
        List<Vendor> vendors = vendorRepository.findAll();
        for (Vendor vendor : vendors) {
            vendor.setVendorName(vendor.getVendorCode() + "사");
        }
        vendorRepository.saveAll(vendors);
        log.info("[데모시드] 공급사 이름을 가칭으로 바꿨다 ({}곳)", vendors.size());
    }

    /**
     * 플레이스홀더 그림을 jar에서 꺼내 제품 이미지 폴더에 내려놓는다.
     *
     * @return 실제로 놓인 아이콘 이름들. 못 놓았으면 빈 집합 — 그러면 아무 제품에도 붙이지 않는다.
     *         없는 파일을 가리키는 imageUrl은 화면에서 깨진 이미지가 되므로, 비어 있는 편이 낫다.
     */
    private Set<String> copyPlaceholderImages() {
        Set<String> icons = new LinkedHashSet<>(IMAGE_BY_CATEGORY.values());
        icons.add(FALLBACK_IMAGE);

        Set<String> placed = new LinkedHashSet<>();
        try {
            Files.createDirectories(IMAGE_TARGET_DIR);
        } catch (IOException e) {
            log.warn("[데모시드] 제품 이미지 폴더를 만들지 못했다 — 이미지 없이 뜬다: {}", e.toString());
            return placed;
        }

        for (String icon : icons) {
            Path target = IMAGE_TARGET_DIR.resolve(IMAGE_FILE_PREFIX + icon + ".png");
            try (InputStream is = new ClassPathResource(IMAGE_RESOURCE_DIR + icon + ".png").getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                placed.add(icon);
            } catch (IOException e) {
                log.warn("[데모시드] 플레이스홀더 이미지를 놓지 못했다: {} ({})", icon, e.toString());
            }
        }
        return placed;
    }

    /** 이미지가 없는 제품에 대분류별 플레이스홀더를 붙인다. */
    private void assignPlaceholderImages(Set<String> placed) {
        if (placed.isEmpty()) return;

        List<VendorProduct> products = productRepository.findAll().stream()
                .filter(p -> p.getImageUrl() == null || p.getImageUrl().isBlank())
                .toList();
        if (products.isEmpty()) return;

        for (VendorProduct product : products) {
            String icon = IMAGE_BY_CATEGORY.getOrDefault(product.getCategoryLarge(), FALLBACK_IMAGE);
            if (!placed.contains(icon)) icon = FALLBACK_IMAGE;
            if (!placed.contains(icon)) continue;
            product.setImageUrl(IMAGE_URL_PREFIX + IMAGE_FILE_PREFIX + icon + ".png");
        }
        productRepository.saveAll(products);
        log.info("[데모시드] 플레이스홀더 이미지 {}건 연결 (그림 {}장)", products.size(), placed.size());
    }
}
