package com.example.esti.config;

import com.example.esti.entity.Vendor;
import com.example.esti.repository.VendorProductRepository;
import com.example.esti.repository.VendorRepository;
import com.example.esti.service.CatalogImportAsyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 데모 시드는 <b>제품이 0건인 공급사에만</b> 적재한다 (G11-2).
 *
 * <p>데모 DB가 영속(PostgreSQL)이 되면서 전체 건수로 판정하던 방식이 깨졌다 — 한 공급사만 들어가고
 * 다른 쪽이 실패하면, 다음 기동부터 「이미 있다」로 건너뛰어 그쪽은 영영 비어 있었다.
 * 한 공급사의 제품을 FK째 지워 재현하는 대신 리포지토리를 흉내 내 판정만 본다.
 */
class DemoSeedRunnerVendorTest {

    @TempDir Path imageDir;

    CatalogImportAsyncService importService = mock(CatalogImportAsyncService.class);
    VendorProductRepository productRepository = mock(VendorProductRepository.class);
    VendorRepository vendorRepository = mock(VendorRepository.class);
    DemoSeedRunner runner;

    @BeforeEach
    void setUp() {
        runner = new DemoSeedRunner(importService, productRepository, vendorRepository);
        ReflectionTestUtils.setField(runner, "imageDir", imageDir.toString());
        when(productRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void 비어_있는_공급사만_적재하고_그_공급사만_가칭으로_바꾼다() {
        when(productRepository.countByVendor_VendorCode("A")).thenReturn(12L);
        when(productRepository.countByVendor_VendorCode("B")).thenReturn(0L);
        Vendor b = new Vendor(2L, "B", "원래 상호");
        when(vendorRepository.findByVendorCode("B")).thenReturn(Optional.of(b));

        runner.run(null);

        verify(importService, never()).importVendorCatalog(eq("A"), any());
        verify(importService).importVendorCatalog(eq("B"), any());
        verify(vendorRepository, never()).findByVendorCode("A");
        assertThat(b.getVendorName()).isEqualTo("B사");
    }

    @Test
    void 모두_있으면_적재하지_않지만_그림은_다시_놓는다() {
        when(productRepository.countByVendor_VendorCode(any())).thenReturn(5L);

        runner.run(null);

        verify(importService, never()).importVendorCatalog(any(), any());
        // 컨테이너만 바뀌어 파일이 사라진 경우 — 이미 연결된 imageUrl이 깨지지 않게 매 기동 놓는다
        assertThat(imageDir.resolve("demo-product.png")).exists();
        assertThat(imageDir.resolve("demo-toilet.png")).exists();
    }

    @Test
    void 적재가_실패한_공급사는_가칭을_바꾸지_않는다() {
        when(productRepository.countByVendor_VendorCode(any())).thenReturn(0L);
        when(importService.importVendorCatalog(eq("A"), any())).thenThrow(new IllegalStateException("파싱 실패"));
        when(vendorRepository.findByVendorCode("B")).thenReturn(Optional.of(new Vendor(2L, "B", "원래 상호")));

        runner.run(null); // 기동을 막지 않는다

        verify(vendorRepository, never()).findByVendorCode("A");
        verify(vendorRepository).findByVendorCode("B");
    }
}
