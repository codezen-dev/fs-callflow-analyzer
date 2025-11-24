package com.fscallflow.analyzer.parser;

import com.fscallflow.analyzer.correlate.CallIdStrategy;
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
 * 把输入流中的 FreeSWITCH 日志转成 UnifiedEvent 列表。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FreeSwitchLogAdapter {

    private final FsLogLineParser lineParser;
    private final CallIdStrategy callIdStrategy;

    // sofia/external/15849466429@10.101.1.131:5081
    private static final Pattern INBOUND_NUM_PATTERN = Pattern.compile(
            "sofia/external/(\\d+)@", Pattern.CASE_INSENSITIVE);

    // sofia/internal/1003@10.37.200.4:5060
    private static final Pattern INTERNAL_AGENT_PATTERN = Pattern.compile(
            "sofia/internal/(\\d+)@", Pattern.CASE_INSENSITIVE);


    /** 从行文本中提取 SIP Call-ID */
    private static final Pattern CALL_ID_PATTERN = Pattern.compile(
            "call-id\\s*[:=]\\s*(\\S+)", Pattern.CASE_INSENSITIVE
    );

    /** DTMF 数字提取 */
    // DTMF 3:2560 / DTMF A:2560 / DTMF *:2560
    private static final Pattern DTMF_PATTERN = Pattern.compile(
            "\\bDTMF\\s+([0-9A-D#*])\\b", Pattern.CASE_INSENSITIVE);

    // digits 3 / digits 33
    private static final Pattern DIGITS_PATTERN = Pattern.compile(
            "digits\\s+([0-9A-D#*]+)", Pattern.CASE_INSENSITIVE);


    /** 队列名称 */
    private static final Pattern QUEUE_PATTERN = Pattern.compile(
            "Queue\\s+\"?([^\"\\s]+)\"?", Pattern.CASE_INSENSITIVE
    );

    /** “加入队列”日志：joining queue office79@default */
    private static final Pattern QUEUE_JOIN_PATTERN = Pattern.compile(
            "joining\\s+queue\\s+([^\\s]+)",
            Pattern.CASE_INSENSITIVE
    );

    /** 变量形式：callcenter_queue=office79@default,... */
    private static final Pattern CALLCENTER_QUEUE_VAR_PATTERN = Pattern.compile(
            "callcenter_queue=([^,\\s]+)",
            Pattern.CASE_INSENSITIVE
    );


    /** 坐席 ID / 分机号 */
    private static final Pattern AGENT_PATTERN = Pattern.compile(
            "agent\\s+([0-9a-zA-Z_\\-]+)", Pattern.CASE_INSENSITIVE
    );

    // mod_callcenter bridge：Member "15849466429" 15849466429 is bridged to agent 1003
    private static final Pattern CC_BRIDGE_PATTERN = Pattern.compile(
            "Member\\s+\"?(\\d+)\"?\\s+\\1\\s+is\\s+bridged\\s+to\\s+agent\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INLINE_UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private static String sanitizeQueueName(String raw) {
        if (raw == null) return null;
        Matcher m = INLINE_UUID_PATTERN.matcher(raw);
        if (m.find()) {
            // 截到 UUID 前一位
            return raw.substring(0, m.start()).trim();
        }
        return raw.trim();
    }


    public List<UnifiedEvent> parse(InputStream inputStream) {
        List<UnifiedEvent> result = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                RawEvent raw = lineParser.parse(line);
                if (raw == null) {
                    continue;
                }
                UnifiedEvent evt = mapToUnified(raw);
                if (evt != null) {
                    result.add(evt);
                }
            }
        } catch (Exception e) {
            log.error("Error while reading FS log stream", e);
        }

        return result;
    }

    private UnifiedEvent mapToUnified(RawEvent raw) {
        UnifiedEvent e = new UnifiedEvent();
        e.setSourceSystem("FS");
        e.setTs(raw.getTs());
        e.setLegId(raw.getUuid());
        e.setRaw(raw.getRaw());

        // 先把 RawEvent.kv 搬过来作为基础 attrs
        Map<String, String> attrs = new LinkedHashMap<>();
        if (raw.getKv() != null) {
            attrs.putAll(raw.getKv());
        }

        String msg = raw.getMsg() != null ? raw.getMsg() : "";

        // SIP Call-ID
        Matcher cid = CALL_ID_PATTERN.matcher(msg);
        if (cid.find()) {
            attrs.putIfAbsent("sipCallId", cid.group(1));
        }

        // 号码解析
        Matcher inM = INBOUND_NUM_PATTERN.matcher(msg);
        if (inM.find()) {
            attrs.putIfAbsent("callerNumber", inM.group(1));  // 呼入主叫
        }

        Matcher agentM = INTERNAL_AGENT_PATTERN.matcher(msg);
        if (agentM.find()) {
            attrs.putIfAbsent("agentId", agentM.group(1));     // 内部分机/坐席
        }

        // DTMF
        FsEventType type = classifyEventType(raw, attrs);
        e.setType(type);
        e.setCategory(classifyCategory(type));
        e.setVerb(mapVerb(type));

        // 类型相关的附加解析
        switch (type) {
            case DTMF -> {
                String digit = null;

                Matcher m1 = DTMF_PATTERN.matcher(msg);
                if (m1.find()) {
                    digit = m1.group(1);
                } else {
                    Matcher m2 = DIGITS_PATTERN.matcher(msg);
                    if (m2.find()) {
                        digit = m2.group(1);  // 可能是 "3" 或 "33"
                    }
                }

                if (digit != null) {
                    attrs.put("digit", digit.toUpperCase());  // 统一大写
                }
            }
            case CALLCENTER_EVENT -> {
                String queueName = null;
                String rawQueue = null;

                // 1) Queue "office79@default"
                Matcher qm = QUEUE_PATTERN.matcher(msg);
                if (qm.find()) {
                    rawQueue = qm.group(1);
                }

                // 2) Member ... joining queue office79@default
                if (rawQueue == null) {
                    Matcher jm = QUEUE_JOIN_PATTERN.matcher(msg);
                    if (jm.find()) {
                        rawQueue = jm.group(1);
                    }
                }

                // 3) set: callcenter_queue=office79@defaultba75b...
                Matcher vm = CALLCENTER_QUEUE_VAR_PATTERN.matcher(msg);
                if (vm.find()) {
                    // 原始值先放进去，方便排查
                    String v = vm.group(1);
                    attrs.put("callcenter_queue", v);
                    if (rawQueue == null) {
                        rawQueue = v;
                    }
                }

                if (rawQueue != null) {
                    queueName = sanitizeQueueName(rawQueue);
                    attrs.put("queueName", queueName);
                }

                // 通用 agent 提取
                Matcher am = AGENT_PATTERN.matcher(msg);
                if (am.find()) {
                    attrs.put("agentId", am.group(1));
                }

                // bridge 行保留你原来的逻辑
                Matcher cc = CC_BRIDGE_PATTERN.matcher(msg);
                if (cc.find()) {
                    String caller = cc.group(1);
                    String agent  = cc.group(2);

                    attrs.putIfAbsent("callerNumber", caller);
                    attrs.put("agentId", agent);
                    attrs.put("callcenterBridge", "true");
                }
            }

            default -> {
                // 其他类型暂时不做更多解析，后续可以按需补充
            }
        }

        e.setAttrs(attrs);

        // 使用策略生成 callId
        String callId = callIdStrategy.resolve(e);
        if (callId == null || callId.isBlank()) {
            callId = "unknown";
        }
        e.setCallId(callId);

        return e;
    }

    private FsEventType classifyEventType(RawEvent raw, Map<String, String> attrs) {
        String module = Optional.ofNullable(raw.getModule()).orElse("");
        String msg = Optional.ofNullable(raw.getMsg()).orElse("");

        String lowerMsg = msg.toLowerCase(Locale.ROOT);

        // 先按最明显的关键字判断
        if (lowerMsg.contains(" new channel ") || lowerMsg.contains("receive invite")) {
            // INBOUND / OUTBOUND 大致用 external / internal 区分
            if (lowerMsg.contains("sofia/external") || lowerMsg.contains("external/")) {
                attrs.put("direction", "inbound");
                return FsEventType.INVITE_INBOUND;
            } else {
                attrs.put("direction", "outbound");
                return FsEventType.INVITE_OUTBOUND;
            }
        }

        if (lowerMsg.contains("answer") && !lowerMsg.contains("hangup")) {
            return FsEventType.ANSWER;
        }

        if (lowerMsg.contains("hangup") || lowerMsg.contains("channel destroy")) {
            return FsEventType.HANGUP;
        }

        if (lowerMsg.contains("execute extension") || lowerMsg.contains("execute app")) {
            return FsEventType.DIALPLAN_ACTION;
        }

        if (module.toLowerCase(Locale.ROOT).contains("mod_callcenter")
                || lowerMsg.contains("callcenter::")
                || lowerMsg.contains(" joining queue ")
                || lowerMsg.contains(" leaving queue ")
                || lowerMsg.contains(" callcenter_queue=")) {
            return FsEventType.CALLCENTER_EVENT;
        }


        if (lowerMsg.contains("bridge") && lowerMsg.contains("uuid")) {
            return FsEventType.BRIDGE;
        }

        if (lowerMsg.contains("dtmf")) {
            return FsEventType.DTMF;
        }

        if (lowerMsg.contains("http") && lowerMsg.contains("url")) {
            return FsEventType.HTTP_REQUEST;
        }

        if (lowerMsg.contains("lua ") || lowerMsg.contains("python ") || lowerMsg.contains("script")) {
            return FsEventType.SCRIPT_EXEC;
        }

        if (lowerMsg.contains("rtcp") || lowerMsg.contains("rtp ")) {
            return FsEventType.RTP_EVENT;
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
            case DTMF, RTP_EVENT -> "MEDIA";
            default -> "OTHER";
        };
    }

    private String mapVerb(FsEventType type) {
        return switch (type) {
            case INVITE_INBOUND, INVITE_OUTBOUND -> "INVITE";
            case ANSWER -> "ANSWER";
            case HANGUP -> "HANGUP";
            case BRIDGE -> "BRIDGE";
            case CALLCENTER_EVENT -> "QUEUE";
            case DTMF -> "DTMF";
            case SCRIPT_EXEC -> "SCRIPT";
            case HTTP_REQUEST -> "HTTP";
            case RTP_EVENT -> "RTP";
            case DIALPLAN_STEP, DIALPLAN_ACTION -> "DIALPLAN";
            default -> "OTHER";
        };
    }
}
