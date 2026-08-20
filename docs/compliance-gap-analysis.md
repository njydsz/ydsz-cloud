# DDD 合规扫描报告

> 扫描范围：ydsz-userinfo, ydsz-nextwiki, ydsz-message, ydsz-workflow, ydsz-cronjob, ydsz-literule, ydsz-agent
> 扫描依据：云顶编码规范第 34 节
> 扫描日期：2025 年

---

## 总览

| 模块 | 规则A | 规则B | 规则C | 规则D | 规则E | 规则F | 规则G | 规则H | 严重度 |
|------|-------|-------|-------|-------|-------|-------|--------|-------|--------|
| ydsz-cronjob | 1 | 0 | 0 | 0 | 0 | 4 | 3 | 0 | **高** |
| ydsz-message | 2 | 5 | 1 | 0 | 0 | 5 | 2 | 0 | **高** |
| ydsz-literule | 2 | 1 | 0 | 1 | 0 | 0 | 2 | 0 | **中** |
| ydsz-userinfo | 2 | 0 | 0 | 0 | 0 | 0 | 1 | 2 | **中** |
| ydsz-nextwiki | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | **低** |
| ydsz-workflow | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **合规** |
| ydsz-agent | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | **低** |

---

## ydsz-cronjob 模块

**规则A违规（domain框架依赖）**：
- 路径：`ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/repository/JobRepository.java`，行268-270：`toMybatisPage()` 方法直接使用 `com.baomidou.mybatisplus.extension.plugins.pagination.Page` 类，domain 层出现 MyBatis-Plus 框架依赖

**规则B违规（Repository返回infra实体）**：
- 无违规（Repository 返回值均为 VO 或基本类型）

**规则C违规（domain import infra entity）**：
- 无违规

**规则D违规（Converter位置/MapStruct）**：
- 无违规

**规则E违规（Controller入参/返回值）**：
- 无违规（Controller 使用 DTO 入参，VO/PageResponse 返回值）

**规则F违规（Service接口签名）**：
- 路径：`ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/job/JobService.java`，行50：`String create(Job job)` — CUD 方法入参 `Job` 是 infra 实体（`com.njydsz.cronjob.infra.entity.job.Job`），应使用 DTO
- 路径：`ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/job/JobService.java`，行60：`void update(Job job)` — CUD 方法入参 `Job` 是 infra 实体，应使用 DTO
- 路径：`ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/job/JobService.java`，行193：`Job getById(String id)` — 查询方法返回 infra 实体 `Job`，应返回 VO
- 路径：`ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/job/JobService.java`，行207：`Page<Job> page(...)` — 查询方法返回 `Page<Job>`（infra 实体分页），应返回 `PageResponse<List<JobVO>>`

**规则G违规（domain POM依赖）**：
- 路径：`ydsz-cronjob-domain/pom.xml`，行26：`ydsz-common-jdbc` — domain 层禁止引入持久化/框架依赖
- 路径：`ydsz-cronjob-domain/pom.xml`，行30：`mybatis-plus-annotation` — domain 层禁止引入 MyBatis-Plus 依赖
- 路径：`ydsz-cronjob-domain/pom.xml`，行43：`spring-expression` — domain 层禁止引入 Spring 框架依赖
- 路径：`ydsz-cronjob-domain/pom.xml`，行48：`spring-context` — domain 层禁止引入 Spring 框架依赖

**规则H违规（domain @Service）**：
- 无违规

---

## ydsz-message 模块

**规则A违规（domain框架依赖）**：
- 路径：`ydsz-message-domain/src/main/java/com/njydsz/message/domain/repository/MsgLogRepository.java`，行6：`import com.baomidou.mybatisplus.core.conditions.Wrapper;`
- 路径：`ydsz-message-domain/src/main/java/com/njydsz/message/domain/repository/MsgLogRepository.java`，行7：`import com.baomidou.mybatisplus.core.metadata.IPage;`

**规则B违规（Repository返回infra实体）**：
- 路径：`ydsz-message-domain/src/main/java/com/njydsz/message/domain/repository/MsgLogRepository.java`，方法：`MsgLog selectById(String id)`，问题：返回 `MsgLog` 是 infra 实体（位于 `infra/entity/MsgLog.java`）
- 路径：`ydsz-message-domain/src/main/java/com/njydsz/message/domain/repository/MsgLogRepository.java`，方法：`MsgLog selectOne(Wrapper<MsgLog> queryWrapper)`，问题：返回 infra 实体 `MsgLog`
- 路径：`ydsz-message-domain/src/main/java/com/njydsz/message/domain/repository/MsgLogRepository.java`，方法：`List<MsgLog> selectList(Wrapper<MsgLog> queryWrapper)`，问题：返回 infra 实体列表
- 路径：`ydsz-message-domain/src/main/java/com/njydsz/message/domain/repository/MsgLogRepository.java`，方法：`IPage<MsgLog> selectPage(IPage<MsgLog> page, Wrapper<MsgLog> queryWrapper)`，问题：返回 infra 实体分页
- 路径：`ydsz-message-domain/src/main/java/com/njydsz/message/domain/repository/MsgLogRepository.java`，方法：`int update(MsgLog entity, Wrapper<MsgLog> updateWrapper)`，问题：入参 `MsgLog` 是 infra 实体

