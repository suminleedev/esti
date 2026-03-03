package com.example.esti.repository;

import com.example.esti.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    List<Proposal> findByDeletedAtIsNull();
    Optional<Proposal> findByIdAndDeletedAtIsNull(Long id);
}

