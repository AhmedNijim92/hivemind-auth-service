# Auth Service

> HiveMind Authentication & Authorization Microservice

## Overview

The auth-service handles user authentication via phone-based OTP (One-Time Password), JWT token generation, and role-based access control. It is the entry point for all new users and the issuer of identity tokens used across the platform.

## Service Info

| Property | Value |
|----------|-------|
| Port | 8081 |
| Service Name | `auth-service` |
| Database | Apache Cassandra |
| Keyspace | `auth_keyspace` |
| Spring Boot | 3.3.5 |
| Spring Cloud | 2023.0.3 |
| Java | 17 |

## Architecture

```
Client
  │
  ▼
AuthenticationController
  │
  ├── IAuthenticationService (sendOtp, signin, createUser, createAdmin)
  │       ├── IOtpService (send, verify, cleanup)
  │       ├── IJWTService (generate, validate, extract)
  │       └── Kafka Producer → user-created-topic
  │
  └── Spring Security (SecurityConfig)
        └── UserDetailsService → Cassandra User table
```

## API Endpoints

Base path: `/api/v1/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/sendOtp` | Public | Send OTP to mobile number |
| POST | `/signin` | Public | Verify OTP and return JWT |
| POST | `/createUser` | Public | Register new user, return JWT |
| POST | `/createAdmin` | ADMIN role | Create admin account |

### Request/Response Examples

#### POST /api/v1/auth/sendOtp
```json
// Request
{ "mobileNumber": "+46701234567" }

// Response (200)
{ "message": "OTP sent successfully" }
```

#### POST /api/v1/auth/signin
```json
// Request
{ "mobileNumber": "+46701234567", "otp": "123456" }

// Response (200)
{ "token": "eyJhbGciOi...", "userId": "uuid-here", "role": "USER" }
```

#### POST /api/v1/auth/createUser
```json
// Request
{
  "mobileNumber": "+46701234567",
  "name": "Ahmed",
  "email": "ahmed@example.com"
}

// Response (200)
{ "token": "eyJhbGciOi...", "userId": "uuid-here", "role": "USER" }
```

## Data Model

### User (Cassandra table: `users`)

| Column | Type | Description |
|--------|------|-------------|
| user_id | UUID | Primary key |
| mobile_number | String | Phone number (E.164 format) |
| email | String | Email address |
| name | String | Display name |
| role | String | USER, ADMIN, SUPER_ADMIN |
| otp | String | Current OTP (temporary) |
| created_at | LocalDate | Account creation date |

### Role (Enum)

| Role | Permissions |
|------|-------------|
| USER | user:read, user:write |
| ADMIN | user:read, user:write, admin:read, admin:write |
| SUPER_ADMIN | All permissions |

## Kafka Events

### Produces: `user-created-topic`

Published when a new user is registered via `/createUser`:

```json
{
  "userId": "uuid",
  "mobileNumber": "+46701234567",
  "name": "Ahmed",
  "email": "ahmed@example.com",
  "timestamp": "2025-06-04T10:30:00"
}
```

**Consumers:**
- `user-service` — creates a UserProfile record
- `notification-service` — generates a welcome notification

## Security

- JWT Algorithm: HS256 (HMAC-SHA256)
- Token Expiry: 24 hours (configurable via `JWT_EXPIRATION`)
- OTP Delivery: Twilio SMS
- OTP Expiry: 5 minutes
- Password: Not used — OTP-only authentication
- Spring Security: All `/api/v1/auth/**` endpoints are publicly accessible except `/createAdmin`

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| CASSANDRA_HOST | localhost | Cassandra contact point |
| CASSANDRA_PORT | 9042 | Cassandra port |
| CASSANDRA_DATACENTER | datacenter1 | Cassandra datacenter |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 | Kafka brokers |
| JWT_SECRET | (base64 key) | HMAC signing key |
| JWT_EXPIRATION | 86400000 | Token TTL in milliseconds |
| TWILIO_ACCOUNT_SID | — | Twilio account SID |
| TWILIO_AUTH_TOKEN | — | Twilio auth token |
| TWILIO_PHONE_NUMBER | — | Twilio sender number |
| EUREKA_SERVER | http://localhost:8761/eureka | Eureka registry URL |

## Dependencies

- spring-boot-starter-web
- spring-boot-starter-security
- spring-boot-starter-data-cassandra
- spring-boot-starter-validation
- spring-boot-starter-actuator
- spring-cloud-starter-netflix-eureka-client
- spring-kafka
- jjwt-api / jjwt-impl / jjwt-jackson (0.12.3)
- twilio (10.0.0)
- hivemind-common (1.0.0)
- lombok

## Running Locally

```bash
# Prerequisites: Cassandra running on port 9042, Kafka on 9092

cd microservices/auth-service
mvn spring-boot:run
```

The service auto-creates the `auth_keyspace` and `users` table on startup, including a secondary index on `mobile_number`.

## Known Issues

1. No token refresh — users must re-login after 24h
2. OTP is stored in the same User row (could use a separate TTL-based table)
3. Twilio credentials required for OTP delivery in production
