# NameAssembler 使用规范

> **强制规范** — 适用于所有业务模块的 server 层服务实现

## 1. 背景与目的

业务模块的 VO 普遍包含外键 ID（如 `createdBy`、`assigneeId`、`deptId`），展示给前端时需要富化为可读名称（如 `createdByName`、`assigneeName`、`deptName`）。

**NameAssembler** 是 `ydsz-common-feign` 提供的跨服务名称解析门面，统一封装了：
- Feign 调用 `ydsz-userinfo` 服务的批量查询接口
- 本地缓存（5 分钟 TTL）
- 降级策略（Feign 失败时用 ID 顶替名称）

**禁止各模块自行实现名称解析逻辑**，必须统一使用 NameAssembler。

## 2. 核心接口

```java
public interface NameAssembler {
    // 批量富化集合中对象的外键 name 字段（推荐用于分页查询）
    <T> void enrich(Collection<T> objects,
                    Function<T, String> idGetter,
                    BiConsumer<T, String> nameSetter,
                    NameType type);

    // 富化单个对象的外键 name 字段（推荐用于详情查询）
    <T> void enrichOne(T obj,
                       Function<T, String> idGetter,
                       BiConsumer<T, String> nameSetter,
                       NameType type);

    // 批量解析 ID → 名称映射（底层方法，一般不直接调用）
    Map<String, String> batchResolveNames(NameType type, Collection<String> ids);

    // 解析单个 ID 的名称（走本地缓存）
    String resolveName(NameType type, String id);
}
```

## 3. NameType 枚举

```java
public enum NameType {
    USER,       // 用户 ID → 真实姓名
    DEPARTMENT, // 部门 ID → 部门名称
    ROLE,       // 角色 ID → 角色名称
    POST,       // 岗位 ID → 岗位名称
    COMPANY,    // 公司 ID → 公司名称
    CUSTOMER    // 客户 ID → 客户名称
}
```

## 4. 标准用法示例

### 4.1 分页查询场景（批量富化）

```java
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl {
    private final NameAssembler nameAssembler;

    public Page<ProjectVO> page(ProjectQueryDTO query) {
        // 1. 查询分页数据
        Page<ProjectDO> page = repository.page(query);
        
        // 2. 转换为 VO
        List<ProjectVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .toList();
        
        // 3. 批量富化外键名称（一次 Feign 调用解决整页数据）
        nameAssembler.enrich(voList,
                ProjectVO::getPmId,           // ID getter
                ProjectVO::setPmName,         // name setter
                NameType.USER);               // 名称类型
        
        // 4. 可链式富化多个字段
        nameAssembler.enrich(voList,
                ProjectVO::getDeptId,
                ProjectVO::setDeptName,
                NameType.DEPARTMENT);
        
        // 5. 返回结果
        Page<ProjectVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(voList);
        return result;
    }
}
```

### 4.2 详情查询场景（单个富化）

```java
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl {
    private final NameAssembler nameAssembler;

    public ProjectVO getById(String id) {
        // 1. 查询实体
        ProjectDO entity = repository.getById(id);
        if (entity == null) {
            return null;
        }
        
        // 2. 转换为 VO
        ProjectVO vo = convertToVO(entity);
        
        // 3. 富化外键名称（走本地缓存）
        nameAssembler.enrichOne(vo,
                ProjectVO::getPmId,
                ProjectVO::setPmName,
                NameType.USER);
        
        nameAssembler.enrichOne(vo,
                ProjectVO::getDeptId,
                ProjectVO::setDeptName,
                NameType.DEPARTMENT);
        
        return vo;
    }
}
```

### 4.3 多个外键字段富化

```java
public ProjectVO getById(String id) {
    ProjectVO vo = convertToVO(repository.getById(id));
    
    // 富化多个外键字段
    nameAssembler.enrichOne(vo, ProjectVO::getCreatedBy, ProjectVO::setCreatedByName, NameType.USER);
    nameAssembler.enrichOne(vo, ProjectVO::getUpdatedBy, ProjectVO::setUpdatedByName, NameType.USER);
    nameAssembler.enrichOne(vo, ProjectVO::getPmId, ProjectVO::setPmName, NameType.USER);
    nameAssembler.enrichOne(vo, ProjectVO::getDeptId, ProjectVO::setDeptName, NameType.DEPARTMENT);
    nameAssembler.enrichOne(vo, ProjectVO::getCustomerId, ProjectVO::setCustomerName, NameType.CUSTOMER);
    
    return vo;
}
```

## 5. 注入方式

