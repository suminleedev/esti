package com.example.esti.service;

import com.example.esti.dto.MasterCodeReorderRequest;
import com.example.esti.dto.MasterCodeRequest;
import com.example.esti.dto.MasterCodeResponse;
import com.example.esti.entity.MasterCodeType;
import com.example.esti.exception.BadRequestException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:derby:memory:masterCodeTest;create=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
// 서비스가 실제로 커밋하는 통합 테스트라 한 클래스 안에서 데이터가 누적된다.
// 시딩 직후 상태를 보는 검증을 먼저, 목록을 흔드는 검증을 뒤로 고정한다.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MasterCodeServiceTest {

    @Autowired private MasterCodeService service;

    private MasterCodeRequest request(MasterCodeType type, String label) {
        MasterCodeRequest req = new MasterCodeRequest();
        req.setType(type);
        req.setLabel(label);
        return req;
    }

    private List<String> labels(MasterCodeType type) {
        return service.list(type).stream().map(MasterCodeResponse::getLabel).toList();
    }

    @Test
    @Order(1)
    void 기동_시_세_종류가_기본값으로_시딩되고_순서가_보존된다() {
        // MasterCodeSeeder(ApplicationRunner)가 컨텍스트 기동 때 넣는다
        for (MasterCodeType type : MasterCodeType.values()) {
            assertThat(labels(type)).containsExactlyElementsOf(type.getDefaults());
        }
    }

    @Test
    @Order(2)
    void 값을_추가하면_목록_맨_뒤에_붙는다() {
        MasterCodeResponse created = service.create(request(MasterCodeType.AREA, "  현관  "));

        assertThat(created.getLabel()).isEqualTo("현관"); // 앞뒤 공백은 정리된다
        assertThat(created.getActive()).isTrue();
        assertThat(labels(MasterCodeType.AREA)).last().isEqualTo("현관");
    }

    @Test
    @Order(3)
    void 같은_종류에_같은_이름은_추가할_수_없다() {
        String label = MasterCodeType.CATEGORY.getDefaults().get(0);

        assertThrows(BadRequestException.class,
                () -> service.create(request(MasterCodeType.CATEGORY, label)));
    }

    @Test
    @Order(4)
    void 이름이_비면_거부한다() {
        assertThrows(BadRequestException.class,
                () -> service.create(request(MasterCodeType.AREA, "   ")));
    }

    @Test
    @Order(5)
    void 숨기면_드롭다운에서만_빠지고_설정_목록에는_남는다() {
        MasterCodeResponse created = service.create(request(MasterCodeType.BUILDING_TYPE, "지하주차장"));

        service.deactivate(created.getId());

        assertThat(service.activeLabelsByType().get(MasterCodeType.BUILDING_TYPE))
                .doesNotContain("지하주차장");
        assertThat(labels(MasterCodeType.BUILDING_TYPE)).contains("지하주차장");
    }

    @Test
    @Order(6)
    void 숨긴_값은_복원할_수_있다() {
        MasterCodeResponse created = service.create(request(MasterCodeType.BUILDING_TYPE, "관리동"));
        service.deactivate(created.getId());

        MasterCodeRequest restore = new MasterCodeRequest();
        restore.setActive(true);
        service.update(created.getId(), restore);

        assertThat(service.activeLabelsByType().get(MasterCodeType.BUILDING_TYPE)).contains("관리동");
    }

    @Test
    @Order(7)
    void 숨긴_이름과_같은_값을_새로_만들려_하면_복원하라고_막는다() {
        MasterCodeResponse created = service.create(request(MasterCodeType.AREA, "발코니"));
        service.deactivate(created.getId());

        // UNIQUE(type,label)에 걸리므로 새로 만들 수 없다 — 사용자에게는 복원 경로를 알려준다
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.create(request(MasterCodeType.AREA, "발코니")));
        assertThat(e.getMessage()).contains("복원");
    }

    @Test
    @Order(8)
    void 이름을_바꿔도_다른_항목과_겹치면_거부한다() {
        MasterCodeResponse created = service.create(request(MasterCodeType.CATEGORY, "젠더세면기"));

        MasterCodeRequest rename = new MasterCodeRequest();
        rename.setLabel(MasterCodeType.CATEGORY.getDefaults().get(0));

        assertThrows(BadRequestException.class, () -> service.update(created.getId(), rename));
    }

    @Test
    @Order(9)
    void 순서를_바꾸면_그_순서대로_다시_매겨진다() {
        List<MasterCodeResponse> before = service.list(MasterCodeType.CATEGORY);
        List<Long> reversed = new ArrayList<>(before.stream().map(MasterCodeResponse::getId).toList());
        java.util.Collections.reverse(reversed);

        MasterCodeReorderRequest req = new MasterCodeReorderRequest();
        req.setType(MasterCodeType.CATEGORY);
        req.setIds(reversed);
        service.reorder(req);

        assertThat(service.list(MasterCodeType.CATEGORY).stream().map(MasterCodeResponse::getId).toList())
                .containsExactlyElementsOf(reversed);
        assertThat(service.list(MasterCodeType.CATEGORY).get(0).getSortOrder()).isZero();
    }

    @Test
    @Order(10)
    void 정렬_요청이_전건이_아니면_거부한다() {
        List<Long> partial = List.of(service.list(MasterCodeType.AREA).get(0).getId());

        MasterCodeReorderRequest req = new MasterCodeReorderRequest();
        req.setType(MasterCodeType.AREA);
        req.setIds(partial);

        assertThrows(BadRequestException.class, () -> service.reorder(req));
    }
}
