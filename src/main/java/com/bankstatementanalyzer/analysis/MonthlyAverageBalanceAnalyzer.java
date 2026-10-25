package com.bankstatementanalyzer.analysis;

import com.bankstatementanalyzer.model.BankStatement;
import com.bankstatementanalyzer.model.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.TreeMap;

public class MonthlyAverageBalanceAnalyzer {
    public Map<String, String> analyze(BankStatement statement) {
        LocalDate firstDate = statement.transactions().get(0).date();
        LocalDate lastDate = statement.transactions().get(statement.transactions().size() - 1).date();
        Map<LocalDate, BigDecimal> dailyClosings = new TreeMap<>();
        for (Transaction transaction : statement.transactions()) {
            dailyClosings.put(transaction.date(), transaction.balance());
        }

        Map<String, String> averages = new TreeMap<>();
        YearMonth month = YearMonth.from(firstDate);
        YearMonth lastMonth = YearMonth.from(lastDate);
        BigDecimal closingBalance = statement.openingBalance().balance();
        while (!month.isAfter(lastMonth)) {
            LocalDate coveredStart = firstDate.isAfter(month.atDay(1)) ? firstDate : month.atDay(1);
            LocalDate coveredEnd = lastDate.isBefore(month.atEndOfMonth()) ? lastDate : month.atEndOfMonth();
            BigDecimal total = BigDecimal.ZERO;
            for (LocalDate date = coveredStart; !date.isAfter(coveredEnd); date = date.plusDays(1)) {
                BigDecimal dailyClosing = dailyClosings.get(date);
                if (dailyClosing != null) {
                    closingBalance = dailyClosing;
                }
                total = total.add(closingBalance);
            }
            long dayCount = coveredStart.datesUntil(coveredEnd.plusDays(1)).count();
            averages.put(month.toString(),
                    total.divide(BigDecimal.valueOf(dayCount), 2, RoundingMode.HALF_UP).toPlainString());
            month = month.plusMonths(1);
        }
        return averages;
    }
}
