package com.example.esti.repository;

import com.example.esti.entity.MasterCode;
import com.example.esti.entity.MasterCodeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MasterCodeRepository extends JpaRepository<MasterCode, Long> {

    /** 설정 화면용 — 비활성 포함 전건. */
    List<MasterCode> findByTypeOrderBySortOrderAscIdAsc(MasterCodeType type);

    /** 드롭다운용 — 활성만. */
    List<MasterCode> findByTypeAndActiveTrueOrderBySortOrderAscIdAsc(MasterCodeType type);

    /** 중복 검사 — 비활성 항목과도 부딪히므로 active를 보지 않는다(UNIQUE 제약과 같은 범위). */
    Optional<MasterCode> findByTypeAndLabel(MasterCodeType type, String label);

    boolean existsByType(MasterCodeType type);
}
