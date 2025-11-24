package com.fscallflow.analyzer.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 从 RawEvent 抽象后的“统一事件”，后续所有逻辑都基于它。
 * 这里不强行限制来源，只要能凑齐这些字段的都可以塞进来。
 */
@Data
public class UnifiedEvent {

    /** 来源系统：FS / PBX / SCRIPT / HTTP 等 */
    private String sourceSystem;

    /** 事件时间 */
    private LocalDateTime ts;

    /**
     * 全局呼叫 ID：
     * - 优先使用自定义业务 ID（globalCallId / traceId 等）
     * - 再用 SIP Call-ID
     * - 再用 FS UUID（主腿）
     * - 最后兜底 "unknown"
     */
    private String callId;

    /**
     * 腿 ID：A腿 / B腿 / queue leg 等。
     * 对 FS 来说，一般就是该行日志里的 UUID。
     */
    private String legId;

    /**
     * 事件大类：
     * SIGNAL / DIALPLAN / CALLCENTER / MEDIA / SCRIPT / HTTP / OTHER
     */
    private String category;

    /** 动词：INVITE / ANSWER / BRIDGE / HANGUP / DTMF / QUEUE 等 */
    private String verb;

    /** 更细的类型，用 FsEventType 来表达 */
    private FsEventType type;

    /**
     * 额外属性：
     * digit / queue / agentId / sipCallId / globalCallId / caller / callee / direction ...
     */
    private Map<String, String> attrs;

    /** 原始行文本（方便跳转和排查） */
    private String raw;
}
