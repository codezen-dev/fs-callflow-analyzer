package com.fscallflow.analyzer.correlate;

import com.fscallflow.analyzer.model.*;

import java.util.*;

public class EventCorrelator {

    public CallGraph build(List<UnifiedEvent> events) {

        // 简化：按 callId 聚合（第一版不处理跨节点）
        String callId = events.stream()
                .map(UnifiedEvent::getCallId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("unknown");

        CallGraph graph = new CallGraph();
        graph.setGlobalId(callId);

        // 节点先写死（后面才做动态）
        CallNode pstn = new CallNode();
        pstn.setId("node-pstn");
        pstn.setType("PSTN");

        CallNode fs = new CallNode();
        fs.setId("node-fs");
        fs.setType("FS");

        graph.setNodes(List.of(pstn, fs));

        // 边
        List<CallEdge> edges = new ArrayList<>();

        for (UnifiedEvent e : events) {
            CallEdge edge = new CallEdge();
            edge.setFromId("node-pstn");
            edge.setToId("node-fs");
            edge.setType(e.getVerb());
            edge.setStartTs(e.getTs().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            edges.add(edge);
        }

        graph.setEdges(edges);
        graph.setDiagnoses(List.of()); // 诊断后面再做

        return graph;
    }
}
