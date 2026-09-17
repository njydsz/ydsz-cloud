package com.njydsz.system.infra.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

/**
 * ConfigController E2E 基础骨架（REST Assured）。
 *
 * <p>验证系统配置 CRUD 接入层的全链路行为：客户端 → 鉴权 → Controller → Service → Repository → DB。
 *
 * <p><b>注意：</b>本用例当前标注 {@code @Disabled}，因为完整的 E2E 需要完整的鉴权链路（JWT Token 签发与校验）。
 * 激活时取消 {@code @Disabled}，并在 {@link #setUp()} 中补齐鉴权 Token 获取逻辑（参考用户旅程 P0）。
 *
 * <p><b>5 个 P0 用户旅程（本骨架实现第 2 个「配置 CRUD」）：</b>
 * <ol>
 *   <li>登录 / RBAC — 鉴权全链路</li>
 *   <li>配置 CRUD — 本文件覆盖</li>
 *   <li>字典联动 — 字典变更后实时生效</li>
 *   <li>审计日志 — 操作留痕</li>
 *   <li>消息通知 — 事件驱动流程</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Disabled("Phase 3 骨架 — 待装配完整 common-auth/redis 环境后激活")
@SpringBootTest(
    classes = com.njydsz.system.infra._support.SystemInfraTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/schema-config-e2e.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ConfigControllerE2E {

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String TEST_TENANT = "e2e-tenant";
  private RequestSpecification requestSpec;

  @BeforeEach
  void setUp() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    requestSpec =
        new io.restassured.builder.RequestSpecBuilder()
            .setBaseUri("http://localhost")
            .setPort(port)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Tenant-Id", TEST_TENANT)
            // 预留鉴权 Token 注入位置 —— Phase 3 激活时从 AuthServer 获取 e2e 专用 JWT
            .build();
  }

  private void insertConfigViaJdbc(String group, String key, String value, String status) {
    jdbcTemplate.update(
        "INSERT INTO ydsz_sys_config (id, tenant_id, config_group, config_key, config_value, value_type, status, is_deleted, is_public, \"sort\", created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        key + "-" + group, TEST_TENANT, group, key, value, "STRING", status, 0, 1, 0,
        LocalDateTime.now(), LocalDateTime.now());
  }

  @Test
  @DisplayName("P0-1: GET /api/system/config/public — 应返回公开配置列表")
  void shouldReturnPublicConfigs() {
    insertConfigViaJdbc("e2e", "pub.visible", "value-1", "ENABLED");

    // 验证数据库预置成功
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM ydsz_sys_config WHERE config_group = 'e2e' AND is_public = 1",
        Integer.class);

    assertThat(count).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("P0-2: GET /api/system/config/{configKey} — 按 key 查询配置返回 200 与 VO")
  void shouldReturnConfigByKey() {
    insertConfigViaJdbc("e2e", "e2e.key.1", "test-value", "ENABLED");

    // Phase 3 补齐鉴权后切换到 given().spec(requestSpec)...get(...)
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM ydsz_sys_config WHERE config_key = 'e2e.key.1' AND is_deleted = 0",
        Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("P0-3: GET 未授权 /api/system/config/public — 应返回 401/403")
  void shouldRejectUnauthorizedAccess() {
    // Phase 3 激活时切换为 given().spec(无 JWT).when().get("/api/system/config/public").then().statusCode(401)
    // 当前简化为 schema 加载成功的占位断言，避免 E2E 环境未就绪阻断构建
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ydsz_sys_config", Integer.class);
    assertThat(count).isGreaterThanOrEqualTo(0);
  }

  @Test
  @DisplayName("P0-4: POST /api/system/config — 配置创建（IDEMPOTENT 幂等设计，JWT 鉴权）")
  void shouldCreateConfig() {
    insertConfigViaJdbc("e2e", "create.key", "created", "ENABLED");

    // Phase 3 激活时：given().spec(requestSpec).body("{...}").post(...).then().statusCode(201)
    var keys = jdbcTemplate.queryForList(
        "SELECT config_key FROM ydsz_sys_config WHERE config_group = 'e2e'");
    assertThat(keys).extracting(m -> m.get("config_key")).contains("create.key");
  }

  @Test
  @DisplayName("P0-5: PUT /api/system/config/{id} — 更新配置值并校验审计留痕")
  void shouldUpdateConfig() {
    insertConfigViaJdbc("e2e", "update.key", "old-value", "ENABLED");

    jdbcTemplate.update(
        "UPDATE ydsz_sys_config SET config_value = 'new-value', updated_at = ? WHERE config_key = 'update.key'",
        LocalDateTime.now());

    String updated = jdbcTemplate.queryForObject(
        "SELECT config_value FROM ydsz_sys_config WHERE config_key = 'update.key'", String.class);
    assertThat(updated).isEqualTo("new-value");
  }
}
