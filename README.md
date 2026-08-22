# HiveMind Auth Service

Authentication, user profiles, and social graph service for the HiveMind platform.

## Responsibilities

- **Authentication**: OTP-based phone login via Vonage SMS, JWT token generation
- **User Profiles**: CRUD for user profiles (name, bio, avatar, cover photo)
- **Follow System**: User-to-user follow/unfollow relationships
- **User Search**: Search users by name

## API Endpoints

### Authentication (`/api/v1/auth`)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/sendOtp` | Send OTP to mobile number |
| POST | `/signin` | Verify OTP and get JWT |
| POST | `/createUser` | Register new user |
| POST | `/createAdmin` | Create admin (requires ADMIN role) |

### User Profiles (`/api/v1/users`)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/search?q=` | Search users by name |
| GET | `/{userId}` | Get user profile |
| PUT | `/{userId}` | Update profile |
| POST | `/{userId}/follow/{targetUserId}` | Follow a user |
| DELETE | `/{userId}/follow/{targetUserId}` | Unfollow a user |
| GET | `/{userId}/followers` | Get user's followers |
| GET | `/{userId}/following` | Get who user follows |

## Tech Stack

- Java 17, Spring Boot 3.3
- Cassandra (auth_keyspace): users, user_profiles, follows tables
- Redis: OTP storage, caching
- Kafka: Publishes `user-created-topic` events
- Eureka: Service discovery

## Running

```bash
mvn clean package -DskipTests
java -jar target/*.jar
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| CASSANDRA_HOST | localhost | Cassandra contact point |
| REDIS_HOST | localhost | Redis host |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 | Kafka brokers |
| JWT_SECRET | (dev key) | JWT signing key |
| VONAGE_API_KEY | — | Vonage SMS API key |
| VONAGE_API_SECRET | — | Vonage SMS secret |
| SPRING_PROFILES_ACTIVE | dev | dev (logs OTP) / prod (sends SMS) |
