package com.fscallflow.analyzer.model;

import lombok.Data;

import java.util.List;

@Data
public class CallSummary {
    private String caller;
    private String callee;
    private String direction;        // inbound / outbound
    private String startTime;
    private String endTime;
    private Long durationMs;
    private boolean answered;
    private boolean queued;
    private String queueName;
    private String agentId;
    private String hangupCause;
    private String dtmfSequence;     // 用户按键序列（简单版）
    private String primaryFsCallId; // 本组内主要 FS callId，用于关联事件
    private List<String> fsCallIds;  // 本组内所有 FS callId，用于在前端显示与原始 UUID 对应关系
}
