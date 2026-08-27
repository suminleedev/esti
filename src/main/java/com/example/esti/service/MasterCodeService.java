package com.example.esti.service;

import com.example.esti.dto.MasterCodeReorderRequest;
import com.example.esti.dto.MasterCodeRequest;
import com.example.esti.dto.MasterCodeResponse;
import com.example.esti.entity.MasterCode;
import com.example.esti.entity.MasterCodeType;
import com.example.esti.exception.BadRequestException;
import com.example.esti.exception.NotFoundException;
import com.example.esti.repository.MasterCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MasterCodeService {

    private final MasterCodeRepository repo;

    /* ===== 조회 ===== */

    /** 설정 화면용 — 비활성 포함. */
    @Transactional(readOnly = true)
    public List<MasterCodeResponse> list(MasterCodeType type) {
        return repo.findByTypeOrderBySortOrderAscIdAsc(type).stream()
                .map(MasterCodeResponse::from)
                .collect(Collectors.toList());
    }

    /** 드롭다운용 — 활성만. 화면이 한 번에 세 종류를 다 쓰므로 종류별로 묶어 한 번에 준다. */
    @Transactional(readOnly = true)
    public Map<MasterCodeType, List<String>> activeLabelsByType() {
        Map<MasterCodeType, List<String>> result = new LinkedHashMap<>();
        for (MasterCodeType type : MasterCodeType.values()) {
            result.put(type, repo.findByTypeAndActiveTrueOrderBySortOrderAscIdAsc(type).stream()
                    .map(MasterCode::getLabel)
                    .collect(Collectors.toList()));
        }
        return result;
    }

    /* ===== 생성 ===== */

    @Transactional
    public MasterCodeResponse create(MasterCodeRequest req) {
        if (req.getType() == null) throw new BadRequestException("종류(type)가 필요합니다.");
        String label = normalizeLabel(req.getLabel());

        repo.findByTypeAndLabel(req.getType(), label).ifPresent(existing -> {
            // 비활성으로 남아 있어도 UNIQUE 제약에 걸린다. 새로 만드는 대신 복원하도록 안내한다.
            throw new BadRequestException(Boolean.TRUE.equals(existing.getActive())
                    ? "이미 있는 값입니다: " + label
                    : "숨김 처리된 값입니다. 목록에서 복원하세요: " + label);
        });

        MasterCode saved = repo.save(new MasterCode(req.getType(), label, nextSortOrder(req.getType())));
        return MasterCodeResponse.from(saved);
    }

    /* ===== 수정 (이름 변경 · 숨김/복원) ===== */

    @Transactional
    public MasterCodeResponse update(Long id, MasterCodeRequest req) {
        MasterCode code = find(id);

        if (req.getLabel() != null) {
            String label = normalizeLabel(req.getLabel());
            // 이름을 바꿔도 이미 저장된 제안서의 값은 그대로 둔다(M-6). 여기서 바뀌는 건 앞으로의 선택지뿐이다.
            repo.findByTypeAndLabel(code.getType(), label).ifPresent(other -> {
                if (!other.getId().equals(id)) throw new BadRequestException("이미 있는 값입니다: " + label);
            });
            code.setLabel(label);
        }

        if (req.getActive() != null) code.setActive(req.getActive());

        return MasterCodeResponse.from(repo.save(code));
    }

    /* ===== 삭제 (soft) ===== */

    /**
     * 하드 삭제하지 않는다(M-6). 이 값을 문자열로 들고 있는 제안서가 있어도 안전하게 감추기 위해서다.
     * 이미 숨겨진 항목을 다시 눌러도 예외로 막지 않는다 — 결과가 같다.
     */
    @Transactional
    public void deactivate(Long id) {
        MasterCode code = find(id);
        code.setActive(false);
        repo.save(code);
    }

    /* ===== 정렬 ===== */

    @Transactional
    public List<MasterCodeResponse> reorder(MasterCodeReorderRequest req) {
        if (req.getType() == null) throw new BadRequestException("종류(type)가 필요합니다.");
        if (req.getIds() == null || req.getIds().isEmpty()) throw new BadRequestException("정렬할 항목이 없습니다.");

        List<MasterCode> current = repo.findByTypeOrderBySortOrderAscIdAsc(req.getType());
        Map<Long, MasterCode> byId = current.stream()
                .collect(Collectors.toMap(MasterCode::getId, Function.identity()));

        // 요청이 해당 종류의 전건을 담고 있어야 한다. 일부만 오면 빠진 항목의 순서가 의미를 잃는다.
        if (req.getIds().size() != current.size() || !byId.keySet().containsAll(req.getIds())) {
            throw new BadRequestException("정렬 요청이 현재 목록과 일치하지 않습니다. 새로고침 후 다시 시도하세요.");
        }

        int order = 0;
        for (Long id : req.getIds()) {
            byId.get(id).setSortOrder(order++);
        }
        repo.saveAll(current);

        return list(req.getType());
    }

    /* ===== 내부 ===== */

    private MasterCode find(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("마스터 값을 찾을 수 없습니다: " + id));
    }

    private String normalizeLabel(String raw) {
        String label = raw == null ? "" : raw.trim();
        if (label.isEmpty()) throw new BadRequestException("이름이 비어 있습니다.");
        if (label.length() > 100) throw new BadRequestException("이름은 100자를 넘을 수 없습니다.");
        return label;
    }

    private int nextSortOrder(MasterCodeType type) {
        return repo.findByTypeOrderBySortOrderAscIdAsc(type).stream()
                .mapToInt(MasterCode::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }
}