**规则C违规（domain import infra entity）**：
- 路径：`ydsz-message-domain/src/main/java/com/njydsz/message/domain/repository/MsgLogRepository.java`，行11：`import com.njydsz.message.domain.entity.MsgLog;` — 实际类文件位于 `ydsz-message-infra/src/main/java/com/njydsz/message/infra/entity/MsgLog.java`，domain 层引用了 infra 持久化实体

**规则D违规（Converter位置/MapStruct）**：
- 无违规

**规则E违规（Controller入参/返回值）**：
- 无违规（Controller 使用 DTO 入参，VO/PageResponse 返回值）

**规则F违规（Service接口签名）**：
- 路径：`ydsz-message-server/src/main/java/com/njydsz/message/server/service/TemplateService.java`，行48：`MsgTemplate create(TemplateCreateDTO dto)` — 返回 `MsgTemplate` 是 infra 实体（`com.njydsz.message.infra.entity.MsgTemplate`），应返回 VO
- 路径：`ydsz-message-server/src/main/java/com/njydsz/message/server/service/TemplateService.java`，行59：`MsgTemplate update(String id, TemplateCreateDTO dto)` — 返回 infra 实体，应返回 VO
- 路径：`ydsz-message-server/src/main/java/com/njydsz/message/server/service/TemplateService.java`，行76：`MsgTemplate getById(String id)` — 查询方法返回 infra 实体，应返回 VO
- 路径：`ydsz-message-server/src/main/java/com/njydsz/message/server/service/TemplateService.java`，行86：`Page<MsgTemplate> page(TemplateQueryDTO query)` — 返回 `Page<MsgTemplate>`（infra 实体分页），应返回 `PageResponse<List<MsgTemplateVO>>`
- 路径：`ydsz-message-server/src/main/java/com/njydsz/message/server/service/TemplateService.java`，行100：`MsgTemplate loadByCodeAndChannel(...)` — 查询方法返回 infra 实体，应返回 VO

**规则G违规（domain POM依赖）**：
- 路径：`ydsz-message-domain/pom.xml`，行31：`ydsz-common-jdbc` — domain 层禁止引入持久化/框架依赖
- 路径：`ydsz-message-domain/pom.xml`，行39：`mybatis-plus-annotation` — domain 层禁止引入 MyBatis-Plus 依赖

**规则H违规（domain @Service）**：
- 无违规

---

## ydsz-literule 模块

**规则A违规（domain框架依赖）**：
- 路径：`ydsz-literule-domain/src/main/java/com/njydsz/literule/domain/annotation/LiteRule.java`，行9：`import org.springframework.stereotype.Component;` — domain 层引入 Spring 注解（用于元注解定义，但违反了零框架依赖原则）
- 路径：`ydsz-literule-domain/src/main/java/com/njydsz/literule/domain/repository/RuleVersionRepository.java`，行6：`import com.baomidou.mybatisplus.core.metadata.IPage;` — domain 层引入 MyBatis-Plus 接口

**规则B违规（Repository返回infra实体）**：
- 路径：`ydsz-literule-domain/src/main/java/com/njydsz/literule/domain/repository/RuleVersionRepository.java`，方法：`IPage<RuleVersionVO> pageVersions(String ruleCode, IPage<RuleVersionVO> page)`，问题：方法签名中使用了 `IPage`（MyBatis-Plus 接口）作为参数和返回值的泛型包装，MyBatis-Plus API 透传至 domain 层

**规则C违规（domain import infra entity）**：
- 无违规

**规则D违规（Converter位置/MapStruct）**：
- 描述：`LiteruleConverter` 类位于 `ydsz-literule-infra/src/main/java/com/njydsz/literule/infra/converter/LiteruleConverter.java`，位置正确（infra 层），但使用手动 `new` + getter/setter 方式实现转换，未使用 MapStruct（`@Mapper`）

**规则E违规（Controller入参/返回值）**：
- 无违规（Controller 使用 DTO/Request 入参，VO 返回值）

**规则F违规（Service接口签名）**：
- 无违规

**规则G违规（domain POM依赖）**：
- 路径：`ydsz-literule-domain/pom.xml`，行34：`ydsz-common-jdbc` — domain 层禁止引入持久化/框架依赖
- 路径：`ydsz-literule-domain/pom.xml`，行38：`mybatis-plus-annotation` — domain 层禁止引入 MyBatis-Plus 依赖

**规则H违规（domain @Service）**：
- 无违规

---

## ydsz-userinfo 模块

**规则A违规（domain框架依赖）**：
- 路径：`ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/config/SocialAuthProperties.java`，行7：`import org.springframework.boot.context.properties.ConfigurationProperties;`
- 路径：`ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/auth/UserIdentityProviderFactory.java`，行6：`import org.springframework.stereotype.Component;`

