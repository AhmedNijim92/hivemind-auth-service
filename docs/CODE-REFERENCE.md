# Auth Service — Code-Level Reference

## AuthServiceApplication

**Package:** `com.hivemind.auth`

**Annotations:**
- `@SpringBootApplication` — Enables auto-configuration, component scanning, and configuration properties
- `@EnableDiscoveryClient` — Registers with Eureka service registry for service discovery
- `@EnableScheduling` — Enables scheduled task execution (used by OTP cleanup)

**Design Pattern:** Application Entry Point (Spring Boot convention)

### Methods

#### `main(String[] args)`
- **Signature:** `public static void main(String[] args)`
- **Logic:** Calls `SpringApplication.run(AuthServiceApplication.class, args)` to bootstrap the Spring context
- **Returns:** void

---

## SecurityConfig

**Package:** `com.hivemind.auth.config`

**Annotations:**
- `@Configuration` — Marks as Spring configuration class
- `@EnableWebSecurity` — Enables Spring Security's web security support

**Design Patterns:** 
- Template Method (via OncePerRequestFilter)
- Chain of Responsibility (filter chain)
- Strategy (authentication provider)

### Fields

| Field | Type | Source |
|-------|------|--------|
| userRepository | UserRepository | Constructor injection |

### Beans

#### `userDetailsService()`
- **Signature:** `@Bean public UserDetailsService userDetailsService()`
- **Logic:** Returns a lambda that loads a `User` by UUID from Cassandra via `UserRepository.findByUserId(UUID.fromString(username))`. Throws `UsernameNotFoundException` if not found.
- **Returns:** `UserDetailsService`

#### `filterChain(HttpSecurity http)`
- **Signature:** `@Bean public SecurityFilterChain filterChain(HttpSecurity http)`
- **Logic:**
  1. Disables CSRF (stateless API)
  2. Configures URL authorization:
     - Public (permitAll): `/signin`, `/sendOtp`, `/createUser`, `/actuator/**`
     - Admin-restricted: `/createAdmin` requires `ADMIN` or `SUPER_ADMIN` role
     - All other requests: authenticated
  3. Sets session management to `STATELESS`
  4. Sets the custom `authenticationProvider`
  5. Adds `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`
- **Returns:** `SecurityFilterChain`

#### `authenticationProvider()`
- **Signature:** `@Bean public AuthenticationProvider authenticationProvider()`
- **Logic:** Creates `DaoAuthenticationProvider`, sets the `userDetailsService` and `passwordEncoder`
- **Returns:** `AuthenticationProvider` (DaoAuthenticationProvider)

#### `passwordEncoder()`
- **Signature:** `@Bean public PasswordEncoder passwordEncoder()`
- **Logic:** Returns `new BCryptPasswordEncoder()`
- **Returns:** `PasswordEncoder`

#### `authenticationManager(AuthenticationConfiguration config)`
- **Signature:** `@Bean public AuthenticationManager authenticationManager(AuthenticationConfiguration config)`
- **Logic:** Delegates to `config.getAuthenticationManager()`
- **Returns:** `AuthenticationManager`

### Inner Class: JwtAuthFilter

**Extends:** `OncePerRequestFilter`

**Design Pattern:** Template Method — `doFilterInternal` is the hook method

#### Fields

| Field | Type | Source |
|-------|------|--------|
| jwtService | IJWTService | Constructor injection |
| userRepository | UserRepository | Constructor injection |

#### `doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)`
- **Signature:** `@Override protected void doFilterInternal(...)`
- **Logic:**
  1. Extracts `Authorization` header from the request
  2. Checks if header exists and starts with `"Bearer "`
  3. Extracts the JWT token (substring after "Bearer ")
  4. Extracts `userId` from token via `jwtService.extractUserId(token)`
  5. If userId is not null and SecurityContext has no existing authentication:
     a. Loads `UserDetails` from `userRepository.findByUserId(UUID.fromString(userId))`
     b. Validates token via `jwtService.validateToken(token)`
     c. If valid: creates `UsernamePasswordAuthenticationToken` with user's authorities
     d. Sets `WebAuthenticationDetailsSource` details
     e. Sets the authentication in `SecurityContextHolder`
  6. Calls `filterChain.doFilter(request, response)` to continue the chain
