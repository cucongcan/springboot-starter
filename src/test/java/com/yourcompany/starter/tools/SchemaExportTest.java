package com.yourcompany.starter.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy để generate DDL từ JPA entities sang file SQL.
 *
 * Lệnh Docker:
 *   docker run --rm -v $(pwd):/app -w /app maven:3.9.6-eclipse-temurin-17 \
 *     mvn test -Dtest=SchemaExportTest -B
 *
 * Output: target/generated-schema.sql
 *
 * Dùng cho: tạo Flyway migration mới khi thêm entity.
 * KHÔNG dùng để generate ALTER TABLE — phần đó viết tay.
 */
@SpringBootTest
@ActiveProfiles("ddl")
class SchemaExportTest {

    @Test
    void generateSchema() throws Exception {
        Path output = Path.of("target/generated-schema.sql");
        assertThat(output).exists();

        String sql = Files.readString(output);
        assertThat(sql).isNotBlank();

        System.out.println("\n========================================");
        System.out.println("Schema generated: " + output.toAbsolutePath());
        System.out.println("Nội dung:");
        System.out.println("----------------------------------------");
        System.out.println(sql);
        System.out.println("========================================\n");
        System.out.println("Bước tiếp theo:");
        System.out.println("  1. Review SQL ở target/generated-schema.sql");
        System.out.println("  2. Điều chỉnh nếu cần (BIGSERIAL, TEXT, indexes...)");
        System.out.println("  3. Copy vào src/main/resources/db/migration/V{n}__....sql");
    }
}