**规则B违规（Repository返回infra实体）**：
- 无违规

**规则C违规（domain import infra entity）**：
- 无违规

**规则D违规（Converter位置/MapStruct）**：
- 无违规

**规则E违规（Controller入参/返回值）**：
- 无违规

**规则F违规（Service接口签名）**：
- 无违规

**规则G违规（domain POM依赖）**：
- 路径：`ydsz-userinfo-domain/pom.xml`，行47：`spring-boot` — domain 层引入 Spring Boot 依赖（用于 `@ConfigurationProperties`）

**规则H违规（domain @Service）**：
- 路径：`ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/config/SocialAuthProperties.java`，行43：`@ConfigurationProperties(prefix = "ydsz.userinfo.social")`，问题：domain 层配置类不应使用 Spring Boot 注解
- 路径：`ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/auth/UserIdentityProviderFactory.java`，行21：`@Component`，问题：domain 层服务类不应使用 Spring 注解

---

## ydsz-nextwiki 模块

**规则A违规（domain框架依赖）**：
- 无违规

**规则B违规（Repository返回infra实体）**：
- 无违规

**规则C违规（domain import infra entity）**：
- 无违规

**规则D违规（Converter位置/MapStruct）**：
- 无违规

**规则E违规（Controller入参/返回值）**：
- 无违规

**规则F违规（Service接口签名）**：
- 无违规

**规则G违规（domain POM依赖）**：
- 路径：`ydsz-nextwiki-domain/pom.xml`，行39：`spring-security-crypto` — domain 层引入 Spring Security 依赖（用于密码加密）

**规则H违规（domain @Service）**：
- 无违规

---

## ydsz-workflow 模块

**规则A违规（domain框架依赖）**：
- 无违规

**规则B违规（Repository返回infra实体）**：
- 无违规

**规则C违规（domain import infra entity）**：
- 无违规

**规则D违规（Converter位置/MapStruct）**：
- 无违规

**规则E违规（Controller入参/返回值）**：
- 无违规

**规则F违规（Service接口签名）**：
- 无违规

**规则G违规（domain POM依赖）**：
- 无违规

**规则H违规（domain @Service）**：
- 无违规

**结论：ydsz-workflow 模块完全合规。**

---

## ydsz-agent 模块

**规则A违规（domain框架依赖）**：
- 无违规

**规则B违规（Repository返回infra实体）**：
- 无违规

**规则C违规（domain import infra entity）**：
- 无违规

**规则D违规（Converter位置/MapStruct）**：
- 无违规

**规则E违规（Controller入参/返回值）**：
- 无违规

**规则F违规（Service接口签名）**：
- 路径：`ydsz-agent-server/src/main/java/com/njydsz/agent/server/agent/AgentDefinitionService.java`，行48：`AgentDefinitionVO create(AgentDefinitionVO vo)` — CUD 方法入参使用了 VO 而非 DTO
- 路径：`ydsz-agent-server/src/main/java/com/njydsz/agent/server/agent/AgentDefinitionService.java`，行56：`AgentDefinitionVO update(AgentDefinitionVO vo)` — CUD 方法入参使用了 VO 而非 DTO

**规则G违规（domain POM依赖）**：
- 无违规

**规则H违规（domain @Service）**：
- 无违规

---

## 重点问题汇总

### 严重违规（需立即修复）

1. **ydsz-message 模块**：`MsgLogRepository` 大量使用 MyBatis-Plus API（`Wrapper`、`IPage`）和 infra 实体（`MsgLog`），严重违反 domain 层零框架依赖原则
2. **ydsz-message 模块**：`TemplateService` 接口所有查询方法返回 infra 实体 `MsgTemplate`，违反 Service 接口层规范
3. **ydsz-cronjob 模块**：`JobService` 接口 CUD 方法使用 infra 实体 `Job` 作为入参，查询方法返回 infra 实体
4. **ydsz-cronjob 模块**：`JobRepository` 的 `toMybatisPage()` 方法直接使用 MyBatis-Plus `Page` 类

### 中等违规（需规划修复）

5. **ydsz-cronjob 模块**：domain POM 引入 `mybatis-plus-annotation`、`common-jdbc`、`spring-expression`、`spring-context`
6. **ydsz-message 模块**：domain POM 引入 `mybatis-plus-annotation`、`common-jdbc`
7. **ydsz-literule 模块**：domain POM 引入 `mybatis-plus-annotation`、`common-jdbc`；`LiteRule` 注解引入 Spring `Component`；`RuleVersionRepository` 使用 `IPage`
8. **ydsz-userinfo 模块**：domain POM 引入 `spring-boot`；`SocialAuthProperties` 使用 `@ConfigurationProperties`；`UserIdentityProviderFactory` 使用 `@Component`

### 轻微违规（建议修复）

9. **ydsz-nextwiki 模块**：domain POM 引入 `spring-security-crypto`
10. **ydsz-agent 模块**：`AgentDefinitionService` CUD 方法入参使用 VO 而非 DTO
11. **ydsz-literule 模块**：`LiteruleConverter` 未使用 MapStruct
