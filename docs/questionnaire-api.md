# Questionnaire API (categories, questions, progress)

Structure: **categories** only, each with a list of **questions**. No subcategories. Each question has **title**, **description**, **type**, **options** (when applicable), and **weight**. Each category has **weight**. **Per-category** completion % = (sum of weights of answered questions) / (sum of weights of all questions in that category) × 100. **Overall profile** completion % = sum(categoryWeight × categoryPercentage) / sum(categoryWeights) for categories that have questions. Frontend can show a search bar for multi-choice when the number of options is large.

---

## Question types (frontend display)

| type | Display | Answer format when submitting |
|------|--------|-------------------------------|
| **TEXT** | Text input (single or multiline) | String |
| **SINGLE_CHOICE** | Radio / single select | String (one option) |
| **MULTIPLE_CHOICE** | Checkboxes / multi select. Frontend may show search when options count is large. | List of strings |
| **RATING** | Rating (e.g. 1–5) | String or number (frontend convention) |

Each question has: **id**, **title**, **description**, **type**, **options** (if applicable), **categoryId**, **categoryName**, **weight** (default 1.0; used for category completion %). Each category has **weight** (default 1.0; used for overall profile %), and optional **imageUrl** (URL for a category image, e.g. for cards or headers).

---

## Endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| GET | `/v1/questions` | No | Get all categories with their questions |
| GET | `/v1/user/questionnaire/category/{categoryId}` | Yes | Get one category (questions) + user's answers for that category only |
| POST | `/v1/user/questionnaire` | Yes (`x-amzn-oidc-identity`) | Submit answers (map of question id → value). **Merges** with existing; safe to POST one category at a time. |
| GET | `/v1/user/questionnaire` | Yes | Get saved answers (all categories) |
| GET | `/v1/user/questionnaire/progress` | Yes | Per-category completion % and overall profile % |

---

## GET /v1/questions – Response shape

```json
{
  "success": true,
  "data": [
    {
      "id": "background",
      "name": "Background",
      "description": "Your background and upbringing",
      "weight": 1.0,
      "imageUrl": "https://images.unsplash.com/photo-1511895426328-dc8714191300?w=800&q=80",
      "questions": [
        {
          "id": "fluent_languages",
          "title": "Fluent languages",
          "description": "What languages do you speak fluently?",
          "type": "MULTIPLE_CHOICE",
          "options": ["Hindi", "Telugu", "English", "Kannada", "Tamil", "Malayalam"],
          "categoryId": "background",
          "categoryName": "Background",
          "weight": 1.0
        },
        {
          "id": "sibling_order",
          "title": "Sibling order",
          "description": "I am a ___ child",
          "type": "SINGLE_CHOICE",
          "options": ["Younger", "Middle", "Older", "Only"],
          "categoryId": "background",
          "categoryName": "Background",
          "weight": 1.0
        },
        {
          "id": "home_town",
          "title": "Home town",
          "description": "What is your home town?",
          "type": "TEXT",
          "options": null,
          "categoryId": "background",
          "categoryName": "Background",
          "weight": 1.0
        },
        {
          "id": "financial_status",
          "title": "Financial status",
          "description": "What financial status did you grow up in?",
          "type": "SINGLE_CHOICE",
          "options": ["Working class", "Middle class", "Upper class", "Ultra wealthy"],
          "categoryId": "background",
          "categoryName": "Background",
          "weight": 1.0
        },
        {
          "id": "interesting_fact",
          "title": "Interesting fact",
          "description": "What is the fun or interesting fact about you that you want to share with your group?",
          "type": "TEXT",
          "options": null,
          "categoryId": "background",
          "categoryName": "Background",
          "weight": 1.0
        }
      ]
    },
    {
      "id": "interests",
      "name": "Interests",
      "description": "Your interests and preferences",
      "weight": 1.0,
      "imageUrl": null,
      "questions": [
        {
          "id": "hobbies",
          "title": "Hobbies",
          "description": "What are your hobbies or activities you enjoy?",
          "type": "MULTIPLE_CHOICE",
          "options": ["Reading", "Travel", "Music", "Sports", "Cooking", "Photography", "Gardening", "Gaming", "Movies", "Art", "Dancing", "Trekking"],
          "categoryId": "interests",
          "categoryName": "Interests",
          "weight": 1.0
        }
      ]
    }
  ]
}
```

---

## GET /v1/user/questionnaire/category/{categoryId} – Open a category

Use when the user opens a category screen: returns that category (with questions) and only the user's answers for that category. Pre-fill the form with `data.answers`.

**Example:** `GET /v1/user/questionnaire/category/background`

**Response:**
```json
{
  "success": true,
  "data": {
    "category": {
      "id": "background",
      "name": "Background",
      "description": "Your background and upbringing",
      "weight": 1.0,
      "imageUrl": "https://...",
      "questions": [ ... ]
    },
    "answers": {
      "fluent_languages": ["Hindi", "English"],
      "sibling_order": "Middle",
      "home_town": "Mumbai"
    }
  }
}
```

If the user has not answered any question in that category, `answers` is `{}`. 404 if `categoryId` is invalid.

---

## POST /v1/user/questionnaire – Request body (merge behaviour)

Keys = question **id**s. Values: **string** for TEXT / SINGLE_CHOICE, **array of strings** for MULTIPLE_CHOICE.  
**Merge behaviour:** Incoming answers are **merged** with existing ones. You can POST only one category's answers (e.g. when the user finishes that category); other categories' answers are kept. Overall profile % is recomputed from the full merged map after each save.

