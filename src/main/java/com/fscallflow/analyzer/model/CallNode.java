package com.fscallflow.analyzer.model;

import lombok.Data;

@Data
public class CallNode {
    private String id;       // node-pstn / node-fs / node-agent / node-ivr ...
    private String type;     // PSTN / FS / Agent / IVR / Queue ...
    private String label;    // 展示名称
}
