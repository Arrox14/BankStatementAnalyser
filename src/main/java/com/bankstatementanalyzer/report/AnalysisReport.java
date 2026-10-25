package com.bankstatementanalyzer.report;

import java.util.List;
import java.util.Map;

public record AnalysisReport(
        Map<String, String> monthly_average_balance,
        List<Bounce> bounces,
        List<RoundTrip> round_trips,
        List<BalanceBreak> balance_breaks) {
}
