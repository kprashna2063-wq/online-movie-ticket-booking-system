# Authentication and Authorization Design

**Status:** registration, login, logout, current-user lookup and forgot/reset password are implemented.
Role-based rules for future modules (STAFF / ADMIN endpoints) are still to be added.

## Registration
Collects full name, email, phone and password. Inputs are validated, the email must be unique
(stored lower-case), passwords are hashed with BCrypt and the role is always `CUSTOMER`.

## Login and logout
- `POST /api/auth/login` authenticates with Spring Security (`AuthenticationManager` + BCrypt) and stores the
  security context in an HTTP session. The session id is regenerated on login (session-fixation protection).
- `POST /api/auth/logout` is handled by Spring Security's logout filter: it **invalidates the session, clears the
  authentication and deletes the `JSESSIONID` cookie**. It is safe to call when already logged out.
- The session cookie is `HttpOnly` and `SameSite=Lax`; set `server.servlet.session.cookie.secure=true` on HTTPS.
- The frontend asks `GET /api/auth/me` on every page to show Login/Register or "Hi, name" + Logout. Pages marked
  `data-requires-auth` (booking, seats) redirect to the login page when nobody is logged in.

## Password management (forgot password)
1. `POST /api/auth/forgot-password {email}` always answers `200` with the same text, whether or not the email is
   registered (prevents account enumeration). If the account exists, a 256-bit random token is created and a link
   `…/reset-password.html?token=…` is emailed. Older unused tokens of that user are invalidated.
2. Only the **SHA-256 hash** of the token is stored in `password_reset_tokens`.
3. The token is valid for 30 minutes (`app.password-reset.token-minutes`) and can be used **once**
   (redeeming is an atomic database update, so concurrent requests cannot both succeed).
4. `POST /api/auth/reset-password {token, newPassword}` validates the token, stores the new BCrypt hash and
   invalidates all remaining tokens of the user. Unknown, used and expired tokens give the same error message.
5. Without SMTP settings the link is printed in the backend log (development only).

Allowing *logged-in* users to change their password from a profile page is still a future feature.

## API summary
| Method | Path | Access | Purpose |
|--------|------|--------|---------|
| POST | `/api/auth/register` | public | Create a CUSTOMER account |
| POST | `/api/auth/login` | public | Log in (creates session) |
| POST | `/api/auth/logout` | public | Log out (destroys session) |
| GET | `/api/auth/me` | logged in | Current user (401 if not) |
| POST | `/api/auth/forgot-password` | public | Email a reset link |
| POST | `/api/auth/reset-password` | public | Set a new password with a token |

## Roles
- CUSTOMER: browse movies, view showtimes, select seats, book tickets and view own booking history.
- STAFF: view booking details and verify QR tickets.
- ADMIN: manage users, movies, cinemas, screens, showtimes, seats, bookings and reports.

## Authorization
Every `/api/**` endpoint requires login unless explicitly listed as public in `SecurityConfig`. Enforce
permissions on backend endpoints (e.g. `.requestMatchers("/api/admin/**").hasRole("ADMIN")`). Never rely only on
hiding frontend buttons. Customers must only access their own booking records.

## Known hardening items before production
- Enable CSRF tokens (currently disabled; mitigated by `SameSite=Lax`).
- Rate-limit login and forgot-password requests.
- Serve over HTTPS and set the session cookie `secure` flag.
- Sessions already open on other devices stay valid after a password reset (a session registry would be needed to end them).
