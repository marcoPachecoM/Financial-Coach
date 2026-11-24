# Manual QA checklist

What the automated tests do not cover: how things look, phones, and the AI features. Go through it before a release, on a desktop browser and at **375 px** wide (DevTools → device toolbar). Use a fresh user unless a step says otherwise; mark each box, and note anything odd with the screen and the steps.

Backend running with a real `GEMINI_API_KEY` (the copilot and the AI categorization need it).

## Accounts and access
- [ ] Sign up: field errors (short password, password = email, taken email or username) are shown under each field.
- [ ] The recovery codes dialog: Copy and Download `.txt` work; "Go to my dashboard" stays disabled until the box is checked.
- [ ] After signing up and logging out, the **sign-in** screen appears (not the sign-up one).
- [ ] Forgot password with a code (typed in upper case or with spaces) → new password works; the same code fails the second time.
- [ ] Security (shield icon): change password keeps you signed in; a second browser session is signed out on its next action.
- [ ] Security: generate new codes asks for the password; the old codes stop working; the count of codes left is right.

## Import and categorization
- [ ] Import a CSV **with** groups: rows keep their group; the copilot says "Imported N…" (also for the very first import of a new user).
- [ ] Import a bank CSV with an **empty** `Category Group`: rows get sensible groups; the copilot says "N categorized automatically"; the backend log has one `categorization:` line.
- [ ] Import the same file again: everything is skipped as already imported.
- [ ] Change the group of one transaction, then add another of the same merchant with "Automatic": it uses your choice.
- [ ] Export (one account and all accounts, a period): the file opens in a spreadsheet with the Tags column; an empty period shows the notice.

## Dashboard
- [ ] Date presets (All time, This month, Last 30/7 days) change the cards, the trend and the donut.
- [ ] Clicking a category filters the trend; the donut and the trend agree.
- [ ] Budget card: edit the monthly budget; the bar color follows the status.
- [ ] Category budgets: "Set budgets" → add groups (the ones with spending are listed first) → rows sorted by how close they are to the limit; "Show N more" with more than 4; the amber warning when they add up to more than the account budget.
- [ ] Recurring card: stop and delete a rule.

## Transactions
- [ ] Calendar: day shading; clicking a day filters the list; moving between months does not flicker.
- [ ] Search (name, category, notes, tags), category chips, amount range and tag chips combine correctly; pagination of 10.
- [ ] Add / view / edit / delete a transaction; tags as chips in the form (Enter, comma, Backspace, suggestions).
- [ ] Make a transaction recurring; its next occurrences appear with its tags.

## Goals
- [ ] Create a goal with and without a date; the card sentence makes sense ("needs $X/mo", "on track for…", "Add money to see a projection").
- [ ] Add money with a past date and withdraw; the panel chart shows the saved line, the dashed projection, the target and the deadline.
- [ ] Move the date closer until it is **Behind**: the sidebar badge appears (also with the sidebar collapsed).
- [ ] Switching accounts does not change the goals.

## Copilot
- [ ] A streamed answer can be stopped; switching accounts keeps one chat per account.
- [ ] "How much did I spend on #trip-cancun?" answers only with that tag; an unknown tag lists the real ones.
- [ ] "How am I doing with my food budget?" mentions the category budget.
- [ ] Alerts in the panel: budget, category budget, recurring (with their action buttons).

## Phone (375 px)
- [ ] The ☰ opens the drawer; choosing a view closes it; nothing scrolls sideways on any view.
- [ ] Modals fit the screen (forms, upload, goal panel full screen, recovery codes).
- [ ] Header buttons are reachable (Import, Export icon, Security, Log out).
