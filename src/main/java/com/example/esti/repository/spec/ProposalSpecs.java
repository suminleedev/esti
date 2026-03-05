package com.example.esti.repository.spec;

import com.example.esti.entity.Proposal;
import org.springframework.data.jpa.domain.Specification;

public class ProposalSpecs {

    private ProposalSpecs() {}

    public static Specification<Proposal> search(String keyword, String apartmentType, String templateFilter) {
        return Specification.<Proposal>unrestricted()
                .and(keywordLike(keyword))
                .and(apartmentTypeEq(apartmentType))
                .and(templateFilter(templateFilter));
    }

    /** projectName LIKE %keyword% (필요하면 manager 등 OR 조건으로 확장 가능) */
    public static Specification<Proposal> keywordLike(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty()) return cb.conjunction();
            String like = "%" + keyword.trim() + "%";

            // ✅ 필드명 맞추기: projectName
            return cb.like(root.get("projectName"), like);
        };
    }

    /** apartmentType = ? */
    public static Specification<Proposal> apartmentTypeEq(String apartmentType) {
        return (root, query, cb) -> {
            if (apartmentType == null || apartmentType.isBlank()) return cb.conjunction();

            // ✅ 필드명 맞추기: apartmentType
            return cb.equal(root.get("apartmentType"), apartmentType);
        };
    }

    /**
     * templateFilter:
     *  - templated: templateId IS NOT NULL
     *  - manual:    templateId IS NULL
     *
     * ✅ ProposalResponse에 templateId가 있으니 이 방식이 가장 자연스러움
     */
    public static Specification<Proposal> templateFilter(String templateFilter) {
        return (root, query, cb) -> {
            if (templateFilter == null || templateFilter.isBlank()) return cb.conjunction();

            String tf = templateFilter.trim().toLowerCase();

            // ✅ 필드명 맞추기: templateId
            if ("templated".equals(tf)) return cb.isNotNull(root.get("templateId"));
            if ("manual".equals(tf)) return cb.isNull(root.get("templateId"));

            return cb.conjunction();
        };
    }

    /** status = ? */
    public static Specification<Proposal> statusEq(String status) {
        return (root, query, cb) -> {
            if (status == null || status.isBlank()) return cb.conjunction();

            // status가 String 컬럼이면 그대로
            // ✅ 필드명 맞추기: status
            return cb.equal(root.get("status"), status);

            // 만약 enum이면:
            // return cb.equal(root.get("status"), ProposalStatus.valueOf(status));
        };
    }

    // =========================
    // ✅ 앞으로 조건 늘어날 때 여기부터 계속 추가
    // 예: statusEq, dateBetween, managerLike, householdsGte ...
    // =========================


}
