package com.fscallflow.analyzer.model;

import lombok.Data;
import java.util.Map;

@Data
public class CallNode {
    private String id;
    private String type;   // FS / Agent / Gateway / PBX / Script / HTTP
    private Map<String,String> attrs;
}
