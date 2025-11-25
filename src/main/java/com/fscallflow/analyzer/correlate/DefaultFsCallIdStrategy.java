package com.fscallflow.analyzer.correlate;

import com.fscallflow.analyzer.model.UnifiedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FreeSWITCH 环境下的默认 callId 生成策略。
 *
 * 语义约定：
 * - UnifiedEvent.legId                 : FS 通道 UUID（技术层 ID）
 * - attrs.globalCallId/bizId/traceId  : 上游系统定义的“业务呼叫 ID”
 * - attrs.callId/sipCallId            : SIP Call-ID（同一个对话下所有腿共享）
 *
 * 本策略只负责生成“业务上可读的 callId”，用于 UI 展示和链路追踪。
 * 不参与 CallJoiner 的分组逻辑（CallJoiner 只看 legId / sipCallId 等技术 key）。
 *
 * 同时在 attrs 中写入 callIdSource，方便后续统计与排查：
 * - global / sip / leg / unknown
 */
@Component
@Slf4j
public class DefaultFsCallIdStrategy implements CallIdStrategy {

    @Override
    public String resolve(UnifiedEvent event) {
        if (event == null) {
            return "unknown";
        }

        Map<String, String> attrs = event.getAttrs();
        if (attrs == null) {
            // 理论上不会发生（Adapter 已经先 setAttrs），但这里兜一下
            attrs = new LinkedHashMap<>();
            event.setAttrs(attrs);
        }

        // 1. 全局业务 ID（上游系统主动传入）
        String globalId = firstNonEmpty(
                attrs.get("globalCallId"),
                attrs.get("bizId"),
                attrs.get("traceId")
        );
        if (globalId != null) {
            attrs.put("callIdSource", "global");
            return globalId;
        }

        // 2. SIP Call-ID 层：同一个 SIP 对话共享同一个 ID
        String sipCallId = firstNonEmpty(
                attrs.get("callId"),
                attrs.get("sipCallId")
        );
        if (sipCallId != null) {
            attrs.put("callIdSource", "sip");
            return sipCallId;
        }

        // 3. 兜底：直接使用 FS UUID（legId）
        String legId = event.getLegId();
        if (legId != null && !legId.isBlank()) {
            attrs.put("callIdSource", "leg");
            return legId;
        }

        // 4. 最后兜底：unknown
        attrs.put("callIdSource", "unknown");
        return "unknown";
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
