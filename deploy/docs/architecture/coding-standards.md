# YDSZ-PMIS 编码规范

> 本文档定义 YDSZ-PMIS 项目的强制编码规范，所有贡献者必须遵守。

---

## Section 1: 实体类命名规范

### 1.1 规则

数据库实体类（Entity）**不以 `DO` 为后缀**，直接使用业务名称作为类名。

| 层次 | 命名规则 | 示例 |
|------|----------|------|
| Entity（数据库实体） | `Xxx`（无后缀） | `UserAccount`、`Role`、`FlowDefinition`、`Job` |
| VO（视图对象） | `XxxVO`（保留后缀） | `UserAccountVO`、`RoleVO` |
| DTO（数据传输对象） | `XxxDTO`（保留后缀） | `InitiationCreateDTO`、`UserAccountDTO` |

### 1.2 基类命名

| 旧名称 | 新名称 | 说明 |
|--------|--------|------|
| `BaseDO` | `Base` | String 主键数据库实体基类 |
| `BaseLongDO` | `BaseLong` | Long 主键数据库实体基类 |
| `LogBaseDO` | `LogBase` | 日志型实体基类 |

### 1.3 例外：命名冲突保留 DO 后缀

当移除 `DO` 后缀后与同模块中已有的领域模型/API 类同名时，**保留 `DO` 后缀**作为消歧义手段。
这符合 DDD 分层架构中持久层实体与领域模型共存的模式。

| DO 类名 | 保留原因 | 冲突类 |
|---------|----------|--------|
| `AgentDefinitionDO` | 与领域值对象同名 | `com.njydsz.agent.domain.agent.AgentDefinition` |
| `RuleDefinitionDO` | 与 API 模型同名 | `com.njydsz.literule.api.RuleDefinition` |
| `RuleExecutionTraceDO` | 与 API 模型同名 | `com.njydsz.literule.api.RuleExecutionTrace` |
| `RulePackDO` | 与 API 模型同名 | `com.njydsz.literule.api.RulePack` |
| `RuleChainGraphDO` | 与 Server 编排模型同名 | `com.njydsz.literule.server.orchestrator.RuleChainGraph` |
| `RuleTestCaseDO` | 与 Server 测试模型同名 | `com.njydsz.literule.server.testing.RuleTestCase` |

### 1.4 MyBatis-Plus 注意事项

- `@TableName("xxx")` 注解的表名**不变**，仅类名变化
- Mapper XML 中 `resultType` / `type` 属性的 FQN 需同步更新
- `mybatis-plus.type-aliases-package` 配置引用的是包路径，不受类名变更影响

### 1.5 变量命名

- 实体类型变量名使用 camelCase：`UserAccount userAccount = ...`
- **不要求**变量名也移除 DO 后缀（如 `userAccountDO` → `userAccount`），但建议新代码遵循无 DO 后缀的命名

---

## Section 2: 禁止行内全限定类名（FQN）

Java 代码中不允许出现行内 FQN 用法，必须使用标准 `import` 语句后在代码中直接引用简单类名。

- **覆盖范围**：类型引用、`.class` 字面量、注解参数、静态方法调用、`new` 表达式、`instanceof` 检查、方法引用、Javadoc `@throws`/`@see`/`@param`/`@return` 标签
- **唯一例外**：字符串字面量中的 FQN、Javadoc `{@link FQN}` 引用（但已 import 的类必须用简单类名）
- **同名类冲突**：使用 FQN 并添加 `// FQN-OK: name conflict with <ClassName>` 注释
- **规则文件**：`.trae/rules/no-inline-fqn.md`（`alwaysApply: true`）
- **检测脚本**：`deploy/scripts/check-inline-fqn.sh`
- **工程化防线**：IDE 检查 → Pre-commit Hook → Checkstyle(severity=error) → Spotless → CI 流水线

---

## Section 3: 禁止使用 @SuppressWarnings 注解

Java 代码中不允许出现 `@SuppressWarnings` 注解。所有警告必须从根源修复而非压制。

- **常见修复**：`unchecked`→泛型方法签名、`unused`→删除死代码、`rawtypes`→指定泛型参数、`deprecation`→迁移新 API
- **规则文件**：`.trae/rules/no-inline-fqn.md`（与 FQN 规则同一文件）
- **检测脚本**：`deploy/scripts/check-inline-fqn.sh` 同时检测 `@SuppressWarnings`

---

## Section 4: 脚本执行优先使用 Python

在 ydsz 项目中执行脚本命令时（批量文件处理、文本替换、代码生成等），**必须优先使用 Python**，禁止使用 PowerShell。

- **原因**：PowerShell 编码损坏（UTF-16 LE BOM）、BOM 污染、转义陷阱、跨平台不一致
- **正确做法**：使用 Python `pathlib`、`io` 模块，固定 `encoding="utf-8"`
- **规则文件**：`.trae/rules/prefer-python-over-powershell.md`（`alwaysApply: true`）

