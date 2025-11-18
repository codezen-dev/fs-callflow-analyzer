package com.fscallflow.analyzer.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UnifiedEvent {
    private String sourceSystem;   // FS / PBX / SCRIPT / HTTP / etc
    private LocalDateTime ts;
    private String callId;
    private String legId;
    private String category;
    private String verb;
    private Map<String, String> attrs;
}
