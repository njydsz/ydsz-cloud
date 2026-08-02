# YdszJson 注解使用规范

> 本文档建立 `ydsz-common-json` 模块的注解使用标准，确保全项目 JSON 处理的一致性。

## 1. 注解选型优先级

| 场景 | 首选注解 | 兼容注解 | 说明 |
|------|----------|----------|------|
| 字段重命名 | `@YdszJsonField(name = "xxx")` | `@JsonProperty("xxx")` | 两者等价，优先使用 `@YdszJsonField` |
| 字段忽略 | `@YdszJsonField(ignore = true)` | `@JsonIgnore` | 优先使用 `@YdszJsonField` |
| 日期格式 | `@YdszJsonField(format = "yyyy-MM-dd")` | `@JsonFormat(pattern = "yyyy-MM-dd")` | 优先使用 `@YdszJsonField` |
| 类级别忽略 | `@JsonIgnoreProperties` | — | YdszJson 原生支持 |
| 包含策略 | `@JsonInclude` | — | YdszJson 原生支持 |
| 视图过滤 | `@YdszJsonView` | — | YdszJson 独有，对标 Jackson `@JsonView` |
| 命名策略 | `@JsonNaming` | — | YdszJson 原生支持 |
| 自动类型白名单 | `@YdszJsonClass` | — | YdszJson 独有，用于安全模式 |

## 2. 禁止事项

1. **禁止导入 `com.fasterxml.jackson.annotation.*`**：业务代码必须使用 `com.njydsz.common.json.annotation.*` 下的注解
2. **禁止混用 Jackson `ObjectMapper`**：所有 JSON 操作必须通过 `YdszJson` 或 `YdszJsonMapper`
3. **禁止手动 StringBuilder 拼接 JSON**：必须使用 `YdszJson.toJson()` / `YdszJsonMapper.toJson()`

## 3. YdszJsonField 使用示例

```java
import com.njydsz.common.json.annotation.YdszJsonField;

public class UserVO {
    @YdszJsonField(name = "user_id")
    private String userId;
    
    @YdszJsonField(ignore = true)
    private String password;
    
    @YdszJsonField(format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
```

## 4. YdszJsonView 使用示例

```java
// 1. 定义视图接口
public class Views {
    public interface Summary {}
    public interface Detail extends Summary {}
}

// 2. 在实体类上标注视图
public class FlowDefinition {
    @YdszJsonView(Views.Summary.class)
    private String flowCode;
    
    @YdszJsonView(Views.Summary.class)
    private String flowName;
    
    @YdszJsonView(Views.Detail.class)
    private String ext;  // 仅详情视图返回
}

// 3. 在 Controller 中使用
@GetMapping("/list")
public List<FlowDefinition> list() {
    // 默认输出 Summary 视图字段
    return service.list();
}

@GetMapping("/detail/{id}")
public FlowDefinition detail(@PathVariable String id) {
    // 通过 YdszJsonMapper 指定 Detail 视图
    return service.getById(id);
}
```

## 5. 动态字段排除（权限场景）

对于运行时动态确定排除字段的场景（如列级权限控制），使用 `YdszJson.toJsonExcludeFields()`：

```java
// AuthColPermissionAspect 中的列级权限过滤
Set<String> excludedFields = permissionService.getExcludedFields(userId, tableName);
String json = YdszJson.toJsonExcludeFields(returnValue, excludedFields);
```

> 注意：`@YdszJsonView` 用于静态视图定义，`toJsonExcludeFields` 用于运行时动态排除。两者不互斥。

## 6. 迁移指南

如从 Jackson 迁移到 YdszJson：

| Jackson 注解 | YdszJson 替代 |
|-------------|--------------|
| `@JsonProperty("name")` | `@YdszJsonField(name = "name")` 或 `@JsonProperty("name")`（兼容） |
| `@JsonIgnore` | `@YdszJsonField(ignore = true)` 或 `@JsonIgnore`（兼容） |
| `@JsonFormat` | `@YdszJsonField(format = "xxx")` 或 `@JsonFormat`（兼容） |
| `@JsonView` | `@YdszJsonView` |
| `@JsonAlias` | `@YdszJsonField(aliases = {"alias1", "alias2"})` |

> **兼容说明**：`ydsz-common-json` 引擎兼容 Jackson 注解（`@JsonProperty`、`@JsonIgnore`、`@JsonFormat`、`@JsonAlias`），
> 第三方库引入的 Jackson 注解类无需修改。但业务代码新开发必须使用 `com.njydsz.common.json.annotation.*` 下的注解。
