# How to add an experience

## API

**Endpoint:** `POST /v1/experience`  
**Auth:** Required. Send the Cognito user ID in the header (for local Postman you can use any non-empty value).

| Header               | Value |
|----------------------|--------|
| `Content-Type`       | `application/json` |
| `x-amzn-oidc-identity` | Your Cognito user ID (e.g. `a1b2c3d4-e5f6-7890-abcd-ef1234567890`). For local testing in Postman, any string like `test-user-123` works. |

---

## Images

**Yes, images are saved.** The backend stores an **array of image URLs** in the `images` field (up to 10 URLs). Each value must be a full **http://** or **https://** URL. They are persisted in DynamoDB and returned when you GET the experience or list all experiences.

- **No file upload in the API** – you send URLs only. To use your own photo, upload it elsewhere first (e.g. S3, Imgur, your CDN), then put the resulting URL in `images`. Example: `"images": ["https://your-bucket.s3.amazonaws.com/experience-photo.jpg"]`.
- You can use public image URLs (e.g. from Unsplash) for testing: `"images": ["https://images.unsplash.com/photo-xxx"]`.

---

## Required body fields

| Field | Type | Rules |
|-------|------|--------|
| `title` | string | Required, max 200 chars |
| `type` | string | Required. One of: `DINING`, `ACTIVITY`, `TOUR`, `WORKSHOP`, `EVENT`, `ENTERTAINMENT`, `ADVENTURE`, `CULTURAL`, `WELLNESS`, `BUSINESS`, `OTHER` |
| `experienceDate` | string | Required, date in the **future**, format `yyyy-MM-dd` |

---

## All optional body fields

| Field | Type | Rules |
|-------|------|--------|
| `description` | string | Max 2000 chars |
| `status` | string | `DRAFT`, `PUBLISHED`, `FULL`, `CANCELLED`, `COMPLETED`, `DELETED`. Default: `DRAFT` |
| `location` | string | Max 200 |
| `address` | string | Max 500 |
| `city` | string | Max 100 |
| `country` | string | Max 100 |
| `latitude` | number | -90 to 90 |
| `longitude` | number | -180 to 180 |
| `startTime` | string | `HH:mm` (e.g. `"09:00"`) |
| `endTime` | string | `HH:mm` (e.g. `"17:30"`) |
| `pricePerPerson` | number | ≥ 0, up to 10 digits and 2 decimals |
| `currency` | string | 3 chars (e.g. `"USD"`, `"INR"`) |
| `maxCapacity` | integer | 1–10000 |
| `tags` | array of strings | Max 20 tags, each max 50 chars |
| `images` | array of strings | Max 10 URLs, each must start with `http://` or `https://` |
| `contactInfo` | string | Max 500 |
| `requirements` | string | Max 1000 |
| `cancellationPolicy` | string | Max 1000 |

---

## Postman

1. **Method:** POST  
2. **URL:** `http://localhost:3000/v1/experience` (or your server URL)  
3. **Headers:**
   - `Content-Type`: `application/json`
   - `x-amzn-oidc-identity`: `test-user-123` (or your real Cognito user ID)  
4. **Body (raw, JSON):** use one of the examples below.

---

## Example 1: Minimal (required fields only)

```json
{
  "title": "Sunset dinner by the lake",
  "type": "DINING",
  "experienceDate": "2026-03-15"
}
```

---

## Example 2: All fields (copy-paste ready)

Use this to test with every field set, including images. Change `experienceDate` to a future date if needed.

```json
{
  "title": "Sunset dinner by the lake",
  "description": "A relaxed 3-course dinner with lake view. Vegetarian and vegan options available.",
  "type": "DINING",
  "status": "PUBLISHED",
  "location": "Lakeside Pavilion",
  "address": "123 Lake Road, Bandra West",
  "city": "Mumbai",
  "country": "India",
  "latitude": 19.076,
  "longitude": 72.8777,
  "experienceDate": "2026-03-15",
  "startTime": "18:30",
  "endTime": "21:00",
  "pricePerPerson": 25.50,
  "currency": "USD",
  "maxCapacity": 10,
  "tags": ["dinner", "sunset", "outdoor", "vegetarian", "lake"],
  "images": [
    "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4",
    "https://images.unsplash.com/photo-1414235077428-338989a2e8c0"
  ],
  "contactInfo": "host@example.com or +91 98765 43210",
  "requirements": "No special requirements. Dress code: smart casual.",
  "cancellationPolicy": "Full refund if cancelled at least 24 hours in advance. No refund within 24 hours."
}
```

---

## Response

- **201 Created** – Body is the created experience (includes `experienceId`, `title`, `type`, `createdBy`, `images`, etc.).
- **400** – Validation error (e.g. missing `title`/`type`, or `experienceDate` not in the future, or invalid image URL). Check the response message for details.
- **401 / 403** – Missing or invalid auth (ensure `x-amzn-oidc-identity` is set for local testing).

The created experience is stored in your **user-profiles** DynamoDB table with `recordType = "EXPERIENCE"`. The `images` array is saved and returned on **GET** `/v1/experience/{id}` and **GET** `/v1/experiences/all`.

---

## Mark an experience as interested (wishlist)

Users can mark an experience as interested (e.g. for a wishlist). This is stored on the **user profile** (`interestedExperienceIds`).

- **PUT** `/v1/experiences/{experienceId}/interest`  
  - **Headers:** `Content-Type: application/json`, `x-amzn-oidc-identity: <userId>`  
  - **Body:** `{"interested": true}` to add, `{"interested": false}` to remove.  
  - **Response:** `{"success": true, "message": "Marked as interested", "data": {"experienceId": "...", "interested": true}}`

- **GET** `/v1/user/interested-experiences`  
  - **Headers:** `x-amzn-oidc-identity: <userId>`  
  - **Response:** List of full experience details for all experiences the user marked as interested.