```java
@Service
@RequiredArgsConstructor  // 推荐：构造器注入
public class ProjectServiceImpl {
    private final NameAssembler nameAssembler;  // Spring 自动注入
}
```

**禁止**使用 `@Autowired` 字段注入：
```java
// ❌ 错误示例
@Autowired
private NameAssembler nameAssembler;
```

## 6. 降级语义

- **Feign 调用失败**：用 ID 字符串本身顶替 name 字段（避免前端显示空白），记录 WARN 日志
- **ID 为 null/空白**：对应字段保持原值不变
- **ID 未命中**：用 ID 字符串顶替

## 7. 性能语义

- `enrich()` 内部自动收集所有 ID 后一次批量解析，避免 N+1 调用
- `enrichOne()` 优先走本地缓存（默认 5 分钟 TTL），缓存未命中时发起 Feign 调用
- 批量富化时优先使用 `enrich()` 而非循环调用 `enrichOne()`

## 8. 适用场景

| 场景 | 推荐方法 | 示例 |
|-----|---------|------|
| 分页查询 | `enrich()` | 项目列表、任务列表、审批列表 |
| 详情查询 | `enrichOne()` | 项目详情、任务详情 |
| 批量导出 | `enrich()` | Excel 导出前富化 |
| 单个对象 | `enrichOne()` | 创建后返回详情 |

## 9. 不适用场景

以下场景**不应**使用 NameAssembler：

1. **写操作**：创建/更新时不需要富化名称，名称由读路径按需解析
2. **内部计算**：业务逻辑中不需要名称，只需 ID
3. **审计字段自动填充**：`createdBy`/`updatedBy` 由 `AuditFieldFiller` 自动填充，不需要手动富化

## 10. 模块采用清单

### 已采用（✅）

- [x] `ydsz-project`：`ProjectInitiationServiceImpl`
- [x] `ydsz-workflow`：`FlowInstanceServiceImpl`

### 待采用（⚠️）

以下模块的 ServiceImpl 中存在外键 ID 字段，应逐步采用 NameAssembler：

- [ ] `ydsz-system`：配置管理、字典管理
- [ ] `ydsz-cronjob`：任务管理、任务日志
- [ ] `ydsz-message`：消息模板、消息日志
- [ ] `ydsz-nextwiki`：文档管理
- [ ] `ydsz-userinfo`：用户管理（内部使用，不跨服务）
- [ ] `ydsz-literule`：规则管理

## 11. 验收标准

1. **所有读路径**（`getById`、`page`、`list`）返回的 VO 必须富化外键名称
2. **富化方式**必须使用 NameAssembler，禁止自行实现 Feign 调用
3. **注入方式**必须使用构造器注入（`@RequiredArgsConstructor`）
4. **性能要求**：分页查询使用 `enrich()`，禁止循环调用 `enrichOne()`

## 12. 常见问题

### Q1: 为什么不直接在 DO 中冗余存储名称？

**A**: 冗余存储会导致数据不一致（用户改名后历史数据不更新），且增加存储成本。NameAssembler 通过缓存 + 降级机制平衡了性能与一致性。

### Q2: NameAssembler 的缓存会不会导致名称更新不及时？

**A**: 缓存 TTL 为 5 分钟，对于大多数场景可接受。如需实时性，可在用户改名后主动清除缓存（通过 `UserInfoNameAssembler` 的内部方法）。

### Q3: 如果 Feign 调用失败，前端会显示什么？

**A**: 前端会显示 ID 字符串（如 `"123456"`），不会显示空白。这是降级策略，确保系统可用性。

### Q4: 能否在 Controller 层调用 NameAssembler？

**A**: **禁止**。NameAssembler 应在 server 层调用，Controller 层只负责参数校验和响应封装。

## 13. 相关文件

- 接口定义：[NameAssembler.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-feign/src/main/java/com/njydsz/common/feign/assembler/NameAssembler.java)
- 实现类：[UserInfoNameAssembler.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-userinfo/ydsz-userinfo-api/src/main/java/com/njydsz/userinfo/api/assembler/UserInfoNameAssembler.java)
- 自动配置：[NameAssemblerAutoConfiguration.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-feign/src/main/java/com/njydsz/common/feign/assembler/NameAssemblerAutoConfiguration.java)
- 示例代码：[ProjectInitiationServiceImpl.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-project/ydsz-project-server/src/main/java/com/njydsz/project/server/service/impl/ProjectInitiationServiceImpl.java)

---

**最后更新**: 2026-07-27  
**维护团队**: ydsz-architecture-team
