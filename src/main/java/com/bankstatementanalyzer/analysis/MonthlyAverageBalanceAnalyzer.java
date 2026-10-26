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
        LocalDate openingDate = statement.openingBalance().date();
        LocalDate lastDate = statement.transactions()
                .get(statement.transactions().size() - 1).date();

        Map<LocalDate, BigDecimal> dailyClosings = new TreeMap<>();

        for (Transaction transaction : statement.transactions()) {
            // The last transaction on a date determines that day's closing balance.
            dailyClosings.put(transaction.date(), transaction.balance());
        }

        Map<String, String> averages = new TreeMap<>();
        YearMonth month = YearMonth.from(openingDate);
        YearMonth lastMonth = YearMonth.from(lastDate);
        BigDecimal closingBalance = statement.openingBalance().balance();

        while (!month.isAfter(lastMonth)) {
            BigDecimal total = BigDecimal.ZERO;
            LocalDate monthStart = month.atDay(1);
            if (month.equals(YearMonth.from(openingDate)) && openingDate.isAfter(monthStart)) {
                monthStart = openingDate;
            }
            LocalDate monthEnd = month.atEndOfMonth();

            for (LocalDate date = monthStart;
                 !date.isAfter(monthEnd);
                 date = date.plusDays(1)) {

                BigDecimal dailyClosing = dailyClosings.get(date);

                if (dailyClosing != null) {
                    closingBalance = dailyClosing;
                }

                total = total.add(closingBalance);
            }

            long dayCount = monthStart.datesUntil(monthEnd.plusDays(1)).count();

            averages.put(
                    month.toString(),
                    total.divide(
                            BigDecimal.valueOf(dayCount),
                            2,
                            RoundingMode.HALF_UP
                    ).toPlainString()
            );

            month = month.plusMonths(1);
        }

        return averages;
    }
}
