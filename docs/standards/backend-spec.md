# 后端工程规范

> 文档版本: V1.0 | 编制日期: 2026-06-30
> 技术栈: Spring Boot 3.3+ / Spring Cloud Alibaba 2023+ / MyBatis-Plus 3.5+ / PostgreSQL 18 / Redis 7 / 自研工作流引擎 / Nacos 2

## 1. 多模块结构

```
ydsz-pmis-backend/
├── pom.xml                          # 父 POM (dependencyManagement)
├── ydsz-pmis-common/                # 公共模块
│   ├── pom.xml
│   └── src/main/java/com/njydsz/pmis/common/
│       ├── annotation/              # 自定义注解
│       ├── api/                     # 统一响应 Result<T>
│       ├── constant/                # 公共常量
│       ├── enums/                   # 通用枚举 (BizErrorCode 等)
│       ├── exception/               # 业务异常
│       ├── util/                    # 工具类
│       └── converter/               # MapStruct 通用转换器
├── ydsz-pmis-gateway/               # API 网关
│   └── src/main/java/com/njydsz/pmis/gateway/
│       ├── filter/                  # 全局过滤器
│       ├── config/                  # 路由配置
│       └── GatewayApplication.java
├── ydsz-pmis-auth/                  # 认证授权
│   └── src/main/java/com/njydsz/pmis/auth/
│       ├── controller/
│       ├── service/
│       ├── token/                   # JWT 工具
│       └── AuthApplication.java
├── ydsz-pmis-user/                  # 用户/组织
│   ├── pom.xml
│   └── src/main/java/com/njydsz/pmis/user/
│       ├── controller/
│       ├── service/
│       ├── mapper/
│       ├── entity/
│       ├── dto/
│       ├── vo/
│       ├── convert/
│       └── UserApplication.java
├── ... (其他微服务结构同 user)
└── sql/                              # 业务 SQL 文件
    ├── user/
    └── project/
```

## 2. 包结构规范

每个微服务包结构固定：

```
com.njydsz.pmis.<module>
├── controller/         # 对外接口 (REST)
├── service/            # 业务接口
│   └── impl/           # 业务实现
├── mapper/             # MyBatis 持久化
├── entity/             # 数据库实体 (DO)
├── dto/                # 入参对象
├── vo/                 # 出参对象
├── convert/            # MapStruct 转换
├── config/             # 配置类
├── enums/              # 业务枚举
├── constants/          # 模块常量
├── listener/           # 事件监听
└── <Module>Application.java
```

## 3. 统一响应格式

```java
package com.njydsz.pmis.common.api;

import lombok.Data;
import java.io.Serializable;

/**
 * 统一响应封装
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    /** 状态码: 0=成功, 其他=失败 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 响应数据 */
    private T data;
    /** 链路追踪 ID */
    private String traceId;
    /** 服务器时间戳 */
    private long timestamp = System.currentTimeMillis();

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(0);
        r.setMessage("ok");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> failed(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> failed(BizErrorCode errorCode) {
        return failed(errorCode.getCode(), errorCode.getMessage());
    }
}
```

## 4. 异常处理

### 4.1 业务异常

```java
package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.enums.BizErrorCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final String errorMessage;

    public BizException(BizErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorMessage = errorCode.getMessage();
    }

    public BizException(BizErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.errorMessage = message;
    }
}
```

### 4.2 全局异常处理

```java
package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.enums.BizErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("业务异常 [{}] {}: {}", req.getRequestURI(), e.getCode(), e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArg(IllegalArgumentException e) {
        return Result.failed(BizErrorCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest req) {
        log.error("系统异常 [{}]", req.getRequestURI(), e);
        return Result.failed(BizErrorCode.INTERNAL_ERROR);
    }
}
```

## 5. 日志规范