- **Returns:** void
- **Exceptions:** Continues filter chain without authentication if token is invalid/missing

---

## CassandraConfig

**Package:** `com.hivemind.auth.config`

**Extends:** `AbstractCassandraConfiguration`

**Annotations:**
- `@Configuration`

**Design Pattern:** Template Method — overrides hook methods from abstract parent

### Overridden Methods

#### `getKeyspaceName()`
- **Returns:** `"auth_keyspace"`

#### `getContactPoints()`
- **Returns:** Cassandra contact points (from configuration or default `"localhost"`)

#### `getPort()`
- **Returns:** Cassandra port (default `9042`)

#### `getLocalDataCenter()`
- **Returns:** `"datacenter1"`

#### `getSchemaAction()`
- **Returns:** `SchemaAction.CREATE_IF_NOT_EXISTS` — auto-creates tables on startup

#### `getEntityBasePackages()`
- **Returns:** `new String[] { "com.hivemind.auth.entity" }`

#### `getKeyspaceCreations()`
- **Logic:** Creates keyspace with:
  - Replication: `SimpleStrategy`, replication factor = 1
  - `DURABLE_WRITES = true`
- **Returns:** `List<CreateKeyspaceSpecification>`

---

## CassandraIndexInitializer

**Package:** `com.hivemind.auth.config`

**Annotations:**
- `@Component`

**Implements:** `CommandLineRunner`

**Design Pattern:** Command pattern — executes initialization command on application startup

### Fields

| Field | Type | Source |
|-------|------|--------|
| cqlSession | CqlSession | Constructor injection |

### Methods

#### `run(String... args)`
- **Signature:** `@Override public void run(String... args)`
- **Logic:** Executes CQL statement: `"CREATE INDEX IF NOT EXISTS users_mobile_idx ON users (mobile_number)"`
- **Purpose:** Creates a secondary index on `mobile_number` column to enable lookup by phone number
- **Returns:** void

---

## KafkaProducerConfig

**Package:** `com.hivemind.auth.config`

**Annotations:**
- `@Configuration`

**Design Pattern:** Factory Method — creates configured Kafka producer components

### Beans

#### `producerFactory()`
- **Signature:** `@Bean public ProducerFactory<String, UserCreatedEvent> producerFactory()`
- **Logic:** Configures producer with:
  - `bootstrap.servers` from application properties
  - Key serializer: `StringSerializer`
  - Value serializer: `JsonSerializer` (for UserCreatedEvent)
- **Returns:** `DefaultKafkaProducerFactory<String, UserCreatedEvent>`

#### `kafkaTemplate()`
- **Signature:** `@Bean public KafkaTemplate<String, UserCreatedEvent> kafkaTemplate()`
- **Logic:** Wraps the `producerFactory()` in a `KafkaTemplate`
- **Returns:** `KafkaTemplate<String, UserCreatedEvent>`

---

## AuthenticationController

**Package:** `com.hivemind.auth.controller`

**Annotations:**
- `@RestController`
- `@RequestMapping("/api/v1/auth")`

**Design Pattern:** Façade — exposes simplified API over complex authentication logic

### Fields

| Field | Type | Source |
|-------|------|--------|
| authenticationService | IAuthenticationService | Constructor injection |

### Endpoints

#### `POST /sendOtp`
- **Signature:** `public ResponseEntity<?> sendOtp(@Valid @RequestBody SendOtpDto sendOtpDto)`
- **Logic:** Delegates to `authenticationService.sendOtp(sendOtpDto.getMobileNumber())`
- **Returns:** Success message response
- **Validation:** `@Valid` triggers bean validation on `SendOtpDto`

#### `POST /signin`
- **Signature:** `public ResponseEntity<JwtAuthenticationResponse> signin(@Valid @RequestBody SigninRequest signinRequest)`
- **Logic:** Delegates to `authenticationService.signin(signinRequest)`
- **Returns:** `JwtAuthenticationResponse` containing JWT token, userId, and role

#### `POST /createUser`
- **Signature:** `public ResponseEntity<JwtAuthenticationResponse> createUser(@Valid @RequestBody UserDto userDto)`
- **Logic:** Delegates to `authenticationService.createUser(userDto)`
- **Returns:** `JwtAuthenticationResponse` with new user's token

