# Online Movie Ticket Booking System

A web-based system for browsing movies, viewing showtimes and booking cinema seats.

## Technology
- Frontend: HTML, CSS, JavaScript
- Backend: Java Spring Boot
- Database: MySQL

## Project structure
- `frontend/` - website pages and assets (incl. login, register, forgot/reset password)
- `backend/` - Spring Boot Maven application (authentication API under `/api/auth`)
- `database/` - initial MySQL schema
- `documentation/` - project planning documents

## Run the project
1. Install Java 17+, Maven and MySQL. Create the database: `mysql -u root -p < database/schema.sql`
   (or just create `movie_booking` and let Hibernate create the tables).
2. Set your MySQL password in `backend/src/main/resources/application.properties`.
3. Start the backend **from the `backend` folder**:
```bash
cd backend
mvn spring-boot:run
```
4. Open **http://localhost:8080/** - the backend also serves the `frontend/` folder, so login sessions work
   without any extra setup.

Opening `frontend/index.html` directly from disk (`file://`) will not work for login, because browsers do not
send session cookies from `file://` pages. If you prefer a separate dev server (e.g. VS Code Live Server on
port 5500), that works too - `http://localhost:5500` and `http://127.0.0.1:5500` are already allowed by CORS
(`app.cors.allowed-origins`).

## Authentication features
- Register, **login**, **logout** (session is destroyed, cookie deleted), and **forgot / reset password**.
- Pages: `login.html`, `register.html`, `forgot-password.html`, `reset-password.html`.
- `booking.html` and `seats.html` require login.
- Details, API table and security notes: `documentation/Authentication-Authorization.md`.

### Forgot password and email
Without SMTP settings, the reset link is **printed in the backend console** (development only), look for
`DEV ONLY - password reset link`. To send real emails, uncomment and fill in the `spring.mail.*` lines in
`application.properties` and set `app.frontend-base-url` to your site's address.

## Run the tests
```bash
cd backend
mvn test
```
Tests use an in-memory H2 database (profile `test`), so MySQL is not needed.

Database connection values in `backend/src/main/resources/application.properties` are examples.
Update them for your local MySQL setup before running database-backed features.

## GitHub
After extracting this ZIP, open a terminal in this folder and run:
```bash
git init
git add .
git commit -m "Initial project structure"
git branch -M main
git remote add origin YOUR_GITHUB_REPOSITORY_URL
git push -u origin main
```
