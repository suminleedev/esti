package com.example.esti.service;

import com.example.esti.exception.InvalidStateException;
import com.example.esti.exception.NotFoundException;
import com.example.esti.dto.QuoteTargetView;
import com.example.esti.entity.Proposal;
import com.example.esti.entity.ProposalLine;
import com.example.esti.output.ProposalCardExcelWriter;
import com.example.esti.output.QuoteExcelWriter;
import com.example.esti.output.QuoteTarget;
import com.example.esti.repository.ProposalLineRepository;
import com.example.esti.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 제안서·견적서 엑셀 출력 (Phase 6).
 *
 * <p>출력물은 <b>2종</b>이고 수신자가 다르다 — 제안서는 고객 제출용(사입가·마진 없음),
 * 견적서는 내부 검토용(사입가·마진 포함)이다. 한 파일 2시트로 묶지 않고 엔드포인트부터 나눈 이유는,
 * 사입가가 든 시트를 실수로 고객에게 보내는 사고를 구조적으로 막기 위해서다(O-6).
 *
 * <p>양식 재현은 {@link ProposalCardExcelWriter} · {@link QuoteExcelWriter}가 맡고,
 * 이 서비스는 조회·상태 검증·채번만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProposalExcelService {

    private final ProposalRepository proposalRepository;
    private final ProposalLineRepository proposalLineRepository;
    private final QuoteNumberService quoteNumberService;

    /** 견적서 서명란에 찍히는 대표이사명. 회사 정보라 설정으로 뺀다. */
    @Value("${app.company.ceo:}")
    private String ceoName;

    /** 내려받을 엑셀 1건 — 내용과 파일명. 파일명 규칙이 양식마다 달라 함께 돌려준다. */
    public record ExcelDownload(byte[] content, String fileName) {}

    /** 제안서(고객 제출용) — 8행 카드 × 4열 그리드. */
    public ExcelDownload exportProposal(Long proposalId) {
        Proposal proposal = loadSentProposal(proposalId);
        byte[] content = ProposalCardExcelWriter.write(proposal, lines(proposalId));
        return new ExcelDownload(content,
                "제안서_%s_%s.xlsx".formatted(safeName(proposal.getProjectName()), stamp(proposal)));
    }

    /**
     * 견적서(내부 검토용) — 표 + 4단 집계.
     *
     * <p>대상은 평형별 본세대 또는 부속동·상가 합본이다(O-7). 견적번호는 첫 출력 때 부여하고 재사용한다(O-9).
     */
    @Transactional
    public ExcelDownload exportQuote(Long proposalId, QuoteTarget target) {
        Proposal proposal = loadSentProposal(proposalId);
        String quoteNo = quoteNumberService.assign(proposal);
        byte[] content = QuoteExcelWriter.write(proposal, lines(proposalId), target, quoteNo, ceoName);

        String scope = target.kind() == QuoteTarget.Kind.ANNEX
                ? "부속동상가"
                : safeName(target.apartmentType() == null || target.apartmentType().isBlank()
                        ? "본세대" : target.apartmentType());

        return new ExcelDownload(content, "견적서_%s_%s_%s.xlsx"
                .formatted(safeName(proposal.getProjectName()), scope, stamp(proposal)));
    }

    /**
     * 이 제안서에서 뽑을 수 있는 견적서 대상 목록 — 화면의 대상 선택에 쓴다.
     *
     * <p>본세대는 평형마다 한 부씩, 부속동·상가는 있으면 합본 한 부다. <b>품목이 없는 대상은 넣지 않는다</b> —
     * 빈 견적서를 내려받게 되는 걸 막는다.
     */
    public List<QuoteTargetView> listQuoteTargets(Long proposalId) {
        loadSentProposal(proposalId);
        List<ProposalLine> lines = lines(proposalId);

        // 본세대 평형은 라인 등장 순서를 유지한다 — 화면 정렬과 어긋나지 않게
        Set<String> apartmentTypes = new LinkedHashSet<>();
        int annexCount = 0;
        for (ProposalLine line : lines) {
            if (QuoteTarget.annex().matches(line)) {
                annexCount++;
            } else {
                apartmentTypes.add(nvl(line.getApartmentType()));
            }
        }

        List<QuoteTargetView> targets = new ArrayList<>();
        for (String type : apartmentTypes) {
            QuoteTarget target = QuoteTarget.main(type.isEmpty() ? null : type);
            int count = (int) lines.stream().filter(target::matches).count();
            if (count == 0) continue;
            targets.add(new QuoteTargetView(
                    QuoteTarget.Kind.MAIN.name(), type,
                    type.isEmpty() ? "본세대 (평형 미지정)" : type, count));
        }
        if (annexCount > 0) {
            targets.add(new QuoteTargetView(QuoteTarget.Kind.ANNEX.name(), null, "부속동·상가", annexCount));
        }
        return targets;
    }

    /** 발송완료 상태만 출력 대상이다. */
    private Proposal loadSentProposal(Long proposalId) {
        Proposal proposal = proposalRepository.findByIdAndDeletedAtIsNull(proposalId)
                .orElseThrow(() -> new NotFoundException("제안서를 찾을 수 없습니다. id=" + proposalId));

        if (proposal.getStatus() != Proposal.Status.SENT) {
            throw new InvalidStateException("발송완료 상태의 제안서만 출력할 수 있습니다.");
        }
        return proposal;
    }

    private List<ProposalLine> lines(Long proposalId) {
        return proposalLineRepository.findByProposalIdOrderBySortOrderAscIdAsc(proposalId);
    }

    private static String nvl(String value) {
        return value != null ? value : "";
    }

    /** 파일명에 쓸 수 없는 문자를 밀어낸다. */
    private static String safeName(String value) {
        if (value == null || value.isBlank()) return "제안서";
        return value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /** 파일명 끝의 날짜 도장(yyMMdd). 제안서 작성일을 쓰고, 없거나 형식이 어긋나면 오늘로 본다. */
    private static String stamp(Proposal proposal) {
        try {
            return LocalDate.parse(proposal.getDate().trim())
                    .format(DateTimeFormatter.ofPattern("yyMMdd"));
        } catch (Exception e) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        }
    }
}
