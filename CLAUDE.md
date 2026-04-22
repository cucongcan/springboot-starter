# CLAUDE.md — Spring Boot Starter Template

Tài liệu hướng dẫn cho AI assistant (Claude, Cursor, Copilot, v.v.) khi làm việc với dự án này.

---

## Tổng quan dự án

**Spring Boot Starter Template** — một backend RESTful API được xây dựng sẵn với các tính năng auth, CRUD, GraphQL và phân trang, sẵn sàng để mở rộng cho các dự án thực tế.

| Mục | Chi tiết |
|-----|----------|
| **Framework** | Spring Boot 3.x (Java 17+) |
| **Build tool** | Maven (hoặc Gradle) |
| **Database** | PostgreSQL (có thể thay bằng MySQL) |
| **Auth** | JWT (Access Token + Refresh Token) |
| **API** | REST + GraphQL |
| **Test** | JUnit 5 + Mockito |

---

## Cấu trúc thư mục

```
src/
├── main/
│   ├── java/com/yourcompany/starter/
│   │   ├── StarterApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java          # Spring Security + JWT filter chain
│   │   │   ├── JwtConfig.java               # JWT secret, expiry config
│   │   │   └── GraphQLConfig.java           # GraphQL scalar, CORS config
│   │   ├── auth/
│   │   │   ├── controller/
│   │   │   │   └── AuthController.java      # /api/auth/register, login, logout, refresh
│   │   │   ├── dto/
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   ├── LoginRequest.java
│   │   │   │   └── AuthResponse.java        # accessToken + refreshToken
│   │   │   ├── service/
│   │   │   │   └── AuthService.java
│   │   │   └── filter/
│   │   │       └── JwtAuthFilter.java       # OncePerRequestFilter
│   │   ├── user/
│   │   │   ├── entity/
│   │   │   │   ├── User.java                # @Entity, roles: ADMIN/USER
│   │   │   │   └── Role.java                # enum Role { ADMIN, USER }
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java
│   │   │   └── service/
│   │   │       └── UserDetailsServiceImpl.java
│   │   ├── post/
│   │   │   ├── controller/
│   │   │   │   └── PostController.java      # REST CRUD /api/posts
│   │   │   ├── graphql/
│   │   │   │   └── PostResolver.java        # @QueryMapping, @MutationMapping
│   │   │   ├── dto/
│   │   │   │   ├── PostRequest.java
│   │   │   │   └── PostResponse.java
│   │   │   ├── entity/
│   │   │   │   └── Post.java
│   │   │   ├── repository/
│   │   │   │   └── PostRepository.java      # extends JpaRepository + PagingAndSortingRepository
│   │   │   └── service/
│   │   │       └── PostService.java
│   │   ├── common/
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice
│   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   └── UnauthorizedException.java
│   │   │   ├── response/
│   │   │   │   ├── ApiResponse.java         # wrapper: { success, message, data }
│   │   │   │   └── PagedResponse.java       # wrapper cho phân trang
│   │   │   └── util/
│   │   │       └── JwtUtil.java             # generate, validate, extract claims
│   │   └── token/
│   │       ├── entity/
│   │       │   └── RefreshToken.java
│   │       ├── repository/
│   │       │   └── RefreshTokenRepository.java
│   │       └── service/
│   │           └── RefreshTokenService.java
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       ├── application-prod.yml
│       └── graphql/
│           └── schema.graphqls              # GraphQL schema definition
└── test/
    └── java/com/yourcompany/starter/
        ├── auth/
        │   └── AuthControllerTest.java
        └── post/
            └── PostServiceTest.java
```

---

## Stack & Dependencies (pom.xml)

```xml
<!-- Spring Boot Parent -->
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.2.x</version>
</parent>

<!-- Core dependencies -->
spring-boot-starter-web
spring-boot-starter-security
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-starter-graphql          <!-- Spring for GraphQL (tích hợp sẵn từ Boot 3) -->
spring-boot-starter-actuator

<!-- JWT -->
io.jsonwebtoken:jjwt-api:0.12.x
io.jsonwebtoken:jjwt-impl:0.12.x
io.jsonwebtoken:jjwt-jackson:0.12.x

<!-- Database -->
postgresql (hoặc mysql-connector-j)
flyway-core                           <!-- Database migration -->

<!-- Utilities -->
org.mapstruct:mapstruct               <!-- DTO mapping -->
org.projectlombok:lombok

<!-- Test -->
spring-boot-starter-test
spring-security-test
```

