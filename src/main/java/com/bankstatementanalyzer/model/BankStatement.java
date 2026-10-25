package com.bankstatementanalyzer.model;

import java.util.List;

public record BankStatement(Transaction openingBalance, List<Transaction> transactions) {
    public BankStatement {
        transactions = List.copyOf(transactions);
    }
}
