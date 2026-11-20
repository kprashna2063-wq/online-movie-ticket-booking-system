CREATE DATABASE IF NOT EXISTS movie_booking;
USE movie_booking;

CREATE TABLE users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    role ENUM('CUSTOMER', 'STAFF', 'ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Single-use, time-limited tokens for the "forgot password" flow.
-- Only the SHA-256 hash of the token is stored (the raw token is only in the emailed link).
CREATE TABLE password_reset_tokens (
    token_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE cinemas (
    cinema_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cinema_name VARCHAR(150) NOT NULL,
    location VARCHAR(255) NOT NULL
);

CREATE TABLE screens (
    screen_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cinema_id BIGINT NOT NULL,
    screen_name VARCHAR(80) NOT NULL,
    FOREIGN KEY (cinema_id) REFERENCES cinemas(cinema_id)
);

CREATE TABLE movies (
    movie_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(180) NOT NULL,
    genre VARCHAR(80),
    duration_minutes INT,
    description TEXT,
    poster_url VARCHAR(500),
    status VARCHAR(30) DEFAULT 'ACTIVE'
);

CREATE TABLE showtimes (
    showtime_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    movie_id BIGINT NOT NULL,
    screen_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    ticket_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (movie_id) REFERENCES movies(movie_id),
    FOREIGN KEY (screen_id) REFERENCES screens(screen_id)
);

CREATE TABLE seats (
    seat_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    screen_id BIGINT NOT NULL,
    seat_number VARCHAR(12) NOT NULL,
    seat_type VARCHAR(30) DEFAULT 'REGULAR',
    UNIQUE (screen_id, seat_number),
    FOREIGN KEY (screen_id) REFERENCES screens(screen_id)
);

CREATE TABLE bookings (
    booking_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    showtime_id BIGINT NOT NULL,
    booking_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_amount DECIMAL(10,2) NOT NULL,
    booking_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (showtime_id) REFERENCES showtimes(showtime_id)
);

CREATE TABLE booking_seats (
    booking_seat_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    seat_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id),
    FOREIGN KEY (seat_id) REFERENCES seats(seat_id)
);

CREATE TABLE tickets (
    ticket_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    booking_code VARCHAR(80) NOT NULL UNIQUE,
    qr_code_value VARCHAR(500) NOT NULL,
    ticket_status VARCHAR(30) NOT NULL DEFAULT 'VALID',
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id)
);

-- Important: implement transactional seat locking / active-booking validation
-- in the backend before accepting a booking to prevent double-booking.