**Example (Background only – safe to send per category):**

```json
{
  "answers": {
    "fluent_languages": ["Hindi", "English"],
    "sibling_order": "Middle",
    "home_town": "Mumbai",
    "financial_status": "Middle class",
    "interesting_fact": "I love trekking."
  }
}
```

---

## GET /v1/user/questionnaire/progress – Response shape

Per-category completion and overall profile completion:

```json
{
  "success": true,
  "data": {
    "categories": [
      {
        "categoryId": "background",
        "categoryName": "Background",
        "answeredCount": 5,
        "totalCount": 5,
        "percentage": 100.0
      }
    ],
    "totalAnswered": 5,
    "totalQuestions": 5,
    "totalPercentage": 100.0
  }
}
```

- **categories**: For each category, `answeredCount`, `totalCount`, `percentage`. The `percentage` is **weighted** (sum of weights of answered questions / sum of weights of all questions in that category × 100).
- **totalAnswered**, **totalQuestions**: Total question counts (for display).
- **totalPercentage**: **Weighted** overall profile completion: sum(categoryWeight × categoryPercentage) / sum(categoryWeights).

---

## How to test in Postman

Base URL: **http://localhost:3001** (or your port).

1. **GET** `http://localhost:3001/v1/questions` – No auth. Response: categories with `questions` array (no subcategories).
2. **POST** `http://localhost:3001/api/auth/login` – Body: `{"username":"...","password":"..."}`. Copy **data.userId**.
3. **POST** `http://localhost:3001/v1/user/questionnaire` – Headers: `Content-Type: application/json`, `x-amzn-oidc-identity: <userId>`. Body: `{"answers": {"fluent_languages": ["Hindi","English"], "sibling_order": "Middle", "home_town": "Mumbai", "financial_status": "Middle class", "interesting_fact": "I love trekking."}}`.
4. **GET** `http://localhost:3001/v1/user/questionnaire` – Header: `x-amzn-oidc-identity: <userId>`.
5. **GET** `http://localhost:3001/v1/user/questionnaire/progress` – Header: `x-amzn-oidc-identity: <userId>`. Check per-category and total percentage.
6. **GET** `http://localhost:3001/v1/user/questionnaire/category/background` – Header: `x-amzn-oidc-identity: <userId>`. Response: `category` (with questions) + `answers` for that category only.

---

## Frontend flow (one question or one category at a time)

1. **Open a category**  
   `GET /v1/user/questionnaire/category/{categoryId}` (e.g. `background`, `interests`).  
   Use `data.category.questions` to render the form and `data.answers` to pre-fill (key = question id).

2. **Save that category’s answers**  
   When the user finishes (or after each question), `POST /v1/user/questionnaire` with **only that category’s** question ids and values, e.g. `{"answers": {"fluent_languages": ["Hindi"], "sibling_order": "Middle", ...}}`.  
   The backend **merges** with existing answers, so other categories are not overwritten.

3. **Overall profile %**  
   After each save, the backend recomputes category and overall completion from the full merged map. Use `GET /v1/user/questionnaire/progress` to show progress; the response includes `totalPercentage` and per-category `percentage`.

---

## Profile response: questionnaire by category (stored in profile)

**GET** `/v1/user/profile` (and the profile payload in **POST** `/v1/user/questionnaire`) include **`questionnaireByCategory`**: a list of categories, each with **all questions** and the user's **answer on each question** (or **null** if not answered). This structure is **stored in the user profile** so each user has their own copy of categories, questions, and answers.

**Shape of each item in `questionnaireByCategory`:**
- **id**, **name**, **description**, **weight**, **imageUrl**: category fields.
- **questions**: array of question objects, each with:
  - **id**, **title**, **description**, **type**, **options**, **categoryId**, **categoryName**, **weight**
  - **answer**: the user's answer (string, list of strings, or **null** if not answered).

Example: `data.questionnaireByCategory[0].questions[0]` → `{ "id": "fluent_languages", "title": "Fluent languages", "description": "...", "type": "MULTIPLE_CHOICE", "options": ["Hindi", "Telugu", ...], "answer": ["Hindi", "English"] }`. Unanswered questions have `"answer": null`. Pass each category object to your pages and use `question.answer` to pre-fill or display.

---

## Weightage

- **Question weight**: In `QuestionServiceImpl`, each question has a `weight` (default 1.0). Change a question’s weight to give it more/less impact on that category’s completion %.
- **Category weight**: Each category has a `weight` (default 1.0). Categories with higher weight contribute more to overall profile completion %.

## Background category (current)

| id | title | type | options | weight |
|----|--------|------|--------|--------|
| fluent_languages | Fluent languages | MULTIPLE_CHOICE | Hindi, Telugu, English, Kannada, Tamil, Malayalam | 1.0 |
| sibling_order | Sibling order | SINGLE_CHOICE | Younger, Middle, Older, Only | 1.0 |
| home_town | Home town | TEXT | — | 1.0 |
| financial_status | Financial status | SINGLE_CHOICE | Working class, Middle class, Upper class, Ultra wealthy | 1.0 |
| interesting_fact | Interesting fact | TEXT | — | 1.0 |

## Interests category (current)

| id | title | type | options | weight |
|----|--------|------|--------|--------|
| hobbies | Hobbies | MULTIPLE_CHOICE | Reading, Travel, Music, Sports, Cooking, Photography, Gardening, Gaming, Movies, Art, Dancing, Trekking | 1.0 |

Frontend: for **MULTIPLE_CHOICE**, if `options.length` is large, you can show a search bar and multi-select; backend only sends the options list.
