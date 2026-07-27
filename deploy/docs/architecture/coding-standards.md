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

**核心原则**：Controller 层负责 DTO ↔ Entity ↔ VO 的转换，**禁止将 Entity 直接作为 @RequestBody 入参**，**禁止在 Service 层封装 toVO 等对象转换方法**。

#### 6.5.1 入参规范

| HTTP 方法 | 入参类型 | 命名规则 | 说明 |
|-----------|---------|----------|------|
| `@PostMapping` | `@RequestBody XxxPostDTO` | `XxxPostDTO` | 新增请求，**不含 `id` 字段** |
| `@PutMapping` | `@RequestBody XxxPutDTO` | `XxxPutDTO` | 修改请求，**包含 `id` 字段** |
| `@GetMapping` | `@PathVariable` / `XxxQuery` | `XxxQuery` | 查询请求 |
| `@DeleteMapping` | `@PathVariable` / `@RequestParam` | — | 删除请求 |

#### 6.5.2 PostDTO vs PutDTO 规范

```java
// ===== PostDTO（新增）— 不含 id =====
@Data
@Schema(description = "消息模板新增请求")
public class MsgTemplatePostDTO implements Serializable {
    private String templateCode;    // 业务字段
    private String channel;
    private String content;
    // ... 其他业务字段
    // ❌ 不包含 id（由数据库生成）
    // ❌ 不包含 deleted/revision/tenantId/createdBy/createdAt 等自动填充字段
}

// ===== PutDTO（修改）— 包含 id =====
@Data
@Schema(description = "消息模板修改请求")
public class MsgTemplatePutDTO implements Serializable {
    private String id;              // ✅ 包含 id（指定修改哪条记录）
    private String templateCode;    // 业务字段
    private String channel;
    private String content;
    // ... 其他业务字段
}
```

#### 6.5.3 Controller 完整示例

```java
@RestController
@RequestMapping("/api/v1/message/template")
public class TemplateController {

    private final TemplateService templateService;

    // 查询详情 → Service 返回 Entity，Controller 转换为 VO
    @GetMapping("/{id}")
    public BaseResponse<MsgTemplateVO> getById(@PathVariable String id) {
        MsgTemplate entity = templateService.getById(id);
        return BaseResponse.success(MsgTemplateConverter.INSTANT.entityToVO(entity));
    }

    // 分页查询 → 返回 Page<VO>
    @GetMapping("/page")
    public BaseResponse<Page<MsgTemplateVO>> page(MsgTemplateQuery query) {
        Page<MsgTemplate> page = templateService.page(query);
        Page<MsgTemplateVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(MsgTemplateConverter.INSTANT.templateListToVO(page.getRecords()));
        return BaseResponse.success(voPage);
    }

    // 新增 → PostDTO 转 Entity 后调用 Service
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody MsgTemplatePostDTO dto) {
        MsgTemplate entity = MsgTemplateConverter.INSTANT.postDtoToEntity(dto);
        return BaseResponse.success(templateService.save(entity));
    }

    // 修改 → PutDTO 转 Entity 后调用 Service
    @PutMapping("/{id}")
    public BaseResponse<Boolean> update(@PathVariable String id, @Valid @RequestBody MsgTemplatePutDTO dto) {
        MsgTemplate entity = MsgTemplateConverter.INSTANT.putDtoToEntity(dto);
        entity.setId(id);
        return BaseResponse.success(templateService.updateById(entity));
    }
}
```

#### 6.5.4 禁止事项

| ❌ 禁止 | ✅ 正确 |
|---------|--------|
| `@RequestBody Job job`（直接用 Entity） | `@RequestBody JobPostDTO dto` + `Converter.INSTANT.postDtoToEntity(dto)` |
| Service 层 `private XxxVO toVO(Xxx entity)` | Controller 层 `Converter.INSTANT.entityToVO(entity)` |
| `BeanUtils.copyProperties(dto, entity)` | `Converter.INSTANT.postDtoToEntity(dto)` |
| 所有方法共用一个 `XxxSaveDTO` | 新增用 `XxxPostDTO`，修改用 `XxxPutDTO` |

