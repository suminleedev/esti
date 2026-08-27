package com.example.esti.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link MasterCodeType}을 이름 문자열로 저장한다.
 *
 * <p>{@code @Enumerated(STRING)} 대신 쓰는 이유는 {@link MasterCode#getType()}의 주석에 있다 —
 * 요약하면 Hibernate가 붙이는 check 제약이 {@code ddl-auto=update}로 갱신되지 않아,
 * 종류를 추가하는 순간 기존 DB가 그 값을 거부한다.
 */
@Converter
public class MasterCodeTypeConverter implements AttributeConverter<MasterCodeType, String> {

    @Override
    public String convertToDatabaseColumn(MasterCodeType type) {
        return type == null ? null : type.name();
    }

    @Override
    public MasterCodeType convertToEntityAttribute(String value) {
        return value == null ? null : MasterCodeType.valueOf(value);
    }
}