---

## Section 5: 忽略单元测试覆盖率检查

YDSZ 项目全局禁用 JaCoCo 单元测试覆盖率采集和阈值检查。项目已移除全部单元测试代码。

- **配置**：`ydsz-backend/pom.xml` 中 `<skipJacoco>true</skipJacoco>`
- **临时启用**：`mvn verify -DskipJacoco=false -DskipTests=false`
- **CI 影响**：CI 流水线仅执行 `mvn compile -DskipTests`，不涉及 verify 阶段

---

## Section 6: Domain 分层架构与 MapStruct 转换规范

### 6.1 核心原则

**参考架构**：`D:\Code\ydsz\scm-tm\sdt-mps`（sdt-mps 模块）的 domain + web 分层架构设计。

- Controller 层（Web 层）**禁止直接返回数据库实体类（Entity）**，必须通过 VO（View Object）对外暴露数据
- Entity ↔ VO / DTO ↔ Entity 之间的转换**统一使用 MapStruct**，禁止使用 `BeanUtils.copyProperties` 反射方式
- 每个业务实体必须有对应的 **VO + DTO + Query + Converter** 四件套

### 6.2 Domain 层目录结构规范

每个业务模块的 `domain` 包必须按以下结构组织：

```
com.njydsz.{module}.domain/
├── entity/              # 数据库实体（继承 MpBaseEntity，@TableName）
│   └── Xxx.java
├── vo/                  # 视图对象（Controller 返回）
│   └── XxxVO.java
├── dto/                 # 数据传输对象（Controller 入参）
│   ├── post/            # 新增 DTO（XxxPostDTO / XxxCreateDTO）
│   │   └── XxxPostDTO.java
│   └── put/             # 修改 DTO（XxxPutDTO / XxxUpdateDTO）
│       └── XxxPutDTO.java
├── query/               # 查询参数对象
│   └── XxxQuery.java
├── converter/           # MapStruct 转换器接口
│   └── XxxConverter.java
└── enums/               # 业务枚举
    └── XxxEnum.java
```

| 层次 | 包路径 | 职责 | 示例 |
|------|--------|------|------|
| Entity | `domain/entity/` | 数据库实体，仅用于持久层 | `MsgLog`、`Job`、`FlowDefinition` |
| VO | `domain/vo/` | 视图对象，Controller 返回 | `MsgLogVO`、`JobVO` |
| DTO(post) | `domain/dto/post/` | 新增请求体 | `MsgTemplatePostDTO` |
| DTO(put) | `domain/dto/put/` | 修改请求体 | `MsgTemplatePutDTO` |
| Query | `domain/query/` | 查询参数对象 | `MsgTemplateQuery` |
| Converter | `domain/converter/` | MapStruct 转换器 | `MsgTemplateConverter` |

### 6.3 VO 编写规范

1. **命名**：`XxxVO`，与对应 Entity 同名加 `VO` 后缀
2. **位置**：`domain/vo/` 包下
3. **继承**：VO **不继承** `MpBaseEntity`，为独立 POJO，`implements Serializable`
4. **注解**：`@Data` + `@Schema`（Swagger），不使用 `@TableName`、`@TableField` 等 MyBatis-Plus 注解
5. **字段规则**：
   - 包含 Entity 中的业务字段
   - 包含 `id`、`createdBy`、`createdAt`、`updatedBy`、`updatedAt`、`status`（按需）
   - **不包含** `revision`（乐观锁）、`deleted`（逻辑删除标识）、`tenantId`（租户ID）
6. **敏感字段**：如需脱敏，使用 `@SensitiveData` 注解

```java
@Data
@Schema(description = "消息模板视图对象")
public class MsgTemplateVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String templateCode;
    private String channel;
    // ... 业务字段
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
```

### 6.4 MapStruct Converter 编写规范

每个业务实体必须有对应的 Converter 接口，位于 `domain/converter/` 包下。

**命名**：`XxxConverter`，与 Entity 同名加 `Converter` 后缀。

**四要素**：`postDtoToEntity` + `putDtoToEntity` + `queryToEntity` + `entityToVO`

```java
package com.njydsz.message.domain.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.message.domain.dto.post.MsgTemplatePostDTO;
import com.njydsz.message.domain.dto.put.MsgTemplatePutDTO;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.domain.query.MsgTemplateQuery;
import com.njydsz.message.domain.vo.MsgTemplateVO;

/**
 * 消息模板 MapStruct 转换器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgTemplateConverter {

    MsgTemplateConverter INSTANT = Mappers.getMapper(MsgTemplateConverter.class);

    /** PostDTO → Entity（忽略自动填充字段） */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MsgTemplate postDtoToEntity(MsgTemplatePostDTO dto);

    /** PutDTO → Entity（忽略自动填充字段，保留 id） */
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MsgTemplate putDtoToEntity(MsgTemplatePutDTO dto);

    /** Query → Entity（忽略自动填充字段） */
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "revision", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MsgTemplate queryToEntity(MsgTemplateQuery query);

    /** Entity → VO */
    MsgTemplateVO entityToVO(MsgTemplate entity);
}
```

