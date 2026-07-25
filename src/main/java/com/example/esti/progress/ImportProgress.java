package com.example.esti.progress;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImportProgress {
    private int percent;      // 0~100
    private String message;   // 현재 단계 메시지
    private boolean done;     // 완료 여부
    private boolean error;    // 에러 여부
    private Integer created;   // 완료 시 신규 세트 수(그 외 null)
    private Integer updated;   // 완료 시 갱신 세트 수(그 외 null)
}
