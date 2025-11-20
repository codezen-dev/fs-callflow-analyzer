package com.fscallflow.analyzer.controller;

import com.fscallflow.analyzer.correlate.EventCorrelator;
import com.fscallflow.analyzer.model.AnalyzeResult;
import com.fscallflow.analyzer.parser.FreeSwitchLogAdapter;
import com.fscallflow.analyzer.model.UnifiedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/analyze")
@Slf4j
@RequiredArgsConstructor
public class AnalyzeController {

    private final FreeSwitchLogAdapter logAdapter;
    private final EventCorrelator correlator;

    /**
     * 兼容你现有前端：/api/analyze/log2，返回 List<AnalyzeResult>
     */
    @PostMapping(value = "/log2", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<AnalyzeResult> analyzeLogV2(@RequestPart("file") MultipartFile file) throws Exception {
        log.info("收到日志文件: name={}, size={}", file.getOriginalFilename(), file.getSize());
        try (InputStream in = file.getInputStream()) {
            List<UnifiedEvent> events = logAdapter.parse(in);
            log.info("解析得到 UnifiedEvent 数量: {}", events.size());
            return correlator.buildCallResults(events);
        }
    }

    /**
     * 简单版本：/api/analyze/log，返回单个结果（第一通呼叫），你自己看要不要保留。
     */
    @PostMapping(value = "/log", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeResult analyzeLog(@RequestPart("file") MultipartFile file) throws Exception {
        try (InputStream in = file.getInputStream()) {
            List<UnifiedEvent> events = logAdapter.parse(in);
            List<AnalyzeResult> results = correlator.buildCallResults(events);
            return results.isEmpty() ? null : results.get(0);
        }
    }
}
