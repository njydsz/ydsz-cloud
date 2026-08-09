# YdszJson 1.2.0 Release Notes

> 发布日期：2026-08-09
> 审查状态：已通过深度代码审查与竞品对标
> 主要目标：对标互联网大厂研发规范，补齐测试基座、性能基线、RFC 标准支持

---

## 一、测试基座（P0 - 止血）

### 新增测试类
- `YdszJsonRoundTripTest`：核心 Round-Trip 测试
  - 标量类型（String/int/long/double/boolean/BigDecimal/BigInteger）
  - POJO（简单、空、带注解）
  - 集合类型（List/Map/Array/空集合）
  - 嵌套对象（多层嵌套）
  - 继承字段（基类字段不再丢失）
  - 特殊字符（Unicode/Emoji/U+2028/U+2029/孤立代理）
  - 日期类型（LocalDate/LocalDateTime/Date）
  - 总共 20+ 个测试方法

- `YdszJsonSecurityTest`：安全边界测试
  - 深度限制（对象/数组 300 层嵌套防护）
  - 数组大小限制（10000+ 元素防护）
  - 字符串长度限制（1MB+ 防护）
  - AutoType 白名单验证
  - RFI/DDE 注入防护（JNDI、模板引擎）
  - 异常信息质量验证
  - 总共 10+ 个安全测试用例

---

## 二、性能基线（P1）

### JMH 基准测试
- `YdszJsonBenchmark`：性能基准测试
  - 竞品对比：Jackson、Fastjson2
  - 场景：简单对象序列化/反序列化、中等集合（100 对象）
  - 指标：AverageTime（微秒）
  - 预热：3 次 × 1 秒
  - 测量：5 次 × 2 秒

### 运行方式
```bash
# 通过 Maven 执行
mvn package -DskipTests
java -cp target/ydsz-common-json-*.jar com.njydsz.common.json.benchmark.YdszJsonBenchmark

# 详细模式（导出 JSON 报告）
java -jar target/benchmarks.jar -rf json -rff benchmark-results.json
```

---

## 三、RFC 标准支持（P2）

### JSON Patch (RFC 6902) + Merge Patch (RFC 7396)

#### 新增文件
- `com.njydsz.common.json.tree.JsonPatch`：完整 RFC 6902 实现
  - 6 种操作：add、remove、replace、move、copy、test
  - JSON Pointer (RFC 6901) 路径解析
  - 链式 TEST 验证
  - 异常路径检测（非法操作、越界索引）

- `JsonPatch.applyMerge()`：RFC 7396 Merge Patch 实现
  - null 值语义表示删除字段
  - 嵌套对象递归合并

#### API 入口（YdszJson 便捷方法）
```java
// JSON PATCH
List<JsonPatch.PatchOp> ops = YdszJson.parsePatch(patchJson);
User patched = YdszJson.applyPatch(patchJson, existingUser, User.class);

// MERGE PATCH（更简单）
User patched = YdszJson.applyMergePatch(mergeJson, existingUser, User.class);
```

---

## 四、流式读写（P2）

### JSON Lines (NDJSON) 支持
- `JsonLines` 工具类
- 用途：日志导出、大数据批量导入、SSE 流 API

#### API
```java
// 写入
try (JsonLines.Writer<User> writer = JsonLines.writer(outputStream, true)) {
    users.forEach(writer::write);
}

// 读取
try (JsonLines.Reader<User> reader = JsonLines.reader(inputStream, User.class)) {
    reader.forEach(user -> process(user));
}

// Stream API
try (Stream<User> stream = JsonLines.stream(inputStream, User.class)) {
    stream.filter(u -> u.isActive()).forEach(handler);
}
```

---

## 五、泛型类型推断增强（P2）

### TypeRef 工具类
- 对标 Jackson 的 TypeReference
- 更直观的类型构造 API

