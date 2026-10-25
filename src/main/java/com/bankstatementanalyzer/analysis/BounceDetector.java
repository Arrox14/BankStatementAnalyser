package com.bankstatementanalyzer.analysis;

import com.bankstatementanalyzer.model.BankStatement;
import com.bankstatementanalyzer.model.Transaction;
import com.bankstatementanalyzer.report.Bounce;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BounceDetector {
    private static final Pattern DEPOSIT = Pattern.compile("^CHQ DEP\\s+(\\d+)\\s*-\\s*.+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETURN = Pattern.compile("^CHQ RTN\\s+(\\d+)\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARGE = Pattern.compile("^RTN CHGS\\s+(\\d+)\\b.*$", Pattern.CASE_INSENSITIVE);

    public List<Bounce> detect(BankStatement statement) {
        Map<String, List<Transaction>> deposits = new HashMap<>();
        Map<String, List<Transaction>> returns = new HashMap<>();
        Map<String, List<Transaction>> charges = new HashMap<>();
        for (Transaction transaction : statement.transactions()) {
            addIfMatched(deposits, DEPOSIT, transaction);
            addIfMatched(returns, RETURN, transaction);
            addIfMatched(charges, CHARGE, transaction);
        }

        List<Bounce> bounces = new ArrayList<>();
        for (var entry : returns.entrySet()) {
            String chequeId = entry.getKey();
            List<Transaction> depositsForId = deposits.getOrDefault(chequeId, List.of());
            List<Transaction> returnsForId = entry.getValue();
            List<Transaction> chargesForId = charges.getOrDefault(chequeId, List.of());
            if (depositsForId.size() != 1 || returnsForId.size() != 1 || chargesForId.size() != 1) {
                continue;
            }

            var deposit = depositsForId.get(0);
            var returned = returnsForId.get(0);
            var charge = chargesForId.get(0);
            if (deposit.line() >= returned.line()
                    || returned.line() >= charge.line()
                    || deposit.credit().signum() <= 0
                    || returned.debit().signum() <= 0
                    || charge.debit().signum() <= 0
                    || deposit.credit().compareTo(returned.debit()) != 0) {
                continue;
            }
            bounces.add(new Bounce(deposit.line(), returned.line(), charge.line()));
        }
        bounces.sort(Comparator.comparingInt(Bounce::returned));
        return List.copyOf(bounces);
    }

    private static void addIfMatched(
            Map<String, List<Transaction>> matches, Pattern pattern, Transaction transaction) {
        String chequeId = identifier(pattern, transaction.description());
        if (chequeId != null) {
            matches.computeIfAbsent(chequeId, ignored -> new ArrayList<>()).add(transaction);
        }
    }

    private static String identifier(Pattern pattern, String description) {
        Matcher matcher = pattern.matcher(description);
        return matcher.matches() ? matcher.group(1) : null;
    }
}
