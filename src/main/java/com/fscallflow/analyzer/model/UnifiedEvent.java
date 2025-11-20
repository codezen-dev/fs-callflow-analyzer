package com.fscallflow.analyzer.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 从 RawEvent 抽象后的“统一事件”，后续所有逻辑都基于它。
 */
@Data
public class UnifiedEvent {

    /** 来源系统：FS / PBX / SCRIPT / HTTP 等 */
    private String sourceSystem;

    /** 事件时间 */
    private LocalDateTime ts;

    /** 呼叫全局标识：优先用 UUID，没有就用 call-id 或我们构造的 key */
    private String callId;

    /** A腿 / B腿 / queue 等 */
    private String legId;

    /** 大类：SIGNAL / DIALPLAN / CALLCENTER / MEDIA / SCRIPT / HTTP ... */
    private String category;

    /** 动词：INVITE / ANSWER / BRIDGE / HANGUP / DTMF / QUEUE 等 */
    private String verb;

    /** 更细的类型，用 FsEventType 来表达 */
    private FsEventType type;

    /** 额外属性，比如 digit、queue、agentId、destNumber 等 */
    private Map<String, String> attrs;

    /** 原始行文本（方便跳转和排查） */
    private String raw;
}
