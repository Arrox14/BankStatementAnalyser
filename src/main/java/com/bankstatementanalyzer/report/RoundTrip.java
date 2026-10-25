package com.bankstatementanalyzer.report;

public record RoundTrip(int credit, int debit, String counterparty, String amount, long gap_days) {
}
