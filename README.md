# Bank Statement Analyzer

A Java 25 command-line application that validates a bank statement CSV and writes a JSON first-pass analysis. It uses no database or frontend, and it leaves the input statement unchanged.

## Prerequisites and commands

Install a Java 25 or newer JDK and set `JAVA_HOME` to the JDK installation directory. Maven is not required when using the included Maven Wrapper; the wrapper downloads the configured Maven distribution the first time it runs.

From this workspace root on Windows:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean package
.\mvnw.cmd clean verify
.\mvnw.cmd exec:java -Dexec.args="statement_A.csv report.json"
```

The CLI accepts exactly two paths: input CSV and output JSON. It rejects the same normalized input and output path. On Unix-like systems use `./mvnw` with the same arguments.

## Architecture

- `StatementParser` parses and validates CSV records and records their physical file line numbers.
- `Transaction` and `BankStatement` represent parsed input.
- `MonthlyAverageBalanceAnalyzer`, `BounceDetector`, `RoundTripMatcher`, and `BalanceIntegrityChecker` implement the four analyses independently.
- `AnalysisReport` and `JsonReportSerializer` produce the JSON output.
- `BankStatementAnalyzerCli` wires the components together.

## Input schema and validation

The CSV must have exactly these headers, in this order: `date,description,debit,credit,balance`. Each data row has exactly five fields. Dates use `YYYY-MM-DD`; monetary values are nonnegative decimal debit/credit amounts with at most two fractional digits, while a printed balance may be negative for an overdraft. Each non-opening row must provide exactly one of debit or credit. The first data row must be the only `OPENING BALANCE` row; it must have a balance and empty debit/credit fields. Transaction dates must be nondecreasing. At least one transaction is required.

Example (synthetic):

```csv
date,description,debit,credit,balance
2026-04-01,OPENING BALANCE,,,1000.00
2026-04-01,"NEFT-CR-ACME, SONS",,250.00,1250.00
2026-04-03,UPI-DR-SUPPLIES,50.00,,1200.00
```

CSV parsing supports quoted commas and quoted quotes. Invalid headers, dates, amounts, missing fields, malformed row widths, and invalid statement structure produce an error; malformed data is never skipped. Errors identify the physical CSV line when a row is responsible. The header is physical line 1 and the opening row is physical line 2. For a quoted field spanning several physical lines, a row's number is its starting line.

## Output schema

The JSON has exactly four top-level properties:

- `monthly_average_balance`: object mapping `YYYY-MM` to a two-decimal decimal string.
- `bounces`: array of `{ "deposit": line, "return": line, "charge": line }`.
- `round_trips`: array of `{ "credit": line, "debit": line, "counterparty": name, "amount": two-decimal string, "gap_days": integer }`.
- `balance_breaks`: array of `{ "line": line, "expected": two-decimal string, "printed": two-decimal string }`.

All money is parsed, compared, summed, and averaged with `BigDecimal`; JSON monetary values are strings formatted to two decimal places.

Example output:

```json
{
  "monthly_average_balance": {"2026-04": "1203.33"},
  "bounces": [],
  "round_trips": [],
  "balance_breaks": []
}
```

## Assumptions and matching rules

- **Average daily balance:** the mean uses every calendar day in each represented month, including days after the last transaction, for which the latest known closing balance is carried forward to month end. The opening balance is known from its stated date onward; days before that date in a partial first month are excluded rather than backfilled with an unknown historical balance. Later transaction-free days carry the previous closing balance forward. On a date with multiple transactions, the last row's printed balance is that day's closing balance. The daily values are summed exactly and the monthly mean is rounded to two decimal places using `HALF_UP`.
- **Bounced cheques:** descriptions must follow `CHQ DEP <identifier> - ...`, `CHQ RTN <identifier> ...`, and `RTN CHGS <identifier> ...`. A match requires the same identifier, positive deposit/return/charge amounts, deposit/return/charge rows in that order, an exact deposit-credit/return-debit amount, and a charge after the return. If an identifier has multiple deposits, returns, or charges, it is omitted rather than guessed. Each matched deposit, return, and charge is used at most once. The charge amount is not used to infer the instrument.
- **Round trips:** amounts must be exactly equal and at least INR 100000.00. Supported incoming descriptions are `NEFT-CR-...`, `UPI-CR-...`, and `CHQ DEP <identifier> - ...`; supported outgoing descriptions are `NEFT-DR-...`, `UPI-DR-...`, and `POS PURCHASE ...`. The outgoing row must follow the incoming row in file order, and its date must be no more than 48 hours later. Dates have no times, so each is treated as midnight; `gap_days` is the calendar-day difference, and differences of 0, 1, or 2 qualify. Counterparties match only after trimming/collapsing whitespace, removing an initial `M/S` and quote marks, and comparing case-insensitively. No fuzzy company-name matching is attempted. When candidates compete, the matcher chooses the smallest day gap first, then credit line, then debit line. A transaction is never reused in multiple pairs.
- **Balance integrity:** each transaction is checked against the prior row's printed balance plus credit minus debit. Even after a mismatch, the next row uses the current row's printed balance, as a bank statement would.

## Automated tests

The JUnit suite covers daily carry-forward, same-day transactions, month boundaries, partial first-month handling, leap February, September 2026 month-end carry-forward, half-up rounding, cheque bounce matching and ambiguity, round-trip thresholds/timing/order, balance breaks, exact decimal arithmetic, quoted CSV fields, malformed input, and physical line numbers.

## Physical line numbers and limitations

Reported line numbers include the header and count physical CSV lines, including line breaks embedded in quoted fields. Bounce matching depends on the described cheque-number patterns and intentionally avoids uncertain matches. Round-trip counterparty detection only understands the listed transaction description prefixes; other bank-specific formats are ignored. No inference is made about times, counterparty identity beyond normalized exact names, or omitted dates beyond statement coverage.

## AI usage

GitHub Copilot in VS Code assisted with implementation, repository review, and validation. The code and tests should be independently run and understood before relying on their results.

## Possible improvements

Support bank-specific description profiles, expose matching explanations and ambiguous candidates in an optional diagnostic report, and add broader property-based tests for malformed CSV and unusual date ranges.
