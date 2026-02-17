# AWS integrations in YAS Backend

This document lists **everything in the backend that is integrated with AWS**: which services are used, what for, and what you need to create/configure in AWS.

---

## 1. AWS Cognito (authentication)

| What | Details |
|------|---------|
| **Service** | Amazon Cognito (User Pools) |
| **Used for** | Sign up, login, confirm signup, resend confirmation code. User identity is the Cognito user ID (`sub`). |
| **Where in code** | `CognitoService`, `AuthController`, `AwsConfig` (CognitoIdentityProviderClient) |
| **Config (application.properties)** | `aws.cognito.user-pool-id`, `aws.cognito.client-id`, `aws.cognito.client-secret` (optional), `aws.region` |

### What you need in AWS

- **Cognito User Pool** in the same region as `aws.region`.
- **App client** in that user pool (no client secret, or with secret if you set `aws.cognito.client-secret`).
- **Auth flow**: enable **ALLOW_USER_PASSWORD_AUTH** on the app client so login works.
- **Username**: if you use email as username, configure the user pool accordingly (the app uses email as username for signup/login).

### Local / Postman

- AWS credentials (env or `~/.aws/credentials`) must have permission to call Cognito (e.g. `cognito-idp:SignUp`, `cognito-idp:InitiateAuth`, etc.).
- For “current user” routes, send header **`x-amzn-oidc-identity`** = Cognito user ID (`sub`), e.g. from the login response.

---

## 2. DynamoDB (data storage)

The app uses **DynamoDB** for user profiles and for experiences (and related data). Tables must exist in the same region as `aws.region`.

### 2.1 User profiles table

| What | Details |
|------|---------|
| **Used for** | All `/v1/user/profile` endpoints: create, get, update, soft delete, reactivate, status. |
| **Where in code** | `UserProfileRepository` |
| **Config** | Property `aws.dynamodb.user-profile-table` (default: **`user-profiles`**). |

**Table schema**

| Setting | Value |
|--------|--------|
| **Table name** | `user-profiles` (or value of `aws.dynamodb.user-profile-table`) |
| **Partition key** | `userId` (String) – Cognito user ID |
| **Sort key** | `createdAt` (String) – ISO-8601 timestamp |
| **Region** | Same as `aws.region` (e.g. `ap-south-1`, `eu-west-2`) |

**Create via AWS CLI (use your region):**

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
  --region ap-south-1
