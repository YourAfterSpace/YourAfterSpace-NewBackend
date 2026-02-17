# DynamoDB setup (single table for profiles + experiences)

The backend uses **one** DynamoDB table for both **user profiles** and **experiences** (single-table design). You do **not** need a separate table for experiences.

- **Config property:** `aws.dynamodb.user-profile-table` (default: `YourAfterSpace`)
- **User profiles:** `userId` = Cognito user ID, `createdAt` = timestamp
- **Experiences:** `userId` = `exp-<uuid>`, `createdAt` = timestamp, `recordType` = `EXPERIENCE`

---

## 1. Create the table (if you don’t have it yet)

### Option A – AWS Console

1. Open **DynamoDB** → **Tables** → **Create table**.
2. **Table name:** `YourAfterSpace` (or the name you set in `aws.dynamodb.user-profile-table`).
3. **Partition key:** `userId` (String).
4. **Sort key:** `createdAt` (String).
5. Use default settings (or adjust capacity as needed) → **Create table**.

### Option B – AWS CLI

```bash
aws dynamodb create-table \
  --table-name YourAfterSpace \
  --attribute-definitions \
    AttributeName=userId,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
  --key-schema \
    AttributeName=userId,KeyType=HASH \
    AttributeName=createdAt,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --region ap-south-1
```

---

## 2. IAM permissions

The IAM user/role used by the app (e.g. `yas-backend-dev`) must be allowed to use this table. Include **Scan** (required for “get all experiences”).

Example policy (replace account id/region/table name if different):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:BatchGetItem",
        "dynamodb:BatchWriteItem",
        "dynamodb:Query",
        "dynamodb:Scan"
      ],
      "Resource": [
        "arn:aws:dynamodb:ap-south-1:516993964014:table/YourAfterSpace",
        "arn:aws:dynamodb:ap-south-1:516993964014:table/YourAfterSpace/index/*"
      ]
    }
  ]
}
```

Attach this policy to the IAM user/role your app uses (e.g. `yas-backend-dev`).

---

## 3. Point the app at the table (optional)

If you use a different table name, set it in config:

- **application.properties / application-dev.properties:**  
  `aws.dynamodb.user-profile-table=YourTableName`
- Or **environment:**  
  `AWS_DYNAMODB_USER_PROFILE_TABLE=YourTableName` (if you add that binding in the app).

After the table exists and IAM has the above permissions (including `dynamodb:Scan`), **GET /v1/experiences/all** and other experience/profile APIs should work.
