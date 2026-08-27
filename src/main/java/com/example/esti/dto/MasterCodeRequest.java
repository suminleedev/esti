package com.example.esti.dto;

import com.example.esti.entity.MasterCodeType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MasterCodeRequest {

    /** 생성 시에만 쓴다 — 수정에서는 종류를 바꾸지 않는다(다른 종류의 값이 돼 버린다). */
    private MasterCodeType type;

    private String label;

    /** null이면 유지(수정) 또는 true(생성). soft delete 복원도 이 값으로 한다. */
    private Boolean active;
}