### 6.6 Service 层规范

**Service 层只处理 Entity 对象**，不感知 VO/DTO 的存在：

```java
// ✅ 正确：Service 接口只接收和返回 Entity
public interface TemplateService {
    MsgTemplate getById(String id);
    Page<MsgTemplate> page(TemplateQuery query);
    String save(MsgTemplate entity);      // 接收 Entity
    boolean updateById(MsgTemplate entity);
}

// ❌ 错误：Service 不应返回 VO 或接收 DTO
public interface TemplateService {
    MsgTemplateVO getById(String id);      // 禁止返回 VO
    String save(TemplatePostDTO dto);      // 禁止接收 DTO
    private MsgTemplateVO toVO(MsgTemplate e) { ... }  // 禁止在 Service 中做转换
}
```

### 6.7 已遵循规范的模块（参考标杆）

- `scm-tm/sdt-mps`：完整的 PostDTO/PutDTO + MapStruct Converter 模式（`RoleConverter`、`MenuConverter` 等）
- `ydsz-project`：33 个 Controller 全部 PostDTO/PutDTO 化 + ProjectConverter 66 个转换方法（2026-07-27 完成）
- `ydsz-userinfo`：6 个 Controller SaveDTO → PostDTO/PutDTO 拆分 + 10 个 VO + UserInfoConverter + Service toVO 清理（2026-07-27 完成）
- `ydsz-system`：6 个 VO + SystemConverter + Service toVO 死代码清理（2026-07-27 完成）
- `ydsz-cronjob`：JobWebhook PostDTO/PutDTO + ConnectorConfigPostDTO + 4 个 SaveDTO → PostDTO/PutDTO 拆分（2026-07-27 完成）
- `ydsz-agent`：AgentDefinitionDO PostDTO/PutDTO + AgentConverter（2026-07-27 完成）
- `ydsz-literule`：RuleTestCaseDO/DecisionTable/RuleABPolicy 3 个域实体 DTO 化 + LiteruleConverter（2026-07-27 完成）
- `ydsz-workflow`：FlowDelegateAuthSaveDTO → PostDTO/PutDTO 拆分（2026-07-27 完成）
- `ydsz-nextwiki`：FileApplicationService toVO 调用替换为 NextwikiConverter（2026-07-27 完成）

**全项目审计结果**：零 `@RequestBody Entity` 违规，零 `XxxSaveDTO` 共用违规，零 Service 层 `toVO` 残留。

### 6.8 POM 依赖配置

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

### 6.9 检测方式

```bash
# 1. 检测 Controller 中直接返回 Entity 的方法
grep -rn "BaseResponse<.*>" --include="*Controller.java" | grep -v "VO\|DTO\|Map\|String\|Integer\|Boolean\|List\|Page<.*VO\|void"

# 2. 检测 BeanUtils.copyProperties 残留（应迁移到 MapStruct）
grep -rn "BeanUtils.copyProperties\|BeanUtil.copyProperties" --include="*.java"

# 3. 检测 Controller 中直接使用 Entity 作为 @RequestBody（应使用 PostDTO/PutDTO）
grep -rn "@RequestBody.*Entity\b\|@RequestBody.*\bJob\b\|@RequestBody.*\bFlow" --include="*Controller.java"

# 4. 检测 Service 层 toVO 方法残留（应迁移到 Controller 层 Converter 调用）
grep -rn "private.*VO toVO\|public.*VO toVO" --include="*ServiceImpl.java"

# 5. 检测 DTO 目录结构（应有 post/ 和 put/ 子目录）
find . -path "*/domain/dto" -type d -exec sh -c 'ls -d "$1/post" "$1/put" 2>/dev/null || echo "MISSING: $1"' _ {} \;
```


---

## Section 7: 错误码段位规范

### 7.1 编码格式

所有业务异常码采用 6 位字符串格式：

```
[类型(1位)] + [模块(2位)] + [序号(3位)]
```

- **类型字母**：标识异常大类，决定 `ExceptionCategory` 主分类
- **模块号**：两位数字，标识具体子模块
- **序号**：三位数字，模块内自增序号

