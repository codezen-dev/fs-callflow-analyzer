package com.fscallflow.analyzer.model;

import lombok.Data;

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
}
