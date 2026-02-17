# Create DynamoDB table for user profiles

The **GET /v1/user/profile** and other user profile endpoints use a DynamoDB table.  
If the table does not exist or has the wrong schema, you get **500 Internal Server Error**.

## Required table

| Setting      | Value        |
|-------------|--------------|
| Table name  | `user-profiles` |
| Partition key | `userId` (String) |
| Sort key    | `createdAt` (String) |
| Region      | Same as your app (e.g. `eu-west-2` from application.properties) |

## Option 1: AWS Console

1. Open **AWS Console** → **DynamoDB** → **Tables** → **Create table**.
2. **Table name:** `user-profiles`
3. **Partition key:** `userId` (String)
4. **Sort key:** `createdAt` (String)
5. Create table (default capacity is fine for local/dev).

## Option 2: AWS CLI

```bash
aws dynamodb create-table \
  --table-name user-profiles \
  --attribute-definitions \
    AttributeName=userId,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
  --key-schema \
    AttributeName=userId,KeyType=HASH \
    AttributeName=createdAt,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-2
```

Use the same region as in your `application.properties` (`aws.region=eu-west-2`).

## After creating the table

- **GET /v1/user/profile** will still return **404** until you create a profile.
- Create a profile first: **POST /v1/user/profile** with header `x-amzn-oidc-identity: <your-user-id>` and body e.g. `{"city":"London","country":"UK"}`.
- Then **GET /v1/user/profile** should return **200** with your profile.

## If you use a different table name

Set in `application.properties` or `application-dev.properties`:

```properties
aws.dynamodb.user-profile-table=your-table-name
```

The table must still have partition key `userId` (String) and sort key `createdAt` (String).
