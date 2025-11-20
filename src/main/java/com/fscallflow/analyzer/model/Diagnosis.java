package com.fscallflow.analyzer.model;

import lombok.Data;

import java.util.List;

@Data
public class Diagnosis {
    private String type;        // 例如 QUEUE, BRIDGE, SCRIPT
    private String severity;    // INFO / WARNING / ERROR
    private String title;       // 人类可读标题
    private String detail;      // 详情
    private List<String> hints; // 建议
}
