# Validation Rules (Auth + Onboarding)

| Area | Field | Rule | Error Message |
|---|---|---|---|
| Auth | Email | Required, valid format | Email is required / Enter a valid email |
| Auth | Password | >= 8 chars | Password must be at least 8 characters |
| Auth | Password | 1 letter + 1 number | Must include at least one letter and one number |
| Auth | Password | 1 special character | Must include at least one special character |
| Auth | Confirm | Matches password | Passwords do not match |
| Profile | Name | Required | Please complete all required fields |
| Profile | Age | 13–60 | Please complete all required fields |
| Profile | Weight | 35–180 | Please complete all required fields |
| Profile | Height | 120–200 | Please complete all required fields |
| Profile | Insulin | Required | Please select your insulin resistance level |
| Profile | Budget | 0–20000 | Please enter a valid weekly budget |