---

## Cấu hình môi trường (application.yml)

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/starter_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASS:password}
  jpa:
    hibernate:
      ddl-auto: validate          # Dùng Flyway, KHÔNG để create/update trên prod
    show-sql: false
  graphql:
    graphiql:
      enabled: true               # Chỉ bật trên dev
    path: /graphql

app:
  jwt:
    secret: ${JWT_SECRET}         # Phải set qua biến môi trường, KHÔNG hardcode
    expiration-ms: 900000         # 15 phút cho access token
    refresh-expiration-ms: 604800000  # 7 ngày cho refresh token
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:3000}
```

---

## Authentication & Security

### Luồng xác thực (Auth Flow)

```
Client                          Server
  |                               |
  |-- POST /api/auth/register --> |  (bcrypt hash password, lưu DB)
  |<-- 201 Created -------------- |
  |                               |
  |-- POST /api/auth/login -----> |  (validate credentials)
  |<-- { accessToken,             |  (JWT access token 15p + refresh token 7 ngày)
  |      refreshToken } --------- |
  |                               |
  |-- GET /api/posts              |
  |   Authorization: Bearer <AT>  |  (JwtAuthFilter validate token)
  |<-- 200 OK ------------------- |
  |                               |
  |-- POST /api/auth/refresh ----> | (validate refresh token, issue new access token)
  |<-- { accessToken } ---------- |
  |                               |
  |-- POST /api/auth/logout -----> | (invalidate refresh token trong DB)
  |<-- 200 OK ------------------- |
```

### Endpoints Auth

| Method | URL | Mô tả | Auth |
|--------|-----|--------|------|
| POST | `/api/auth/register` | Đăng ký tài khoản mới | Public |
| POST | `/api/auth/login` | Đăng nhập, lấy token | Public |
| POST | `/api/auth/logout` | Đăng xuất, invalidate refresh token | Bearer Token |
| POST | `/api/auth/refresh` | Làm mới access token | Refresh Token |

### Role-based Access Control

- `ROLE_USER` — CRUD bài viết của chính mình, đọc bài viết người khác
- `ROLE_ADMIN` — Toàn quyền: đọc/sửa/xoá tất cả bài viết, quản lý user

```java
// Ví dụ SecurityConfig
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

### JwtAuthFilter — Quy tắc quan trọng

- Kế thừa `OncePerRequestFilter` — chỉ chạy 1 lần mỗi request
- Extract token từ header `Authorization: Bearer <token>`
- Validate: chữ ký hợp lệ + chưa hết hạn + không bị blacklist
- Set `SecurityContextHolder` nếu token hợp lệ
- **Không ném exception trong filter** — trả về 401 qua `AuthenticationEntryPoint`

---

## REST API — Posts (Bài viết)

### Endpoints

| Method | URL | Mô tả | Auth |
|--------|-----|--------|------|
| GET | `/api/posts` | Lấy danh sách bài viết (có phân trang) | Public |
| GET | `/api/posts/{id}` | Lấy chi tiết 1 bài viết | Public |
| POST | `/api/posts` | Tạo bài viết mới | USER, ADMIN |
| PUT | `/api/posts/{id}` | Cập nhật bài viết (chỉ owner hoặc ADMIN) | USER, ADMIN |
| DELETE | `/api/posts/{id}` | Xoá bài viết (chỉ owner hoặc ADMIN) | USER, ADMIN |
| GET | `/api/posts/my` | Lấy bài viết của user hiện tại | USER, ADMIN |
| GET | `/api/admin/posts` | Lấy tất cả bài viết (ADMIN only) | ADMIN |

### Phân trang (Pagination)

**Query params chuẩn:**
```
GET /api/posts?page=0&size=10&sort=createdAt,desc
```

**Response format:**
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

