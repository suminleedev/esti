package com.example.esti.repository;

import com.example.esti.entity.ProposalTemplateLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProposalTemplateLineRepository extends JpaRepository<ProposalTemplateLine, Long> {
    List<ProposalTemplateLine> findByTemplateId(Long templateId);
    void deleteByTemplateId(Long templateId);
}
