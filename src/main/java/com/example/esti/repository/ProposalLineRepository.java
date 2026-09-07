package com.example.esti.repository;

import com.example.esti.entity.ProposalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface ProposalLineRepository extends JpaRepository<ProposalLine, Long> {

    List<ProposalLine> findByProposalId(Long proposalId);

    /** 표시 순서대로 조회.
     *  기존 행은 sortOrder가 null인데 Derby는 ASC에서 null을 마지막에 놓는다.
     *  한 제안서의 라인은 저장 때마다 전체 재생성되므로 sortOrder는 전부 있거나 전부 없다.
     *  따라서 legacy 제안서는 id ASC(= 최초 입력 순서)로 안정 정렬된다. */
    List<ProposalLine> findByProposalIdOrderBySortOrderAscIdAsc(Long proposalId);

    /**
     * 제안서가 참조 중인 카탈로그 제품 id 전량.
     *
     * <p>재적재가 최신본에서 사라진 제품을 지울 때 <b>이 목록은 건드리지 않는다.</b>
     * {@code ProposalLine.productId}는 연관이 아니라 그냥 {@code Long} 컬럼이라 <b>외래키가 없다</b> —
     * 참조 중인 제품을 지워도 DB가 막아주지 않고 끊어진 id만 조용히 남는다. 그래서 코드로 확인한다.
     */
    @Query("select distinct l.productId from ProposalLine l where l.productId is not null")
    Set<Long> findReferencedProductIds();

    void deleteByProposalId(Long proposalId);
}

