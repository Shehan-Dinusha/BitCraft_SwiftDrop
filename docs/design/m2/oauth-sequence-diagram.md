# Google OAuth 2.0 Sequence Diagram — SwiftDrop IAM

> **Member 2 deliverable — Stage 20**  
> Documents the Google OAuth 2.0 login flow as implemented in `auth-iam-service`.

## Flow Overview

SwiftDrop uses a **backend-driven OAuth 2.0 Authorization Code flow** with a two-redirect pattern.  
Tokens are **never** placed in URLs — the browser only ever receives a short-lived one-time code  
that it immediately exchanges via a secure `POST` to the IAM backend.

## Sequence Diagram

```mermaid
sequenceDiagram
    actor User as User / Browser
    participant FE   as Frontend (React)
    participant GW   as API Gateway :8080
    participant IAM  as auth-iam-service :8081
    participant Redis as Redis
    participant DB   as IAM PostgreSQL
    participant G    as Google OAuth 2.0

    Note over User,G: ── 1. Initiate login ──────────────────────────────────────

    User->>FE: Click "Continue with Google"
    FE->>User: Browser navigates to<br/>/api/v1/auth/oauth2/google/authorize

    User->>GW: GET /api/v1/auth/oauth2/google/authorize
    GW->>IAM: proxy (public route, no JWT required)
    IAM->>Redis: SET oauth2:google:state:{state} = "valid"<br/>TTL = state-ttl-seconds (default 300 s)
    IAM-->>GW: 302 → accounts.google.com/o/oauth2/v2/auth<br/>?client_id=…&redirect_uri=…&state={state}&scope=openid email profile
    GW-->>User: 302 redirect

    Note over User,G: ── 2. Google consent ──────────────────────────────────────

    User->>G: GET consent screen
    G-->>User: Display "Sign in with Google"
    User->>G: Grants permission
    G-->>User: 302 → {redirect_uri}?code={authCode}&state={state}

    Note over User,G: ── 3. Backend callback ────────────────────────────────────

    User->>GW: GET /api/v1/auth/oauth2/google/callback?code={authCode}&state={state}
    GW->>IAM: proxy (public route)

    IAM->>Redis: GET oauth2:google:state:{state}
    Redis-->>IAM: "valid" (or nil → reject)
    IAM->>Redis: DEL oauth2:google:state:{state}  (single-use)

    IAM->>G: POST https://oauth2.googleapis.com/token<br/>grant_type=authorization_code, code={authCode}
    G-->>IAM: { access_token, id_token, … }

    IAM->>G: GET https://www.googleapis.com/oauth2/v3/userinfo<br/>Authorization: Bearer {access_token}
    G-->>IAM: { sub, email, name, picture }

    IAM->>DB: SELECT user_accounts WHERE email = {email}
    alt Account exists
        DB-->>IAM: existing UserAccount row
    else New user
        IAM->>DB: INSERT user_accounts (role=CUSTOMER, password='OAUTH_NO_PASSWORD:…')
        DB-->>IAM: new UserAccount row
    end

    IAM->>Redis: SET oauth2:google:code:{oneTimeCode} = {userId}<br/>TTL = 120 s
    IAM-->>GW: 302 → /auth/callback?code={oneTimeCode}
    GW-->>User: 302 redirect

    Note over User,G: ── 4. Frontend token exchange ─────────────────────────────

    User->>FE: GET /auth/callback?code={oneTimeCode}
    FE->>FE: OAuthCallbackPage mounts,<br/>reads ?code from URL

    FE->>GW: POST /api/v1/auth/oauth2/exchange<br/>{ "code": "{oneTimeCode}" }
    GW->>IAM: proxy (public route)

    IAM->>Redis: GET oauth2:google:code:{oneTimeCode}
    Redis-->>IAM: userId (or nil → 401)
    IAM->>Redis: DEL oauth2:google:code:{oneTimeCode}  (single-use)

    IAM->>DB: SELECT user_accounts WHERE public_id = {userId}
    DB-->>IAM: UserAccount row

    IAM->>IAM: Issue RS256 access token (15 min)<br/>+ rotate refresh token (7 days)
    IAM-->>GW: 200 { accessToken, refreshToken, role, … }
    GW-->>FE: 200 TokenResponse

    FE->>FE: setAuth(tokens)<br/>navigate to roleHome[role]
    FE-->>User: Lands on role dashboard
```

## Key Security Properties

| Property | Implementation |
|---|---|
| **CSRF protection** | PKCE-equivalent: `state` token is a random UUID stored in Redis with 5-minute TTL; validated and deleted on callback |
| **Token-free URLs** | Tokens never appear in redirect URIs — only a 2-minute one-time `code` is passed |
| **Single-use codes** | Both the `state` and exchange `code` are deleted from Redis on first use |
| **OAuth account isolation** | Password hash set to `OAUTH_NO_PASSWORD:{UUID}` — mathematically unreproducible; blocks password-based login |
| **New account role** | OAuth registrations always start as `CUSTOMER`; role promotion is an admin action |
| **Strict Mode safe** | `OAuthCallbackPage` uses an `exchanged` ref to guard against double-call in React Strict Mode |

## Configuration

```yaml
# application.yml / environment variables
swiftdrop:
  oauth2:
    google:
      client-id:     ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
      redirect-uri:  ${GOOGLE_REDIRECT_URI:http://localhost:8081/api/v1/auth/oauth2/google/callback}
      state-ttl-seconds: 300   # 5 minutes
```

## Related Files

| File | Purpose |
|---|---|
| [`GoogleOAuthConfig.java`](../../backend/auth-iam-service/src/main/java/com/swiftdrop/iam/config/GoogleOAuthConfig.java) | `@ConfigurationProperties` binding |
| [`GoogleOAuthService.java`](../../backend/auth-iam-service/src/main/java/com/swiftdrop/iam/service/GoogleOAuthService.java) | Full flow implementation |
| [`AuthController.java`](../../backend/auth-iam-service/src/main/java/com/swiftdrop/iam/controller/AuthController.java) | `/authorize`, `/callback`, `/exchange` endpoints |
| [`SecurityConfig.java`](../../backend/auth-iam-service/src/main/java/com/swiftdrop/iam/config/SecurityConfig.java) | Permit-all rules for OAuth paths |
| [`OAuthCallbackPage.tsx`](../../frontend/src/features/auth/pages/OAuthCallbackPage.tsx) | Frontend code-exchange page |
| [`authApi.ts`](../../frontend/src/services/api/authApi.ts) | `exchangeOAuthCode()`, `googleAuthorizeUrl()` |