#### `POST /createAdmin`
- **Signature:** `@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')") public ResponseEntity<JwtAuthenticationResponse> createAdmin(@Valid @RequestBody UserDto userDto)`
- **Logic:** Delegates to `authenticationService.createAdmin(userDto)`
- **Returns:** `JwtAuthenticationResponse` with new admin's token
- **Security:** Only accessible by users with ADMIN or SUPER_ADMIN role

---

## User (Entity)

**Package:** `com.hivemind.auth.entity`

**Annotations:**
- `@Table("users")` — Maps to Cassandra `users` table

**Implements:** `UserDetails` (Spring Security)

**Design Pattern:** Adapter — adapts domain entity to Spring Security's UserDetails interface

### Fields

| Field | Type | Annotation | Description |
|-------|------|------------|-------------|
| userId | UUID | `@PrimaryKey` | Unique user identifier |
| mobileNumber | String | | E.164 phone number |
| email | String | | User email address |
| name | String | | Display name |
| role | String | | Role name (USER/ADMIN/SUPER_ADMIN) |
| otp | String | | BCrypt-hashed OTP (acts as password) |
| createdAt | LocalDate | | Account creation date |

### Methods

#### `getAuthorities()`
- **Signature:** `@Override public Collection<? extends GrantedAuthority> getAuthorities()`
- **Logic:** Converts the `role` string to `Role` enum, calls `Role.getAuthorities()` which returns both permissions and role-based `GrantedAuthority` objects
- **Returns:** `Collection<SimpleGrantedAuthority>`

#### `getPassword()`
- **Signature:** `@Override public String getPassword()`
- **Logic:** Returns the `otp` field (BCrypt-hashed OTP serves as the password)
- **Returns:** `String` (hashed OTP)

#### `getUsername()`
- **Signature:** `@Override public String getUsername()`
- **Logic:** Returns the `mobileNumber` field
- **Returns:** `String` (mobile number)

---

## Role (Enum)

**Package:** `com.hivemind.auth.entity`

**Design Pattern:** Strategy — each enum constant defines its own permission set

### Constants

| Constant | Permissions |
|----------|------------|
| USER | `user:read`, `user:write` |
| ADMIN | `user:read`, `user:write`, `admin:read`, `admin:write` |
| SUPER_ADMIN | `user:read`, `user:write`, `admin:read`, `admin:write`, `super_admin:all` |

### Methods

#### `getAuthorities()`
- **Signature:** `public List<SimpleGrantedAuthority> getAuthorities()`
- **Logic:**
  1. Maps each permission string to a `SimpleGrantedAuthority`
  2. Adds `new SimpleGrantedAuthority("ROLE_" + this.name())` for role-based checks
- **Returns:** `List<SimpleGrantedAuthority>` containing all permissions + role authority

---

## UserRepository

**Package:** `com.hivemind.auth.repository`

**Extends:** `CassandraRepository<User, UUID>`

**Design Pattern:** Repository pattern — abstracts data access layer

### Methods

#### `findByMobileNumber(String mobileNumber)`
- **Signature:** `@Query(allowFiltering = true) Optional<User> findByMobileNumber(String mobileNumber)`
- **Logic:** CQL query with `ALLOW FILTERING` to find user by mobile number
- **Returns:** `Optional<User>`
- **Note:** Uses ALLOW FILTERING — acceptable for small datasets, secondary index (`users_mobile_idx`) improves performance

#### `findByUserId(UUID userId)`
- **Signature:** `@Query(allowFiltering = true) Optional<User> findByUserId(UUID userId)`
- **Logic:** CQL query to find user by UUID
- **Returns:** `Optional<User>`

---

## IAuthenticationService (Interface)

**Package:** `com.hivemind.auth.service`

### Method Signatures

| Method | Parameters | Returns |
|--------|-----------|---------|
| `sendOtp` | `String mobileNumber` | `void` |
| `signin` | `SigninRequest request` | `JwtAuthenticationResponse` |
| `createUser` | `UserDto userDto` | `JwtAuthenticationResponse` |
| `createAdmin` | `UserDto userDto` | `JwtAuthenticationResponse` |

---

## AuthenticationServiceImpl

**Package:** `com.hivemind.auth.service.impl`

**Annotations:**
- `@Service`

**Implements:** `IAuthenticationService`

