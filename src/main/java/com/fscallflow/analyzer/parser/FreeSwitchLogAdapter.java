package com.fscallflow.analyzer.parser;

import com.fscallflow.analyzer.model.FsEventType;
import com.fscallflow.analyzer.model.RawEvent;
import com.fscallflow.analyzer.model.UnifiedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把原始日志文件解析成统一事件列表。
 * 目前是“粗粒度”的识别：INVITE / ANSWER / HANGUP / DTMF / CALLCENTER_EVENT 等。
 * 后面我们可以根据样本继续迭代。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FreeSwitchLogAdapter {

    private final FsLogLineParser lineParser;

    // UUID 正则
    private static final Pattern UUID_PAT = Pattern.compile("([0-9a-fA-F\\-]{36})");

    // 呼叫 id 正则：call-id: 17621679...
    private static final Pattern CALL_ID_PAT = Pattern.compile("call-id:\\s*([^\\s]+)");

    // DTMF
    private static final Pattern DTMF_PAT = Pattern.compile("DTMF(?:\\s+)?(?<digit>\\d|\\*)");

    public List<UnifiedEvent> parse(InputStream in) throws Exception {
        List<UnifiedEvent> result = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                RawEvent raw = lineParser.parse(line);
                if (raw == null) {
                    continue;
                }

                UnifiedEvent e = mapToUnified(raw);
                if (e != null) {
                    result.add(e);
                }
            }
        }

        // 简单按时间排序
        result.sort(Comparator.comparing(
                e -> Optional.ofNullable(e.getTs()).orElseGet(() -> null),
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        log.info("FreeSwitchLogAdapter.parse: 生成 UnifiedEvent 数量={}", result.size());
        return result;
    }

    private UnifiedEvent mapToUnified(RawEvent raw) {
        String msg = Optional.ofNullable(raw.getMessage()).orElse("");
        String uuid = raw.getUuid();
        if (uuid == null) {
            Matcher um = UUID_PAT.matcher(msg);
            if (um.find()) {
                uuid = um.group(1);
            }
        }

        String callId = extractCallId(msg);
        String callKey = callId != null ? callId : (uuid != null ? uuid : "unknown");

        FsEventType type = classifyType(raw, msg);
        String category = classifyCategory(type);

        Map<String, String> attrs = new LinkedHashMap<>();
        if (callId != null) attrs.put("callId", callId);
        if (uuid != null) attrs.put("uuid", uuid);

        // DTMF
        if (type == FsEventType.DTMF) {
            Matcher dm = DTMF_PAT.matcher(msg);
            if (dm.find()) {
                attrs.put("digit", dm.group("digit"));
            }
        }

        UnifiedEvent e = new UnifiedEvent();
        e.setSourceSystem("FS");
        e.setTs(raw.getTs());
        e.setCallId(callKey);
        e.setLegId(uuid); // 暂时用 uuid 代表 leg
        e.setCategory(category);
        e.setVerb(type.name());
        e.setType(type);
        e.setAttrs(attrs);
        e.setRaw(raw.getRaw());
        return e;
    }

    private String extractCallId(String msg) {
        Matcher m = CALL_ID_PAT.matcher(msg);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private FsEventType classifyType(RawEvent raw, String msg) {
        String m = msg.toUpperCase(Locale.ROOT);

        if (m.contains("INVITE") && m.contains("New Channel".toUpperCase(Locale.ROOT))) {
            return FsEventType.INVITE_INBOUND;
        }
        if (m.contains("ANSWER")) {
            return FsEventType.ANSWER;
        }
        if (m.contains("HANGUP")) {
            return FsEventType.HANGUP;
        }
        if (m.contains("DTMF")) {
            return FsEventType.DTMF;
        }
        if (m.contains("callcenter".toUpperCase(Locale.ROOT))) {
            return FsEventType.CALLCENTER_EVENT;
        }
        if (m.contains("BRIDGE")) {
            return FsEventType.BRIDGE;
        }
        if (m.contains("EXECUTE") || m.contains("lua") || m.contains(".lua") || m.contains(".js")) {
            return FsEventType.SCRIPT_EXEC;
        }
        if (m.contains("HTTP") || m.contains("CURL")) {
            return FsEventType.HTTP_REQUEST;
        }
        if (raw.getModule() != null && raw.getModule().toLowerCase(Locale.ROOT).contains("dialplan")) {
            return FsEventType.DIALPLAN_STEP;
        }

        return FsEventType.OTHER;
    }

    private String classifyCategory(FsEventType type) {
        return switch (type) {
            case INVITE_INBOUND, INVITE_OUTBOUND, ANSWER, HANGUP, BRIDGE -> "SIGNAL";
            case DIALPLAN_STEP, DIALPLAN_ACTION -> "DIALPLAN";
            case CALLCENTER_EVENT -> "CALLCENTER";
            case SCRIPT_EXEC -> "SCRIPT";
            case HTTP_REQUEST -> "HTTP";
            case DTMF -> "MEDIA";
            default -> "OTHER";
        };
    }
}