### 7.2 主分类段位（5 大主分类，A/B/C/D/E）

| 段位 | 主分类 | 含义 | HTTP 状态码 | 持有枚举 |
|------|--------|------|-------------|----------|
| `A` | BUSINESS | 业务级错误 | 4xx | `UnifiedExceptionCode` |
| `B` | SYSTEM | 系统级错误 | 5xx | `UnifiedExceptionCode`、`UserInfoResultCode` |
| `C` | SECURITY | 安全级错误 | 401/403 | `UnifiedExceptionCode` |
| `D` | RATE_LIMIT | 限流/熔断/降级 | 429/503 | `RateLimitExceptionCode` |
| `E` | EXTERNAL | 外部/三方服务 | 502/504 | `ExternalExceptionCode` |

### 7.3 模块专属段位（F/G/H/W）

模块专属段位是某个公共/业务模块独占的字母前缀，避免与主分类段位冲突。
`ExceptionCode.getCategory()` 将其映射到合适的主分类。

| 段位 | 模块 | 持有枚举 | 主分类映射 |
|------|------|----------|------------|
| `F` | 文件存储 (common-file) | `FileExceptionCode` | INFRASTRUCTURE |
| `G` | 文档处理 (common-docs) | `DocumentExceptionCode` | BUSINESS |
| `H` | Excel 处理 (common-excel) | `ExcelExceptionCode` | BUSINESS |
| `W` | 网盘知识库 (nextwiki) | `NextwikiExceptionCode` | BUSINESS |

### 7.4 段位分配约束

- **禁止段位复用**：同一个字母前缀只能由一个枚举类独占，不允许两个枚举共用 `D01xxx` 区间。
  - 历史冲突已修复：`DocumentExceptionCode` 从 `D` 迁移到 `G`；`ExcelExceptionCode` 从 `E` 迁移到 `H`。
- **新模块段位申请**：新增业务模块需要专属段位时，在本文档登记并更新 `ExceptionCode.getCategory()` 的 switch 分支。
- **主分类段位保留序号 051+**：`UnifiedExceptionCode` 各模块序号从 `051` 起始，避免与已废弃的 `CommExceptionCode` 冲突。
- **模块专属段位从 001 起始**：`F`/`G`/`H`/`W` 等模块专属段位的序号从 `001` 起始。

### 7.5 模块内子段位规划

#### A 段位（业务级，UnifiedExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `A00xxx` | 成功 |
| `A01xxx` | 参数/业务异常 |
| `A02xxx` | 认证异常 |
| `A03xxx` | 权限异常 |
| `A04xxx` | 数据异常 |

#### B 段位（系统级）

| 子段位 | 用途 | 持有枚举 |
|--------|------|----------|
| `B01xxx` | 系统异常 | `UnifiedExceptionCode` |
| `B02xxx` | 外部服务异常 | `UnifiedExceptionCode` |
| `B30xxx` | 用户/认证 | `UserInfoResultCode` |
| `B31xxx` | 组织架构 | `UserInfoResultCode` |
| `B32xxx` | RBAC（角色/权限/菜单/岗位/语言） | `UserInfoResultCode` |

#### D 段位（限流，RateLimitExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `D01xxx` | 全局限流（IP / 用户 / 租户维度） |
| `D02xxx` | 接口粒度限流 |
| `D03xxx` | 热点参数限流 |
| `D04xxx` | 熔断器 |
| `D05xxx` | 服务降级 |
| `D06xxx` | 集群限流 |
| `D07xxx` | 自适应限流 |

#### E 段位（外部服务，ExternalExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `E01xxx` | 通用外部服务 |
| `E02xxx` | Feign / OpenFeign |
| `E03xxx` | 网关 / API Gateway |
| `E04xxx` | 支付服务 |
| `E05xxx` | 短信 / 邮件 / 推送 |
| `E06xxx` | 存储 / OSS / CDN |
| `E07xxx` | 消息队列 |
| `E08xxx` | 搜索引擎 / ES |
| `E09xxx` | 第三方 OAuth |

