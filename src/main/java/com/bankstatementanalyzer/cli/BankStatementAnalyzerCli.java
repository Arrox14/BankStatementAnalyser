package com.bankstatementanalyzer.cli;

import com.bankstatementanalyzer.analysis.BalanceIntegrityChecker;
import com.bankstatementanalyzer.analysis.BounceDetector;
import com.bankstatementanalyzer.analysis.MonthlyAverageBalanceAnalyzer;
import com.bankstatementanalyzer.analysis.RoundTripMatcher;
import com.bankstatementanalyzer.model.BankStatement;
import com.bankstatementanalyzer.parser.StatementParseException;
import com.bankstatementanalyzer.parser.StatementParser;
import com.bankstatementanalyzer.report.AnalysisReport;
import com.bankstatementanalyzer.report.JsonReportSerializer;

import java.io.IOException;
import java.nio.file.Path;

public class BankStatementAnalyzerCli {
    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception exception) {
            System.err.println("Error: " + exception.getMessage());
            throw new IllegalStateException("Bank statement analysis failed.", exception);
        }
    }

    static void run(String[] args) throws IOException, StatementParseException {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: .\\mvnw.cmd exec:java -Dexec.args=\"<statement.csv> <report.json>\"");
        }
        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (input.equals(output)) {
            throw new IllegalArgumentException("Input CSV and output JSON paths must be different.");
        }

        BankStatement statement = new StatementParser().parse(input);
        AnalysisReport report = new AnalysisReport(
                new MonthlyAverageBalanceAnalyzer().analyze(statement),
                new BounceDetector().detect(statement),
                new RoundTripMatcher().match(statement),
                new BalanceIntegrityChecker().check(statement));
        new JsonReportSerializer().write(report, output);
    }
}
