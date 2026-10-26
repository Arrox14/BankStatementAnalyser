# Bank Statement Analyzer

A Java command-line application that analyzes bank statements in CSV format and generates a JSON report. It calculates monthly average balances, detects cheque bounces, identifies large round-trip transactions, and checks balance mismatches.

## Features

- Calculates monthly average daily balances.
- Detects bounced cheques by matching deposits, returns, and charges.
- Identifies matching incoming and outgoing transactions of ₹1,00,000 or more within 48 hours.
- Checks whether transaction balances match the expected values.
- Validates CSV input and reports invalid data.
- Uses `BigDecimal` for accurate monetary calculations.

## Technologies

- Java 25
- Maven
- Apache Commons CSV
- Jackson
- JUnit 5

## Requirements

- Java 25 or a compatible JDK.
- The included Maven Wrapper (no separate Maven installation required).

Make sure `JAVA_HOME` points to your JDK installation.

## How to Run

Open a terminal in the project directory.

Run the tests:

```powershell
.\mvnw.cmd clean test
```

Build the project:

```powershell
.\mvnw.cmd clean package
```

Analyze a bank statement:

```powershell
.\mvnw.cmd exec:java "-Dexec.args=statement_A.csv report.json"
```

The command reads `statement_A.csv` and writes the analysis to `report.json`. You can replace these filenames with your own input and output paths.

On Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd`.

## Input Format

The CSV file must contain these columns in the specified order:

```csv
date,description,debit,credit,balance
```

The first data row must be `OPENING BALANCE`. Dates must use `YYYY-MM-DD`, and monetary amounts can have up to two decimal places.

Example:

```csv
date,description,debit,credit,balance
2026-04-01,OPENING BALANCE,,,1000.00
2026-04-01,"NEFT-CR-ACME, SONS",,250.00,1250.00
2026-04-03,UPI-DR-SUPPLIES,50.00,,1200.00
```

Quoted descriptions containing commas are supported. Invalid data causes an error instead of being silently skipped.

## Output

The generated JSON report contains four sections:

- `monthly_average_balance` – average daily balance for each month.
- `bounces` – matched cheque deposits, returns, and charges.
- `round_trips` – matching incoming and outgoing transactions.
- `balance_breaks` – transactions where the printed balance differs from the expected balance.

Reported line numbers refer to physical lines in the original CSV, including the header as line 1.

## Assumptions

- **Monthly balance:** The closing balance is carried forward on days without transactions. When multiple transactions occur on the same day, the last row's printed balance is used. In the first partial month, calculations start from the opening balance date because earlier balances are unknown.The monthly average is rounded to two decimal places using the HALF_UP rounding rule.
- **Cheque bounces:** Deposits, returns, and charges are matched using supported cheque-description patterns and instrument identifiers. Ambiguous matches are skipped rather than guessed.
- **Round trips:** Incoming and outgoing amounts must match exactly, be at least ₹1,00,000, and have matching normalized counterparties. The outgoing transaction must follow the incoming transaction within 48 hours. Since the CSV contains dates but no times, dates are treated as midnight.
- **Balance validation:** Each transaction is checked using the previous printed balance plus credit minus debit. After a mismatch, the next check uses the current row's printed balance.

## AI Usage

GitHub Copilot and ChatGPT were used to assist with implementation, code review, and testing. I am responsible for reviewing the code ,debugging and understanding how the implementation works.
