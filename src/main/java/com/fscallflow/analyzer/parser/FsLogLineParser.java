package com.fscallflow.analyzer.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FsLogLineParser {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    public FsLogLine parse(String line) {
        try {
            // 格式示例：
            // uuid timestamp xxxx [INFO] module:line content
            String[] parts = line.split(" ", 4);
            if (parts.length < 4) return null;

            FsLogLine f = new FsLogLine();
            f.setUuid(parts[0]);
            f.setTs(LocalDateTime.parse(parts[1] + " " + parts[2], TS_FORMAT));

            // 剩下的部分手动切，不用太严
            String rest = parts[3];
            int modIdx = rest.indexOf("]");
            if (modIdx > 0) {
                f.setModule(rest.substring(modIdx + 2, rest.indexOf(" ", modIdx + 2)));
                f.setContent(rest);
            }

            return f;

        } catch (Exception e) {
            return null;
        }
    }
}
