package com.example.esti.repository;

import com.example.esti.entity.ItemComponent;
import com.example.esti.entity.ProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemComponentRepository extends JpaRepository<ItemComponent, Long> {

    List<ItemComponent> findByCatalog(ProductCatalog catalog);

    void deleteByCatalog(ProductCatalog catalog);
}

