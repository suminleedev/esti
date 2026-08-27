package com.example.esti.config;

import com.example.esti.entity.MasterCode;
import com.example.esti.entity.MasterCodeType;
import com.example.esti.repository.MasterCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 마스터 테이블 최초 시딩 — Phase 6까지 {@code labels.js}에 하드코딩돼 있던 값을 그대로 넣는다.
 *
 * <p><b>종류별로 row가 0건일 때만</b> 넣는다. 사용자가 지운(=숨긴) 항목을 재기동 때마다 되살리거나
 * 바꾼 순서를 되돌리면 안 되기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterCodeSeeder implements ApplicationRunner {

    private final MasterCodeRepository repo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (MasterCodeType type : MasterCodeType.values()) {
            if (repo.existsByType(type)) continue;

            List<MasterCode> seeds = new ArrayList<>();
            int order = 0;
            for (String label : type.getDefaults()) {
                seeds.add(new MasterCode(type, label, order++));
            }
            repo.saveAll(seeds);
            log.info("마스터 코드 시딩: {} {}건", type, seeds.size());
        }
    }
}
