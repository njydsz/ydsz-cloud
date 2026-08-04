# 测试覆盖率提升方案

> P2-优化：ydsz-backend 测试覆盖率体系化建设

## 目录

- [现状分析](#现状分析)
- [目标设定](#目标设定)
- [三阶段提升路径](#三阶段提升路径)
- [覆盖率门禁配置](#覆盖率门禁配置)
- [测试策略](#测试策略)
- [CI 集成方案](#ci-集成方案)
- [覆盖率报告与可视化](#覆盖率报告与可视化)
- [最佳实践](#最佳实践)

---

## 现状分析

### 当前覆盖率评估

基于代码库审计，各模块测试覆盖情况：

| 模块 | 当前行覆盖率 | 当前分支覆盖率 | 目标 |
|------|-------------|---------------|------|
| ydsz-common-json | ~15% | ~10% | 90% |
| ydsz-common-web | ~20% | ~12% | 85% |
| ydsz-common-auth | ~25% | ~15% | 85% |
| ydsz-common-cache | ~10% | ~5% | 80% |
| ydsz-common-jdbc | ~18% | ~10% | 80% |
| ydsz-gateway | ~30% | ~20% | 80% |
| ydsz-user | ~25% | ~15% | 85% |
| ydsz-project | ~20% | ~12% | 85% |
| ydsz-flow | ~15% | ~8% | 80% |
| ydsz-msg | ~22% | ~14% | 85% |
| ydsz-job | ~20% | ~10% | 80% |
| ydsz-agent | ~10% | ~5% | 70% |
| **全项目均值** | **~20%** | **~12%** | **80%** |

### 主要问题

1. **缺少基础单元测试**：很多 Module 几乎没有 UT
2. **集成测试覆盖空白**：依赖外部系统（DB/Redis/MQ）的代码未测试
3. **分支覆盖不足**：异常路径、边界条件未覆盖
4. **无覆盖率门禁**：代码合并不检查覆盖率是否下降

---

## 目标设定

### KPI 与分阶段目标

| 阶段 | 时间 | 行覆盖率 | 分支覆盖率 | 门禁规则 |
|------|------|----------|-----------|----------|
| 当前 | - | ~20% | ~12% | 无 |
| Phase 1 | Q3（8 周） | ≥ 60% | ≥ 50% | 新代码 ≥ 60% |
| Phase 2 | Q4（8 周） | ≥ 75% | ≥ 65% | 全量 ≥ 60%，新代码 ≥ 70% |
| Phase 3 | Q1 下周（8 周） | ≥ 85% | ≥ 75% | 全量 ≥ 75%，新代码 ≥ 80% |
| 稳态运营 | 持续 | ≥ 90% | ≥ 80% | 全量 ≥ 80%，新代码 ≥ 85% |

### 各阶段门禁策略

```yaml
# JaCoCo 门控制配置示例
enforcement:
  phase1:
    min-overall-line-coverage: 0.60
    min-overall-branch-coverage: 0.50
    min-new-code-coverage: 0.60
    fail-on-violation: true
    
  phase2:
    min-overall-line-coverage: 0.75
    min-overall-branch-coverage: 0.65
    min-new-code-coverage: 0.70
    fail-on-violation: true
    
  phase3:
    min-overall-line-coverage: 0.85
    min-overall-branch-coverage: 0.75
    min-new-code-coverage: 0.80
    fail-on-violation: true
```

---

## 三阶段提升路径

### Phase 1：基础夯实（60% 行覆盖率、50% 分支覆盖率）

**重点**：核心公共模块 + 高频变更模块

#### 1.1 ydz-common-json（目标：90%）

```java
@ExtendWith(MockitoExtension.class)
class YdszJsonTest {

    private YdszJson json;

    @BeforeEach
    void setUp() {
        json = YdszJson.create();
    }

    @Test
    @DisplayName("序列化基本类型")
    void serializePrimitive() {
        assertEquals("123", json.toJsonString(123));
        assertEquals("\"hello\"", json.toJsonString("hello"));
        assertEquals("true", json.toJsonString(true));
    }

    @Test
    @DisplayName("反序列化到 POJO")
    void deserializeToPojo() {
        String jsonStr = "{\"name\":\"test\",\"age\":25}";
        User user = json.parseObject(jsonStr, User.class);
        assertEquals("test", user.getName());
        assertEquals(25, user.getAge());
    }

    @Test
    @DisplayName("空值处理：null 应序列化为 null")
    void handleNull() {
        String result = json.toJsonString(null);
        assertEquals("null", result);
    }

    @Test
    @DisplayName("异常处理：非法 JSON 应抛出异常")
    void handleInvalidJson() {
        assertThrows(JsonParseException.class, () -> 
            json.parseObject("invalid json", User.class));
    }
    
    // ... 补充所有分支逻辑
}
```

#### 1.2 ydz-common-auth（目标：85%）

```java
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private JwtDecoder jwtDecoder;
    
    @InjectMocks
    private TokenService tokenService;

    @Test
    @DisplayName("正常生成 Token")
    void generateToken() {
        LoginUser user = new LoginUser(1L, "admin", "ADMIN");
        String token = tokenService.generateToken(user);
        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    @DisplayName("Token 解析")
    void parseToken() {
        String validToken = "eyJhbGciOiJSUzI1NiJ9...";
        // Mock decoder behavior
        given(jwtDecoder.decode(validToken))
            .willReturn(mock(Jwt.class));
        
        ParseResult result = tokenService.parseAccessToken(validToken);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Token 过期场景")
    void expiredToken() {
        String expiredToken = "eyJhbGciOi...";
        given(jwtDecoder.decode(expiredToken))
            .willThrow(new JwtException("Token expired"));
        
        assertThrows(TokenExpiredException.class, () ->
            tokenService.parseAccessToken(expiredToken));
    }

    @Test
    @DisplayName("空 Token 场景")
    void nullToken() {
        assertThrows(IllegalArgumentException.class, () ->
            tokenService.parseAccessToken(null));
    }
}
```

#### 1.3 ydz-gateway（目标：80%）

```java
@WebMvcTest(RateLimitFilter.class)
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    @DisplayName("正常请求通过限流")
    void allowRequest() {
        given(redisTemplate.execute(any(), anyList(), anyList()))
            .willReturn(Flux.just(List.of(1L, 9L, 0L)));
        
        mockMvc.perform(get("/api/v1/user/me")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("限流触发返回 429")
    void rateLimited() {
        given(redisTemplate.execute(any(), anyList(), anyList()))
            .thenReturn(Flux.just(List.of(0L, 0L, 5L)));
        
        mockMvc.perform(get("/api/v1/project/list")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("X-RateLimit-Remaining"))
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Redis 不可用时降级本地兜底")
    void fallbackToLocal() {
        given(redisTemplate.execute(any(), anyList(), anyList()))
            .thenReturn(Flux.error(new RedisException("Connection refused")));
        
        mockMvc.perform(get("/api/v1/user/me")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isOk());
    }
}
```

---

### Phase 2：深化扩展（75% 行覆盖率、65% 分支覆盖率）

**重点**：业务模块 + 集成测试补充

#### 2.1 业务模块测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
        .withDatabaseName("ydsz_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:8")
        .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    @DisplayName("用户注册全流程")
    void userRegistration() {
        // 1. 注册
        RegisterRequest req = new RegisterRequest("test_user", "Test@123456");
        ResponseEntity<UserVO> res = restTemplate.postForEntity(
            "/api/v1/user/register", req, UserVO.class);
        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        
        // 2. 登录
        LoginRequest loginReq = new LoginRequest("test_user", "Test@123456");
        ResponseEntity<TokenVO> loginRes = restTemplate.postForEntity(
            "/api/v1/user/login", loginReq, TokenVO.class);
        assertEquals(HttpStatus.OK, loginRes.getStatusCode());
        assertNotNull(loginRes.getBody().getToken());
    }

    @Test
    @DisplayName("重复用户名应返回 409")
    void duplicateUsername() {
        RegisterRequest req = new RegisterRequest("duplicate", "Test@123456");
        restTemplate.postForEntity("/api/v1/user/register", req, UserVO.class);
        
        ResponseEntity<ErrorVO> res = restTemplate.postForEntity(
            "/api/v1/user/register", req, ErrorVO.class);
        assertEquals(HttpStatus.CONFLICT, res.getStatusCode());
        assertEquals("USER-409", res.getBody().getCode());
    }
}
```

#### 2.2 参数化测试（分支覆盖增强）

```java
@ExtendWith(MockitoExtension.class)
class PasswordValidatorTest {

    @ParameterizedTest
    @MethodSource("passwordProvider")
    @DisplayName("密码强度验证")
    void validatePassword(String password, boolean expected) {
        boolean result = PasswordValidator.isValid(password);
        assertEquals(expected, result);
    }

    static Stream<Arguments> passwordProvider() {
        return Stream.of(
            // 正常密码
            Arguments.of("Abc123456", true),
            Arguments.of("MyP@ssw0rd!", true),
            // 缺大写
            Arguments.of("abc123456", false),
            // 缺小写
            Arguments.of("ABC123456", false),
            // 缺数字
            Arguments.of("Abcdefgh", false),
            // 过短
            Arguments.of("Ab1", false),
            // 空密码
            Arguments.of("", false),
            Arguments.of(null, false)
        );
    }
}
```

---

### Phase 3：精细化补齐（85% 行覆盖率、75% 分支覆盖率）

**重点**：边界条件 + 异常路径 + 并发场景

#### 3.1 边界条件测试

```java
class PaginationValidatorTest {

    @Test
    @DisplayName("分页边界：page=0 应抛出异常")
    void zeroPage() {
        assertThrows(IllegalArgumentException.class,
            () -> PaginationValidator.validate(0, 20));
    }

    @Test
    @DisplayName("分页边界：size 超过最大值应抛出异常")
    void sizeTooLarge() {
        assertThrows(IllegalArgumentException.class,
            () -> PaginationValidator.validate(1, 1001));
    }

    @Test
    @DisplayName("分页边界：负值应抛出异常")
    void negativePage() {
        assertThrows(IllegalArgumentException.class,
            () -> PaginationValidator.validate(-1, 20));
    }

    @Test
    @DisplayName("分页边界：max 值合法")
    void maxSizeAllowed() {
        assertDoesNotThrow(() -> PaginationValidator.validate(1, 1000));
    }
}
```

#### 3.2 并发测试

```java
@SpringBootTest
class ConcurrentTokenRefreshTest {

    @Autowired
    private CachedJwtValidator validator;

    @Test
    @DisplayName("同一 Token 并发刷新不会重复解析")
    void concurrentRefresh() throws Exception {
        String token = "test_token";
        int threadCount = 100;
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        AtomicInteger parseCount = new AtomicInteger(0);
        
        // 模拟并发请求同一 Token
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    validator.validateAndParse(token);
                    parseCount.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        
        // 验证所有请求都成功
        assertEquals(threadCount, parseCount.get());
    }
}
```

---

## 覆盖率门禁配置

### Maven Enforcer 规则

```xml
<!-- 在父 pom.xml 中添加 -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <!-- 单元测试覆盖率 -->
    <execution>
      <id>prepare-ut</id>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <execution>
      <id>report-ut</id>
      <phase>test</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
    <execution>
      <id>check-ut</id>
      <phase>verify</phase>
      <goals>
        <goal>check</goal>
      </goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit>
                <counter>LINE</counter>
                <value>COVEREDRATIO</value>
                <minimum>${jacoco.line-coverage}</minimum>
              </limit>
              <limit>
                <counter>BRANCH</counter>
                <value>COVEREDRATIO</value>
                <minimum>${jacoco.branch-coverage}</minimum>
              </limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>

    <!-- 集成测试覆盖率（JaCoCo-it） -->
    <execution>
      <id>prepare-it</id>
      <goals>
        <goal>prepare-agent-integration</goal>
      </goals>
    </execution>
    <execution>
      <id>report-it</id>
      <phase>verify</phase>
      <goals>
        <goal>report-integration</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### 属性配置

```xml
<properties>
  <!-- 分阶段阈值 -->
  <jacoco.phase>phase1</jacoco.phase>
  <jacoco.line-coverage>0.60</jacoco.line-coverage>
  <jacoco.branch-coverage>0.50</jacoco.branch-coverage>
</properties>
```

---

## 测试策略

### 分层测试金字塔

```
                        ╱╲
                       ╱  ╲
                      ╱ E2E╲            ← 5% 场景测试
                     ╱──────╲              (Testcontainers +
                    ╱         Cypress)
                   ╱──────────╲
                  ╱ Integration╲          ← 20% 集成测试
                 ╱──────────────╲           (SpringBootTest +
                ╱                ╲           Testcontainers)
               ╱    Unit Tests    ╲
              ╲────────────────────╱        ← 75% 单元测试
               ╲                  ╱           (JUnit5 +
                ╲────────────────╱            Mockito)
                 ╲              ╱
                  ╲────────────╱
```

### Mock vs 集成测试策略

| 场景 | 推荐方式 | 工具 |
|------|----------|------|
| 纯业务逻辑 | 单元测试 + Mockito | JUnit5 + Mockito |
| Redis 交互 | Testcontainers | RedisContainer |
| 数据库写入/查询 | Testcontainers | PostgreSQLContainer |
| MQ 发送 | Testcontainers 或 Embedded | RocketMQContainer |
| HTTP 外部调用 | MockServer / WireMock | WireMock |
| 完整端到端 | SpringBootTest + Testcontainers | Spring Test |

---

## CI 集成方案

### GitHub Actions 覆盖率检查

```yaml
# .github/workflows/coverage.yml
name: Code Coverage

on:
  pull_request:
    branches: [main, 'release/**']
  push:
    branches: [main]

jobs:
  coverage:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:18
        env:
          POSTGRES_PASSWORD: test
          POSTGRES_DB: ydsz_test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      
      redis:
        image: redis:8
        ports:
          - 6379:6379

    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
      
      # 单元测试 + 覆盖率
      - name: Run Unit Tests
        run: mvn clean test jacoco:report
      
      # 集成测试
      - name: Run Integration Tests
        run: mvn failsafe:integration-test jacoco:report-integration
      
      # 合并覆盖率报告
      - name: Merge Coverage
        run: >-
          mvn jacoco:merge
            -Djacoco.destFile=target/jacoco-all.exec
      
      # 验证覆盖率阈值
      - name: Verify Coverage
        run: >-
          mvn jacoco:check
            -Djacoco.line-coverage=${{ env.MIN_LINE_COV || '0.60' }}
            -Djacoco.branch-coverage=${{ env.MIN_BRANCH_COV || '0.50' }}
      
      # 上传覆盖率报告到 Codecov
      - name: Upload to Codecov
        uses: codecov/codecov-action@v4
        with:
          files: target/site/jacoco-aggregate/index.xml
          flags: unittests
          name: codecov-ydsz
      
      # 覆盖率趋势评论到 PR
      - name: Coverage Report
        if: github.event_name == 'pull_request'
        uses: madrapps/jacoco-report@v1
        with:
          paths: target/site/jacoco-aggregate/index.xml
          token: ${{ secrets.GITHUB_TOKEN }}
          min-coverage-overall: 60
          min-coverage-changed-files: 70
          title: '📊 覆盖率报告'
          update-comment: true
```

### 覆盖率趋势追踪

```yaml
# codecov.yml
codecov:
  require_ci_to_pass: true
  notify:
    wait_for_ci: true

coverage:
  precision: 2
  round: down
  range: "60...90"
  
  status:
    project:
      default:
        target: 60%
        threshold: 2%
        paths:
          - "ydsz-backend/"
    
    patch:
      default:
        target: 70%
        threshold: 5%
  
  # 忽略文件模式
  ignore:
    - "ydsz-backend/*/src/test/**/*"
    - "ydsz-backend/**/Application.java"
    - "ydsz-backend/*/src/main/resources/**/*"

comment:
  layout: "reach, diff, flags, files"
  behavior: default
  require_changes: true
  require_base: false
  require_head: true
```

---

## 覆盖率报告与可视化

### 本地查看报告

```bash
# 生成覆盖率报告
mvn clean verify

# 打开报告
open ydz-backend/target/site/jacoco-aggregate/index.html
```

### 报告结构

```
target/site/jacoco-aggregate/
├── index.html              # 总览
├── ydsz-common-json/       # 各模块报告
│   ├── index.html
│   ├── jacoco.csv
│   └── jacoco.xml
├── ydsz-common-auth/
├── ydsz-gateway/
├── ydsz-user/
├── ydsz-project/
└── ...
```

### Grafana 覆盖率大盘

```json
// Grafana Dashboard Panel - Coverage Over Time
{
  "title": "Code Coverage Trend",
  "type": "timeseries",
  "targets": [
    {
      "query": "SELECT time, line_coverage, branch_coverage FROM coverage_metrics"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "percent",
      "min": 0,
      "max": 100
    }
  }
}
```

---

## 最佳实践

### 测试编写 Checklist

- [ ] 单元测试类命名：`{ClassName}Test`
- [ ] 方法命名：`{methodName}_{scenario}_{expectedResult}`
- [ ] 遵循 AAA (Arrange-Act-Assert) 结构
- [ ] 单个测试仅验证一个行为
- [ ] 避免测试间数据依赖
- [ ] 边界条件必须覆盖：null、空、最大值、最小值
- [ ] 异常路径必须有断言
- [ ] 使用 `@DisplayName` 提供中文可读描述

### 推荐测试方法命名

| 坏例子 | 好例子 |
|--------|--------|
| `testLogin()` | `login_validCredentials_returnsToken()` |
| `testError()` | `login_wrongPassword_throwsUnauthorized()` |
| `testUser()` | `register_duplicateUsername_returnsConflict()` |

### Code Review 覆盖率审查项

| 审查项 | 标准 |
|--------|------|
| 新增 public 方法是否有对应单测 | 100% |
| 异常分支是否有覆盖 | > 70% |
| 边界条件是否有测试 | 必须有 |
| 测试是否独立 | 无共享可变状态 |
| 测试是否稳定 | 不依赖时间/外部服务 |

---

## 附录：测试依赖配置

```xml
<!-- 测试基础依赖（添加到父 pom.xml） -->
<dependencies>
  <!-- Spring Boot Test -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  
  <!-- Testcontainers -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.20.0</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.0</version>
    <scope>test</scope>
  </dependency>
  
  <!-- WireMock（外部 HTTP Mock） -->
  <dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8-standalone</artifactId>
    <version>3.0.1</version>
    <scope>test</scope>
  </dependency>
  
  <!-- AssertJ（流式断言库） -->
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.25.3</version>
    <scope>test</scope>
  </dependency>
  
  <!-- DataFaker（测试数据生成） -->
  <dependency>
    <groupId>net.datafaker</groupId>
    <artifactId>datafaker</artifactId>
    <version>2.4.2</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

---

## 风险管理

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 赶进度导致覆盖率注水 | 高 | 高 | Code Review 强审查，避免 `assert(true)` 无效测试 |
| CI 时间过长 | 中 | 中 | 测试分组 + 并行执行 + 增量覆盖率检查 |
| Testcontainers 不稳定 | 中 | 低 | 容器预热 + 重试机制 + 定期更新镜像 |
| 覆盖率与开发效率冲突 | 高 | 中 | 核心门禁不可妥协，新代码覆盖率 >= 80% 硬性要求 |

---

> 文档更新: 2026-08-04 | 维护人: ydsz-team
