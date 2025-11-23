package com.fscallflow.analyzer.correlate;

import com.fscallflow.analyzer.model.UnifiedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * FreeSWITCH 环境下的默认 callId 生成策略。
 *
 * 优先级：
 * 1. attrs.globalCallId（上游系统传入的业务 ID / traceId）
 * 2. attrs.callId / attrs.sipCallId（SIP Call-ID）
 * 3. legId（FS 的 UUID）
 * 4. "unknown"
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

        // 1. 全局业务 ID
        if (attrs != null) {
            String globalCallId = firstNonEmpty(
                    attrs.get("globalCallId"),
                    attrs.get("bizId"),
                    attrs.get("traceId")
            );
            if (globalCallId != null) {
                return globalCallId;
            }

            // 2. SIP Call-ID
            String sipCallId = firstNonEmpty(
                    attrs.get("callId"),
                    attrs.get("sipCallId")
            );
            if (sipCallId != null) {
                return sipCallId;
            }
        }

        // 3. FS uuid / legId
        if (event.getLegId() != null && !event.getLegId().isBlank()) {
            return event.getLegId();
        }

        // 4. 兜底
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