#### F 段位（文件存储，FileExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `F01xxx` | 文件操作错误 |
| `F02xxx` | 存储桶错误 |
| `F03xxx` | 目录错误 |
| `F04xxx` | 配置错误 |
| `F05xxx` | 私有链接错误 |
| `F06xxx` | 范围下载错误 |
| `F07xxx` | 分片上传错误 |
| `F99xxx` | 未知错误（兜底） |

#### G 段位（文档处理，DocumentExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `G01xxx` | 解析错误 |
| `G02xxx` | 预处理错误 |
| `G03xxx` | 安全扫描错误 |
| `G04xxx` | PII 检测错误 |
| `G05xxx` | 脱敏错误 |
| `G06xxx` | 水印错误 |
| `G07xxx` | 转换错误 |
| `G99xxx` | 未知错误（兜底） |

#### H 段位（Excel 处理，ExcelExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `H01xxx` | 读取异常 |
| `H02xxx` | 写入异常 |
| `H03xxx` | 转换异常 |
| `H04xxx` | 配置异常 |

#### W 段位（网盘知识库，NextwikiExceptionCode）

| 子段位 | 用途 |
|--------|------|
| `W01xxx` | 文件操作错误 |
| `W02xxx` | 版本错误 |
| `W03xxx` | 分享错误 |
| `W04xxx` | 配额错误 |
| `W05xxx` | 权限错误 |
| `W06xxx` | 回收站错误 |
| `W07xxx` | 标签错误 |
| `W08xxx` | 预览错误 |
| `W09xxx` | 系统错误 |

### 7.6 注册规范

- 所有 `ExceptionCode` 实现类**必须**在静态块中通过 `ExceptionCodeRegistry.register(map)` 完成注册，否则 `ExceptionCode.fromCode(code)` 无法反查。
- 重复注册时默认宽松模式（保留首次注册值 + warn 日志）；如需 fail-fast，使用 `registerStrict(map)`。
- **段位冲突检测**：CI 可通过 `ExceptionCodeRegistry.allRegistered()` 反向扫描所有已注册 code，发现同 code 跨枚举类即报警。

### 7.7 相关文件

- [UnifiedExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/code/UnifiedExceptionCode.java)
- [ExternalExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/code/ExternalExceptionCode.java)
- [RateLimitExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/code/RateLimitExceptionCode.java)
- [DocumentExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-docs/src/main/java/com/njydsz/common/docs/exception/DocumentExceptionCode.java)
- [ExcelExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-excel/src/main/java/com/njydsz/common/excel/exception/ExcelExceptionCode.java)
- [FileExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-file/src/main/java/com/njydsz/common/file/exception/FileExceptionCode.java)
- [NextwikiExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-nextwiki/ydsz-nextwiki-domain/src/main/java/com/njydsz/nextwiki/domain/enums/NextwikiExceptionCode.java)
- [UserInfoResultCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-userinfo/ydsz-userinfo-domain/src/main/java/com/njydsz/userinfo/domain/enums/UserInfoResultCode.java)
- [ExceptionCode.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/enums/ExceptionCode.java)
- [ExceptionCategory.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/enums/ExceptionCategory.java)

---

## Section 8: 配置管理与 Nacos 共享配置规范

### 8.1 配置分层

YDSZ 微服务配置采用三层结构，优先级从高到低：

| 层级 | 文件 / 来源 | 职责 | 示例 |
|------|-------------|------|------|
| 1 (最高) | `application.yml`（本地） | 服务私有配置 + override | `ydsz.thread.pools.*`、`ydsz.literule.*`、`nextwiki.*` |
| 2 | Nacos `ydsz-{module}.yml`（shared-configs） | 模块私有但需动态刷新的配置 | `ydsz-userinfo.yml`、`ydsz-project.yml` |
| 3 (最低) | Nacos `ydsz-common.yaml`（shared-configs） | **全集群共享的公共配置** | `mybatis-plus.*`、`management.*`、`springdoc.*`、`spring.datasource.*`、`spring.data.redis.*` |

### 8.2 公共配置下沉清单（ydsz-common.yaml）

