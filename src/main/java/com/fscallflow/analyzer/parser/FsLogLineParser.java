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

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    /**
     * 通用格式：
     *  [可选] <uuid>
     *  <ts> <percent>% [LEVEL] module: msg [可选uuid]
     *
     *  例1（无行首 uuid，有行尾 uuid）：
     *  2025-11-03 19:05:24.187278 98.17% [NOTICE] switch_channel.c:1142 ... [ba75bf3b-...]
     *
     *  例2（有行首 uuid，无行尾 uuid）：
     *  ba75bf3b-... 2025-11-03 19:05:24.187278 98.17% [DEBUG] sofia.c:7493 ...
     */
    private static final Pattern PATTERN_MAIN = Pattern.compile(
            "^(?:(?<uuid1>[0-9a-fA-F\\-]{36})\\s+)?" +                 // optional leading UUID
                    "(?<ts>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+)" + // timestamp
                    "\\s+\\S+\\s+\\[(?<level>\\w+)\\]\\s+" +                    // 98.17% [NOTICE]
                    "(?<module>[^:]+):\\s*" +                                  // switch_channel.c:
                    "(?<msg>.*?)(?:\\s+\\[(?<uuid2>[0-9a-fA-F\\-]{36})])?$"     // optional tail [uuid]
    );

    public RawEvent parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        Matcher m = PATTERN_MAIN.matcher(line);
        if (m.find()) {
            RawEvent ev = new RawEvent();
            String uuid = m.group("uuid1");
            if (uuid == null) {
                uuid = m.group("uuid2");
            }
            ev.setUuid(uuid);
            ev.setTs(LocalDateTime.parse(m.group("ts"), TS_FMT));
            ev.setLevel(m.group("level"));
            ev.setModule(m.group("module"));
            ev.setMsg(m.group("msg"));
            ev.setRaw(line);
            return ev;
        }

        // 其他完全匹配不上的（纯 SDP、XML、栈轨迹等），先原样保留 raw，
        // 但不要生成新的“会话”，后面聚合时可以忽略 uuid=null 的。
        RawEvent ev = new RawEvent();
        ev.setRaw(line);
        return ev;
    }
}