**`@Mapping(target = "xxx", ignore = true)` 必须忽略的字段清单**（对应 `MpBaseEntity` 的自动填充字段）：

| 字段 | 说明 | postDtoToEntity | putDtoToEntity | queryToEntity |
|------|------|:---:|:---:|:---:|
| `id` | 主键（雪花算法生成） | ✅ ignore | ❌ 保留 | ✅ ignore |
| `deleted` | 逻辑删除标识 | ✅ ignore | ✅ ignore | ✅ ignore |
| `revision` | 乐观锁版本号 | ✅ ignore | ✅ ignore | ✅ ignore |
| `tenantId` | 租户 ID | ✅ ignore | ✅ ignore | ✅ ignore |
| `createdBy` | 创建人 | ✅ ignore | ✅ ignore | ✅ ignore |
| `createdAt` | 创建时间 | ✅ ignore | ✅ ignore | ✅ ignore |
| `updatedBy` | 更新人 | ✅ ignore | ✅ ignore | ✅ ignore |
| `updatedAt` | 更新时间 | ✅ ignore | ✅ ignore | ✅ ignore |

### 6.5 Controller 层使用规范

Controller 层通过 `Converter.INSTANT` 调用 MapStruct 生成的转换方法：

```java
@RestController
@RequestMapping("/message/template")
public class TemplateController {

    private final TemplateService templateService;

    // 查询详情 → 返回 VO
    @GetMapping("/{id}")
    public BaseResponse<MsgTemplateVO> getById(@PathVariable String id) {
        MsgTemplate entity = templateService.getById(id);
        return BaseResponse.success(MsgTemplateConverter.INSTANT.entityToVO(entity));
    }

    // 分页查询 → 返回 Page<VO>
    @GetMapping("/page")
    public BaseResponse<Page<MsgTemplateVO>> page(MsgTemplateQuery query) {
        Page<MsgTemplate> page = templateService.page(query);
        // 列表转换
        List<MsgTemplateVO> voList = page.getRecords().stream()
                .map(MsgTemplateConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
        Page<MsgTemplateVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return BaseResponse.success(voPage);
    }

    // 新增 → DTO 转 Entity 后调用 Service
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody MsgTemplatePostDTO dto) {
        MsgTemplate entity = MsgTemplateConverter.INSTANT.postDtoToEntity(dto);
        return BaseResponse.success(templateService.save(entity));
    }

    // 修改 → DTO 转 Entity 后调用 Service
    @PutMapping("/{id}")
    public BaseResponse<Boolean> update(@PathVariable String id, @Valid @RequestBody MsgTemplatePutDTO dto) {
        MsgTemplate entity = MsgTemplateConverter.INSTANT.putDtoToEntity(dto);
        entity.setId(id);
        return BaseResponse.success(templateService.updateById(entity));
    }
}
```

### 6.6 已遵循规范的模块（参考标杆）

- `ydsz-userinfo`：`domain/vo/` 下 10 个 VO，所有 Controller 返回 VO（待补 MapStruct Converter）
- `ydsz-system`：`domain/vo/` 下 6 个 VO，所有 Controller 返回 VO（待补 MapStruct Converter）
- `scm-tm/sdt-mps`：完整的 MapStruct Converter 模式（`RoleConverter`、`MenuConverter` 等）

### 6.7 POM 依赖配置

项目已在根 POM 全局配置 MapStruct 注解处理器（`ydsz-backend/pom.xml`）：

```xml
<!-- dependencyManagement -->
<mapstruct.version>1.6.3</mapstruct.version>

<!-- annotationProcessorPaths -->
<path>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>${lombok.version}</version>
</path>
<path>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>${mapstruct.version}</version>
</path>
```

业务模块的 `domain` POM 需添加 MapStruct 依赖（`provided` scope）：

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <scope>provided</scope>
</dependency>
```

### 6.8 检测方式

```bash
# 1. 检测 Controller 中直接返回 Entity 的方法
grep -rn "BaseResponse<.*>" --include="*Controller.java" | grep -v "VO\|DTO\|Map\|String\|Integer\|Boolean\|List\|Page<.*VO\|void"

# 2. 检测 BeanUtils.copyProperties 残留（应迁移到 MapStruct）
grep -rn "BeanUtils.copyProperties\|BeanUtil.copyProperties" --include="*.java"
```