Sử dụng `Pageable` từ Spring Data JPA:
```java
Page<Post> findAll(Pageable pageable);
Page<Post> findByAuthorId(Long authorId, Pageable pageable);
Page<Post> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
```

---

## GraphQL API — Posts

### Schema (`schema.graphqls`)

```graphql
type Query {
  posts(page: Int = 0, size: Int = 10): PostConnection!
  post(id: ID!): Post
  myPosts(page: Int = 0, size: Int = 10): PostConnection!
}

type Mutation {
  createPost(input: PostInput!): Post!
  updatePost(id: ID!, input: PostInput!): Post!
  deletePost(id: ID!): Boolean!
}

type Post {
  id: ID!
  title: String!
  content: String!
  published: Boolean!
  author: User!
  createdAt: String!
  updatedAt: String!
}

type PostConnection {
  content: [Post!]!
  totalElements: Int!
  totalPages: Int!
  pageNumber: Int!
  pageSize: Int!
}

input PostInput {
  title: String!
  content: String!
  published: Boolean = false
}

type User {
  id: ID!
  username: String!
  email: String!
  roles: [String!]!
}
```

### GraphQL Auth

Truyền JWT qua HTTP Header trong GraphQL request:
```
Authorization: Bearer <accessToken>
```

Dùng `@PreAuthorize` trên resolver hoặc custom `SecurityGraphQLContext`.

### Endpoint GraphQL

- Playground (dev): `GET /graphiql`
- API endpoint: `POST /graphql`

---

## Các tính năng đề xuất bổ sung (Gợi ý mở rộng)

### 1. Email Verification
- Gửi email xác thực khi đăng ký (Spring Mail + token)
- Endpoint: `GET /api/auth/verify?token=xxx`

### 2. Password Reset
- Endpoint: `POST /api/auth/forgot-password` + `POST /api/auth/reset-password`
- Dùng token ngắn hạn (15 phút), lưu DB hoặc Redis

### 3. Rate Limiting
- Dùng Bucket4j hoặc Resilience4j để giới hạn số request
- Quan trọng cho `/api/auth/login` để chống brute-force

### 4. Soft Delete
- Thêm field `deletedAt` vào entity, dùng `@SQLRestriction("deleted_at IS NULL")`
- Tránh xoá cứng dữ liệu quan trọng

### 5. Audit Logging
- `@EntityListeners(AuditingEntityListener.class)` với `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`
- Enable qua `@EnableJpaAuditing`

### 6. File Upload
- Endpoint: `POST /api/posts/{id}/thumbnail` (multipart/form-data)
- Lưu S3 hoặc MinIO, lưu URL vào DB

### 7. Caching
- `@EnableCaching` + Redis để cache danh sách bài viết
- `@Cacheable("posts")`, `@CacheEvict` khi create/update/delete

### 8. Swagger / OpenAPI
- Tích hợp `springdoc-openapi-starter-webmvc-ui`
- Tự động sinh docs tại `/swagger-ui.html`

### 9. Search & Filter nâng cao
- Dùng Spring Data Specifications hoặc QueryDSL để filter động
- Ví dụ: `GET /api/posts?title=java&published=true&authorId=1`

### 10. WebSocket / Notifications
- Thông báo realtime khi có bài viết mới (Spring WebSocket + STOMP)
- Tích hợp với event `ApplicationEventPublisher`

### 11. Docker & Docker Compose
```yaml
# docker-compose.yml
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      - DB_URL=jdbc:postgresql://db:5432/starter_db
      - JWT_SECRET=${JWT_SECRET}
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: starter_db
```

### 12. CI/CD
- GitHub Actions workflow: build → test → Docker build → deploy
- Đảm bảo `JWT_SECRET` được set trong GitHub Secrets

---

## Quy tắc code (Coding Guidelines)

### Bắt buộc

- **KHÔNG hardcode secret/password** trong code. Luôn dùng `${ENV_VAR}` trong `application.yml`
- **KHÔNG trả về entity trực tiếp** từ controller — luôn dùng DTO/Response class
- **KHÔNG để `ddl-auto: create` hoặc `update` trên production** — dùng Flyway migration
- **Mọi endpoint cần auth phải có test** với `@WithMockUser` hoặc JWT token giả
- **Password phải hash bằng BCrypt** — không dùng MD5, SHA-1, plain text