**Design Pattern:** Service Layer — encapsulates business logic for authentication workflows

### Fields (Constructor Injection)

| Field | Type |
|-------|------|
| userRepository | UserRepository |
| otpService | IOtpService |
| jwtService | IJWTService |
| kafkaTemplate | KafkaTemplate<String, UserCreatedEvent> |

### Methods

#### `sendOtp(String mobileNumber)`
- **Signature:** `@Override public void sendOtp(String mobileNumber)`
- **Logic:** Delegates to `otpService.sendOtp(mobileNumber)`
- **Returns:** void

#### `signin(SigninRequest request)`
- **Signature:** `@Override public JwtAuthenticationResponse signin(SigninRequest request)`
- **Logic:**
  1. Calls `otpService.verifyOtp(request.getMobileNumber(), request.getOtp())`
  2. If verification fails → throws exception (invalid OTP)
  3. Finds user by mobile number via `userRepository.findByMobileNumber()`
  4. If user not found → throws exception
  5. Generates JWT token via `jwtService.generateToken(user)`
  6. Returns `JwtAuthenticationResponse(token, userId, role)`
- **Returns:** `JwtAuthenticationResponse`
- **Exceptions:** RuntimeException if OTP invalid or user not found

#### `createUser(UserDto userDto)`
- **Signature:** `@Override public JwtAuthenticationResponse createUser(UserDto userDto)`
- **Logic:**
  1. Creates new `User` with `UUID.randomUUID()` as userId
  2. Sets role to `"USER"`
  3. Sets mobileNumber, email, name from DTO
  4. Saves user via `userRepository.save(user)`
  5. Publishes `UserCreatedEvent` to Kafka topic `"user-created-topic"` (contains userId, mobileNumber, name, email)
  6. Generates JWT token via `jwtService.generateToken(user)`
  7. Returns `JwtAuthenticationResponse(token, userId, role)`
- **Returns:** `JwtAuthenticationResponse`

#### `createAdmin(UserDto userDto)`
- **Signature:** `@Override public JwtAuthenticationResponse createAdmin(UserDto userDto)`
- **Logic:**
  1. Creates new `User` with `UUID.randomUUID()` as userId
  2. Sets role to `"ADMIN"`
  3. Sets mobileNumber, email, name from DTO
  4. Saves user via `userRepository.save(user)`
  5. Generates JWT token via `jwtService.generateToken(user)`
  6. Returns `JwtAuthenticationResponse(token, userId, role)`
- **Returns:** `JwtAuthenticationResponse`
- **Note:** Does NOT publish Kafka event (admin creation is internal)

---

## IJWTService (Interface)

**Package:** `com.hivemind.auth.service`

### Method Signatures

| Method | Parameters | Returns |
|--------|-----------|---------|
| `generateToken` | `User user` | `String` |
| `validateToken` | `String token` | `boolean` |
| `extractUserId` | `String token` | `String` |
| `extractRole` | `String token` | `String` |

---

## JWTServiceImpl

**Package:** `com.hivemind.auth.service.impl`

**Annotations:**
- `@Service`

**Implements:** `IJWTService`

**Design Pattern:** Strategy — encapsulates JWT algorithm and signing logic

### Fields

| Field | Type | Source |
|-------|------|--------|
| jwtSecret | String | `@Value("${jwt.secret}")` |
| jwtExpiration | long | `@Value("${jwt.expiration}")` |

### Methods

#### `generateToken(User user)`
- **Signature:** `@Override public String generateToken(User user)`
- **Logic:**
  1. Builds claims map: `userId` → user.getUserId().toString(), `role` → user.getRole(), `email` → user.getEmail()
  2. Creates JWT with:
     - Subject: `user.getUserId().toString()`
     - Claims: the map above
     - IssuedAt: `new Date()`
     - Expiration: `new Date(System.currentTimeMillis() + jwtExpiration)`
     - Signs with HMAC key from `getSigningKey()`
  3. Compacts to string
- **Returns:** `String` (JWT token)

#### `validateToken(String token)`
- **Signature:** `@Override public boolean validateToken(String token)`
- **Logic:** Attempts to parse the token with the signing key. Returns `true` if successful, `false` if any exception (expired, malformed, invalid signature).
- **Returns:** `boolean`

