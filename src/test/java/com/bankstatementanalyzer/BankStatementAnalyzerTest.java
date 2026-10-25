package com.bankstatementanalyzer;

import com.bankstatementanalyzer.analysis.BalanceIntegrityChecker;
import com.bankstatementanalyzer.analysis.BounceDetector;
import com.bankstatementanalyzer.analysis.MonthlyAverageBalanceAnalyzer;
import com.bankstatementanalyzer.analysis.RoundTripMatcher;
import com.bankstatementanalyzer.model.BankStatement;
import com.bankstatementanalyzer.parser.StatementParseException;
import com.bankstatementanalyzer.parser.StatementParser;
import com.bankstatementanalyzer.report.BalanceBreak;
import com.bankstatementanalyzer.report.Bounce;
import com.bankstatementanalyzer.report.RoundTrip;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStatementAnalyzerTest {
    private final StatementParser parser = new StatementParser();

    @Test
    void averagesCarryClosingBalanceAcrossDaysWithoutTransactions() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "100.00"),
                row("2024-01-01", "UPI-CR-ALPHA", "", "10.00", "110.00"),
                row("2024-01-03", "UPI-DR-BETA", "5.00", "", "105.00"));

        assertEquals(Map.of("2024-01", "108.33"),
                new MonthlyAverageBalanceAnalyzer().analyze(statement));
    }

    @Test
    void usesLastPrintedBalanceWhenMultipleTransactionsOccurOnOneDay() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "100.00"),
                row("2024-01-01", "UPI-CR-ALPHA", "", "10.00", "110.00"),
                row("2024-01-01", "UPI-DR-BETA", "3.00", "", "107.00"),
                row("2024-01-02", "UPI-DR-BETA", "2.00", "", "105.00"));

        assertEquals(Map.of("2024-01", "106.00"),
                new MonthlyAverageBalanceAnalyzer().analyze(statement));
    }

    @Test
    void carriesTheLastClosingBalanceAcrossMonthBoundaries() throws Exception {
        BankStatement statement = parse(
                row("2024-01-31", "OPENING BALANCE", "", "", "50.00"),
                row("2024-01-31", "UPI-CR-ALPHA", "", "10.00", "60.00"),
                row("2024-02-02", "UPI-DR-BETA", "10.00", "", "50.00"));

        assertEquals(Map.of("2024-01", "60.00", "2024-02", "55.00"),
                new MonthlyAverageBalanceAnalyzer().analyze(statement));
    }

    @Test
    void roundsMonthlyAverageHalfUpToTwoDecimalPlaces() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "0.01"),
                row("2024-01-01", "UPI-CR-ALPHA", "", "0.00", "0.01"),
                row("2024-01-02", "UPI-CR-ALPHA", "", "0.01", "0.02"));

        assertEquals(Map.of("2024-01", "0.02"),
                new MonthlyAverageBalanceAnalyzer().analyze(statement));
    }

    @Test
    void matchesBounceByChequeNumberAmountAndAssociatedCharge() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "500000.00"),
                row("2024-01-02", "CHQ DEP 123456 - SUPPLIER", "", "125000.00", "625000.00"),
                row("2024-01-05", "CHQ RTN 123456 INSUFF FUNDS", "125000.00", "", "500000.00"),
                row("2024-01-05", "RTN CHGS 123456", "590.00", "", "499410.00"));

        assertEquals(List.of(new Bounce(3, 4, 5)), new BounceDetector().detect(statement));
    }

    @Test
    void omitsBouncesWhenChequeIdentifierHasAmbiguousDeposits() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "500.00"),
                row("2024-01-02", "CHQ DEP 123456 - SUPPLIER", "", "100.00", "600.00"),
                row("2024-01-03", "CHQ DEP 123456 - SUPPLIER", "", "100.00", "700.00"),
                row("2024-01-04", "CHQ RTN 123456 INSUFF FUNDS", "100.00", "", "600.00"),
                row("2024-01-04", "RTN CHGS 123456", "5.00", "", "595.00"));

        assertTrue(new BounceDetector().detect(statement).isEmpty());
    }

    @Test
    void matchesThresholdRoundTripWithinExactlyFortyEightHours() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "500000.00"),
                row("2024-01-01", "NEFT-CR-ALPHA TRADING", "", "100000.00", "600000.00"),
                row("2024-01-03", "NEFT-DR-ALPHA TRADING", "100000.00", "", "500000.00"));

        assertEquals(List.of(new RoundTrip(3, 4, "ALPHA TRADING", "100000.00", 2)),
                new RoundTripMatcher().match(statement));
    }

    @Test
    void doesNotMatchSameDayDebitThatPrecedesItsCredit() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "500000.00"),
                row("2024-01-01", "NEFT-DR-ALPHA", "100000.00", "", "400000.00"),
                row("2024-01-01", "NEFT-CR-ALPHA", "", "100000.00", "500000.00"));

        assertTrue(new RoundTripMatcher().match(statement).isEmpty());
    }

    @Test
    void doesNotMatchBelowThresholdOrOutsideFortyEightHours() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "500000.00"),
                row("2024-01-01", "NEFT-CR-ALPHA", "", "99999.99", "599999.99"),
                row("2024-01-02", "NEFT-DR-ALPHA", "99999.99", "", "500000.00"),
                row("2024-01-03", "NEFT-CR-BETA", "", "100000.00", "600000.00"),
                row("2024-01-06", "NEFT-DR-BETA", "100000.00", "", "500000.00"));

        assertTrue(new RoundTripMatcher().match(statement).isEmpty());
    }

    @Test
    void reportsOnlyIncorrectRunningBalancesUsingPreviousPrintedBalance() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "100.00"),
                row("2024-01-01", "UPI-CR-ALPHA", "", "20.00", "120.00"),
                row("2024-01-02", "UPI-DR-BETA", "5.00", "", "118.00"),
                row("2024-01-03", "UPI-CR-GAMMA", "", "2.00", "130.00"));

        assertEquals(List.of(
                        new BalanceBreak(4, "115.00", "118.00"),
                        new BalanceBreak(5, "120.00", "130.00")),
                new BalanceIntegrityChecker().check(statement));
    }

    @Test
    void addsDecimalAmountsExactlyWithoutFloatingPointDrift() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "0.10"),
                row("2024-01-01", "UPI-CR-ALPHA", "", "0.20", "0.30"));

        assertTrue(new BalanceIntegrityChecker().check(statement).isEmpty());
    }

    @Test
    void quotedCommasArePreservedAndLineNumbersReferToPhysicalRows() throws Exception {
        BankStatement statement = parser.parse(
                "date,description,debit,credit,balance\r\n"
                        + "2024-01-01,OPENING BALANCE,,,100.00\r\n"
                        + "2024-01-01,\"NEFT-CR-ACME, SONS\",,1.00,101.00\r\n");

        assertEquals("NEFT-CR-ACME, SONS", statement.transactions().get(0).description());
        assertEquals(3, statement.transactions().get(0).line());
    }

    @Test
    void rejectsMalformedAmountsWithPhysicalLineNumber() {
        StatementParseException exception = assertThrows(StatementParseException.class, () -> parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "100.00"),
                row("2024-01-02", "UPI-CR-ALPHA", "", "1.001", "101.00")));

        assertTrue(exception.getMessage().contains("physical line 3"));
    }

    @Test
    void rejectsMissingHeaders() {
        StatementParseException exception = assertThrows(StatementParseException.class,
                () -> parser.parse("date,description,debit,credit\n"));

        assertTrue(exception.getMessage().contains("line 1"));
    }

    @Test
    void rejectsMalformedQuotedCsvRows() {
        String csv = "date,description,debit,credit,balance\n"
                + "2024-01-01,OPENING BALANCE,,,100.00\n"
                + "2024-01-02,\"UNCLOSED DESCRIPTION,,1.00,101.00\n";

        StatementParseException exception = assertThrows(StatementParseException.class, () -> parser.parse(csv));
        assertTrue(exception.getMessage().contains("physical line 3"));
    }

    @Test
    void competingRoundTripsChooseTheClosestCreditThenFileOrder() throws Exception {
        BankStatement statement = parse(
                row("2024-01-01", "OPENING BALANCE", "", "", "500000.00"),
                row("2024-01-01", "NEFT-CR-ALPHA", "", "100000.00", "600000.00"),
                row("2024-01-02", "NEFT-CR-ALPHA", "", "100000.00", "700000.00"),
                row("2024-01-03", "NEFT-DR-ALPHA", "100000.00", "", "600000.00"));

        assertEquals(List.of(new RoundTrip(4, 5, "ALPHA", "100000.00", 1)),
                new RoundTripMatcher().match(statement));
    }

    private BankStatement parse(String... rows) throws StatementParseException {
        return parser.parse("date,description,debit,credit,balance\n" + String.join("\n", rows) + "\n");
    }

    private static String row(String date, String description, String debit, String credit, String balance) {
        return String.join(",", date, description, debit, credit, balance);
    }
}
