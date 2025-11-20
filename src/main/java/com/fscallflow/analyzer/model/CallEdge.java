package com.fscallflow.analyzer.model;

import lombok.Data;

import java.util.Map;

@Data
public class CallEdge {
    private String fromId;
    private String toId;
    private String type;          // 对应 FsEventType.name()
    private long startTs;         // 毫秒时间戳，便于前端显示耗时
    private Long endTs;           // 可选
    private Map<String, String> attrs;
}
