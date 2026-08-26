package com.example.esti.repository;

import com.example.esti.entity.Proposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProposalRepository
        extends JpaRepository<Proposal, Long>, JpaSpecificationExecutor<Proposal> {
    List<Proposal> findByDeletedAtIsNull();
    Optional<Proposal> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 오늘 날짜 접두어로 부여된 견적번호 중 가장 큰 것 — 다음 일련번호를 정할 때 쓴다.
     * 형식이 {@code syt-YYYYMMDDNN}으로 자릿수가 고정이라 사전순 최대가 곧 번호 최대다.
     */
    Optional<Proposal> findTopByQuoteNoStartingWithOrderByQuoteNoDesc(String prefix);

    /** 해당 템플릿을 참조하는 제안서가 있는지 — 템플릿 삭제 가드용. */
    boolean existsByTemplate_Id(Long templateId);
    @Override
    @EntityGraph(attributePaths = {"template"})
    Page<Proposal> findAll(Specification<Proposal> spec, Pageable pageable);
}

