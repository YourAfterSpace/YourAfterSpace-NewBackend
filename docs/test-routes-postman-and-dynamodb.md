# Test routes in Postman and see changes in DynamoDB

Base URL (local): **`http://localhost:3000`**

---

## Step 1: Health check (no auth)

1. In Postman: **GET** `http://localhost:3000/health`
2. Send. You should get **200** and a body like `{"success":true,"message":"Service is healthy",...}`

If this fails, the backend isn’t running or the port is wrong.

---

## Step 2: Login to get your user ID

1. **POST** `http://localhost:3000/api/auth/login`
2. **Headers:** `Content-Type` = `application/json`
3. **Body** → **raw** → **JSON:**
   ```json
   {
     "username": "your-email@example.com",
     "password": "YourPassword123!"
   }
   ```
   Use the email and password of a user you already signed up with in Cognito.
4. Send. You should get **200** with a `data` object.
5. From the response, copy **`data.userId`** (e.g. `21d38dfa-d0c1-7097-f11c-611276151ea4`). This is your Cognito user ID (sub). You’ll use it in the next steps.

If you don’t have a user yet, use **Step 2b** to sign up first.

---

## Step 2b: Sign up (if you don’t have a user)

1. **POST** `http://localhost:3000/api/auth/signup`
2. **Headers:** `Content-Type` = `application/json`
3. **Body** → **raw** → **JSON:**
   ```json
   {
     "email": "your-email@example.com",
     "password": "YourPassword123!"
   }
   ```
   (Password: at least 8 chars; Cognito often requires upper, lower, number, special char.)
4. Send. You should get **200**. Then confirm the user in Cognito (email code or admin confirm), then do **Step 2** to login and get **userId**.

---

## Step 3: Create a user profile (writes to DynamoDB)

1. **POST** `http://localhost:3000/v1/user/profile`
2. **Headers:**
   - `Content-Type` = `application/json`
   - `x-amzn-oidc-identity` = **your userId** (the value you copied from login)
3. **Body** → **raw** → **JSON:**
   ```json
   {
     "city": "Mumbai",
     "country": "India",
     "bio": "Testing from Postman"
   }
   ```
   You can add: `dateOfBirth`, `address`, `state`, `zipCode`, `gender`, `profession`, `company`, `phoneNumber`.
4. Send. You should get **200** and a message like "User profile saved successfully".

This request writes one item to the **user-profiles** DynamoDB table.

---

## Step 4: Get your profile (reads from DynamoDB)

1. **GET** `http://localhost:3000/v1/user/profile`
2. **Headers:** `x-amzn-oidc-identity` = **your userId**
3. No body. Send.
4. You should get **200** with the same profile data you just saved.

---

## Step 5: See the data in DynamoDB

1. Open **AWS Console** → **DynamoDB**.
2. Make sure the **region** (top-right) matches your app (e.g. **ap-south-1**).
3. Left menu → **Tables** → click **user-profiles**.
4. Open the **Explore table items** tab (or **View items**).
5. You should see one (or more) items. Each item has:
   - **userId** (partition key) = your Cognito user ID
   - **createdAt** (sort key) = when the profile was created
   - Other attributes: `city`, `country`, `bio`, `updatedAt`, etc.

After each **POST /v1/user/profile** or **PATCH /v1/user/profile**, refresh the items in DynamoDB to see the latest data.

---

## Quick reference: routes to test

| Method | URL | Auth header | Body |
|--------|-----|-------------|------|
| GET | `http://localhost:3000/health` | — | — |
| POST | `http://localhost:3000/api/auth/signup` | — | `{"email":"...","password":"..."}` |
| POST | `http://localhost:3000/api/auth/login` | — | `{"username":"...","password":"..."}` |
| GET | `http://localhost:3000/api/auth/me` | `x-amzn-oidc-identity`: userId | — |
| POST | `http://localhost:3000/v1/user/profile` | `x-amzn-oidc-identity`: userId | Profile JSON |
| GET | `http://localhost:3000/v1/user/profile` | `x-amzn-oidc-identity`: userId | — |
| PATCH | `http://localhost:3000/v1/user/profile/status` | `x-amzn-oidc-identity`: userId | `{"status":"ACTIVE"}` |

---

## Optional: Postman collection

You can save these as a Postman collection and set a **collection variable** `userId` so you only paste your userId once and use `{{userId}}` in the header `x-amzn-oidc-identity` for all user routes.
