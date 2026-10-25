# Take-home: Bank Statement Analyzer

**Time window:** 3 days from when you receive this. **Expected effort:** about 4-5 hours. We would rather see a small, correct, well-explained solution than a large one.

## The problem

When an MSME applies for a loan, the lender looks at 6-12 months of the borrower's bank statements. Analysts check a few things by hand, and it is slow and error-prone. Your job is to build a small program that does the first pass automatically.

You are given `statement_A.csv`, a synthetic current-account statement for a small business. (All names and numbers are made up.)

## Input format

CSV with a header row:

```
date,description,debit,credit,balance
```

- `date` is `YYYY-MM-DD`.
- `debit` and `credit` are amounts in rupees; exactly one of them is filled per transaction row, the other is empty.
- `balance` is the running balance after that row, as printed by the bank.
- The first data row is an `OPENING BALANCE` row with only a balance.
- Some descriptions contain commas and are quoted. Your CSV handling must cope with that.
- Treat the file as you would a real one: do not assume it is clean.

## What your program must produce

Given a statement file, produce a JSON report with these four sections.

1. **Average monthly balance** for each calendar month in the statement. Definition: the mean of the daily closing balance over every day of the month, carrying the previous day's closing balance forward on days with no transactions. For a day with several transactions, the closing balance is the balance on the last row of that day, as printed in the file.

2. **Bounced cheques.** A cheque deposited and then returned shows up as a deposit row, a later "CHQ RTN ..." row, and a separate "RTN CHGS ..." fee row. Report each bounced instrument once, with the line numbers involved. Count instruments, not rows.

3. **Round-trip transactions.** Money that comes in from a counterparty and goes back out to the same counterparty, same amount, within 48 hours, is a classic way to inflate turnover. Flag these pairs. Use a minimum amount of 1,00,000 so that small refunds are not flagged. State your threshold and matching rule in your README.

4. **Data integrity.** The printed running balance should equal the previous balance plus credit minus debit. Report every row where it does not.

Line numbers in your report are **file line numbers**: the header is line 1, the OPENING BALANCE row is line 2.

### Output shape

You may adapt this, but keep it machine-readable and say so in your README if you change it.

```json
{
  "monthly_average_balance": {"2026-04": "1234567.89"},
  "bounces": [{"deposit": 10, "return": 15, "charge": 16}],
  "round_trips": [{"credit": 20, "debit": 22, "counterparty": "X", "amount": "100000.00", "gap_days": 1}],
  "balance_breaks": [{"line": 99, "expected": "0.00", "printed": "0.00"}]
}
```

## Rules

- **Any language.** Use what you are strongest in.
- **Do not use floats for money.** Use a decimal type or integer paise.
- **Tests.** A few meaningful tests. Not coverage for its own sake.
- **README.** How to run it, the assumptions you made, and what you would do with more time. Where the spec is ambiguous, decide, and write down what you decided.
- **AI tools are allowed.** If you use them, say which and for what in your README. You will be asked to explain and modify every line, so only submit code you understand.
- **Bonus, optional:** expose the analyzer as an HTTP endpoint that accepts a CSV upload and returns the JSON report.

## What to submit

A link to a repository (public, or shared with us) by the deadline. Please do not send a zip.

## The follow-up call

After you submit we will have a call of about 30 minutes. You will walk us through your code and design, and we will give you a second, similar statement file that you have not seen, plus a small change request, and ask you to run or adjust your solution live. This is not a trap. If you wrote and understood your solution, it will go well.

## How we will look at it

Correctness on the four checks, honest handling of messy input, how clearly you explain your decisions, and how comfortably you change your own code under a small new requirement.
