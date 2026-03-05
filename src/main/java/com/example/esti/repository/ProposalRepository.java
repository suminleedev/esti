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
    @Override
    @EntityGraph(attributePaths = {"template"})
    Page<Proposal> findAll(Specification<Proposal> spec, Pageable pageable);
}

