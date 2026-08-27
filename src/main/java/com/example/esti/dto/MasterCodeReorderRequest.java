package com.example.esti.dto;

import com.example.esti.entity.MasterCodeType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 정렬 저장 — 화면에 보이는 순서대로 id를 통째로 받아 {@code sortOrder}를 0부터 다시 매긴다. */
@Getter
@Setter
public class MasterCodeReorderRequest {

    private MasterCodeType type;

    private List<Long> ids;
}
