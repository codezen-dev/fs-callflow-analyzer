package com.fscallflow.analyzer.correlate;

import com.fscallflow.analyzer.model.UnifiedEvent;

/**
 * 决定“这个 UnifiedEvent 属于哪个 callId”的策略。
 * 可以按不同来源（FS / PBX / HTTP）提供不同实现。
 */
public interface CallIdStrategy {

    /**
     * 从 UnifiedEvent 中决定一个全局 callId。
     * 返回 null 或空串时，调用方应兜底成 "unknown"。
     */
    String resolve(UnifiedEvent event);
}
