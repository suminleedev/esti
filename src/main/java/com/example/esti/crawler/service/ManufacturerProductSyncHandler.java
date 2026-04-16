package com.example.esti.crawler.service;

import com.example.esti.crawler.common.CrawledProduct;

public interface ManufacturerProductSyncHandler {

    boolean supports(String maker);

    int order();

    void save(CrawledProduct crawled);
}
