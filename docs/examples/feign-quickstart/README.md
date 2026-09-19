# Feign Quickstart

> YdzsFeign 模块的最小可运行示例，演示如何快速接入并使用 Feign 增强能力。

## 快速开始

### 1. 前置条件

- Java 17+
- Maven 3.9+
- Nacos 注册中心（或 Spring Cloud Config）

### 2. 导入工程

这是一个独立的 quickstart 示例工程，可以直接导入 IDE：

```bash
# 克隆仓库
git clone ...

# 或使用 IDE 打开 docs/examples/feign-quickstart/ 目录
```

### 3. 启动

```bash
cd docs/examples/feign-quickstart
mvn spring-boot:run
```

或在 IDE 中运行 `FeignQuickstartApplication`

### 4. 验证

```bash
# 查询用户（演示自动解包）
curl http://localhost:8080/demo/user/1

# 创建用户（演示返回完整 YdszResponse）
curl -X POST http://localhost:8080/demo/user \
  -H "Content-Type: application/json" \
  -d '{"name":"张三","deptId":1,"roleCode":"ADMIN"}'

# Feign 健康快照
curl http://localhost:8080/demo/health/feign
```

## 核心能力演示

本示例演示了以下 YdszFeign 特性：

| 特性 | 演示位置 | 说明 |
|------|----------|------|
| 自动解包 | `UserClient.getUser()` | 返回 UserVO，自动从 YdszResponse.data 提取 |
| 完整响应 | `UserClient.createUser()` | 返回 YdszResponse，获取 code/msg |
| 服务常量 | `FeignClientConstants.USERINFO` | 服务名常量引用 |
| 健康快照 | `DemoController.feignHealthSnapshot()` | 模块状态快照 |
| 配置外部化 | `application.yml` | 全部能力开关可控 |
| 请求头透传 | `propagation.headers` | 13 个核心业务头自动透传 |
| 链路追踪 | `trace.enabled` | W3C traceparent 自动透传 |

## 文件结构

```
feign-quickstart/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/njydsz/example/feign/
    │   ├── FeignQuickstartApplication.java    # 启动类
    │   ├── client/UserClient.java             # FeignClient 定义
    │   ├── controller/DemoController.java     # 演示控制器
    │   └── vo/
    │       ├── UserVO.java                    # 业务视图对象
    │       └── CreateUserRequest.java         # 创建用户请求
    └── resources/
        └── application.yml                    # 配置
```

## 常见问题

**Q: 为什么不需要显式使用 `@EnableYdszFeign`？**

Spring Boot 自动配置已通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 加载 `FeignConfiguration`。仅在需要精确控制配置加载顺序时才需要显式添加 `@EnableYdszFeign`。

**Q: 自动解包失败怎么办？**

检查返回类型声明：
- 想自动解包 → 声明为业务类型（如 `UserVO`）
- 想获取完整响应 → 声明为 `YdszResponse<UserVO>`
- 服务端返回类型不是 `YdszResponse` 时，使用 `JsonDecoder` fallback

**Q: 如何自定义熔断器参数？**

通过 YAML 配置即可：

```yaml
ydsz:
  feign:
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 40.0
      wait-duration-ms: 5000
```

## 下一步

- 参见 [ydsz-common-feign README](../../../common/ydsz-common-feign/README.md) 获取完整配置文档
- 参见 [优化分析报告](../../../analysis/ydsz-common-feign-optimization-report.md) 了解模块设计详情
