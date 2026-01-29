package com.example.esti.progress;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImportProgress {
    private int percent;     // 0~100
    private String message;  // 현재 단계 메시지
    private boolean done;    // 완료 여부
    private boolean error;   // 에러 여부
}
