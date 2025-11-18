package com.fscallflow.analyzer.parser;

import com.fscallflow.analyzer.model.UnifiedEvent;
import java.util.stream.Stream;

public interface FreeSwitchLogAdapter {

    Stream<UnifiedEvent> parse(Stream<String> lines);

}
