package com.fscallflow.analyzer.parser;

import com.fscallflow.analyzer.model.UnifiedEvent;

import java.util.*;
import java.util.stream.Stream;

public class FsEventExtractor implements FreeSwitchLogAdapter {

    @Override
    public Stream<UnifiedEvent> parse(Stream<String> lines) {
        List<UnifiedEvent> events = new ArrayList<>();
        FsLogLineParser parser = new FsLogLineParser();

        lines.forEach(l -> {
            FsLogLine log = parser.parse(l);
            if (log == null) return;

            String content = log.getContent();

            // --- INVITE ---
            if (content.contains("receiving invite")) {
                UnifiedEvent e = new UnifiedEvent();
                e.setSourceSystem("FS");
                e.setTs(log.getTs());
                e.setVerb("INVITE_INBOUND");
                e.setCallId(extractCallId(content));
                e.setAttrs(Map.of("raw", content));
                events.add(e);
            }

            // --- DIALPLAN ---
            if (content.contains("Dialplan:")) {
                UnifiedEvent e = new UnifiedEvent();
                e.setSourceSystem("FS");
                e.setTs(log.getTs());
                e.setVerb("DIALPLAN_STEP");
                e.setCallId(log.getUuid());
                e.setAttrs(Map.of("raw", content));
                events.add(e);
            }

            // --- HANGUP ---
            if (content.contains("Hangup ")) {
                UnifiedEvent e = new UnifiedEvent();
                e.setSourceSystem("FS");
                e.setTs(log.getTs());
                e.setVerb("HANGUP");
                e.setCallId(log.getUuid());
                e.setAttrs(Map.of("raw", content));
                events.add(e);
            }
        });

        return events.stream();
    }

    private String extractCallId(String content) {
        // 粗解析：call-id: XXXXX
        int idx = content.indexOf("call-id:");
        if (idx > 0) {
            return content.substring(idx + 8).split(" ")[0].trim();
        }
        return null;
    }
}
