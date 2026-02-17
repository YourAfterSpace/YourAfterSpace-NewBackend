# Setup DynamoDB table and use it with your Cognito User Pool

**Important:** There is **no “link table to user pool”** option in AWS. Cognito and DynamoDB are separate. Your **backend code** connects them by using the **Cognito user ID** (`sub`) as the **`userId`** in the DynamoDB table. You only need to create the table and give your app access to both.

---

## Step 1: Create the DynamoDB table (user-profiles)

1. In **AWS Console**, go to **DynamoDB** → **Tables** → **Create table**.
2. Fill in:
   - **Table name:** `user-profiles`
   - **Partition key:** `userId` (String)
   - **Sort key:** `createdAt` (String)
3. **Table settings:** leave default (e.g. **On-demand** for billing).
4. Click **Create table**.
5. Wait until the table status is **Active**.

Use the **same region** as your Cognito User Pool (e.g. **ap-south-1**). You can switch region in the top-right of the AWS Console.

---

## Step 2: Confirm your Cognito User Pool (no DynamoDB link)

1. Go to **Cognito** → **User pools** → open your user pool.
2. Note:
   - **User pool ID** (e.g. `ap-south-1_xxxxx`) → this is your `aws.cognito.user-pool-id`.
   - **App integration** → **App client** → **Client ID** → this is your `aws.cognito.client-id`.
3. There is **no** “attach DynamoDB table” or “link to DynamoDB” in Cognito. The app links them by using the Cognito user ID when calling DynamoDB.

---

## Step 3: How they work together (in your app)

- User signs up / logs in with **Cognito** → Cognito returns an **id token** (and your backend returns **userId** = `sub` from that token).
- For **GET /v1/user/profile** or **POST /v1/user/profile**, the backend:
  - Gets the current user from the **`x-amzn-oidc-identity`** header (that value is the Cognito `sub`).
  - Uses it as **`userId`** in DynamoDB: e.g. **Query** or **PutItem** on the `user-profiles` table with partition key `userId` = that value.

So the “integration” is: **Cognito user ID = DynamoDB `userId`**. No extra step in the AWS console.

---

## Step 4: IAM permissions (so the app can use both)

The IAM user or role your app uses (e.g. credentials in `~/.aws/credentials` or env vars) needs:

**Cognito (already working for you):**
- e.g. `cognito-idp:SignUp`, `cognito-idp:InitiateAuth`, `cognito-idp:ConfirmSignUp`, etc.

**DynamoDB (for `user-profiles` table):**
- `dynamodb:PutItem`
- `dynamodb:GetItem`
- `dynamodb:Query`
- `dynamodb:UpdateItem`
- `dynamodb:BatchGetItem` (if used)

**Resource:** the ARN of your `user-profiles` table in the same region as your app (e.g. `ap-south-1`).

Example policy (replace `ACCOUNT_ID` and `ap-south-1` if different):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:GetItem",
        "dynamodb:Query",
        "dynamodb:UpdateItem",
        "dynamodb:BatchGetItem"
      ],
      "Resource": "arn:aws:dynamodb:ap-south-1:ACCOUNT_ID:table/user-profiles"
    }
  ]
}
```

Attach this (or equivalent) to the IAM user/role your backend uses.

---

## Step 5: App configuration

In `application.properties` you should have (values aligned with your pool and region):

```properties
# Cognito (your existing user pool)
aws.cognito.user-pool-id=ap-south-1_xxxxx
aws.cognito.client-id=your-client-id
aws.region=ap-south-1

# DynamoDB table for user profiles (default name is user-profiles)
# aws.dynamodb.user-profile-table=user-profiles
```

If your table is exactly named `user-profiles`, you don’t need to set `aws.dynamodb.user-profile-table` (the app defaults to `user-profiles`). If you used another name, set it:

```properties
aws.dynamodb.user-profile-table=your-table-name
```

---

## Step 6: Test the flow

1. **Login** (Cognito):  
   `POST /api/auth/login` with `{"username":"your@email.com","password":"YourPass123!"}`  
   → Copy the **userId** (Cognito `sub`) from the response.

2. **Create profile** (DynamoDB):  
   `POST /v1/user/profile`  
   - Header: `x-amzn-oidc-identity` = that **userId**  
   - Body: e.g. `{"city":"Mumbai","country":"India"}`  
   → Backend uses that userId as the DynamoDB partition key and writes to `user-profiles`.

3. **Get profile** (DynamoDB):  
   `GET /v1/user/profile`  
   - Header: `x-amzn-oidc-identity` = same **userId**  
   → Backend queries `user-profiles` by `userId` and returns the profile.

If steps 2 and 3 work, Cognito and DynamoDB are correctly “integrated” via your app.

---

## Summary

| Step | Where | What to do |
|------|--------|------------|
| 1 | DynamoDB | Create table `user-profiles` with PK `userId`, SK `createdAt` in the same region as Cognito. |
| 2 | Cognito | No DynamoDB link; just use your existing User Pool and App Client. |
| 3 | App logic | Use Cognito user ID (`sub`) as `userId` in DynamoDB (already done in your code). |
| 4 | IAM | Grant the app’s credentials DynamoDB access to `user-profiles`. |
| 5 | Config | Set `aws.region` and optionally `aws.dynamodb.user-profile-table` if table name is not `user-profiles`. |
| 6 | Test | Login → use returned userId in `x-amzn-oidc-identity` → POST/GET profile. |

There is no separate “integrate this table with the user pool” action in the AWS console; the integration is **Cognito user ID = DynamoDB userId** in your backend.
