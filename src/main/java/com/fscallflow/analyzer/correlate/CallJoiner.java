package com.fscallflow.analyzer.correlate;

import com.fscallflow.analyzer.model.FsEventType;
import com.fscallflow.analyzer.model.UnifiedEvent;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责“全局呼叫 ID 合并”的组件：
 * - 把同一个 SIP Call-ID 下的所有 UUID 归为同一个呼叫
 * - 把日志文本中互相引用的 UUID 归为同一个呼叫（典型：lua(recording X-LEG-UUID)）
 * - 可选：把 BRIDGE 关联的两个 UUID 归为同一个呼叫（预留 otherLeg 字段）
 *
 * 最终输出：callGroupId -> List<UnifiedEvent>
 */
public class CallJoiner {

    /** UUID 正则，用来在 raw 文本里扫出“其它腿”的 uuid */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
    );

    private final Map<String, String> parent = new HashMap<>();

    // -------- Union-Find 基础 --------
    private String find(String x) {
        parent.putIfAbsent(x, x);
        if (!Objects.equals(parent.get(x), x)) {
            parent.put(x, find(parent.get(x)));
        }
        return parent.get(x);
    }

    private void union(String a, String b) {
        if (a == null || b == null) return;
        if (a.isBlank() || b.isBlank()) return;
        String pa = find(a);
        String pb = find(b);
        if (!pa.equals(pb)) {
            parent.put(pa, pb);
        }
    }

    /**
     * 核心入口：输入所有事件，输出“合并后的呼叫分组”
     */
    public Map<String, List<UnifiedEvent>> groupCalls(List<UnifiedEvent> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        // ===== 第 1 轮：SIP Call-ID 与腿 UUID 合并 =====
        for (UnifiedEvent e : events) {
            String legId = e.getLegId();
            String sipId = null;
            if (e.getAttrs() != null) {
                sipId = firstNonBlank(
                        e.getAttrs().get("globalCallId"),
                        e.getAttrs().get("sipCallId"),
                        e.getAttrs().get("callId")
                );
            }
            if (sipId != null && legId != null) {
                union(sipId, legId);
            }
        }

        // ===== 第 2 轮：BRIDGE 明确标出的两条腿（预留扩展，用不上可以忽略） =====
        for (UnifiedEvent e : events) {
            if (e.getType() == FsEventType.BRIDGE && e.getAttrs() != null) {
                String legA = e.getLegId();
                String legB = e.getAttrs().get("otherLeg"); // 如果以后在解析器里补这个字段，这里会自动生效
                union(legA, legB);
            }
        }

        // ===== 第 3 轮：扫描 raw 文本中的“其它 UUID 引用”，做腿之间的合并 =====
        for (UnifiedEvent e : events) {
            String selfLeg = e.getLegId();
            if (selfLeg == null || e.getRaw() == null) {
                continue;
            }
            Matcher m = UUID_PATTERN.matcher(e.getRaw());
            while (m.find()) {
                String otherUuid = m.group();
                if (!otherUuid.equals(selfLeg)) {
                    // B-leg 日志里提到 A-leg uuid，就把两条腿 union 起来
                    union(selfLeg, otherUuid);
                }
            }
        }

        // ===== 第 3.5 轮：用 callerNumber 做“弱合并” =====
        // 解决：部分 callcenter / IVR 日志没有 UUID / Call-ID，只能靠主叫号来挂到对应呼叫上。
        Map<String, String> callerAnchor = new HashMap<>();

        for (UnifiedEvent e : events) {
            Map<String, String> attrs = e.getAttrs();
            if (attrs == null) continue;

            String caller = attrs.get("callerNumber");
            if (caller == null || caller.isBlank()) continue;

            // 对于这个事件，能代表它的 key（尽量不用 "unknown"）
            String key = firstNonBlank(
                    e.getCallId(),
                    e.getLegId(),
                    attrs.get("sipCallId")
            );
            if (key == null || key.isBlank() || "unknown".equals(key)) {
                continue;
            }

            String anchor = callerAnchor.get(caller);
            if (anchor == null) {
                // 第一次遇到这个主叫号码，用它当前的 key 当“锚点”
                callerAnchor.put(caller, key);
            } else {
                // 之后遇到同一个主叫的其它事件，全部 union 到这个锚点上
                union(anchor, key);
            }
        }


        // ===== 第 4 步：根据“代表 ID（root）”对事件分组 =====
        Map<String, List<UnifiedEvent>> buckets = new HashMap<>();

        for (UnifiedEvent e : events) {
            // 先选一个 key（优先 callId，其次 legId，其次 unknown）
            String key = firstNonBlank(e.getCallId(), e.getLegId(), "unknown");
            String root = find(key);  // 找到它所在的全局呼叫集合代表
            buckets.computeIfAbsent(root, k -> new ArrayList<>()).add(e);
        }

        return buckets;
    }

    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