```java
// 之前（匿名内部类）
List<User> users = YdszJson.fromJson(json, new JsonType<List<User>>() {});

// 现在（TypeRef 工厂方法）
List<User> users = YdszJson.fromJson(json, TypeRef.list(User.class));
Set<String> set = YdszJson.fromJson(json, TypeRef.set(String.class));
Map<String, User> map = YdszJson.fromJson(json, TypeRef.map(String.class, User.class));
Map<String, List<User>> mapOfList = YdszJson.fromJson(json,
    TypeRef.list(TypeRef.map(String.class, User.class)));
```

---

## 六、竞品对标结果

### 能力对标矩阵（vs Jackson 2.17 / Fastjson2 2.0.51）

| 能力维度 | YdszJson 1.2.0 | Jackson 2.17 | Fastjson2 2.0.51 |
|---------|---------------|--------------|-------------------|
| 流式解析 | ✅ JSONReader/Writer | ✅ JsonParser/Generator | ✅ JSONReader/Writer |
| 树模型 | ✅ 完整 | ✅ 完整 | ✅ 完整 |
| 数据绑定 | ✅ 完整 | ✅ 完整 | ✅ 完整 |
| 注解支持 | ✅ 31 个 | ✅ 60+ | ✅ 15+ |
| 多态类型 | ✅ @JsonTypeInfo | ✅ 更丰富 | ✅ @Type |
| JSON Patch | ✅ 新增 | ✅ | ✅ |
| JSON Merge Patch | ✅ 新增 | ✅ | ✅ |
| JSON Lines | ✅ 新增 | ❌ 需第三方 | ❌ |
| JSON Schema | ✅ | ✅ 独立模块 | ❌ |
| AutoType 安全 | ✅ 默认白名单 | ⚠️ 需配置 | ⚠️ 需配置 |
| 字节码加速 | ⚠️ MethodHandle | ✅ Afterburner | ✅ ASM |
| 零外部依赖 | ✅ | ❌ | ❌ |

---

## 七、测试覆盖率目标

| 维度 | 目标 | 当前（1.2.0） |
|------|------|-------------|
| 核心路径（Round-Trip） | >80% | ✅ 已覆盖 |
| 安全边界 | >90% | ✅ 已覆盖 |
| 注解行为 | >70% | ✅ 已覆盖 |
| JSON Patch/Merge | >90% | ⚠️ 待补充单元测试 |
| JSON Lines | >80% | ⚠️ 待补充单元测试 |

---

## 八、后续规划（1.3.0+）

### Phase 2 性能优化
- [ ] BeanSerializer 预计算（减少反射遍历）
- [ ] 直接字节输出路径（bypass StringBuilder）
- [ ] SIMD Vector API 实验性加速（JDK 21+）

### Phase 3 生态
- [ ] Kotlin 扩展函数
- [ ] GraalVM Native Image 支持
- [ ] Micrometer 指标绑定（AutoType 拒绝率、缓存命中率）

### Phase 4 用户体验
- [ ] 编译期注解处理器（APT）
- [ ] 在线格式化工具（`YdszJson.format` 增强）
- [ ] Debug/Trace 模式（字段路径追踪）

---

## 九、破坏性变更

**无**。1.2.0 是向后兼容的功能增强版本。

新增的 API：
- `YdszJson.parsePatch()` / `YdszJson.applyPatch()` / `YdszJson.applyMergePatch()`
- `TypeRef` 工具类
- `JsonLines` 工具类

所有现有 API 保持不变。

---

## 十、致谢

对标参考：
- Jackson `ObjectMapper` / `JsonPatch` / `TypeReference`
- Fastjson2 `JSON` / `JSONReader`
- RFC 6902 (JSON Patch)
- RFC 7396 (JSON Merge Patch)
- JMH (Java Microbenchmark Harness)

---

*文档版本: 1.2.0-RELEASE-NOTES | 最终更新: 2026-08-09*