#### `extractUserId(String token)`
- **Signature:** `@Override public String extractUserId(String token)`
- **Logic:** Parses token, extracts the `subject` claim (which is the userId)
- **Returns:** `String` (userId)

#### `extractRole(String token)`
- **Signature:** `@Override public String extractRole(String token)`
- **Logic:** Parses token, extracts the `"role"` custom claim
- **Returns:** `String` (role name)

#### `getSigningKey()` (Private)
- **Signature:** `private SecretKey getSigningKey()`
- **Logic:** `Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))`
- **Returns:** `SecretKey` (HMAC-SHA key)

---

## IOtpService (Interface)

**Package:** `com.hivemind.auth.service`

### Method Signatures

| Method | Parameters | Returns |
|--------|-----------|---------|
| `sendOtp` | `String mobileNumber` | `void` |
| `verifyOtp` | `String mobileNumber, String otp` | `boolean` |
| `cleanupExpiredOtps` | — | `void` |

---

## OtpServiceImpl

**Package:** `com.hivemind.auth.service.impl`

**Annotations:**
- `@Service`

**Implements:** `IOtpService`

**Design Pattern:** Strategy — behavior differs based on active profile (dev vs prod)

### Fields

| Field | Type | Source |
|-------|------|--------|
| userRepository | UserRepository | Constructor injection |
| passwordEncoder | PasswordEncoder | Constructor injection |
| twilioAccountSid | String | `@Value("${twilio.account-sid}")` |
| twilioAuthToken | String | `@Value("${twilio.auth-token}")` |
| twilioPhoneNumber | String | `@Value("${twilio.phone-number}")` |
| activeProfile | String | `@Value("${spring.profiles.active}")` |

### Methods

#### `sendOtp(String mobileNumber)`
- **Signature:** `@Override public void sendOtp(String mobileNumber)`
- **Logic:**
  1. Generates 6-digit OTP using `SecureRandom` (range 100000–999999)
  2. Hashes the OTP using `passwordEncoder.encode(otp)` (BCrypt)
  3. Looks up user by mobile number
  4. If user exists: updates the `otp` field with hashed value
  5. If user does NOT exist: creates a new `User` entity with the mobile number and hashed OTP, saves it
  6. **In dev mode** (`activeProfile == "dev"`): Logs the plain OTP to console for testing
  7. **In prod mode**: Sends the OTP via Twilio SMS API to the mobile number
- **Returns:** void

#### `verifyOtp(String mobileNumber, String otp)`
- **Signature:** `@Override public boolean verifyOtp(String mobileNumber, String otp)`
- **Logic:**
  1. Loads user by mobile number via `userRepository.findByMobileNumber()`
  2. If user not found → returns `false`
  3. Calls `passwordEncoder.matches(otp, user.getOtp())` to compare plain OTP against BCrypt hash
- **Returns:** `boolean` — `true` if OTP matches, `false` otherwise

#### `cleanupExpiredOtps()`
- **Signature:** `@Scheduled(fixedRate = 600000) @Override public void cleanupExpiredOtps()`
- **Logic:** Placeholder — scheduled to run every 10 minutes (600,000 ms). Intended to clear expired OTPs from the database.
- **Returns:** void

---

## DTOs

**Package:** `com.hivemind.auth.dto`

### SendOtpDto

| Field | Type | Validation |
|-------|------|------------|
| mobileNumber | String | `@NotBlank`, `@Pattern(regexp = "^\\+[1-9]\\d{1,14}$")` (E.164 format) |

### SigninRequest

| Field | Type | Validation |
|-------|------|------------|
| mobileNumber | String | `@NotBlank` |
| otp | String | `@NotBlank` |

### JwtAuthenticationResponse

| Field | Type | Description |
|-------|------|-------------|
| token | String | JWT access token |
| userId | String | User's UUID as string |
| role | String | User's role (USER/ADMIN/SUPER_ADMIN) |

### UserDto

| Field | Type | Description |
|-------|------|-------------|
| mobileNumber | String | E.164 phone number |
| email | String | Email address |
| name | String | Display name |

### UserCreatedEvent (Kafka Event)

| Field | Type | Description |
|-------|------|-------------|
| userId | UUID | New user's ID |
| mobileNumber | String | Phone number |
| name | String | Display name |
| email | String | Email address |
