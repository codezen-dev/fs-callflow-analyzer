package com.fscallflow.analyzer.parser;

import com.fscallflow.analyzer.model.RawEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责把 FreeSWITCH 的一行日志解析成 RawEvent。
 * 增强通用性，支持多种日志格式变体。
 */
@Component
@Slf4j
public class FsLogLineParser {

    /**
     * 增强版日志格式正则，支持：
     * - 可选的CPU百分比（如98.70%）
     * - 可选的线程ID（如[thread-1]）
     * - 可选的模块和行号（如switch_channel.c:1142）
     */
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(?<date>\\d{4}-\\d{2}-\\d{2})\\s+(?<time>\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,6})?)\\s+" +  // 日期时间
                    "(?:\\S+\\s+)?+" +  // 可选的CPU百分比
                    "(?:\\[.*?\\]\\s+)?+" +  // 可选的线程ID
                    "\\[(?<level>\\w+)\\]\\s+" +  // 日志级别
                    "(?:(?<module>[^:]+):(?<line>\\d+)\\s+)?+" +  // 可选的模块和行号
                    "(?<msg>.*)$"  // 日志内容
    );

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\b"
    );

    /**
     * 增强版KV解析正则，支持：
     * - 不带引号的value（key=value）
     * - 双引号包裹的value（key="value with space"）
     * - 单引号包裹的value（key='value with space'）
     */
    private static final Pattern KV_PATTERN = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_\\-]*)=(" +
                    "\"[^\"]*\"|" +  // 双引号value
                    "'[^']*'|" +     // 单引号value
                    "[^\\s]+)"       // 无引号value
    );

    /** 支持多种时间格式，按优先级尝试解析 */
    private static final List<DateTimeFormatter> TS_FORMATTERS = new ArrayList<>() {{
        add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));  // 6位微秒
        add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));     // 3位毫秒
        add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));          // 无毫秒
    }};

    public RawEvent parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher m = LOG_PATTERN.matcher(line);
        if (m.matches()) {
            RawEvent ev = new RawEvent();
            ev.setRaw(line);

            // 解析时间戳（多格式尝试）
            String tsStr = m.group("date") + " " + m.group("time");
            ev.setTs(parseTimestamp(tsStr));

            // 日志级别
            ev.setLevel(m.group("level"));

            // 模块（可能为null）
            ev.setModule(m.group("module"));

            // 消息内容
            String msg = m.group("msg");
            ev.setMessage(msg);
            ev.setMsg(msg);

            // 提取UUID
            extractUuid(line, ev);

            // 提取KV键值对
            extractKv(msg, ev);

            return ev;
        }

        // 非标准格式日志：尽可能提取可用信息
        RawEvent ev = new RawEvent();
        ev.setRaw(line);
        ev.setMessage(line);
        ev.setMsg(line);

        // 尝试从任意位置提取时间戳
        extractTimestampFromRaw(line, ev);

        // 提取UUID
        extractUuid(line, ev);

        // 提取KV键值对
        extractKv(line, ev);

        return ev;
    }

    /** 多格式尝试解析时间戳 */
    private LocalDateTime parseTimestamp(String tsStr) {
        for (DateTimeFormatter formatter : TS_FORMATTERS) {
            try {
                return LocalDateTime.parse(tsStr, formatter);
            } catch (Exception e) {
                // 尝试下一种格式
            }
        }
        log.debug("无法解析时间戳: {}", tsStr);
        return null;
    }

    /** 从原始行中尝试提取时间戳（非标准格式时使用） */
    private void extractTimestampFromRaw(String line, RawEvent ev) {
        // 简单匹配常见时间格式片段（如2025-10-23 17:27:09）
        Pattern looseTsPattern = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,6})?\\b");
        Matcher tsMatcher = looseTsPattern.matcher(line);
        if (tsMatcher.find()) {
            String tsStr = tsMatcher.group();
            ev.setTs(parseTimestamp(tsStr));
        }
    }

    /** 提取UUID */
    private void extractUuid(String line, RawEvent ev) {
        Matcher uuidMatcher = UUID_PATTERN.matcher(line);
        if (uuidMatcher.find()) {
            ev.setUuid(uuidMatcher.group(1));
        }
    }

    /** 提取KV键值对，自动去除引号 */
    private void extractKv(String content, RawEvent ev) {
        Matcher kvMatcher = KV_PATTERN.matcher(content);
        while (kvMatcher.find()) {
            String key = kvMatcher.group(1);
            String value = kvMatcher.group(2);

            // 去除value首尾的引号
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }

            ev.getKv().put(key, value);
        }
    }
}
