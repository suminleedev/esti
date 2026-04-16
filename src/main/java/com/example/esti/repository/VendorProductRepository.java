package com.example.esti.repository;

import com.example.esti.entity.Vendor;
import com.example.esti.entity.VendorProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorProductRepository extends JpaRepository<VendorProduct, Long> {

    Optional<VendorProduct> findByVendorAndProductCode(Vendor vendor, String productCode);

    List<VendorProduct> findByVendorAndRepresentativeCode(Vendor vendor, String representativeCode);

    Optional<VendorProduct> findByVendorAndRepresentativeCodeAndDetailCode(
            Vendor vendor,
            String representativeCode,
            String detailCode
    );
}
