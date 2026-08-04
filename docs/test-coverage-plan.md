# 测试覆盖率提升方案

## 目标

| 阶段 | 行覆盖率 | 分支覆盖率 | 目标时间 |
|------|---------|-----------|---------|
| **当前** | 60% | 50% | - |
| **Phase 1** | 75% | 65% | 1 个月 |
| **Phase 2** | 85% | 75% | 2 个月 |
| **Phase 3** | 90% | 80% | 3 个月 |

## Phase 1: 补齐核心 Service 层单测（目标 75%）

### 优先级排序

1. **ydsz-project-server**（核心业务）
   - ProjectServiceTest
   - ContractServiceTest
   - EvmCalculationServiceTest

2. **ydsz-workflow-server**
   - FlowInstanceServiceTest（流程生命周期）
   - FlowTaskServiceTest（审批任务）

3. **ydsz-literule-server**
   - RuleEngineTest（核心引擎）
   - ExpressionRuleTest

4. **ydsz-cronjob-server**
   - JobDispatchServiceTest
   - DagExecutorTest

### 单测编写规范

```java
@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private Cache<String, ProjectVO> projectCache;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Test
    @DisplayName("创建项目成功时返回完整 VO")
    void createProject_Success() {
        // Arrange
        ProjectCreateDTO dto = new ProjectCreateDTO();
        dto.setName("测试项目");
        dto.setBudget(new BigDecimal("100000"));
        when(projectMapper.insert(any())).thenReturn(1);

        // Act
        ProjectVO result = projectService.createProject(dto);

        // Assert
        assertAll(
            () -> assertNotNull(result),
            () -> assertEquals("测试项目", result.getName()),
            () -> assertNotNull(result.getProjectCode())
        );
        verify(projectCache, times(1)).invalidate(any());
    }

    @Test
    @DisplayName("创建项目时预算为负抛出异常")
    void createProject_NegativeBudget_ThrowsException() {
        ProjectCreateDTO dto = new ProjectCreateDTO();
        dto.setName("测试项目");
        dto.setBudget(new BigDecimal("-100"));

        assertThrows(BizException.class, () -> projectService.createProject(dto));
        verify(projectMapper, never()).insert(any());
    }
}
```

## Phase 2: Controller 层集成测试（目标 85%）

### 使用 @WebMvcTest

```java
@WebMvcTest(ProjectController.class)
@ActiveProfiles("test")
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projectService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/projects/{id} 返回项目详情")
    void getProject_ReturnsDetail() throws Exception {
        Long projectId = 1L;
        ProjectVO mockResult = new ProjectVO();
        mockResult.setId(projectId);
        mockResult.setName("测试项目");

        given(projectService.getProject(projectId)).willReturn(mockResult);

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                .header("Authorization", "Bearer mock-token")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("测试项目"));
    }
}
```

### 使用 Testcontainers 集成测试

```java
@SpringBootTest
@Testcontainers
class IntegrationTestConfig {

    @Container
    static GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse("pgvector/pgvector:pg18"))
        .withDatabaseName("ydsz_test")
        .withUsername("test")
        .withPassword("test")
        .withExposedPorts(5432);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            String.format("jdbc:postgresql://localhost:%d/ydsz_test",
                postgres.getMappedPort(5432)));
        registry.add("spring.data.redis.port", () ->
            redis.getMappedPort(6379));
    }
}
```

## Phase 3: 跨服务端到端测试（目标 90%）

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProjectWorkflowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullProjectLifecycle_Success() {
        // 1. 创建项目
        ResponseEntity<BaseResponse> createResp = restTemplate.postForEntity(
            "/api/v1/projects", createProjectReq, BaseResponse.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());

        Long projectId = extractId(createResp);

        // 2. 启动审批流程
        ResponseEntity<BaseResponse> approveResp = restTemplate.postForEntity(
            "/api/v1/workflow/instance/start", startWorkflowReq, BaseResponse.class);
        assertEquals(HttpStatus.OK, approveResp.getStatusCode());

        // 3. 执行审批
        ResponseEntity<BaseResponse> taskResp = restTemplate.postForEntity(
            "/api/v1/workflow/task/approve", approveTaskReq, BaseResponse.class);
        assertEquals(HttpStatus.OK, taskResp.getStatusCode());

        // 4. 验证项目状态变更
        ResponseEntity<BaseResponse> projectResp = restTemplate.getForEntity(
            "/api/v1/projects/" + projectId, BaseResponse.class);
        assertEquals("APPROVED", extractStatus(projectResp));
    }
}
```

## 覆盖率提升执行清单

- [ ] Phase 1: 补齐核心 Service 层单测
- [ ] Phase 1: 补齐 Feign Client 单元测试
- [ ] Phase 2: Controller 层 @WebMvcTest 集成测试
- [ ] Phase 2: Testcontainers 数据库/Redis 集成测试
- [ ] Phase 3: 关键业务流程端到端测试
- [ ] Phase 3: 性能基准测试用例
- [ ] 持续: CI 覆盖率门禁阻止不达标 PR
