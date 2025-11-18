package com.fscallflow.analyzer.model;

import lombok.Data;
import java.util.Map;

@Data
public class CallEdge {
    private String fromId;
    private String toId;
    private String type;         // INVITE / BRIDGE / DIALPLAN / QUEUE / API_CALL
    private long startTs;
    private Long endTs;
    private Map<String,String> attrs;
}
