# ERD

Main entities:
- User(user_id PK, full_name, email, password_hash, phone, role)
- PasswordResetToken(token_id PK, user_id FK, token_hash, expires_at, used_at, created_at)
- Cinema(cinema_id PK, cinema_name, location)
- Screen(screen_id PK, cinema_id FK, screen_name)
- Movie(movie_id PK, title, genre, duration, description, poster_url, status)
- Showtime(showtime_id PK, movie_id FK, screen_id FK, start_time, ticket_price)
- Seat(seat_id PK, screen_id FK, seat_number, seat_type)
- Booking(booking_id PK, user_id FK, showtime_id FK, booking_date, total_amount, booking_status)
- BookingSeat(booking_seat_id PK, booking_id FK, seat_id FK, seat_price)
- Ticket(ticket_id PK, booking_id FK, booking_code, qr_code_value, ticket_status)

Relationships:
- User 1-to-many Booking
- User 1-to-many PasswordResetToken
- Cinema 1-to-many Screen
- Screen 1-to-many Seat
- Movie 1-to-many Showtime
- Screen 1-to-many Showtime
- Showtime 1-to-many Booking
- Booking 1-to-many BookingSeat
- Seat 1-to-many BookingSeat
- Booking 1-to-many Ticket

Backend booking logic must prevent the same seat being actively booked twice for one showtime.
