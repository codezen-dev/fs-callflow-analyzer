package com.fscallflow.analyzer.model;

import lombok.Data;
import java.util.List;

@Data
public class CallGraph {
    private String globalId;  // SIP call-id
    private List<CallNode> nodes;
    private List<CallEdge> edges;
    private List<Diagnosis> diagnoses;
}
