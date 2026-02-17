# How to create a pull request for these changes

Your remote is: **https://github.com/YourAfterSpace/YourAfterSpace-NewBackend.git** (branch `main`).

## Option A: Create a branch, commit, push, then open PR on GitHub

Run these in a terminal from the project root (`YourAfterSpace-NewBackend`):

### 1. Create and switch to a new branch
```powershell
git checkout -b feature/experiences-interested-city
```

### 2. Stage all changes (modified + new files)
```powershell
git add -A
```

### 3. Commit
```powershell
git commit -m "feat: experiences API, mark as interested, filter by city, docs"
```

### 4. Push the branch to GitHub
```powershell
git push -u origin feature/experiences-interested-city
```

### 5. Open the pull request
- Go to: **https://github.com/YourAfterSpace/YourAfterSpace-NewBackend**
- You should see a yellow banner: **"feature/experiences-interested-city had recent pushes"** with a button **Compare & pull request**. Click it.
- Or: **Pull requests** → **New pull request** → set *base* to `main`, *compare* to `feature/experiences-interested-city` → **Create pull request**.
- Use the title and description below if you like.

---

## Option B: If you need to PR to a different “original” repo (e.g. upstream)

If your **original** repo is another GitHub repo (e.g. a fork’s upstream):

1. Add it as a remote (once):
   ```powershell
   git remote add upstream https://github.com/ORIGINAL_OWNER/REPO_NAME.git
   ```
2. Push your branch to **your** fork (origin), then on GitHub create a PR **from your fork’s branch to upstream’s main**.

---

## Suggested PR title
**feat: Experiences API, mark as interested, filter by city, questionnaire & docs**

## Suggested PR description (copy-paste)

```markdown
### Summary
- Experience APIs: create, get, update, list all, get by ID.
- **Mark as interested:** PUT `/v1/experiences/{experienceId}/interest` (body: `{"interested": true/false}`), stored on user profile (`interestedExperienceIds`).
- **Get interested experiences:** GET `/v1/user/interested-experiences`.
- **Filter by city:** GET `/v1/experiences/all?city=Mumbai` (optional query param).
- Single DynamoDB table for user profiles and experiences (`user-profiles`); IAM and config documented.
- Questionnaire by category, profile merge, progress API; docs for DynamoDB setup, IAM, and adding experiences.

### API overview
| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/experiences/all` | All experiences |
| GET | `/v1/experiences/all?city=CityName` | Experiences in that city |
| GET | `/v1/experience/{id}` | One experience |
| POST | `/v1/experience` | Create experience |
| PATCH | `/v1/experience/{id}` | Update experience |
| PUT | `/v1/experiences/{id}/interest` | Mark/unmark interested |
| GET | `/v1/user/interested-experiences` | My interested experiences |

### Config
- `aws.dynamodb.user-profile-table=user-profiles` in `application.properties`.
- Requires DynamoDB table with partition key `userId`, sort key `createdAt`; IAM includes `dynamodb:Scan`.

### Docs
- `docs/ADD-EXPERIENCE.md` – add experience, images, mark as interested.
- `docs/DYNAMODB-SETUP.md` – table creation and IAM.
- `docs/iam-policy-example.json` – example IAM policy with Scan.
```
