package com.fscallflow.analyzer.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreeSwitchLogExtractor {

    private static final Pattern CALLER_PROC_PAT =
            Pattern.compile("Processing (\\d+) <(\\d+)>");
    private static final Pattern AGENT_PAT =
            Pattern.compile("Agent (\\d+) set");
    private static final Pattern AGENT_SOFTPHONE_PAT =
            Pattern.compile("sofia/internal/(\\d+)@");
    private static final Pattern QUEUE_PAT =
            Pattern.compile("queue ([^ ]+@default)");
    private static final Pattern PEER_UUID_PAT =
            Pattern.compile("Peer UUID: ([0-9a-fA-F\\-]{36})");
    private static final Pattern RECORD_PATH_PAT =
            Pattern.compile("Stop recording file (.*\\.wav)");
    private static final Pattern HANGUP_CAUSE_PAT =
            Pattern.compile("cause: ([A-Z_]+)");

    public static String caller(String msg) {
        Matcher m = CALLER_PROC_PAT.matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    public static String agent(String msg) {
        Matcher m1 = AGENT_PAT.matcher(msg);
        if (m1.find()) return m1.group(1);
        Matcher m2 = AGENT_SOFTPHONE_PAT.matcher(msg);
        return m2.find() ? m2.group(1) : null;
    }

    public static String queue(String msg) {
        Matcher m = QUEUE_PAT.matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    public static String peerUuid(String msg) {
        Matcher m = PEER_UUID_PAT.matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    public static String recordPath(String msg) {
        Matcher m = RECORD_PATH_PAT.matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    public static String hangupCause(String msg) {
        Matcher m = HANGUP_CAUSE_PAT.matcher(msg);
        return m.find() ? m.group(1) : null;
    }
}