以下配置**必须**放在 `deploy/common/nacos/ydsz-common.yaml`，禁止在本地 `application.yml` 重复声明：

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.*` | 数据源（Druid 连接池） |
| `spring.data.redis.*` | Redis 连接 |
| `spring.dynamic.*` | 动态数据源（读写分离） |
| `spring.jackson.*` | Jackson 序列化（日期格式 / 时区 / 非空策略） |
| `spring.cache.type` | Spring Cache 类型（redis） |
| `mybatis-plus.mapper-locations` | Mapper XML 默认扫描路径（`classpath*:mapper/**/*.xml`） |
| `mybatis-plus.configuration.*` | MyBatis-Plus 配置（驼峰 / 日志实现） |
| `mybatis-plus.global-config.*` | MyBatis-Plus 全局配置（逻辑删除 / 主键策略） |
| `management.*` | Actuator 端点暴露 + health 显示策略 |
| `springdoc.*` | OpenAPI / Swagger UI 配置 |
| `knife4j.*` | Knife4j 增强 UI 配置 |
| `feign.*` | Feign 超时 / 重试 / 压缩 |
| `spring.cloud.openfeign.*` | OpenFeign + 熔断 |
| `resilience4j.*` | 重试 / 熔断器 |
| `logging.*` | 日志级别 / 格式 |
| `jasypt.*` | 配置加密 |
| `ydsz.jwt.*` | JWT 密钥 |
| `ydsz.security.*` | IP 白名单 |
| `ydsz.kms.*` | 密钥管理 |
| `ydsz.sentry.*` | 错误监控 |

### 8.3 服务私有配置清单（application.yml）

以下配置**允许**放在本地 `application.yml`：

| 配置项 | 说明 | 示例服务 |
|--------|------|----------|
| `ydsz.thread.pools.*` | 服务专属线程池配置 | cronjob / workflow / literule / nextwiki / agent |
| `ydsz.event.outbox.*` | 事务性 Outbox 事件配置 | workflow |
| `ydsz.seata.*` | Seata 分布式事务开关 | project / workflow |
| `ydsz.literule.*` | 规则引擎核心配置 | literule |
| `nextwiki.*` | 网盘知识库配置 | nextwiki |
| `ydsz.agent.*` | AI Agent 配置 | agent |
| `mybatis-plus.mapper-locations`（override） | 追加特殊路径 | workflow（追加 `mapper/flow/**/*.xml`） |
| `spring.servlet.multipart.*` | 文件上传大小限制 | nextwiki |
| `server.port`（override） | 端口覆盖 | nextwiki（8800） |

### 8.4 配置治理原则

1. **DRY 原则**：公共配置只在 `ydsz-common.yaml` 声明一次，本地 `application.yml` 禁止重复。
2. **Override 显式注释**：本地覆盖共享配置时，必须添加注释说明覆盖原因（如 workflow 追加 flow mapper 路径）。
3. **敏感配置加密**：密码 / 密钥必须使用 Jasypt `ENC()` 加密或通过环境变量注入，禁止明文。
4. **环境隔离**：通过 `spring.profiles.active` + Nacos group 区分 dev/sit/uat/prod，不在本地文件中硬编码环境差异。
5. **健康检查策略统一**：`management.endpoint.health.show-details` 在共享配置中统一为 `when-authorized`（需认证），禁止本地降级为 `always`。
6. **端口一致性**：`server.port` 在 `bootstrap.yml` 与 `application.yml` 中必须一致；如需覆盖，必须同步更新 Nacos 服务注册的 `metadata.port`。

### 8.5 相关文件

- [ydsz-common.yaml](file:///d:/Code/ydsz/ydsz-pmis/deploy/common/nacos/ydsz-common.yaml) — Nacos 共享配置种子文件
- [cronjob application.yml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-cronjob/ydsz-cronjob-web/src/main/resources/application.yml)
- [project application.yml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-project/ydsz-project-web/src/main/resources/application.yml)
- [workflow application.yml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-workflow/ydsz-workflow-web/src/main/resources/application.yml)
- [literule application.yml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-literule/ydsz-literule-web/src/main/resources/application.yml)
- [nextwiki application.yml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-nextwiki/ydsz-nextwiki-web/src/main/resources/application.yml)
