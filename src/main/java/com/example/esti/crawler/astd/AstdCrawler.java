package com.example.esti.crawler.astd;

import com.example.esti.crawler.common.CrawledProduct;
import com.example.esti.crawler.common.ProductImageCrawler;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AstdCrawler implements ProductImageCrawler {

    private static final String AJAX_LIST_PATH = "/main/product/ajaxList.do";

    @Value("${app.crawler.astd.maker}")
    private String maker;

    @Value("${app.crawler.astd.vendor-code}")
    private String vendorCode;

    @Value("${app.crawler.astd.category-url}")
    private String categoryUrl;

    @Value("${app.crawler.astd.category-ids}")
    private String categoryIds;

    @Value("${app.crawler.user-agent}")
    private String userAgent;

    @Value("${app.crawler.timeout-ms}")
    private int timeoutMs;

    @Value("${app.crawler.astd.request-delay-ms}")
    private int requestDelayMs;

    private final AstdParser parser = new AstdParser();

    @Override
    public String maker() {
        return maker;
    }

    @Override
    public String vendorCode() {
        return vendorCode;
    }

    @Override
    public List<CrawledProduct> crawlAllProducts() throws Exception {
        Map<String, CrawledProduct> unique = new LinkedHashMap<>();

        // 카테고리와 페이지를 가리지 않고 "요청과 요청 사이"에 간격을 둬야 하므로
        // 보낸 요청 수를 전체를 통틀어 센다. 카테고리마다 리셋하면 카테고리 경계에서 간격이 사라진다.
        int sent = 0;

        for (Integer cate1 : parseCategoryIds()) {
            String currentCategoryUrl = buildCategoryUrl(cate1);

            throttle(sent++);
            Document firstPage = Jsoup.connect(currentCategoryUrl)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .get();

            collectFromPage(unique, firstPage);

            int totalPages = extractTotalPages(firstPage);
            log.info("ASTD 카테고리 {} — {}페이지, 누적 {}건", cate1, totalPages, unique.size());

            for (int page = 2; page <= totalPages; page++) {
                throttle(sent++);
                Document pageDoc = fetchPageByAjax(currentCategoryUrl, cate1, page);
                collectFromPage(unique, pageDoc);
            }
        }

        log.info("ASTD 수집 완료 — 요청 {}회, 고유 {}건", sent, unique.size());

        return new ArrayList<>(unique.values());
    }

    /**
     * 요청과 요청 사이에 간격을 둔다.
     *
     * <p>사이트가 {@code robots.txt}에 {@code Crawl-delay}를 명시하고 있고, 그 값을 존중하기로 했다
     * (G-5 ① — {@code docs/plan-a-crawler.md}). <b>이 간격이 이 크롤러에서 가장 비싼 줄이다</b> —
     * 요청 44회 기준으로 실행 시간이 통째로 여기서 나온다. 그래도 기능은 잃지 않는다.
     *
     * @param alreadySent 지금까지 보낸 요청 수. 0이면 첫 요청이라 기다리지 않는다
     */
    private void throttle(int alreadySent) throws InterruptedException {
        if (alreadySent > 0) {
            Thread.sleep(requestDelayMs);
        }
    }

    private void collectFromPage(Map<String, CrawledProduct> unique, Document doc) {
        for (Element item : doc.select("ul.list_wrap > li")) {
            parser.parseFromListItem(item, maker, vendorCode)
                    .ifPresent(product -> unique.putIfAbsent(buildUniqueKey(product), product));
        }
    }

    private String buildUniqueKey(CrawledProduct product) {
        String siteId = product.getSiteProductId() != null
                ? String.valueOf(product.getSiteProductId())
                : "NO_ID";

        String code = product.getProductCode() != null
                ? product.getProductCode()
                : "NO_CODE";

        return siteId + "|" + code;
    }

    private int extractTotalPages(Document doc) {
        int maxPage = 1;

        for (Element a : doc.select(".board_paginate a[data-page]")) {
            String pageText = a.attr("data-page");
            try {
                int page = Integer.parseInt(pageText);
                if (page > maxPage) {
                    maxPage = page;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return maxPage;
    }

    private Document fetchPageByAjax(String refererUrl, int cate1, int page) throws Exception {
        Connection.Response response = Jsoup.connect(extractBaseUrl() + AJAX_LIST_PATH)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", refererUrl)
                .data("v_page", String.valueOf(page))
                .data("v_cate1", String.valueOf(cate1))
                .data("v_order", "1")
                .data("v_pagesize", "12")
                .method(Connection.Method.POST)
                .execute();

        return response.parse();
    }

    private List<Integer> parseCategoryIds() {
        List<Integer> ids = new ArrayList<>();

        for (String token : categoryIds.split(",")) {
            String value = token.trim();
            if (value.isBlank()) {
                continue;
            }

            try {
                ids.add(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("잘못된 ASTD category id: " + value, e);
            }
        }

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("ASTD category ids가 비어 있습니다.");
        }

        return ids;
    }

    private String buildCategoryUrl(int cate1) {
        return extractBaseUrl() + "/main/product.do?v_cate1=" + cate1;
    }

    private String extractBaseUrl() {
        int idx = categoryUrl.indexOf("/main/");
        if (idx > -1) {
            return categoryUrl.substring(0, idx);
        }
        return categoryUrl;
    }
}