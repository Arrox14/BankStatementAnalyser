package com.bankstatementanalyzer.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonReportSerializer {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(AnalysisReport report, Path outputPath) throws IOException {
        String json = objectMapper.writeValueAsString(report) + System.lineSeparator();
        Files.writeString(outputPath, json, StandardCharsets.UTF_8);
    }
}
