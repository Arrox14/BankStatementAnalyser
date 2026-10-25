package com.bankstatementanalyzer.analysis;

import com.bankstatementanalyzer.model.BankStatement;
import com.bankstatementanalyzer.model.Transaction;
import com.bankstatementanalyzer.report.BalanceBreak;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BalanceIntegrityChecker {
    public List<BalanceBreak> check(BankStatement statement) {
        BigDecimal previousBalance = statement.openingBalance().balance();
        List<BalanceBreak> breaks = new ArrayList<>();
        for (Transaction transaction : statement.transactions()) {
            BigDecimal expected = previousBalance.add(transaction.credit()).subtract(transaction.debit());
            if (expected.compareTo(transaction.balance()) != 0) {
                breaks.add(new BalanceBreak(
                        transaction.line(),
                        expected.setScale(2).toPlainString(),
                        transaction.balance().setScale(2).toPlainString()));
            }
            previousBalance = transaction.balance();
        }
        return List.copyOf(breaks);
    }
}