```

If you use a different table name, set `aws.dynamodb.user-profile-table=your-table-name` in `application.properties` (or env). The key schema must still be `userId` (PK) and `createdAt` (SK).

---

### 2.2 Experiences table (YourAfterSpace)

| What | Details |
|------|---------|
| **Used for** | Create/get/update experiences, list past attended and upcoming paid experiences per user. |
| **Where in code** | `ExperienceRepository`, `ExperienceDao`, `UserExperienceDao` (all use the same table name from config). |
| **Config** | Property `aws.dynamodb.user-profile-table` (default: **`YourAfterSpace`**). |

**Table schema used by ExperienceRepository (main path for create/get/update):**

| Setting | Value |
|--------|--------|
| **Table name** | `YourAfterSpace` (or value of `aws.dynamodb.user-profile-table` for experiences) |
| **Partition key** | `userId` (String) – for experiences this holds `experienceId`; `recordType` = `EXPERIENCE` |
| **Sort key** | `createdAt` (String) |
| **Region** | Same as `aws.region` |

**Note:** `ExperienceDao` and `UserExperienceDao` use a **pk/sk** (single-table) style in code. If you already have a `YourAfterSpace` table with **pk** (String) and **sk** (String), keep that schema and ensure the app’s config points to it. If you are creating the table only for the Spring Boot app and use only `ExperienceRepository` for experience CRUD, the same **userId + createdAt** schema as user-profiles works for experiences (with `recordType` to distinguish records).

**Create a table with userId + createdAt (matches ExperienceRepository):**

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

If your existing `YourAfterSpace` table uses **pk** and **sk**, do not change it; the DAOs that use pk/sk will work with that. The important part is that the table exists in the correct region and the app has IAM permission to read/write.

---

## 3. AWS credentials (local / non-Lambda)

When running **locally** (or on EC2/ECS without a role), the app uses the **default credential chain**:

- Environment variables: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, optional `AWS_SESSION_TOKEN`
- Or shared config: `~/.aws/credentials`
- Or `aws configure`

Required permissions (minimum):

- **Cognito:** `cognito-idp:SignUp`, `cognito-idp:InitiateAuth`, `cognito-idp:ConfirmSignUp`, `cognito-idp:ResendConfirmationCode`, and related read for the user pool.
- **DynamoDB:** `dynamodb:PutItem`, `dynamodb:GetItem`, `dynamodb:Query`, `dynamodb:UpdateItem`, `dynamodb:Scan` (if used) on the tables above.

Region for all calls is **`aws.region`** (e.g. `ap-south-1`).

---

## 4. API Gateway + Cognito (when deployed behind API Gateway)

When the app is behind **API Gateway** with a **Cognito authorizer**:

- API Gateway validates the JWT with Cognito and sets:
  - **`x-amzn-oidc-identity`** – Cognito user ID (`sub`)
  - **`x-amzn-oidc-data`** – base64-encoded JWT claims
- The backend does **not** validate the JWT again; it trusts these headers and uses `ApiGatewayAuthFilter` / `UserContext` to get the current user.

So in production you need:

- API Gateway REST (or HTTP) API
- Cognito User Pool Authorizer attached to the routes that require a user
- Same Cognito User Pool and region as in `application.properties`

---

## 5. Lambda (optional deployment)

The project also contains **ApiGatewayHandler** (Lambda). When deployed as Lambda:

- **Cognito** and **DynamoDB** are still used; config comes from **environment variables** (e.g. `AWS_COGNITO_USER_POOL_ID`, `AWS_COGNITO_CLIENT_ID`, `AWS_REGION`, `AWS_DYNAMODB_USER_PROFILE_TABLE`).
- The Lambda uses the same DynamoDB table name for experiences and can use additional DAOs (e.g. `GroupDao`, `GroupExperienceDao`, `VenueLocationDao`) that are **not** used by the Spring Boot app. Those DAOs expect a **pk/sk** table design.

For **running the Spring Boot app locally**, you only need:

1. **Cognito** User Pool + App Client (with USER_PASSWORD_AUTH enabled).
2. **DynamoDB** table **user-profiles** (userId, createdAt) for profile endpoints.
3. **DynamoDB** table **YourAfterSpace** (or the name you set) for experience endpoints – either userId/createdAt or pk/sk depending on your existing schema.
4. **AWS credentials** with access to Cognito and DynamoDB in `aws.region`.

---

## Quick checklist (Spring Boot, local)

| # | AWS resource | Purpose | Config / table name |
|---|----------------|--------|---------------------|
| 1 | Cognito User Pool | Auth (signup, login, confirm) | `aws.cognito.user-pool-id`, `aws.region` |
| 2 | Cognito App Client | Auth; enable USER_PASSWORD_AUTH | `aws.cognito.client-id` |
| 3 | DynamoDB table **user-profiles** | User profiles | PK `userId`, SK `createdAt`; default table name `user-profiles` |
| 4 | DynamoDB table **YourAfterSpace** | Experiences (and user-experience links if using DAOs) | PK `userId`/`pk`, SK `createdAt`/`sk` per your schema; default name `YourAfterSpace` |
| 5 | IAM credentials | Call Cognito + DynamoDB | Env or `~/.aws/credentials`, same region as `aws.region` |

All of the above must use the **same region** as `aws.region` in your `application.properties` (e.g. `ap-south-1`).
