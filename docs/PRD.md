# LocalLedger V1 PRD

## Product goal

Build an Android-first local accounting app for shared use on a single device. The app focuses on clear monthly reporting, multi-book support, member attribution, and account balance tracking without requiring network access.

## V1 scope

- Android app only
- Local-only storage
- Multiple books
- Multiple members inside a book
- Income and expense transactions
- Category management
- Account management with running balances
- Transaction list and filters
- Reports for monthly trends, category mix, member totals, and account totals
- Local backup export and import

## Out of scope

- Cloud sync
- Budgeting
- Transfers between accounts
- Receipt attachments
- Advanced transaction types

## Key business rules

- Every transaction belongs to one book
- Every transaction is attributed to one member
- Every transaction uses one account
- Transaction type is either `income` or `expense`
- Account balances are derived from initial balance plus transaction totals
- All members on the device can view all transactions in the current book
- Reports default to the monthly view

## Core screens

1. Dashboard
   - Book switcher
   - Current month totals
   - Quick report summary
2. Add transaction
   - Type, amount, category, account, member, time, note
3. Transaction list
   - Chronological list
   - Filters by member, category, account, and type
4. Reports
   - Monthly trend
   - Category breakdown
   - Member totals
   - Account totals
5. Member management
6. Category management
7. Account management
8. Settings
   - Export backup
   - Import backup

## Data model

- `books`
- `members`
- `accounts`
- `categories`
- `transactions`

## Technical direction

- Kotlin
- Jetpack Compose
- Room
- Navigation Compose
- ViewModel
- Material 3

## Build phases

1. Scaffold app shell and local database
2. Build CRUD for books, members, accounts, categories, and transactions
3. Build reporting queries and dashboards
4. Add backup import and export
