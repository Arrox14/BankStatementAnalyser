package com.bankstatementanalyzer.analysis;

import com.bankstatementanalyzer.model.BankStatement;
import com.bankstatementanalyzer.model.Transaction;
import com.bankstatementanalyzer.report.RoundTrip;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoundTripMatcher {
    private static final BigDecimal MINIMUM_AMOUNT = new BigDecimal("100000.00");
    private static final Pattern CREDIT_DESCRIPTION =
            Pattern.compile("^(?:NEFT-CR-|UPI-CR-)(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHEQUE_DEPOSIT =
            Pattern.compile("^CHQ DEP\\s+\\d+\\s*-\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBIT_DESCRIPTION =
            Pattern.compile("^(?:NEFT-DR-|UPI-DR-|POS PURCHASE\\s+)(.+)$", Pattern.CASE_INSENSITIVE);

    public List<RoundTrip> match(BankStatement statement) {
        List<Candidate> candidates = new ArrayList<>();
        List<Transaction> credits = statement.transactions().stream()
                .filter(transaction -> transaction.credit().compareTo(MINIMUM_AMOUNT) >= 0)
                .filter(transaction -> counterparty(CREDIT_DESCRIPTION, transaction.description()) != null
                        || counterparty(CHEQUE_DEPOSIT, transaction.description()) != null)
                .toList();
        List<Transaction> debits = statement.transactions().stream()
                .filter(transaction -> transaction.debit().compareTo(MINIMUM_AMOUNT) >= 0)
                .filter(transaction -> counterparty(DEBIT_DESCRIPTION, transaction.description()) != null)
                .toList();

        for (Transaction credit : credits) {
            String creditCounterparty = creditCounterparty(credit.description());
            for (Transaction debit : debits) {
                String debitCounterparty = counterparty(DEBIT_DESCRIPTION, debit.description());
                long gapDays = ChronoUnit.DAYS.between(credit.date(), debit.date());
                if (creditCounterparty.equals(debitCounterparty)
                        && credit.credit().compareTo(debit.debit()) == 0
                        && debit.line() > credit.line()
                        && gapDays >= 0 && gapDays <= 2) {
                    candidates.add(new Candidate(credit, debit, creditCounterparty, gapDays));
                }
            }
        }

        candidates.sort(Comparator.comparingLong(Candidate::gapDays)
                .thenComparingInt(candidate -> candidate.credit().line())
                .thenComparingInt(candidate -> candidate.debit().line()));
        Set<Integer> usedLines = new HashSet<>();
        List<RoundTrip> matches = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (usedLines.contains(candidate.credit().line()) || usedLines.contains(candidate.debit().line())) {
                continue;
            }
            matches.add(new RoundTrip(
                    candidate.credit().line(),
                    candidate.debit().line(),
                    candidate.counterparty(),
                    candidate.credit().credit().setScale(2).toPlainString(),
                    candidate.gapDays()));
            usedLines.add(candidate.credit().line());
            usedLines.add(candidate.debit().line());
        }
        return List.copyOf(matches);
    }

    private static String creditCounterparty(String description) {
        String name = counterparty(CREDIT_DESCRIPTION, description);
        return name != null ? name : counterparty(CHEQUE_DEPOSIT, description);
    }

    private static String counterparty(Pattern pattern, String description) {
        Matcher matcher = pattern.matcher(description);
        if (!matcher.matches()) {
            return null;
        }
        String normalized = matcher.group(1).trim().replaceAll("\\s+", " ");
        normalized = normalized.replaceFirst("(?i)^M/S\\s+", "").replace("\"", "");
        normalized = normalized.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private record Candidate(Transaction credit, Transaction debit, String counterparty, long gapDays) {
    }
}
