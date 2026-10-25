package com.bankstatementanalyzer.parser;

import com.bankstatementanalyzer.model.BankStatement;
import com.bankstatementanalyzer.model.Transaction;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StatementParser {
    private static final List<String> HEADERS = List.of("date", "description", "debit", "credit", "balance");
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setIgnoreEmptyLines(false)
            .setIgnoreSurroundingSpaces(false)
            .get();
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[0-9]+(?:\\.[0-9]{1,2})?");
    private static final Pattern BALANCE_PATTERN = Pattern.compile("-?[0-9]+(?:\\.[0-9]{1,2})?");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public BankStatement parse(Path path) throws IOException, StatementParseException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public BankStatement parse(String csvContent) throws StatementParseException {
        try (CSVParser parser = CSVParser.parse(csvContent, CSV_FORMAT)) {
            var records = parser.iterator();
            if (!records.hasNext()) {
                throw new StatementParseException("CSV is empty; expected header on line 1.");
            }

            CSVRecord header = records.next();
            validateHeader(header);

            Transaction opening = null;
            List<Transaction> transactions = new ArrayList<>();
            LocalDate previousDate = null;

            while (hasNextRecord(records, parser)) {
                CSVRecord row;
                try {
                    row = records.next();
                } catch (UncheckedIOException exception) {
                    throw malformedCsv(parser, exception);
                } catch (IllegalStateException exception) {
                    throw new StatementParseException(
                            "Malformed CSV near physical line " + parser.getCurrentLineNumber() + ": "
                                    + exception.getMessage(),
                            exception);
                }
                int line = physicalLine(csvContent, row.getCharacterPosition());
                if (row.size() != HEADERS.size()) {
                    throw invalid(line, "expected 5 columns but found " + row.size());
                }

                String dateText = row.get(0).trim();
                String description = row.get(1).trim();
                if (description.isEmpty()) {
                    throw invalid(line, "description is required");
                }
                LocalDate date = parseDate(dateText, line);
                String debitText = row.get(2).trim();
                String creditText = row.get(3).trim();
                String balanceText = row.get(4).trim();

                if (description.equalsIgnoreCase("OPENING BALANCE")) {
                    if (opening != null || !transactions.isEmpty()) {
                        throw invalid(line, "OPENING BALANCE must appear exactly once as the first data row");
                    }
                    if (!debitText.isEmpty() || !creditText.isEmpty()) {
                        throw invalid(line, "OPENING BALANCE must not have a debit or credit");
                    }
                    BigDecimal balance = parseMoney(balanceText, BALANCE_PATTERN, line, "balance");
                    opening = new Transaction(date, description, ZERO, ZERO, balance, line, true);
                    previousDate = date;
                    continue;
                }

                if (opening == null) {
                    throw invalid(line, "the first data row must be OPENING BALANCE");
                }
                if (date.isBefore(previousDate)) {
                    throw invalid(line, "transaction dates must be in nondecreasing order");
                }
                boolean hasDebit = !debitText.isEmpty();
                boolean hasCredit = !creditText.isEmpty();
                if (hasDebit == hasCredit) {
                    throw invalid(line, "exactly one of debit or credit must be provided");
                }
                BigDecimal debit = hasDebit ? parseMoney(debitText, AMOUNT_PATTERN, line, "debit") : ZERO;
                BigDecimal credit = hasCredit ? parseMoney(creditText, AMOUNT_PATTERN, line, "credit") : ZERO;
                BigDecimal balance = parseMoney(balanceText, BALANCE_PATTERN, line, "balance");
                transactions.add(new Transaction(date, description, debit, credit, balance, line, false));
                previousDate = date;
            }

            if (opening == null) {
                throw new StatementParseException("Missing OPENING BALANCE data row on line 2.");
            }
            if (transactions.isEmpty()) {
                throw new StatementParseException("No transaction rows found after OPENING BALANCE.");
            }
            return new BankStatement(opening, transactions);
        } catch (IOException exception) {
            throw new StatementParseException(
                    "Malformed CSV near physical line " + lineAfterLastRecord(csvContent) + ": "
                            + exception.getMessage(),
                    exception);
        }
    }

    private static void validateHeader(CSVRecord header) throws StatementParseException {
        if (header.size() != HEADERS.size()) {
            throw new StatementParseException("Invalid header on line 1: expected " + HEADERS + ".");
        }
        for (int index = 0; index < HEADERS.size(); index++) {
            if (!HEADERS.get(index).equals(header.get(index).trim())) {
                throw new StatementParseException("Invalid header on line 1: expected " + HEADERS + ".");
            }
        }
    }

    private static LocalDate parseDate(String value, int line) throws StatementParseException {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(line, "date must use YYYY-MM-DD format");
        }
    }

    private static BigDecimal parseMoney(String value, Pattern pattern, int line, String field)
            throws StatementParseException {
        if (!pattern.matcher(value).matches()) {
            throw invalid(line, field + " must be a decimal amount with at most two fractional digits");
        }
        return new BigDecimal(value).setScale(2);
    }

    private static StatementParseException invalid(int line, String message) {
        return new StatementParseException("Invalid input on physical line " + line + ": " + message + ".");
    }

    private static int physicalLine(String content, long characterPosition) {
        int line = 1;
        int end = (int) Math.min(characterPosition, content.length());
        for (int index = 0; index < end; index++) {
            char character = content.charAt(index);
            if (character == '\n' || (character == '\r' && (index + 1 == end || content.charAt(index + 1) != '\n'))) {
                line++;
            }
        }
        return line;
    }

    private static int lineAfterLastRecord(String content) {
        return physicalLine(content, content.length());
    }

    private static boolean hasNextRecord(java.util.Iterator<CSVRecord> records, CSVParser parser)
            throws StatementParseException {
        try {
            return records.hasNext();
        } catch (UncheckedIOException exception) {
            throw malformedCsv(parser, exception);
        }
    }

    private static StatementParseException malformedCsv(CSVParser parser, UncheckedIOException exception) {
        return new StatementParseException(
                "Malformed CSV near physical line " + parser.getCurrentLineNumber() + ": "
                        + exception.getCause().getMessage(),
                exception);
    }
}
