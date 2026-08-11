# HiveMind Auth Service

> OTP-based authentication service with JWT token management.

## Overview

Passwordless authentication using phone number + OTP verification via Vonage Verify API.

## Features

- **OTP Login** — Send OTP → Verify → Receive JWT
- **User Registration** — Create account with name + email + phone
- **JWT Tokens** — 24h expiry, contains userId, role, name, email claims
- **Vonage Verify** — Production SMS/Voice OTP delivery (handles carrier compliance)
- **Dev Mode** — OTP logged to console (no external API calls)
- **Redis-backed** — OTP verify request IDs stored in Redis (survives restarts, multi-replica safe)
- **Admin Creation** — Separate endpoint for admin accounts

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/sendOtp` | No | Send OTP to phone number |
| POST | `/api/v1/auth/signin` | No | Verify OTP, return JWT |
| POST | `/api/v1/auth/createUser` | No | Register new user |
| POST | `/api/v1/auth/createAdmin` | JWT | Create admin account |

## Auth Flow

```
1. Client → POST /sendOtp { mobileNumber: "+46707518829" }
2. Vonage Verify API sends SMS with 6-digit code
3. Client → POST /signin { mobileNumber: "+46707518829", otp: "483921" }
4. Server verifies via Vonage Check API
5. Server returns { token, userId, role, name }
6. Client stores JWT, uses for all subsequent requests
```

## JWT Claims

```json
{
  "sub": "userId (UUID)",
  "userId": "UUID",
  "role": "USER | ADMIN",
  "name": "User Display Name",
  "email": "user@example.com",
  "iat": 1234567890,
  "exp": 1234654290
}
```

## Configuration

```yaml
spring.profiles.active: dev    # dev = logs OTP, prod = sends SMS

vonage:
  api:
    key: ${VONAGE_API_KEY}
    secret: ${VONAGE_API_SECRET}

jwt:
  secret: ${JWT_SECRET}
  expiration: 86400000          # 24 hours

spring.data:
  cassandra:                    # User storage
    keyspace-name: auth_keyspace
  redis:                        # OTP verify request IDs
    host: ${REDIS_HOST}
    password: ${SPRING_DATA_REDIS_PASSWORD}
```

## Tech Stack

- Spring Boot 3.3
- Spring Security
- Spring Data Cassandra
- Spring Data Redis
- Vonage Server SDK
- JJWT (JWT generation/validation)
- Kafka (user-created events)
- Eureka (service discovery)

## Development

```bash
# Run locally (dev mode — OTP logged to console)
mvn spring-boot:run

# Check OTP in logs
grep "OTP for" logs
```

## Docker

```dockerfile
FROM eclipse-temurin:17-jre-alpine
# Non-root user, JVM tuning (G1GC, 75% RAM)
```

## Security

- Passwords never stored (OTP-only auth)
- OTP hashed with BCrypt before storage
- JWT secret configurable via env var
- Stack traces disabled in production
- Rate limiting on auth endpoints (5 req/s per IP)
- Redis TTL on verify requests (10 min expiry)