### Naming Conventions

| Thành phần | Convention | Ví dụ |
|-----------|------------|-------|
| Entity | PascalCase | `Post`, `User` |
| Repository | `{Entity}Repository` | `PostRepository` |
| Service | `{Entity}Service` | `PostService` |
| Controller (REST) | `{Entity}Controller` | `PostController` |
| Resolver (GraphQL) | `{Entity}Resolver` | `PostResolver` |
| DTO Request | `{Entity}Request` | `PostRequest` |
| DTO Response | `{Entity}Response` | `PostResponse` |
| DB table | snake_case | `posts`, `refresh_tokens` |
| Flyway migration | `V{num}__{desc}.sql` | `V1__create_users_table.sql` |

### Error Handling

Dùng `GlobalExceptionHandler` (`@RestControllerAdvice`) để xử lý tập trung:

```json
// Response format lỗi chuẩn
{
  "success": false,
  "message": "Post not found with id: 42",
  "errorCode": "RESOURCE_NOT_FOUND",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

| Exception | HTTP Status |
|-----------|-------------|
| `ResourceNotFoundException` | 404 |
| `UnauthorizedException` | 401 |
| `AccessDeniedException` | 403 |
| `MethodArgumentNotValidException` | 400 |
| `Exception` (catch-all) | 500 |

---

## Database Migrations (Flyway)

Tất cả thay đổi schema phải qua Flyway migration file trong `resources/db/migration/`:

```
V1__create_users_table.sql
V2__create_posts_table.sql
V3__create_refresh_tokens_table.sql
V4__add_post_published_column.sql
```

**Quy tắc:** Không chỉnh sửa migration đã chạy trên prod. Tạo migration mới để alter.

---

## Testing

### Unit Test

```java
@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock PostRepository postRepository;
    @InjectMocks PostService postService;
    // ...
}
```

### Integration Test (Auth)

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Test
    void login_withValidCredentials_returnsTokens() { ... }

    @Test
    void login_withInvalidPassword_returns401() { ... }
}
```

### GraphQL Test

```java
@SpringBootTest
@AutoConfigureHttpGraphQlTester
class PostResolverTest {
    @Autowired HttpGraphQlTester graphQlTester;

    @Test
    void posts_returnsPagedResult() {
        graphQlTester.document("{ posts { content { id title } totalElements } }")
            .execute()
            .path("posts.totalElements")
            .entity(Integer.class)
            .isGreaterThan(0);
    }
}
```

### Chạy tests

```bash
# Tất cả tests
./mvnw test

# Test cụ thể
./mvnw test -Dtest=PostServiceTest

# Với coverage report
./mvnw test jacoco:report
```

---

## Khởi chạy dự án

```bash
# 1. Clone và cấu hình
cp application-dev.yml.example application-dev.yml
# Điền DB credentials và JWT_SECRET

# 2. Khởi động PostgreSQL (dùng Docker)
docker run -d --name starter-db \
  -e POSTGRES_DB=starter_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 postgres:16

# 3. Chạy ứng dụng
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Kiểm tra
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

---

## Security Checklist (trước khi deploy production)

- [ ] `JWT_SECRET` đủ mạnh (>=256 bit, ngẫu nhiên), set qua env var
- [ ] Access token ngắn hạn (15 phút khuyến nghị)
- [ ] Refresh token lưu DB, có thể revoke
- [ ] HTTPS bắt buộc — không chấp nhận HTTP
- [ ] CORS chỉ allow origin cụ thể, không dùng `*`
- [ ] Rate limiting trên `/api/auth/login`
- [ ] `graphiql.enabled: false` trên production
- [ ] `actuator` endpoints được bảo vệ (`/actuator/**` chỉ cho ADMIN)
- [ ] Không log password hoặc JWT token
- [ ] Dependency scan (`./mvnw dependency-check:check`)

---

*Tài liệu này dành cho AI assistant và developer. Cập nhật khi có thay đổi kiến trúc lớn.*
