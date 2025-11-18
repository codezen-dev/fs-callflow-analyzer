package com.fscallflow.analyzer.model;

import lombok.Data;
import java.util.List;

@Data
public class Diagnosis {
    private String type;
    private String severity; // INFO / WARNING / ERROR
    private String title;
    private String detail;
    private List<String> hints;
}
