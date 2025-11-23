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

    private static final Set<FsEventType> CORE_SIGNAL_TYPES = Set.of(
            FsEventType.INVITE_INBOUND,
            FsEventType.INVITE_OUTBOUND,
            FsEventType.ANSWER,
            FsEventType.HANGUP,
            FsEventType.BRIDGE
    );

    public List<AnalyzeResult> buildCallResults(List<UnifiedEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        // ⭐ 使用全局呼叫合并器
        CallJoiner joiner = new CallJoiner();
        Map<String, List<UnifiedEvent>> groups = joiner.groupCalls(events);

        List<AnalyzeResult> results = new ArrayList<>();

        for (var entry : groups.entrySet()) {
            String groupId = entry.getKey();
            List<UnifiedEvent> evts = entry.getValue();

            // ===== 噪声组过滤：callId=unknown 且 没有任何核心信令事件，直接丢掉 =====
            boolean hasCoreSignal = evts.stream().anyMatch(e -> CORE_SIGNAL_TYPES.contains(e.getType()));

            boolean noiseGroup = "unknown".equals(groupId) && !hasCoreSignal;
            if (noiseGroup) {
                log.debug("Skip noise group: id={}, size={}, sample={}",
                        groupId, evts.size(), evts.get(0).getRaw());
                continue;
            }
            // ==========================================================================

            // 排序 + 构图
            List<UnifiedEvent> sorted = evts.stream()
                    .sorted(Comparator.comparing(
                            e -> Optional.ofNullable(e.getTs()).orElse(null),
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            CallGraph graph = buildGraph(groupId, sorted);
            String mermaid = buildMermaid(graph);

            AnalyzeResult ar = new AnalyzeResult();
            ar.setGraph(graph);
            ar.setMermaid(mermaid);
            results.add(ar);
        }


        return results;
    }


    private CallGraph buildGraph(String callId, List<UnifiedEvent> events) {
        CallGraph g = new CallGraph();
        g.setGlobalId(callId);


        // 固定三类节点：PSTN / FS / Agent
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

        // 下面这些 flag 用来“只保留第一条 / 最后一条”
        boolean inviteInAdded = false;
        boolean inviteOutAdded = false;
        boolean answerAdded = false;
        boolean bridgeAdded = false;
        Long lastHangupTs = null;   // 先记录时间，最后再补 HANGUP 边

        for (UnifiedEvent e : events) {
            Long tsMillis = 0l;
            if (e.getTs() != null) {
                tsMillis = e.getTs()
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();

                if (firstTs == null || tsMillis < firstTs) firstTs = tsMillis;
                if (lastTs  == null || tsMillis > lastTs)  lastTs  = tsMillis;
            }

            FsEventType type = e.getType();

            // OTHER / RTP_EVENT 之类已经在外面过滤过，如果还有，这里也继续跳过
            if (type == FsEventType.OTHER || type == FsEventType.RTP_EVENT) {
                continue;
            }

            switch (type) {
                case INVITE_INBOUND -> {
                    if (inviteInAdded) break;   // 只保留第一条
                    inviteInAdded = true;

                    CallEdge edge = new CallEdge();
                    edge.setFromId("node-pstn");
                    edge.setToId("node-fs");
                    edge.setType(type.name());
                    edge.setStartTs(tsMillis == null ? 0L : tsMillis);
                    edge.setAttrs(e.getAttrs() == null ? Map.of() : e.getAttrs());
                    edges.add(edge);
                }

                case INVITE_OUTBOUND -> {
                    if (inviteOutAdded) break;  // 只保留第一条
                    inviteOutAdded = true;

                    CallEdge edge = new CallEdge();
                    edge.setFromId("node-fs");
                    edge.setToId("node-pstn");  // 如果以后要画到 Agent，也可以改成 node-agent
                    edge.setType(type.name());
                    edge.setStartTs(tsMillis == null ? 0L : tsMillis);
                    edge.setAttrs(e.getAttrs() == null ? Map.of() : e.getAttrs());
                    edges.add(edge);
                }

                case ANSWER -> {
                    if (answerAdded) break;     // 先简化为只保留第一条 ANSWER
                    answerAdded = true;
                    answered = true;

                    CallEdge edge = new CallEdge();
                    edge.setFromId("node-fs");
                    edge.setToId("node-agent");
                    edge.setType(type.name());
                    edge.setStartTs(tsMillis == null ? 0L : tsMillis);
                    edge.setAttrs(e.getAttrs() == null ? Map.of() : e.getAttrs());
                    edges.add(edge);
                }

                case DTMF -> {
                    // 不画边，只累积数字
                    String digit = e.getAttrs() != null ? e.getAttrs().get("digit") : null;
                    if (digit != null) {
                        dtmfSeq.append(digit);
                    }
                }

                case CALLCENTER_EVENT -> {
                    if (!anyQueueEvent) {
                        // 只保留第一条队列事件
                        CallEdge edge = new CallEdge();
                        edge.setFromId("node-fs");
                        edge.setToId("node-agent"); // 也可以改成 node-queue
                        edge.setType(type.name());
                        edge.setStartTs(tsMillis);
                        edge.setAttrs(e.getAttrs() == null ? Map.of() : e.getAttrs());
                        edges.add(edge);
                    }
                    anyQueueEvent = true;
                }

                case BRIDGE -> {
                    if (bridgeAdded) break;
                    bridgeAdded = true;
                    anyBridge = true;

                    CallEdge edge = new CallEdge();
                    edge.setFromId("node-fs");
                    edge.setToId("node-agent");
                    edge.setType(type.name());
                    edge.setStartTs(tsMillis);
                    edge.setAttrs(e.getAttrs() == null ? Map.of() : e.getAttrs());
                    edges.add(edge);
                }

                case HANGUP -> {
                    // HANGUP 我们只保留最后一条：先记时间，最后统一加边
                    if (lastHangupTs == null || tsMillis > lastHangupTs) {
                        lastHangupTs = tsMillis;
                    }
                }

                default -> {
                    // 其他暂时不画
                }
            }
        }

        // 把 DTMF 序列合并成一条边
        if (dtmfSeq.length() > 0) {
            CallEdge edge = new CallEdge();
            edge.setFromId("node-agent");
            edge.setToId("node-fs");
            edge.setType("DTMF");
            edge.setStartTs(firstTs != null ? firstTs : 0L);
            edge.setAttrs(Map.of("digit", dtmfSeq.toString()));
            edges.add(edge);
        }

        // 最后一条 HANGUP，补上一条 FS -> PSTN
        if (lastHangupTs != null) {
            CallEdge edge = new CallEdge();
            edge.setFromId("node-fs");
            edge.setToId("node-pstn");
            edge.setType("HANGUP");
            edge.setStartTs(lastHangupTs);
            edge.setAttrs(Map.of());
            edges.add(edge);
        }

        g.setEdges(edges);

        String caller = findFirstAttr(events,
                "callerNumber", "ani", "caller_id_number");

        String callee = findFirstAttr(events,
                "calleeNumber", "did", "destination_number");

        String agentId = findFirstAttr(events,
                "agentId", "agent_id", "extension");

        // ========= Summary =========
        CallSummary summary = new CallSummary();
        summary.setCaller(caller);
        summary.setCallee(callee);
        summary.setAgentId(agentId);
        String direction = events.stream().anyMatch(e -> e.getType() == FsEventType.INVITE_INBOUND)
                ? "inbound" : "outbound";
        summary.setDirection(direction);
        summary.setAnswered(answered);
        String queueNameFromEvents = events.stream()
                .map(ev -> ev.getAttrs() == null ? null : ev.getAttrs().get("queueName"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        // 是否已经进入队列
        boolean queueDetected =
                anyQueueEvent ||
                        events.stream().anyMatch(ev ->
                                ev.getAttrs() != null &&
                                        (ev.getAttrs().containsKey("queueName")
                                                || "true".equals(ev.getAttrs().get("callcenterBridge")))
                        );
        boolean queued = queueNameFromEvents != null || queueDetected; // queueDetected 是诊断里那套

        summary.setQueued(queued);
        summary.setQueueName(queueNameFromEvents);

        summary.setDtmfSequence(dtmfSeq.length() > 0 ? dtmfSeq.toString().toUpperCase() : null);

        if (firstTs != null) {
            summary.setStartTime(String.valueOf(firstTs));
        }
        if (lastTs != null) {
            summary.setEndTime(String.valueOf(lastTs));
            summary.setDurationMs(lastTs - firstTs);
        }

        g.setSummary(summary);

        // ========= Diagnosis 逻辑重写 =========



        // 是否桥接坐席（包含 mod_callcenter 的 bridge 行）
        boolean bridgeDetected =
                anyBridge ||
                        events.stream().anyMatch(ev ->
                                ev.getAttrs() != null &&
                                        "true".equals(ev.getAttrs().get("callcenterBridge"))
                        );

        // 坐席 ID（如果存在）
        String detectedAgent =
                events.stream()
                        .map(ev -> ev.getAttrs() == null ? null : ev.getAttrs().get("agentId"))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        // ================= 队列诊断 =================
        if (!queueDetected) {
            Diagnosis d = new Diagnosis();
            d.setType("QUEUE");
            d.setSeverity("WARNING");
            d.setTitle("呼叫未进入队列");
            d.setDetail("未检测到 callcenter 队列事件或排队痕迹。");
            d.setHints(List.of(
                    "检查 Dialplan 是否正确进入队列流程 (mod_callcenter)",
                    "确认 DID / 分机 呼叫路由是否匹配队列规则"
            ));
            diagnoses.add(d);
        }

        // ================= 坐席桥接诊断 =================
        if (!bridgeDetected) {
            Diagnosis d = new Diagnosis();
            d.setType("BRIDGE");
            d.setSeverity("WARNING");
            d.setTitle("呼叫未桥接坐席");
            d.setDetail("未检测到 BRIDGE 或坐席桥接事件。可能坐席不可用或路由错误。");
            d.setHints(List.of(
                    "检查队列是否有人处于 Ready 状态",
                    "查看 mod_callcenter 日志确认是否尝试拨打坐席"
            ));
            diagnoses.add(d);
        }

        // ================= DTMF =================
        if (dtmfSeq.length() > 0) {
            Diagnosis d = new Diagnosis();
            d.setType("DTMF");
            d.setSeverity("INFO");
            d.setTitle("用户按键序列：" + dtmfSeq);
            d.setDetail("根据日志检测到的按键记录，仅供参考。");
            diagnoses.add(d);
        }

        // 保存诊断结果
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

    private String findFirstAttr(List<UnifiedEvent> events, String... keys) {
        for (UnifiedEvent e : events) {
            Map<String, String> attrs = e.getAttrs();
            if (attrs == null) continue;
            for (String k : keys) {
                String v = attrs.get(k);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

}
