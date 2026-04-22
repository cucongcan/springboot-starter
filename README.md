# Spring Boot Starter Template

A production-ready Spring Boot backend with JWT authentication, REST + GraphQL APIs, pagination, and Flyway migrations — built entirely with Docker, no local Java installation required.

> **Vietnamese docs:** [README-vi.md](README-vi.md)

| | |
|---|---|
| **Framework** | Spring Boot 3.2.x, Java 17 |
| **Database** | PostgreSQL 16 + Flyway |
| **Auth** | JWT Access Token (15 min) + Refresh Token (7 days) |
| **API** | REST `/api/**` + GraphQL `/graphql` |
| **Build** | Maven inside Docker — no Java on host required |

---

## Table of Contents

1. [Build & Run with Docker](#1-build--run-with-docker)
2. [Environment Variables](#2-environment-variables)
3. [API Reference](#3-api-reference)
4. [GraphQL Playground](#4-graphql-playground)
5. [Flyway Migrations](#5-flyway-migrations)
6. [Auto-generate Migration SQL from Entities](#6-auto-generate-migration-sql-from-entities)
7. [Adding a New Feature (REST + GraphQL)](#7-adding-a-new-feature-rest--graphql)
8. [How JwtAuthFilter Works](#8-how-jwtauthfilter-works)
9. [Writing Tests](#9-writing-tests)
10. [Project Structure](#10-project-structure)

---

## 1. Build & Run with Docker

The entire build and runtime lives inside containers — **no Java or Maven needed on your machine**.

**Prerequisite:** Docker Desktop (or Docker Engine + Compose plugin)

### Dev mode (live source mount)

```bash
docker compose -f docker-compose.dev.yml up
```

A Maven container mounts your source directory and runs `mvn spring-boot:run`. Code changes that don't require a restart are picked up automatically; otherwise, restart the container.

```bash
# Follow logs
docker compose -f docker-compose.dev.yml logs -f maven

# Stop
docker compose -f docker-compose.dev.yml down
```

### Production build (multi-stage)

```bash
docker compose up --build        # foreground
docker compose up --build -d     # background
```

Build stages:
1. `maven:3.9.6-eclipse-temurin-17` — `mvn package -DskipTests`
2. `eclipse-temurin:17-jre-jammy` — copies the `.jar` → ~145 MB final image

### Run tests only (no database needed)

Tests use H2 in-memory; no PostgreSQL required.

```bash
docker run --rm \
  -v $(pwd):/app -w /app \
  maven:3.9.6-eclipse-temurin-17 \
  mvn test -B
```

```bash
# Single test class
mvn test -Dtest=PostServiceTest -B

# With JaCoCo coverage report → target/site/jacoco/index.html
mvn test jacoco:report -B
```

---

## 2. Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/starter_db` | JDBC connection URL |
| `DB_USER` | `postgres` | Database username |
| `DB_PASS` | `password` | Database password |
| `JWT_SECRET` | *(required)* | Base64-encoded HMAC-SHA key, minimum 256 bits |
| `CORS_ORIGINS` | `http://localhost:3000` | Comma-separated list of allowed origins |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active profile: `dev` or `prod` |

Generate a strong `JWT_SECRET`:

```bash
openssl rand -base64 32
```

Health check:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## 3. API Reference

### Auth endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| POST | `/api/auth/register` | Public | Create a new account |
| POST | `/api/auth/login` | Public | Login → access token + refresh token |
| POST | `/api/auth/refresh` | Public | Exchange refresh token for a new access token |
| POST | `/api/auth/logout` | Bearer | Revoke refresh token |

**Login request / response:**

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"Password1"}'
```

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "ca92c0bf-...",
    "tokenType": "Bearer"
  }
}
```

**Using the access token:**

```bash
curl http://localhost:8080/api/posts \
  -H "Authorization: Bearer eyJ..."
```

### Post endpoints

| Method | URL | Auth | Description |
|--------|-----|------|-------------|
| GET | `/api/posts?page=0&size=10&sort=createdAt,desc` | Public | Paginated list |
| GET | `/api/posts/{id}` | Public | Single post |
| GET | `/api/posts/my` | Bearer | Posts by the current user |
| POST | `/api/posts` | Bearer | Create post |
| PUT | `/api/posts/{id}` | Bearer | Update post (owner or ADMIN) |
| DELETE | `/api/posts/{id}` | Bearer | Delete post (owner or ADMIN) |
| GET | `/api/admin/posts` | ADMIN | All posts (admin only) |

**Standard response envelope:**

```json
{
  "success": true,
  "message": "Post created successfully",
  "data": { "id": 1, "title": "..." }
}
```

**Error response:**

```json
{
  "success": false,
  "message": "Post not found with id: 42",
  "errorCode": "RESOURCE_NOT_FOUND",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Pagination response:**

```json
{
  "success": true,
  "data": {
    "content": [...],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 50,
    "totalPages": 5,
    "last": false
  }
}
```

---

## 4. GraphQL Playground

Open **http://localhost:8080/playground.html** (dev mode only).

The playground includes pre-filled example queries for all operations. Paste your `accessToken` into the JWT field at the top-right to authenticate mutations.

### Auth mutations

```graphql
# 1. Register
mutation {
  register(username: "alice", email: "alice@example.com", password: "Password1") {
    message
  }
}

# 2. Login → copy accessToken → paste into JWT field
mutation {
  login(username: "alice", password: "Password1") {
    accessToken
    refreshToken
    tokenType
  }
}

# 3. Current user (requires JWT)
query {
  me {
    id
    username
    email
    roles
  }
}

# 4. Refresh access token
mutation {
  refreshToken(refreshToken: "uuid-here") {
    accessToken
  }
}

# 5. Logout (requires JWT)
mutation {
  logout(refreshToken: "uuid-here")
}
```

### Post queries

```graphql
query {
  posts(page: 0, size: 5) {
    content { id title authorUsername published createdAt }
    totalElements totalPages
  }
}

mutation {
  createPost(title: "Hello", content: "World", published: true) {
    id title createdAt
  }
}
```

### GraphQL error format

Errors are returned in the standard GraphQL `errors` array with an `extensions.code` field:

```json
{
  "errors": [{
    "message": "Invalid username or password",
    "path": ["login"],
    "extensions": { "code": "INVALID_CREDENTIALS" }
  }]
}
```

| Code | Cause |
|------|-------|
| `INVALID_CREDENTIALS` | Wrong username or password |
| `UNAUTHORIZED` | Expired or missing token |
| `ACCESS_DENIED` | Insufficient role |
| `NOT_FOUND` | Resource does not exist |
| `BAD_REQUEST` | Validation failure |

---

## 5. Flyway Migrations

All schema changes go through versioned Flyway SQL files. Files that have run in any environment **must never be edited** — create a new version to alter.

**File naming:** `V{n}__{description}.sql`

```
src/main/resources/db/migration/
  V1__create_users_table.sql
  V2__create_posts_table.sql
  V3__create_refresh_tokens_table.sql
  V4__add_post_thumbnail_column.sql   ← new
```

**Adding a column:**

```sql
-- V4__add_post_thumbnail_column.sql
ALTER TABLE posts ADD COLUMN thumbnail_url VARCHAR(500);
```

**Creating a new table:**

```sql
-- V5__create_comments_table.sql
CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT    NOT NULL,
    post_id    BIGINT  NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id  BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_comments_post_id ON comments (post_id);
```

**View migration history:**

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

| Error | Cause | Fix |
|-------|-------|-----|
| `Migration checksum mismatch` | Edited a file that already ran | Create a new migration to alter |
| `Found more than one migration with version X` | Duplicate version number | Rename one file |

---

## 6. Auto-generate Migration SQL from Entities

Hibernate can generate `CREATE TABLE` DDL from your `@Entity` classes. This covers **new tables only** — `ALTER TABLE` for incremental changes must still be written by hand.

```bash
docker run --rm \
  -v $(pwd):/app -w /app \
  maven:3.9.6-eclipse-temurin-17 \
  mvn test -Dtest=SchemaExportTest -B
```

Output: `target/generated-schema.sql`

The test uses H2 in `MODE=PostgreSQL` with `PostgreSQLDialect`, so the output is close to real PostgreSQL syntax (`BIGSERIAL`, `TEXT`, `TIMESTAMP WITH TIME ZONE`).

**Review before use:**

| Issue | Generated | Should be |
|-------|-----------|-----------|
| Constraint name | `FK6xvn0811...` | `fk_posts_author_id` |
| Missing `ON DELETE CASCADE` | `references users` | `references users ON DELETE CASCADE` |
| Missing indexes | *(absent)* | Add `CREATE INDEX` manually |

**Workflow for a new entity:**

```
1. Write @Entity  →  2. Run SchemaExportTest  →  3. Copy CREATE TABLE block
→  4. Create V{n}__....sql  →  5. Fix constraint names + add indexes
→  6. docker compose up  →  Flyway applies migration automatically
```

---

## 7. Adding a New Feature (REST + GraphQL)

Follow the same eight-step pattern used by the `Post` module. Example: **Comment** feature.

### Step 1 — Migration

```sql
-- V5__create_comments_table.sql
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_comments_post_id ON comments(post_id);
```

### Step 2 — Entity

```java
@Entity @Table(name = "comments")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)          // always LAZY for relations
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @CreatedDate @Column(updatable = false) private Instant createdAt;
    @LastModifiedDate                       private Instant updatedAt;
}
```

### Step 3 — Repository

```java
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPostId(Long postId, Pageable pageable);

    // JOIN FETCH prevents N+1 when the author is needed
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.id = :id")
    Optional<Comment> findByIdWithAuthor(@Param("id") Long id);
}
```

### Step 4 — DTO

```java
@Getter @Builder
public class CommentResponse {
    private final Long id;
    private final String content;
    private final Long postId;
    private final String authorUsername;
    private final Instant createdAt;

    public static CommentResponse from(Comment c) {
        return CommentResponse.builder()
                .id(c.getId()).content(c.getContent())
                .postId(c.getPost().getId())
                .authorUsername(c.getAuthor().getUsername())
                .createdAt(c.getCreatedAt()).build();
    }
}
```

### Step 5 — Service

> Authorization checks (owner / ADMIN) belong in the **service layer**, not the controller.

```java
@Service @RequiredArgsConstructor @Transactional
public class CommentService {
    public CommentResponse createComment(Long postId, CommentRequest req, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return CommentResponse.from(commentRepository.save(
                Comment.builder().content(req.getContent()).post(post).author(author).build()));
    }
}
```

### Step 6 — REST Controller

```java
@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CommentResponse>>> list(
            @PathVariable Long postId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(null,
                commentService.getCommentsByPost(postId, pageable)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequest req,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Comment created", commentService.createComment(postId, req, user.getUsername())));
    }
}
```

### Step 7 — GraphQL Schema

```graphql
type Query {
  comments(postId: ID!, page: Int = 0, size: Int = 20): CommentConnection!
}
type Mutation {
  createComment(postId: ID!, content: String!): Comment!
  deleteComment(id: ID!): Boolean!
}
type Comment { id: ID! content: String! postId: ID! authorUsername: String! createdAt: String }
type CommentConnection { content: [Comment!]! totalElements: Int! totalPages: Int! pageNumber: Int! pageSize: Int! last: Boolean! }
```

### Step 8 — GraphQL Resolver

```java
@Controller @RequiredArgsConstructor
public class CommentResolver {
    @QueryMapping
    public PagedResponse<CommentResponse> comments(@Argument Long postId,
            @Argument int page, @Argument int size) { ... }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public CommentResponse createComment(@Argument Long postId, @Argument String content,
            @AuthenticationPrincipal UserDetails user) { ... }
}
```

### Checklist

```
[ ] V{n}__create_{name}_table.sql
[ ] @Entity (FetchType.LAZY on all relations)
[ ] Repository (JOIN FETCH @Query where needed)
[ ] *Request DTO (validation) + *Response DTO (static from())
[ ] Service (@Transactional, auth checks here)
[ ] REST Controller (@PreAuthorize, @AuthenticationPrincipal)
[ ] schema.graphqls (new Query/Mutation/Type)
[ ] GraphQL Resolver (@QueryMapping / @MutationMapping)
[ ] Unit test (service) + Integration test (controller)
```

---

## 8. How JwtAuthFilter Works

**File:** `src/main/java/com/yourcompany/starter/auth/filter/JwtAuthFilter.java`

### Request flow

```
HTTP Request
  │
  ▼
JwtAuthFilter.doFilterInternal()          extends OncePerRequestFilter
  │
  ├─ No "Authorization: Bearer ..." header?
  │       └─ pass through → SecurityConfig decides public/protected
  │
  ├─ Has Bearer token
  │       ├─ JwtUtil.extractUsername(token) → Optional<String>
  │       │       └─ empty (malformed) → skip, continue chain
  │       │
  │       ├─ SecurityContext already set? → skip (already authenticated)
  │       │
  │       ├─ loadUserByUsername(username)
  │       ├─ JwtUtil.isTokenValid(token, userDetails)
  │       │       ├─ signature valid
  │       │       ├─ not expired
  │       │       └─ subject matches username
  │       │
  │       └─ SecurityContextHolder.setAuthentication(authToken)
  │
  └─ filterChain.doFilter()  ← ALWAYS called, valid token or not
```

### Three non-negotiable rules

**1. Extend `OncePerRequestFilter`** — guaranteed to run exactly once per request, including internal forwards.

**2. Never throw exceptions out of the filter.** All JWT logic is wrapped in `try-catch`:

```java
try {
    // ... validation ...
} catch (Exception e) {
    log.warn("JWT auth failed: {}", e.getMessage());
    SecurityContextHolder.clearContext();
}
filterChain.doFilter(request, response); // always called
```

If an exception escapes the filter, Spring returns 500, not 401. Invalid tokens should simply leave the `SecurityContext` empty; the `AuthenticationEntryPoint` in `SecurityConfig` then returns a JSON 401.

**3. Always call `filterChain.doFilter()`** — even when the token is invalid. The filter's job is to authenticate, not to block. Blocking is `SecurityConfig`'s responsibility.

### Why `AuthenticationEntryPoint` matters

Without it, Spring Security redirects unauthenticated requests to `/login` (HTML 302). The custom entry point returns JSON instead:

```java
.authenticationEntryPoint((req, res, ex) -> {
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    objectMapper.writeValue(res.getWriter(),
        ApiResponse.error("Unauthorized", "UNAUTHORIZED"));
})
```

### Security component interaction

```
Request → JwtAuthFilter → SecurityFilterChain → Controller / Resolver
                │                  │
         sets context         checks rules:
         if token valid        permitAll / hasRole / authenticated
                                        │
                              fail → AuthenticationEntryPoint (401 JSON)
                                     AccessDeniedHandler      (403 JSON)
```

---

## 9. Writing Tests

### Unit tests — Service layer

Uses Mockito only; no Spring context or database. Runs fastest.

```java
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;
    @InjectMocks PostService postService;

    @Test
    void createPost_savesAndReturnsResponse() {
        User author = User.builder().id(1L).username("alice").build();
        Post saved  = Post.builder().id(1L).title("T").content("C").author(author).build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(postRepository.save(any())).thenReturn(saved);

        PostRequest req = new PostRequest();
        req.setTitle("T"); req.setContent("C");
        PostResponse res = postService.createPost(req, "alice");

        assertThat(res.getId()).isEqualTo(1L);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void updatePost_byNonOwner_throwsUnauthorized() {
        User owner = User.builder().username("owner").roles(Set.of(Role.ROLE_USER)).build();
        Post post  = Post.builder().id(1L).author(owner).build();

        when(postRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("other"))
                .thenReturn(Optional.of(User.builder().username("other")
                        .roles(Set.of(Role.ROLE_USER)).build()));

        assertThatThrownBy(() -> postService.updatePost(1L, new PostRequest(), "other"))
                .isInstanceOf(UnauthorizedException.class);
        verify(postRepository, never()).save(any());
    }
}
```

### Integration tests — Controller layer

Uses H2 in-memory (`@ActiveProfiles("test")`), tests the full HTTP stack.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void login_withValidCredentials_returnsTokens() throws Exception {
        // setup: save user with encoded password in @BeforeEach
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser"); req.setPassword("Password1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        // ...
        mockMvc.perform(/* ... */)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }
}
```

### Testing with `@WithMockUser`

Skip the login flow when you only need to test controller logic:

```java
@Test
@WithMockUser(username = "alice", roles = {"USER"})
void createPost_authenticated_returns201() throws Exception {
    // No Authorization header needed
    mockMvc.perform(post("/api/posts").contentType(APPLICATION_JSON).content("..."))
            .andExpect(status().isCreated());
}
```

### Run commands

```bash
# All tests (Docker — no Java on host)
docker run --rm -v $(pwd):/app -w /app maven:3.9.6-eclipse-temurin-17 mvn test -B

# Specific class
... mvn test -Dtest=PostServiceTest -B

# Coverage report → target/site/jacoco/index.html
... mvn test jacoco:report -B
```

---

## 10. Project Structure

```
src/
├── main/
│   ├── java/com/yourcompany/starter/
│   │   ├── StarterApplication.java          @EnableJpaAuditing, @EnableConfigurationProperties
│   │   ├── config/
│   │   │   ├── SecurityConfig.java          JWT filter chain, CORS, AuthEntryPoint (401 JSON)
│   │   │   ├── JwtConfig.java               @ConfigurationProperties(prefix = "app.jwt")
│   │   │   └── GraphQLConfig.java
│   │   ├── auth/
│   │   │   ├── controller/AuthController.java   REST: /api/auth/**
│   │   │   ├── graphql/AuthResolver.java         GraphQL: register/login/refreshToken/logout/me
│   │   │   ├── dto/                              RegisterRequest, LoginRequest, AuthResponse
│   │   │   ├── filter/JwtAuthFilter.java         OncePerRequestFilter — never throws
│   │   │   └── service/AuthService.java
│   │   ├── user/
│   │   │   ├── entity/User.java              implements UserDetails, table "users"
│   │   │   ├── entity/Role.java              enum ROLE_USER, ROLE_ADMIN
│   │   │   ├── dto/UserResponse.java
│   │   │   ├── repository/UserRepository.java
│   │   │   └── service/UserDetailsServiceImpl.java
│   │   ├── post/
│   │   │   ├── controller/PostController.java    REST: /api/posts/**
│   │   │   ├── controller/AdminPostController.java
│   │   │   ├── graphql/PostResolver.java          GraphQL: posts/post/myPosts/createPost/...
│   │   │   ├── dto/                              PostRequest, PostResponse
│   │   │   ├── entity/Post.java              FetchType.LAZY on author
│   │   │   ├── repository/PostRepository.java    JOIN FETCH query for N+1 prevention
│   │   │   └── service/PostService.java      auth checks: owner or ADMIN
│   │   ├── common/
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java   @RestControllerAdvice — REST errors
│   │   │   │   ├── GraphQLExceptionResolver.java  DataFetcherExceptionResolver — GQL errors
│   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   └── UnauthorizedException.java
│   │   │   ├── response/
│   │   │   │   ├── ApiResponse.java          { success, message, data, errorCode, timestamp }
│   │   │   │   └── PagedResponse.java        wraps Spring Page<T>
│   │   │   └── util/JwtUtil.java             JJWT 0.12.x — generate / validate
│   │   └── token/
│   │       ├── entity/RefreshToken.java      UUID token (not JWT), revocable
│   │       ├── repository/RefreshTokenRepository.java
│   │       └── service/RefreshTokenService.java
│   └── resources/
│       ├── application.yml                   base config (env var placeholders)
│       ├── application-dev.yml               SQL logging, Apollo Sandbox CORS
│       ├── application-prod.yml              minimal, no graphiql
│       ├── application-test.yml              H2 in-memory, Flyway disabled
│       ├── graphql/schema.graphqls
│       ├── static/playground.html            Self-hosted GraphiQL (cdn.jsdelivr.net)
│       └── db/migration/
│           ├── V1__create_users_table.sql
│           ├── V2__create_posts_table.sql
│           └── V3__create_refresh_tokens_table.sql
└── test/
    └── java/com/yourcompany/starter/
        ├── auth/AuthControllerTest.java      @SpringBootTest + H2
        ├── post/PostServiceTest.java         @ExtendWith(MockitoExtension.class)
        └── tools/SchemaExportTest.java       generates target/generated-schema.sql
```

---

## Security Checklist (before deploying to production)

- [ ] `JWT_SECRET` is at least 256 bits, generated randomly, set via env var
- [ ] Access token TTL is short (15 minutes recommended)
- [ ] Refresh tokens are stored in DB and can be revoked
- [ ] HTTPS enforced — HTTP rejected
- [ ] CORS `allowed-origins` is a specific list, not `*`
- [ ] Rate limiting on `/api/auth/login` (Bucket4j / Resilience4j)
- [ ] `graphiql.enabled: false` in production
- [ ] `/actuator/**` restricted to ADMIN only
- [ ] Passwords and JWT tokens are never logged
- [ ] Dependency scan: `mvn dependency-check:check`
