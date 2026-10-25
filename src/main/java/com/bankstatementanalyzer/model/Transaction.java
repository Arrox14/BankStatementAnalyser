package com.bankstatementanalyzer.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(
        LocalDate date,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance,
        int line,
        boolean openingBalance) {
}
