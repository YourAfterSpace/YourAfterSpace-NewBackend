# Create an IAM user for local development

If you have **no IAM user** yet, follow these steps to create one and use it with your YAS backend (Cognito + DynamoDB).

---

## Step 1: Open IAM and create a user

1. In **AWS Console**, go to **IAM** (search "IAM" in the top search bar).
2. In the left menu, click **Users**.
3. Click **Create user**.

---

## Step 2: Set user name and sign-in (optional)

1. **User name:** e.g. `yas-backend-dev` (any name you like).
2. **Provide user access to the AWS Management Console:**  
   - Choose **I don't want to provide console access** if you only need keys for the app.  
   - Or choose **Password - AWS Management Console** if you also want to log in to the console with this user.
3. Click **Next**.

---

## Step 3: Attach permissions (Cognito + DynamoDB)

1. Select **Attach policies directly**.
2. Click **Create policy** (opens a new tab).

   **In the new tab (Create policy):**
   - Go to the **JSON** tab.
   - Delete the default content and paste the policy below.
   - Replace **`YOUR_ACCOUNT_ID`** with your 12-digit AWS account ID (top-right of the console → click your account name to see it).
   - Replace **`ap-south-1`** with your region if different (e.g. `eu-west-2`).
   - Replace **`YOUR_USER_POOL_ID`** with your Cognito User Pool ID (e.g. `ap-south-1_UomOVMq5X` – from Cognito → User pools → your pool).

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "Cognito",
      "Effect": "Allow",
      "Action": [
        "cognito-idp:SignUp",
        "cognito-idp:InitiateAuth",
        "cognito-idp:ConfirmSignUp",
        "cognito-idp:ResendConfirmationCode",
        "cognito-idp:GetUser",
        "cognito-idp:AdminGetUser"
      ],
      "Resource": "arn:aws:cognito-idp:ap-south-1:YOUR_ACCOUNT_ID:userpool/YOUR_USER_POOL_ID"
    },
    {
      "Sid": "DynamoDBUserProfiles",
      "Effect": "Allow",
      "Action": [
        "dynamodb:PutItem",
        "dynamodb:GetItem",
        "dynamodb:Query",
        "dynamodb:UpdateItem",
        "dynamodb:BatchGetItem"
      ],
      "Resource": "arn:aws:dynamodb:ap-south-1:YOUR_ACCOUNT_ID:table/user-profiles"
    }
  ]
}
```

3. Click **Next**.
4. **Policy name:** e.g. `YASBackend-LocalDev-Policy`.
5. Click **Create policy**.
6. Go back to the **Create user** tab. Click the refresh icon next to the policy search, search for **YASBackend-LocalDev-Policy**, tick it, then click **Next**.

---

## Step 4: Finish creating the user

1. Review and click **Create user**.
2. Click on the **user name** you just created (e.g. `yas-backend-dev`).

---

## Step 5: Create access keys (for your app)

1. Open the **Security credentials** tab.
2. Scroll to **Access keys**.
3. Click **Create access key**.
4. Choose **Application running outside AWS** (or **Command Line Interface**) → **Next**.
5. (Optional) Add a description, e.g. "YAS backend local".
6. Click **Create access key**.
7. **Important:** Copy the **Access key ID** and **Secret access key** and save them somewhere safe. You won’t see the secret again.

---

## Step 6: Use the keys in your app

**Option A – Environment variables (PowerShell, current session):**

```powershell
$env:AWS_ACCESS_KEY_ID = "AKIA..."
$env:AWS_SECRET_ACCESS_KEY = "your-secret-key"
$env:AWS_REGION = "ap-south-1"
```

Then start your app in the same terminal (e.g. `.\mvnw.cmd spring-boot:run`).

**Option B – AWS CLI (persistent):**

```bash
aws configure
```

Enter when prompted:

- **AWS Access Key ID:** (paste the access key)
- **AWS Secret Access Key:** (paste the secret key)
- **Default region name:** `ap-south-1` (or your region)
- **Default output format:** (optional, e.g. `json`)

Your Spring Boot app will then use these credentials from `~/.aws/credentials` and `~/.aws/config`.

---

## Summary

| Step | What you did |
|------|----------------------|
| 1 | IAM → Users → Create user |
| 2 | Set user name, no console access (or with password) |
| 3 | Created policy with Cognito + DynamoDB, attached to user |
| 4 | Created user |
| 5 | Created access key, saved Access key ID + Secret |
| 6 | Set env vars or `aws configure` so the app uses this user |

After this, your backend has an IAM **user** with permissions for Cognito and the `user-profiles` table.
