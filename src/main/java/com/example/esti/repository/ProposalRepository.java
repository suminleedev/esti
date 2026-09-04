package com.example.esti.repository;

import com.example.esti.entity.Proposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProposalRepository
        extends JpaRepository<Proposal, Long>, JpaSpecificationExecutor<Proposal> {
    List<Proposal> findByDeletedAtIsNull();
    Optional<Proposal> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 그날 접두어로 부여된 견적번호 전부 — 다음 일련번호를 정할 때 쓴다.
     *
     * <p>번호 하나만 받아 오지 않는 이유(F-001): 예전에는
     * {@code findTopByQuoteNoStartingWithOrderByQuoteNoDesc}로 <b>사전순 최대</b>를 집었다.
     * «자릿수가 고정이라 사전순 최대가 곧 번호 최대»라는 전제였는데,
     * 100번째부터 일련번호가 세 자리가 되면서 그 전제가 깨진다 —
     * 문자열로는 {@code "99" > "100"}이라 최댓값이 계속 {@code ...99}로 잡히고,
     * 그래서 <b>100번이 매일 몇 번이고 다시 발급됐다.</b>
     * 최댓값은 문자열이 아니라 <b>숫자로</b> 골라야 하므로 후보를 다 가져온다.
     *
     * <p>하루치라 건수가 적고, 번호(문자열)만 뽑으므로 엔티티를 싣지 않는다.
     * 소프트 삭제분도 포함한다 — 이미 나간 문서 번호를 다시 쓰면 안 되기 때문이다.
     */
    @Query("select p.quoteNo from Proposal p where p.quoteNo like concat(:prefix, '%')")
    List<String> findQuoteNosStartingWith(@Param("prefix") String prefix);

    /** 해당 템플릿을 참조하는 제안서가 있는지 — 템플릿 삭제 가드용. */
    boolean existsByTemplate_Id(Long templateId);
    @Override
    @EntityGraph(attributePaths = {"template"})
    Page<Proposal> findAll(Specification<Proposal> spec, Pageable pageable);
}

