package com.fscallflow.analyzer.parser;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FsLogLine {
    private String uuid;
    private LocalDateTime ts;
    private String level;
    private String module;
    private String content;
}
