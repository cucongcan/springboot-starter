# Spring Boot Starter Template

Backend RESTful API + GraphQL với JWT Auth, CRUD phân trang, sẵn sàng mở rộng.

| | |
|---|---|
| **Framework** | Spring Boot 3.2.x, Java 17 |
| **Database** | PostgreSQL 16 + Flyway |
| **Auth** | JWT Access Token (15 phút) + Refresh Token (7 ngày) |
| **API** | REST `/api/**` + GraphQL `/graphql` |
| **Build** | Maven (chạy trong Docker, không cần cài Java trên máy host) |

---

## Mục lục

1. [Build và chạy với Docker](#1-build-và-chạy-với-docker)
2. [Tạo Flyway Migration](#2-tạo-flyway-migration)
3. [Generate Migration SQL tự động từ Entity](#3-generate-migration-sql-tự-động-từ-entity)
4. [Implement Feature mới (REST + GraphQL)](#4-implement-feature-mới-rest--graphql)
5. [JwtAuthFilter — Cách hoạt động](#5-jwtauthfilter--cách-hoạt-động)
6. [Viết Tests](#6-viết-tests)

---

## 1. Build và chạy với Docker

Toàn bộ quá trình build/run đều trong container — **không cần cài Java hay Maven trên máy host**.

### Yêu cầu

- Docker Desktop (hoặc Docker Engine + Compose plugin)

### Chạy môi trường Dev (hot-reload)

```bash
# Khởi động PostgreSQL + Maven container (bind-mount source code)
docker compose -f docker-compose.dev.yml up
```

Maven container mount trực tiếp thư mục source, chạy `spring-boot:run`. Mọi thay đổi file `.java` → restart tự động nếu dùng Spring DevTools (thêm dependency nếu cần).

Xem log:
```bash
docker compose -f docker-compose.dev.yml logs -f maven
```

Dừng:
```bash
docker compose -f docker-compose.dev.yml down
```

### Build và chạy Production

```bash
# Build image + khởi động app + DB
docker compose up --build

# Chạy nền
docker compose up --build -d
```

Quá trình build production (multi-stage):
1. **Stage 1** — `maven:3.9.6-eclipse-temurin-17`: `mvn package -DskipTests`
2. **Stage 2** — `eclipse-temurin:17-jre-jammy`: copy `.jar` → image nhỏ (~145MB)

### Chỉ chạy tests (không cần DB)

Tests dùng H2 in-memory, không cần PostgreSQL:

```bash
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  maven:3.9.6-eclipse-temurin-17 \
  mvn test -B
```

### Biến môi trường

| Biến | Mặc định | Mô tả |
|------|----------|--------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/starter_db` | JDBC URL |
| `DB_USER` | `postgres` | DB username |
| `DB_PASS` | `password` | DB password |
| `JWT_SECRET` | *(bắt buộc)* | Base64-encoded key, tối thiểu 256 bit |
| `CORS_ORIGINS` | `http://localhost:3000` | Allowed origins (comma-separated) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Profile: `dev` hoặc `prod` |

Tạo `JWT_SECRET` đủ mạnh:
```bash
# Tạo secret 256-bit ngẫu nhiên
openssl rand -base64 32
```

### Kiểm tra app đang chạy

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

GraphiQL playground (chỉ profile `dev`): http://localhost:8080/graphiql

---

## 2. Tạo Flyway Migration

Flyway chạy tự động khi app khởi động, theo thứ tự version. File migration **không được sửa sau khi đã chạy trên bất kỳ môi trường nào** — tạo file mới để alter.

### Quy tắc đặt tên

```
V{số}__{mô_tả}.sql
```

Ví dụ:
```
V1__create_users_table.sql
V4__add_post_thumbnail_column.sql
V5__create_comments_table.sql
```

Vị trí: `src/main/resources/db/migration/`

### Ví dụ: Thêm cột mới vào bảng có sẵn

```sql
-- V4__add_post_thumbnail_column.sql
ALTER TABLE posts ADD COLUMN thumbnail_url VARCHAR(500);
```

### Ví dụ: Tạo bảng mới (xem [phần 3](#3-implement-feature-mới-rest--graphql))

```sql
-- V5__create_comments_table.sql
CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT         NOT NULL,
    post_id    BIGINT       NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id  BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comments_post_id   ON comments (post_id);
CREATE INDEX idx_comments_author_id ON comments (author_id);
```

### Kiểm tra migration đã chạy

```sql
-- Xem lịch sử migration
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Lỗi thường gặp

| Lỗi | Nguyên nhân | Xử lý |
|-----|-------------|--------|
| `Found more than one migration with version X` | Trùng số version | Đổi số version |
| `Validate failed: Migration checksum mismatch` | Sửa file đã chạy | Tạo file mới để alter, **không sửa file cũ** |
| `Table 'flyway_schema_history' doesn't exist` | DB chưa được init | Flyway tự tạo khi chạy lần đầu |

---

## 3. Generate Migration SQL tự động từ Entity

> **Giới hạn quan trọng:** Chỉ generate được `CREATE TABLE` (entity mới).
> `ALTER TABLE` cho incremental change (thêm cột, đổi kiểu dữ liệu) **vẫn phải viết tay**.

### Cách hoạt động

Khi JPA context khởi động với profile `ddl`, Hibernate đọc tất cả `@Entity` và ghi DDL ra file `target/generated-schema.sql` — không cần kết nối PostgreSQL thật (dùng H2 mode=PostgreSQL).

```
@Entity classes  →  Hibernate  →  target/generated-schema.sql
(User, Post, ...) (PostgreSQL dialect)  (review → Flyway migration)
```

### Chạy generate

```bash
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  maven:3.9.6-eclipse-temurin-17 \
  mvn test -Dtest=SchemaExportTest -B
```

Output (ví dụ từ project này):

```sql
create table posts (published boolean not null, author_id bigint not null,
  created_at timestamp(6) with time zone, id bigserial not null,
  updated_at timestamp(6) with time zone, content TEXT not null,
  title varchar(255) not null, primary key (id));

create table users (created_at timestamp(6) with time zone,
  id bigserial not null, updated_at timestamp(6) with time zone,
  username varchar(50) not null unique, email varchar(255) not null unique,
  password varchar(255) not null, primary key (id));

alter table if exists posts
  add constraint FK6xvn0811... foreign key (author_id) references users;
```

### Những điều cần review trước khi dùng làm migration

| Vấn đề | SQL generate ra | Nên sửa thành |
|--------|----------------|---------------|
| Constraint name tự sinh | `FK6xvn0811tkyo3nfjk2xvqx6ns` | `fk_posts_author_id` |
| Thiếu `ON DELETE CASCADE` | `foreign key (author_id) references users` | `references users ON DELETE CASCADE` |
| Thiếu indexes | *(không có)* | Thêm `CREATE INDEX idx_...` thủ công |
| Thứ tự cột | ngẫu nhiên | Sắp xếp lại cho dễ đọc |

### Workflow thực tế: Thêm Entity mới

```
1. Viết @Entity mới (ví dụ: Comment.java)
       ↓
2. Chạy SchemaExportTest → xem target/generated-schema.sql
       ↓
3. Lấy phần CREATE TABLE comment + ALTER TABLE liên quan
       ↓
4. Tạo file migration mới:
   src/main/resources/db/migration/V5__create_comments_table.sql
       ↓
5. Sửa: đổi tên constraint, thêm ON DELETE CASCADE, thêm indexes
       ↓
6. Chạy docker compose up → Flyway tự apply migration
```

### Workflow thực tế: Thêm cột vào Entity có sẵn

Generate **không hỗ trợ** trường hợp này. Viết tay:

```sql
-- V6__add_post_thumbnail_column.sql
ALTER TABLE posts ADD COLUMN thumbnail_url VARCHAR(500);
```

### Cách 2 (nhanh hơn, ít chính xác hơn): Capture Hibernate log

Tạm thời set `ddl-auto: create` + chạy với H2 → copy SQL từ console log:

```yaml
# application-dev.yml — tạm thời thêm, nhớ xoá sau
spring:
  jpa:
    hibernate:
      ddl-auto: create    # ← tạm thời
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

```bash
# Chạy app, tìm dòng "create table" trong log, copy ra
docker compose -f docker-compose.dev.yml up maven 2>&1 | grep -A5 "create table"
```

Nhược điểm: SQL là H2 syntax (thiếu `BIGSERIAL`, `TEXT`), cần sửa nhiều hơn. Dùng Cách 1 sẽ tốt hơn.

---

## 4. Implement Feature mới (REST + GraphQL)

Hướng dẫn thêm module `Comment` (bình luận cho bài viết) — theo đúng cấu trúc như module `Post`.

### Bước 1 — Tạo Flyway Migration

```sql
-- src/main/resources/db/migration/V5__create_comments_table.sql
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

### Bước 2 — Tạo Entity

```java
// src/main/java/com/yourcompany/starter/comment/entity/Comment.java
@Entity
@Table(name = "comments")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

> **Quy tắc:** Luôn dùng `FetchType.LAZY` cho quan hệ `@ManyToOne` và `@OneToMany` để tránh N+1 query.

### Bước 3 — Tạo Repository

```java
// src/main/java/com/yourcompany/starter/comment/repository/CommentRepository.java
public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByPostId(Long postId, Pageable pageable);

    Page<Comment> findByAuthorId(Long authorId, Pageable pageable);

    // JOIN FETCH để tránh N+1 khi cần load author
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.id = :id")
    Optional<Comment> findByIdWithAuthor(@Param("id") Long id);
}
```

### Bước 4 — Tạo DTOs

**Request:**
```java
// src/main/java/com/yourcompany/starter/comment/dto/CommentRequest.java
@Data
public class CommentRequest {

    @NotBlank
    private String content;
}
```

**Response** — dùng static factory `from()`, không trả Entity trực tiếp:
```java
// src/main/java/com/yourcompany/starter/comment/dto/CommentResponse.java
@Getter @Builder
public class CommentResponse {

    private final Long id;
    private final String content;
    private final Long postId;
    private final String authorUsername;
    private final Instant createdAt;

    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .postId(comment.getPost().getId())
                .authorUsername(comment.getAuthor().getUsername())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
```

### Bước 5 — Tạo Service

```java
// src/main/java/com/yourcompany/starter/comment/service/CommentService.java
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PagedResponse<CommentResponse> getCommentsByPost(Long postId, Pageable pageable) {
        Page<CommentResponse> page = commentRepository
                .findByPostId(postId, pageable)
                .map(CommentResponse::from);
        return PagedResponse.from(page);
    }

    public CommentResponse createComment(Long postId, CommentRequest request, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        Comment comment = Comment.builder()
                .content(request.getContent())
                .post(post)
                .author(author)
                .build();

        return CommentResponse.from(commentRepository.save(comment));
    }

    public void deleteComment(Long id, String username) {
        Comment comment = commentRepository.findByIdWithAuthor(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", id));

        // Chỉ owner hoặc ADMIN mới được xoá
        if (!comment.getAuthor().getUsername().equals(username) && !isAdmin(username)) {
            throw new UnauthorizedException("Not authorized to delete this comment");
        }
        commentRepository.delete(comment);
    }

    private boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.getRoles().contains(Role.ROLE_ADMIN))
                .orElse(false);
    }
}
```

> **Quy tắc:** Authorization check (owner/ADMIN) luôn ở **Service layer**, không phải Controller.

### Bước 6 — Tạo REST Controller

```java
// src/main/java/com/yourcompany/starter/comment/controller/CommentController.java
@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CommentResponse>>> getComments(
            @PathVariable Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(null, commentService.getCommentsByPost(postId, pageable)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        CommentResponse response = commentService.createComment(
                postId, request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Comment created", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        commentService.deleteComment(id, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Comment deleted", null));
    }
}
```

### Bước 7 — Thêm vào GraphQL Schema

```graphql
# src/main/resources/graphql/schema.graphqls — thêm vào file hiện có

type Query {
  # ... các query hiện có ...
  comments(postId: ID!, page: Int = 0, size: Int = 20): CommentConnection!
}

type Mutation {
  # ... các mutation hiện có ...
  createComment(postId: ID!, content: String!): Comment!
  deleteComment(id: ID!): Boolean!
}

type Comment {
  id: ID!
  content: String!
  postId: ID!
  authorUsername: String!
  createdAt: String
}

type CommentConnection {
  content: [Comment!]!
  totalElements: Int!
  totalPages: Int!
  pageNumber: Int!
  pageSize: Int!
  last: Boolean!
}
```

### Bước 8 — Tạo GraphQL Resolver

```java
// src/main/java/com/yourcompany/starter/comment/graphql/CommentResolver.java
@Controller
@RequiredArgsConstructor
public class CommentResolver {

    private final CommentService commentService;

    @QueryMapping
    public PagedResponse<CommentResponse> comments(
            @Argument Long postId,
            @Argument int page,
            @Argument int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        return commentService.getCommentsByPost(postId, pageable);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public CommentResponse createComment(
            @Argument Long postId,
            @Argument String content,
            @AuthenticationPrincipal UserDetails userDetails) {
        CommentRequest request = new CommentRequest();
        request.setContent(content);
        return commentService.createComment(postId, request, userDetails.getUsername());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean deleteComment(
            @Argument Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        commentService.deleteComment(id, userDetails.getUsername());
        return true;
    }
}
```

### Checklist khi thêm feature mới

```
[ ] V{n}__create_{tên}_table.sql  — migration trước tiên
[ ] entity/                        — @Entity, @EntityListeners, FetchType.LAZY
[ ] repository/                    — extends JpaRepository, @Query JOIN FETCH nếu cần
[ ] dto/                           — *Request (validation) + *Response (static from())
[ ] service/                       — @Transactional, auth check ở đây
[ ] controller/                    — @PreAuthorize, @AuthenticationPrincipal
[ ] schema.graphqls                — thêm Query/Mutation/Type mới
[ ] graphql/                       — @QueryMapping / @MutationMapping
[ ] test/                          — unit test service + integration test controller
```

---

## 5. JwtAuthFilter — Cách hoạt động

File: `src/main/java/com/yourcompany/starter/auth/filter/JwtAuthFilter.java`

### Luồng xử lý mỗi request

```
HTTP Request
     │
     ▼
JwtAuthFilter.doFilterInternal()
     │
     ├─ Không có header "Authorization: Bearer ..."?
     │        └─ filterChain.doFilter() → tiếp tục (endpoint public sẽ pass, protected sẽ bị từ chối bởi SecurityConfig)
     │
     ├─ Có Bearer token
     │        │
     │        ├─ JwtUtil.extractUsername(token) → Optional<String>
     │        │        └─ Nếu empty (token malformed) → bỏ qua, filterChain.doFilter()
     │        │
     │        ├─ SecurityContextHolder đã có auth? → bỏ qua (request đã authenticated)
     │        │
     │        ├─ UserDetailsService.loadUserByUsername(username)
     │        │
     │        ├─ JwtUtil.isTokenValid(token, userDetails)
     │        │        ├─ Kiểm tra: chữ ký hợp lệ
     │        │        ├─ Kiểm tra: chưa hết hạn
     │        │        └─ Kiểm tra: subject khớp username
     │        │
     │        └─ SecurityContextHolder.setAuthentication(authToken)
     │
     └─ filterChain.doFilter() — LUÔN gọi, dù token hợp lệ hay không
```

### Quy tắc bắt buộc

**1. Kế thừa `OncePerRequestFilter`** — đảm bảo filter chỉ chạy đúng 1 lần mỗi request, kể cả khi có forward nội bộ.

**2. Không ném exception ra ngoài filter.** Toàn bộ logic JWT được bọc trong `try-catch`:

```java
try {
    // ... JWT validation ...
} catch (Exception e) {
    log.warn("JWT authentication failed: {}", e.getMessage());
    SecurityContextHolder.clearContext(); // dọn sạch nếu validation dở dang
}
filterChain.doFilter(request, response); // luôn tiếp tục chain
```

Nếu exception bị ném ra khỏi filter, Spring sẽ trả về lỗi 500, không phải 401. Thay vào đó:
- Token invalid → không set `SecurityContextHolder`
- Request đến endpoint protected → `AuthenticationEntryPoint` trong `SecurityConfig` trả về JSON 401

**3. Luôn gọi `filterChain.doFilter()`** ở cuối — kể cả khi token invalid. Filter không được "block" request, chỉ "không authenticate" request.

### `AuthenticationEntryPoint` — xử lý 401

Khi request đến endpoint protected mà không có authentication, `SecurityConfig` trả về JSON thay vì redirect:

```java
.authenticationEntryPoint((request, response, e) -> {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    objectMapper.writeValue(response.getWriter(),
            ApiResponse.error("Unauthorized", "UNAUTHORIZED"));
})
```

Nếu không config `AuthenticationEntryPoint`, Spring Security mặc định redirect về `/login` → client nhận HTML 302, không phải JSON 401.

### Sơ đồ tương tác các component Security

```
Request
  │
  ▼
JwtAuthFilter          ← extends OncePerRequestFilter
  │  (set SecurityContextHolder nếu token hợp lệ)
  ▼
SecurityFilterChain    ← định nghĩa trong SecurityConfig
  │  (kiểm tra rule: permitAll / hasRole / authenticated)
  │
  ├─ Pass → Controller / GraphQL Resolver
  │           │
  │           └─ @PreAuthorize("isAuthenticated()")  ← method-level security
  │
  └─ Fail → AuthenticationEntryPoint (401 JSON)
           └─ AccessDeniedHandler (403 JSON)
```

---

## 6. Viết Tests

### Unit Test — Service layer

Dùng Mockito, không cần Spring context hay DB. Chạy nhanh nhất.

```java
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock PostRepository postRepository;
    @Mock UserRepository userRepository;

    @InjectMocks CommentService commentService;

    @Test
    void createComment_withValidPostAndUser_savesAndReturns() {
        // Arrange
        Post post = Post.builder().id(1L).title("T").content("C").build();
        User author = User.builder().id(1L).username("alice").build();
        Comment saved = Comment.builder()
                .id(10L).content("Nice!").post(post).author(author).build();

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenReturn(saved);

        CommentRequest request = new CommentRequest();
        request.setContent("Nice!");

        // Act
        CommentResponse response = commentService.createComment(1L, request, "alice");

        // Assert
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getContent()).isEqualTo("Nice!");
        assertThat(response.getAuthorUsername()).isEqualTo("alice");
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void deleteComment_byNonOwner_throwsUnauthorized() {
        User owner = User.builder().username("owner").roles(Set.of(Role.ROLE_USER)).build();
        User other = User.builder().username("other").roles(Set.of(Role.ROLE_USER)).build();
        Comment comment = Comment.builder().id(1L).author(owner).build();

        when(commentRepository.findByIdWithAuthor(1L)).thenReturn(Optional.of(comment));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> commentService.deleteComment(1L, "other"))
                .isInstanceOf(UnauthorizedException.class);
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void createComment_withNonexistentPost_throwsResourceNotFound() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        CommentRequest request = new CommentRequest();
        request.setContent("Test");

        assertThatThrownBy(() -> commentService.createComment(99L, request, "alice"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Post not found with id: 99");
    }
}
```

> **Nguyên tắc unit test:**
> - `when(...).thenReturn(...)` cho happy path
> - `assertThatThrownBy(...)` cho error case
> - `verify(mock, never()).method()` để xác nhận side effect KHÔNG xảy ra

### Integration Test — Controller layer

Dùng H2 in-memory (profile `test`), cần `@SpringBootTest` + `@AutoConfigureMockMvc`. Tests này verify toàn bộ stack từ HTTP → DB.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String accessToken;
    private Long postId;

    @BeforeEach
    void setUp() throws Exception {
        // Tạo user + post
        User user = userRepository.save(User.builder()
                .username("alice")
                .email("alice@example.com")
                .password(passwordEncoder.encode("Password1"))
                .roles(Set.of(Role.ROLE_USER))
                .build());

        Post post = postRepository.save(Post.builder()
                .title("Test Post")
                .content("Content")
                .published(true)
                .author(user)
                .build());
        postId = post.getId();

        // Login để lấy access token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("alice");
        loginRequest.setPassword("Password1");

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getContentAsString();

        accessToken = objectMapper.readTree(response)
                .at("/data/accessToken").asText();
    }

    @Test
    void createComment_withValidToken_returns201() throws Exception {
        CommentRequest request = new CommentRequest();
        request.setContent("Great post!");

        mockMvc.perform(post("/api/posts/{postId}/comments", postId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Great post!"))
                .andExpect(jsonPath("$.data.authorUsername").value("alice"));
    }

    @Test
    void createComment_withoutToken_returns401() throws Exception {
        CommentRequest request = new CommentRequest();
        request.setContent("Anonymous comment");

        mockMvc.perform(post("/api/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getComments_publicEndpoint_returns200() throws Exception {
        mockMvc.perform(get("/api/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
```

### Test với MockUser (không cần login thật)

Khi chỉ muốn test logic controller mà không muốn thực hiện login flow:

```java
@Test
@WithMockUser(username = "alice", roles = {"USER"})
void createComment_withMockUser_returns201() throws Exception {
    // Không cần Authorization header — @WithMockUser inject thẳng vào SecurityContext
    CommentRequest request = new CommentRequest();
    request.setContent("Mocked user comment");

    mockMvc.perform(post("/api/posts/{postId}/comments", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
}
```

### Chạy tests

```bash
# Tất cả tests trong Docker (không cần Java host)
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  maven:3.9.6-eclipse-temurin-17 \
  mvn test -B

# Chỉ một class test cụ thể
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  maven:3.9.6-eclipse-temurin-17 \
  mvn test -Dtest=PostServiceTest -B

# Test với coverage report (JaCoCo)
docker run --rm \
  -v $(pwd):/app \
  -w /app \
  maven:3.9.6-eclipse-temurin-17 \
  mvn test jacoco:report -B
# Report tại: target/site/jacoco/index.html
```

---

## Cấu trúc project

```
src/
├── main/
│   ├── java/com/yourcompany/starter/
│   │   ├── StarterApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java       ← JWT filter chain, CORS, AuthEntryPoint
│   │   │   ├── JwtConfig.java            ← @ConfigurationProperties app.jwt.*
│   │   │   └── GraphQLConfig.java
│   │   ├── auth/
│   │   │   ├── controller/AuthController.java
│   │   │   ├── dto/                      ← RegisterRequest, LoginRequest, AuthResponse
│   │   │   ├── filter/JwtAuthFilter.java ← OncePerRequestFilter
│   │   │   └── service/AuthService.java
│   │   ├── user/
│   │   │   ├── entity/User.java          ← implements UserDetails
│   │   │   ├── entity/Role.java          ← enum ROLE_USER, ROLE_ADMIN
│   │   │   ├── repository/UserRepository.java
│   │   │   └── service/UserDetailsServiceImpl.java
│   │   ├── post/
│   │   │   ├── controller/PostController.java
│   │   │   ├── graphql/PostResolver.java ← @QueryMapping, @MutationMapping
│   │   │   ├── dto/                      ← PostRequest, PostResponse
│   │   │   ├── entity/Post.java
│   │   │   ├── repository/PostRepository.java
│   │   │   └── service/PostService.java
│   │   ├── common/
│   │   │   ├── exception/
│   │   │   │   ├── GlobalExceptionHandler.java  ← @RestControllerAdvice
│   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   └── UnauthorizedException.java
│   │   │   ├── response/
│   │   │   │   ├── ApiResponse.java      ← { success, message, data, errorCode, timestamp }
│   │   │   │   └── PagedResponse.java    ← wrapper Spring Page
│   │   │   └── util/JwtUtil.java         ← generate, validate token (JJWT 0.12.x)
│   │   └── token/
│   │       ├── entity/RefreshToken.java
│   │       ├── repository/RefreshTokenRepository.java
│   │       └── service/RefreshTokenService.java
│   └── resources/
│       ├── application.yml               ← base config
│       ├── application-dev.yml           ← graphiql on, sql log on
│       ├── application-prod.yml          ← tối giản, không graphiql
│       ├── application-test.yml          ← H2 in-memory, flyway off
│       ├── graphql/schema.graphqls
│       └── db/migration/
│           ├── V1__create_users_table.sql
│           ├── V2__create_posts_table.sql
│           └── V3__create_refresh_tokens_table.sql
└── test/
    └── java/com/yourcompany/starter/
        ├── auth/AuthControllerTest.java  ← @SpringBootTest + H2
        └── post/PostServiceTest.java     ← @ExtendWith(MockitoExtension.class)
```

## REST API Endpoints

### Auth

| Method | URL | Auth | Mô tả |
|--------|-----|------|--------|
| POST | `/api/auth/register` | Public | Đăng ký |
| POST | `/api/auth/login` | Public | Đăng nhập → access + refresh token |
| POST | `/api/auth/refresh` | Public | Làm mới access token |
| POST | `/api/auth/logout` | Bearer | Revoke refresh token |

### Posts

| Method | URL | Auth | Mô tả |
|--------|-----|------|--------|
| GET | `/api/posts?page=0&size=10&sort=createdAt,desc` | Public | Danh sách (phân trang) |
| GET | `/api/posts/{id}` | Public | Chi tiết |
| GET | `/api/posts/my` | Bearer | Bài viết của tôi |
| POST | `/api/posts` | Bearer | Tạo mới |
| PUT | `/api/posts/{id}` | Bearer | Cập nhật (owner/ADMIN) |
| DELETE | `/api/posts/{id}` | Bearer | Xoá (owner/ADMIN) |
| GET | `/api/admin/posts` | ADMIN | Tất cả bài viết |

### GraphQL

Endpoint: `POST /graphql` với header `Authorization: Bearer <token>`

```graphql
# Query
query {
  posts(page: 0, size: 10) {
    content { id title authorUsername createdAt }
    totalElements totalPages
  }
}

# Mutation (cần auth)
mutation {
  createPost(title: "Hello", content: "World", published: true) {
    id title
  }
}
```
