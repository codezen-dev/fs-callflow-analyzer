package com.fscallflow.analyzer.controller;

import com.fscallflow.analyzer.model.CallGraph;
import com.fscallflow.analyzer.parser.FsEventExtractor;
import com.fscallflow.analyzer.correlate.EventCorrelator;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analyze")
public class AnalyzeController {

    @PostMapping("/log")
    public CallGraph analyze(@RequestParam("file") MultipartFile file) throws Exception {

        List<String> lines = new String(file.getBytes(), StandardCharsets.UTF_8)
                .lines()
                .collect(Collectors.toList());

        FsEventExtractor extractor = new FsEventExtractor();
        List events = extractor.parse(lines.stream()).collect(Collectors.toList());

        EventCorrelator correlator = new EventCorrelator();
        return correlator.build(events);
    }
}
