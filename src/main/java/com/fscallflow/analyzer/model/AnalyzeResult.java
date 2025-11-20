package com.fscallflow.analyzer.model;

import lombok.Data;

/**
 * 前端期望的数据结构：
 * {
 *   graph: CallGraph,
 *   mermaid: "sequenceDiagram ..."
 * }
 */
@Data
public class AnalyzeResult {
    private CallGraph graph;
    private String mermaid;
}
