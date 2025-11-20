package com.fscallflow.analyzer.parser;

import com.fscallflow.analyzer.model.RawEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责把 FreeSWITCH 的一行日志解析成 RawEvent。
 * 这里只做“够用”的解析，后面我们再逐步增强。
 */
@Component
@Slf4j
public class FsLogLineParser {

    // 示例：2025-11-03 19:05:24.187278 98.17% [NOTICE] switch_channel.c:...
    private static final Pattern PATTERN_NO_UUID = Pattern.compile(
            "^\\s*(?<ts>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+)" +
                    "\\s+\\S+\\s+\\[(?<level>\\w+)]\\s+(?<module>[^:]+):[^\\s]+\\s*(?<msg>.*)$");

    // 示例：UUID 2025-11-03 19:05:24.18... [DEBUG] sofia.c:... msg
    private static final Pattern PATTERN_WITH_UUID = Pattern.compile(
            "^(?<uuid>[0-9a-fA-F\\-]{36})\\s+" +
                    "(?<ts>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+).*?\\[(?<level>\\w+)]\\s+(?<module>[^:]+):[^\\s]+\\s*(?<msg>.*)$");

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    public RawEvent parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        RawEvent ev = tryWithUuid(line);
        if (ev == null) {
            ev = tryNoUuid(line);
        }
        if (ev == null) {
            // 实在解析不了，就只保留 raw
            ev = new RawEvent();
            ev.setRaw(line);
        }
        return ev;
    }

    private RawEvent tryWithUuid(String line) {
        Matcher m = PATTERN_WITH_UUID.matcher(line);
        if (!m.find()) return null;

        RawEvent ev = new RawEvent();
        ev.setUuid(m.group("uuid"));
        ev.setTs(parseTs(m.group("ts")));
        ev.setLevel(m.group("level"));
        ev.setModule(m.group("module"));
        ev.setMessage(m.group("msg"));
        ev.setRaw(line);
        return ev;
    }

    private RawEvent tryNoUuid(String line) {
        Matcher m = PATTERN_NO_UUID.matcher(line);
        if (!m.find()) return null;

        RawEvent ev = new RawEvent();
        ev.setTs(parseTs(m.group("ts")));
        ev.setLevel(m.group("level"));
        ev.setModule(m.group("module"));
        ev.setMessage(m.group("msg"));
        ev.setRaw(line);
        return ev;
    }

    private LocalDateTime parseTs(String ts) {
        try {
            return LocalDateTime.parse(ts.trim(), TS_FMT);
        } catch (Exception e) {
            log.debug("解析时间失败: {}", ts);
            return null;
        }
    }
}
