package com.example.esti.repository;

import com.example.esti.entity.ProposalLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProposalLineRepository extends JpaRepository<ProposalLine, Long> {

    List<ProposalLine> findByProposalId(Long proposalId);

    void deleteByProposalId(Long proposalId);
}

