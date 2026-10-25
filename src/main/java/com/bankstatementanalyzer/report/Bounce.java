package com.bankstatementanalyzer.report;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Bounce(int deposit, @JsonProperty("return") int returned, int charge) {
}
