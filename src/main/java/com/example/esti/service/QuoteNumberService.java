package com.example.esti.service;

import com.example.esti.entity.Proposal;
import com.example.esti.repository.ProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 견적번호({@code syt-YYYYMMDDNN}) 채번 (O-9 ⓐ).
 *
 * <p>제안서마다 <b>한 번만</b> 부여하고 이후에는 그대로 재사용한다 — 같은 제안서에서 평형별로
 * 견적서가 여러 부 나와도 문서 번호는 하나를 공유한다. 다시 내려받을 때 번호가 바뀌면 안 되기 때문이다.
 *
 * <p>일련번호는 <b>그날 이미 부여된 번호 중 최댓값 + 1</b>이다.
 * 최댓값은 <b>숫자로</b> 고른다 — 문자열 비교로는 {@code "99" > "100"}이라
 * 100번째부터 같은 번호가 되풀이됐다(F-001). 100번을 넘으면 자릿수가 늘어난다.
 *
 * <p>⚠️ 동시에 두 건을 채번하면 같은 번호가 나올 수 있다. 현재는 단일 사용자 로컬 실행이라
 * 실질적 위험이 없어 잠금을 두지 않았다 — 다중 사용자로 가면 {@code quote_no}에 유니크 제약을 걸고
 * 충돌 시 재시도하는 방식이 필요하다.
 */
@Service
@RequiredArgsConstructor
public class QuoteNumberService {

    private static final String PREFIX = "syt-";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.KOREA);
    /**
     * 일련번호의 <b>최소</b> 자릿수. 1~99는 {@code 01}처럼 두 자리로 채우고,
     * 100번째부터는 자연히 세 자리가 된다 — 잘라내지 않는다.
     * 기존에 나간 번호와 형식을 맞추려고 2를 그대로 둔다.
     */
    private static final int SEQUENCE_MIN_LENGTH = 2;

    private final ProposalRepository proposalRepository;

    /** 이미 번호가 있으면 그대로, 없으면 새로 부여해 저장한다. */
    @Transactional
    public String assign(Proposal proposal) {
        if (proposal.getQuoteNo() != null && !proposal.getQuoteNo().isBlank()) {
            return proposal.getQuoteNo();
        }

        String prefix = PREFIX + LocalDate.now().format(DATE);
        // 최댓값은 반드시 숫자로 고른다 — 문자열로는 "99" > "100"이라 100번이 되풀이된다(F-001).
        int next = proposalRepository.findQuoteNosStartingWith(prefix).stream()
                .mapToInt(quoteNo -> parseSequence(quoteNo, prefix))
                .max()
                .orElse(0) + 1;

        String quoteNo = prefix + String.format("%0" + SEQUENCE_MIN_LENGTH + "d", next);
        proposal.setQuoteNo(quoteNo);
        proposalRepository.save(proposal);
        return quoteNo;
    }

    /** 접두어 뒤 일련번호. 형식이 어긋난 값은 0으로 봐서 다음 번호가 1이 되게 한다. */
    private static int parseSequence(String quoteNo, String prefix) {
        try {
            return Integer.parseInt(quoteNo.substring(prefix.length()));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
