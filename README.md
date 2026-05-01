# Auth Service

Authentication service for the HiveMind platform. Handles OTP-based authentication and JWT token generation/validation.

## Details

| Property | Value |
|----------|-------|
| **Port** | `8081` |
| **Database** | Cassandra |
| **Messaging** | Kafka |
| **Role** | Authentication (OTP + JWT) |

## Build & Run

```bash
# Build
mvn clean package

# Run
java -jar target/*.jar

# Docker
docker build -t hivemind/auth-service .
```

## Links

- [Main Repository](https://github.com/AhmedNijim92/hivemind-backend)
