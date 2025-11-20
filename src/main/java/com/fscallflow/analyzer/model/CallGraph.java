package com.fscallflow.analyzer.model;

import lombok.Data;

import java.util.List;

@Data
public class CallGraph {
    private String globalId;           // 对应前端显示的 globalId
    private List<CallNode> nodes;
    private List<CallEdge> edges;
    private boolean logTruncatedHead;
    private boolean logTruncatedTail;
    private CallSummary summary;
    private List<Diagnosis> diagnoses;
}
