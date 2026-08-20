package com.example.esti.repository;

import com.example.esti.entity.ProposalLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProposalLineRepository extends JpaRepository<ProposalLine, Long> {

    List<ProposalLine> findByProposalId(Long proposalId);

    /** 표시 순서대로 조회.
     *  기존 행은 sortOrder가 null인데 Derby는 ASC에서 null을 마지막에 놓는다.
     *  한 제안서의 라인은 저장 때마다 전체 재생성되므로 sortOrder는 전부 있거나 전부 없다.
     *  따라서 legacy 제안서는 id ASC(= 최초 입력 순서)로 안정 정렬된다. */
    List<ProposalLine> findByProposalIdOrderBySortOrderAscIdAsc(Long proposalId);

    void deleteByProposalId(Long proposalId);
}

