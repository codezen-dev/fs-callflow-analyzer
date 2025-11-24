package com.fscallflow.analyzer.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 一行 FreeSWITCH 日志，尽量从文本里抠出结构化字段。
 */
@Data
public class RawEvent {

    /** 行里解析到的 UUID（A/B腿任意） */
    private String uuid;

    /** 时间 */
    private LocalDateTime ts;

    /** 日志级别：INFO / DEBUG / NOTICE ... */
    private String level;

    /** 模块：sofia.c / mod_callcenter.c ... */
    private String module;

    /** 线程 ID，可选 */
    private String threadId;

    /** 剩余文本（不含时间、级别、模块前缀） */
    private String message;

    /** 完整原始行 */
    private String raw;

    /** msg = message 的别名，保持兼容 */
    private String msg;

    /**
     * 从 message 中自动解析出的 key=value 形式的 KV。
     * 比如：
     *   traceId=123 bizId=abc callId=xxx
     */
    private Map<String, String> kv = new HashMap<>();
}
