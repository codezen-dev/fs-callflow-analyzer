package com.fscallflow.analyzer.correlate;

import com.fscallflow.analyzer.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 把 UnifiedEvent 按 callId 分桶，构建 CallGraph + Mermaid 文本 + 简单诊断。
 * 这一版先保证“能用、能看”，后面再做更精细的关联。
 */
@Component
@Slf4j
public class EventCorrelator {

    public List<AnalyzeResult> buildCallResults(List<UnifiedEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        // 1. 按 callId 分组
        Map<String, List<UnifiedEvent>> byCall =
                events.stream()
                        .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.getCallId()).orElse("unknown")));

        List<AnalyzeResult> results = new ArrayList<>();

        for (Map.Entry<String, List<UnifiedEvent>> entry : byCall.entrySet()) {
            String callId = entry.getKey();
            List<UnifiedEvent> evts = entry.getValue()
                    .stream()
                    .sorted(Comparator.comparing(
                            e -> Optional.ofNullable(e.getTs()).orElse(null),
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            CallGraph graph = buildGraph(callId, evts);
            String mermaid = buildMermaid(graph);

            AnalyzeResult ar = new AnalyzeResult();
            ar.setGraph(graph);
            ar.setMermaid(mermaid);
            results.add(ar);
        }

        // 让顺序稳定一点：按开始时间排序
        results.sort(Comparator.comparing(
                r -> Optional.ofNullable(r.getGraph().getSummary())
                        .map(CallSummary::getStartTime)
                        .orElse("")
        ));

        log.info("EventCorrelator: 生成呼叫数量={}", results.size());
        return results;
    }

    private CallGraph buildGraph(String callId, List<UnifiedEvent> events) {
        CallGraph g = new CallGraph();
        g.setGlobalId(callId);

        // 暂时固定三类节点：PSTN / FS / Agent
        CallNode pstn = new CallNode();
        pstn.setId("node-pstn");
        pstn.setType("PSTN");
        pstn.setLabel("PSTN");

        CallNode fs = new CallNode();
        fs.setId("node-fs");
        fs.setType("FS");
        fs.setLabel("FS");

        CallNode agent = new CallNode();
        agent.setId("node-agent");
        agent.setType("Agent");
        agent.setLabel("Agent");

        g.setNodes(List.of(pstn, fs, agent));

        List<CallEdge> edges = new ArrayList<>();
        List<Diagnosis> diagnoses = new ArrayList<>();

        StringBuilder dtmfSeq = new StringBuilder();
        boolean anyQueueEvent = false;
        boolean anyBridge = false;
        boolean answered = false;
        Long firstTs = null;
        Long lastTs = null;

        for (UnifiedEvent e : events) {
            long tsMillis = e.getTs() == null ? 0L :
                    e.getTs().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            if (firstTs == null || tsMillis < firstTs) firstTs = tsMillis;
            if (lastTs == null || tsMillis > lastTs) lastTs = tsMillis;

            CallEdge edge = new CallEdge();
            edge.setStartTs(tsMillis);
            edge.setType(e.getType().name());
            edge.setAttrs(e.getAttrs() == null ? Map.of() : e.getAttrs());

            switch (e.getType()) {
                case INVITE_INBOUND -> {
                    edge.setFromId("node-pstn");
                    edge.setToId("node-fs");
                }
                case ANSWER -> {
                    edge.setFromId("node-fs");
                    edge.setToId("node-agent");
                    answered = true;
                }
                case DTMF -> {
                    edge.setFromId("node-agent");
                    edge.setToId("node-fs");
                    String digit = e.getAttrs() != null ? e.getAttrs().get("digit") : null;
                    if (digit != null) dtmfSeq.append(digit);
                }
                case CALLCENTER_EVENT -> {
                    edge.setFromId("node-fs");
                    edge.setToId("node-agent");
                    anyQueueEvent = true;
                }
                case BRIDGE -> {
                    edge.setFromId("node-fs");
                    edge.setToId("node-agent");
                    anyBridge = true;
                }
                case HANGUP -> {
                    edge.setFromId("node-fs");
                    edge.setToId("node-pstn");
                }
                default -> {
                    edge.setFromId("node-fs");
                    edge.setToId("node-fs");
                }
            }

            edges.add(edge);
        }

        g.setEdges(edges);

        // Summary
        CallSummary summary = new CallSummary();
        summary.setCaller("");  // 目前还没从日志里抽，后面可以基于样本增加
        summary.setCallee("");
        summary.setDirection("inbound");
        summary.setAnswered(answered);
        summary.setQueued(anyQueueEvent);
        summary.setQueueName(anyQueueEvent ? "callcenter" : null);
        summary.setAgentId(anyBridge ? "some-agent" : null); // 暂时占位
        summary.setDtmfSequence(dtmfSeq.toString().isEmpty() ? null : dtmfSeq.toString());

        if (firstTs != null) {
            summary.setStartTime(String.valueOf(firstTs));
        }
        if (lastTs != null) {
            summary.setEndTime(String.valueOf(lastTs));
            summary.setDurationMs(lastTs - firstTs);
        }

        g.setSummary(summary);

        // 诊断（简单版）
        if (!anyQueueEvent) {
            Diagnosis d = new Diagnosis();
            d.setType("QUEUE");
            d.setSeverity("WARNING");
            d.setTitle("呼叫未进入队列");
            d.setDetail("该呼叫日志中未检测到 callcenter 队列事件，可能未进入排队流程。");
            d.setHints(List.of("检查 Dialplan 是否调用 mod_callcenter",
                    "确认来电 DID 与队列规则是否匹配"));
            diagnoses.add(d);
        }
        if (!anyBridge) {
            Diagnosis d = new Diagnosis();
            d.setType("BRIDGE");
            d.setSeverity("WARNING");
            d.setTitle("呼叫未桥接坐席");
            d.setDetail("未检测到 BRIDGE 或坐席桥接相关事件，有可能队列无人可用或路由错误。");
            d.setHints(List.of("检查队列坐席状态", "查看 FreeSWITCH callcenter 配置"));
            diagnoses.add(d);
        }

        if (dtmfSeq.length() > 0) {
            Diagnosis d = new Diagnosis();
            d.setType("DTMF");
            d.setSeverity("INFO");
            d.setTitle("用户按键序列：" + dtmfSeq);
            d.setDetail("根据日志提取的 DTMF 序列，仅供参考。");
            d.setHints(List.of());
            diagnoses.add(d);
        }

        g.setDiagnoses(diagnoses);
        return g;
    }

    /**
     * 构造 Mermaid sequenceDiagram 文本。
     * 目前只画三条生命线：PSTN / FS / Agent。
     */
    private String buildMermaid(CallGraph g) {
        StringBuilder sb = new StringBuilder();
        sb.append("sequenceDiagram\n");
        sb.append("    participant PSTN\n");
        sb.append("    participant FS\n");
        sb.append("    participant Agent\n");

        for (CallEdge e : g.getEdges()) {
            String from = mapNodeIdToName(e.getFromId());
            String to = mapNodeIdToName(e.getToId());
            String label = e.getType();

            if (e.getAttrs() != null) {
                if (e.getAttrs().containsKey("digit")) {
                    label = "DTMF " + e.getAttrs().get("digit");
                } else if (e.getAttrs().containsKey("queue")) {
                    label = "queue " + e.getAttrs().get("queue");
                }
            }

            sb.append("    ")
                    .append(from)
                    .append("->>")
                    .append(to)
                    .append(": ")
                    .append(label)
                    .append("\n");
        }

        return sb.toString();
    }

    private String mapNodeIdToName(String nodeId) {
        return switch (nodeId) {
            case "node-pstn" -> "PSTN";
            case "node-agent" -> "Agent";
            default -> "FS";
        };
    }
}
