package com.fscallflow.analyzer.correlate;

import com.fscallflow.analyzer.model.UnifiedEvent;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责“全局呼叫 ID 合并”的组件：
 * - 把同一个 SIP Call-ID / globalCallId 下的所有 FS UUID 归为同一个呼叫
 * - 把日志文本中互相引用的 UUID 归为同一个呼叫（典型：lua(recording X-LEG-UUID)）
 *
 * ⚠ 注意：
 *  - 这里只做“技术层”的合并（FS UUID / SIP Call-ID），不再使用业务 callId 做 union
 *  - 也暂时关闭基于 callerNumber 的“弱合并”，避免把同一主叫的多通呼叫错误黏在一起
 *
 * 最终输出：callGroupId -> List<UnifiedEvent>
 */
public class CallJoiner {

    /** 严格匹配 FreeSWITCH UUID：8-4-4-4-12 十六进制 */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"
    );

    /**
     * 主入口：根据 FS UUID / SIP Call-ID / 日志中的引用关系，把事件按“通话”分组。
     */
    public Map<String, List<UnifiedEvent>> groupCalls(List<UnifiedEvent> events) {
        Map<String, List<UnifiedEvent>> buckets = new LinkedHashMap<>();
        if (events == null || events.isEmpty()) {
            return buckets;
        }

        // 并查集 parent
        Map<String, String> parent = new HashMap<>();

        // 简单 DSU 实现
        class DSU {
            String find(String x) {
                if (x == null || x.isBlank()) {
                    return "unknown";
                }
                String p = parent.get(x);
                if (p == null) {
                    parent.put(x, x);
                    return x;
                }
                if (p.equals(x)) {
                    return x;
                }
                String r = find(p);
                parent.put(x, r);
                return r;
            }

            void union(String a, String b) {
                if (a == null || b == null) return;
                if (a.isBlank() || b.isBlank()) return;
                String ra = find(a);
                String rb = find(b);
                if (!ra.equals(rb)) {
                    parent.put(rb, ra);
                }
            }
        }
        DSU dsu = new DSU();

        // ===== 第 1 轮：SIP Call-ID / globalCallId 与 FS UUID 合并 =====
        for (UnifiedEvent e : events) {
            String legId = safeTrim(e.getLegId());
            Map<String, String> attrs = e.getAttrs();

            String sipOrGlobal = null;
            if (attrs != null) {
                sipOrGlobal = firstNonBlank(
                        safeTrim(attrs.get("globalCallId")),
                        safeTrim(attrs.get("sipCallId")),
                        safeTrim(attrs.get("callId"))
                );
            }

            if (legId != null) {
                dsu.find(legId); // 注册
            }
            if (sipOrGlobal != null) {
                dsu.find(sipOrGlobal); // 注册
            }
            if (legId != null && sipOrGlobal != null) {
                dsu.union(legId, sipOrGlobal);
            }
        }

        // ===== 第 2 轮：raw 文本中互相引用的 UUID（录音、提醒、桥接等） =====
        for (UnifiedEvent e : events) {
            String selfLeg = safeTrim(e.getLegId());
            if (selfLeg == null) {
                continue;
            }
            String raw = e.getRaw();
            if (raw == null || raw.isEmpty()) {
                continue;
            }

            Matcher m = UUID_PATTERN.matcher(raw);
            while (m.find()) {
                String otherUuid = safeTrim(m.group());
                if (otherUuid != null && !otherUuid.equals(selfLeg)) {
                    dsu.union(selfLeg, otherUuid);
                }
            }
        }

        // ❌ 暂时关闭：基于 callerNumber 的弱合并，防止把同一主叫的多通话黏成一个
        // 如果后续确实有“必须靠主叫才能挂上”的场景，再按具体模式单独加规则。

        // ===== 建桶：每个事件根据“技术 key”归属到一个 root 下 =====
        for (UnifiedEvent e : events) {
            Map<String, String> attrs = e.getAttrs();

            String technicalKey = firstNonBlank(
                    safeTrim(e.getLegId()),
                    attrs != null ? safeTrim(attrs.get("sipCallId")) : null,
                    attrs != null ? safeTrim(attrs.get("callId")) : null,
                    "unknown"
            );

            String root = dsu.find(technicalKey);
            buckets.computeIfAbsent(root, k -> new ArrayList<>()).add(e);
        }

        return buckets;
    }

    private static String safeTrim(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