### 5.1 Logger 使用

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProjectServiceImpl implements ProjectService {

    public ProjectVO getById(Long id) {
        log.debug("查询项目 id={}", id);
        ProjectDO entity = projectMapper.selectById(id);
        if (entity == null) {
            log.warn("项目不存在 id={}", id);
            throw new BizException(BizErrorCode.PROJECT_NOT_FOUND);
        }
        return ProjectConvert.INSTANCE.toVO(entity);
    }
}
```

### 5.2 日志级别

| 级别 | 场景 |
|------|------|
| ERROR | 系统异常、不可恢复错误 |
| WARN | 业务警告（参数校验失败、状态异常） |
| INFO | 关键节点（启动、停止、关键业务操作） |
| DEBUG | 详细信息（仅 dev 环境输出） |
| TRACE | 全链路追踪（生产关闭） |

### 5.3 MDC 链路追踪

通过过滤器注入 `traceId`，日志格式：

```
<ISO8601时间> <LEVEL> [<traceId>] [thread=<线程>] <Logger> - <message>
```

## 6. Controller 规范

```java
@Tag(name = "项目管理")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "创建项目")
    @PostMapping
    @PreAuthorize("hasAuthority('project:create')")
    public Result<Long> create(@Valid @RequestBody ProjectCreateDTO dto) {
        Long id = projectService.create(dto);
        return Result.ok(id);
    }

    @Operation(summary = "分页查询项目")
    @GetMapping
    public Result<PageResult<ProjectVO>> page(ProjectQuery query) {
        return Result.ok(projectService.page(query));
    }

    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    public Result<ProjectVO> getById(@PathVariable Long id) {
        return Result.ok(projectService.getById(id));
    }
}
```

## 7. Service 规范

- 接口与实现分离，接口在 `service/`，实现在 `service/impl/`
- 业务方法必须有方法级 JavaDoc
- 事务注解：`@Transactional(rollbackFor = Exception.class)`
- 只读事务：`@Transactional(readOnly = true)`
- 禁止在 Service 中直接调用 Mapper 的 `selectList` 等方法后做业务（应放在 Service 层）

## 8. 实体与 DTO/VO 转换

- DO（Entity）不直接返回给 Controller
- 使用 MapStruct 进行对象转换
- 禁止使用 Apache BeanUtils / Spring BeanUtils（性能差）

```java
@Mapper
public interface ProjectConvert {

    ProjectConvert INSTANCE = Mappers.getMapper(ProjectConvert.class);

    ProjectVO toVO(ProjectDO entity);

    ProjectDO toEntity(ProjectCreateDTO dto);

    List<ProjectVO> toVOList(List<ProjectDO> list);
}
```

## 9. 依赖管理

- 父 POM 统一管理 `dependencyManagement` 与 `pluginManagement`
- 子模块按需 `dependencies`，不指定版本
- 引入新依赖前先 review 安全性与许可证
- 锁定版本：`maven-enforcer-plugin` + `dependency-lock.json`

## 10. 配置规范

- 配置统一从 Nacos 拉取
- 敏感配置：用户名/密码/密钥 → 环境变量注入
- 本地开发使用 `application-local.yml`（被 `.gitignore` 忽略）
- 业务配置类使用 `@ConfigurationProperties` + `@EnableConfigurationProperties`

## 11. 单元测试

- 测试类命名：`XxxTest`（单元测试） / `XxxIntegrationTest`（集成测试）
- 测试方法命名：`methodName_scenario_expectedResult`
- 使用 JUnit 5 + Mockito + AssertJ
- Service 层覆盖率 ≥70%
- 关键路径 100% 覆盖

```java
@DisplayName("项目服务测试")
class ProjectServiceTest {

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Mock
    private ProjectMapper projectMapper;

    @Test
    @DisplayName("getById_项目存在_返回项目")
    void getById_projectExists_returnsProject() {
        Long id = 1L;
        ProjectDO entity = new ProjectDO();
        entity.setId(id);
        entity.setName("测试项目");
        when(projectMapper.selectById(id)).thenReturn(entity);

        ProjectVO vo = projectService.getById(id);

        assertThat(vo).isNotNull();
        assertThat(vo.getId()).isEqualTo(id);
        assertThat(vo.getName()).isEqualTo("测试项目");
    }

    @Test
    @DisplayName("getById_项目不存在_抛出业务异常")
    void getById_projectNotExists_throwsBizException() {
        when(projectMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.getById(1L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("项目不存在");
    }
}
```
