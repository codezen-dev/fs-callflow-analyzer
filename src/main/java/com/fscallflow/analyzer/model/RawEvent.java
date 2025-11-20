package com.fscallflow.analyzer.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一行 FreeSWITCH 日志，尽量从文本里抠出结构化字段。
 */
@Data
public class RawEvent {
    private String uuid;             // 行里解析到的 UUID（A/B腿任意）
    private LocalDateTime ts;        // 时间
    private String level;            // INFO / DEBUG / NOTICE ...
    private String module;           // sofia.c / mod_callcenter.c ...
    private String threadId;         // 可选
    private String message;          // 剩余文本（不含前缀）
    private String raw;              // 完整原始行
}
