# GraalVM Native Image 试点方案（ydsz-json 模块）

## 目标

将 ydsz-json（核心 JSON 引擎 YdszJson + ASM 字节码序列化）改造为可在 GraalVM Native Image 中运行：

- **启动时间**：JVM 冷启动 ~1.5s → Native Image 目标 < 100ms（~15x 提升）
- **内存占用**：RSS 减少 50%+
- **容器镜像体积**：~200MB → < 80MB（compressed）

## 兼容性评估

| 运行时特性 | GraalVM Native Image 兼容 | 当前 ydsz-json 使用 | 风险 |
|---|---|---|---|
| ASM 运行期字节码生成 (`asm.ClassWriter`) | ❌ 不支持 | `AsmBeanCodecGenerator`, `AsmSerializer`, `AsmDeserializer` | **P0 阻塞** |
| `java.lang.invoke.MethodHandle` | ⚠️ 需配置 | `FrequencySketch.VarHandle`（非 ydsz-json） | 低 |
| 反射 (`Class.forName`, `Field.get/set`) | ⚠️ 需注册配置 | `SerializerRegistry`, `DeserializationProvider`, `TypeFactory` | **P1 必需** |
| ThreadLocal 缓存 | ✅ 支持 | `ThreadLocal` JSONWriter 缓冲 | 低 |
| 软引用缓存 (`SoftReference` LRU) | ✅ 支持 | `AsmCodecCache.LruSoftCache` | 低 |
| `java.io.Serializable` | ✅ 支持（需注册） | `TokenUsage` / `Serializable` 接口 | 中 |
| AOT 编译友好 (`@IntrospectedBeanInfo`) | ✅ 新功能 | — | 待验证 |

### 结论

**当前 ydsz-json 不支持 GraalVM Native Image 的直接打包，主要障碍是运行时 ASM 字节码生成。**

## 解决方案：双模式序列化器

将 YdszJson 拆分为两种序列化器路径：

1. **ASM 模式**（默认，JVM 下使用，性能最高）：保留当前实现
2. **反射模式**（GraalVM fallback）：用反射 + Jackson-annotations 兼容 GraalVM

### 关键改造点

#### (a) AsmCodecCache 添加 GraalVM 探测

```java
// 新增 GraalVM 工具类
public final class NativeImageDetector {
    private static final boolean NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    public static boolean isInNativeImage() { return NATIVE_IMAGE; }
}
```

#### (b) AsmCodecCache 在 Native Image 下跳过 ASM 生成

```java
// AsmCodecCache 修改
public AsmSerializer<?> getSerializer(Class<?> type) {
    if (ImageInfo.inImageRuntimeCode()) {
        // GraalVM 下走反射路径
        return null;  // 由 AsmBeanCodecGenerator 特殊处理
    }
    // 正常 ASM 生成路径
}
```

#### (c) 反射式序列化器（新增 `ReflectiveSerializer`）

- 预扫描类字段、@JsonProperty 注解
- 通过预先注册的 `MethodHandle` 访问字段（通过 `reflection-config.json` 提前注册）
- 性能为 ASM 的 60-70%，但兼容 Native Image

#### (d) AOT 自动配置处理

新增 `YdszJsonFeature` 实现 `org.springframework.nativex.hint.TypeHint`，在 Spring Boot AOT 阶段注册所有已知的反射元数据。

## 试点范围（分阶段）

### Phase 1：独立 Native Image 验证（1 周）

目标：将 YdszJson 单元测试编译为 Native Image 通过

```bash
# 构建 Native Image
cd ydsz-common-json
mvn native:compile -Pnative -DskipTests=false
# 运行 Native Image 单测
mvn native:test -Pnative
```

### Phase 2：集成 Spring Boot Native（2 周）

- ydsz-json 接入 Spring Boot NativeImage 运行时
- 所有依赖模块的 `reflection-config.json` 收集合并
- Prometheus Micrometer Native Image 兼容性验证

### Phase 3：业务试点（3 周）

- 选取 ydssz-agent 或 ydsz-message 模块 作为业务试点
- 端到端功能回归测试
- 启动时间与内存基准对比

## Maven Profile 配置

在 `ydsz-common-json/pom.xml` 中添加：

```xml
<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <version>${native-maven-plugin.version}</version>
                    <extensions>true</extensions>
                    <executions>
                        <execution>
                            <id>test-native</id>
                            <goals>
                                <goal>test</goal>
                            </goals>
                            <phase>test</phase>
                        </execution>
                        <execution>
                            <id>build-native</id>
                            <goals>
                                <goal>compile-no-fork</goal>
                            </goals>
                            <phase>package</phase>
                        </execution>
                    </executions>
                    <configuration>
                        <imageName>ydsz-common-json</imageName>
                        <mainClass>com.njydsz.common.json.YdszJson</mainClass>
                        <buildArgs>
                            <buildArg>--no-fallback</buildArg>
                            <buildArg>--verbose</buildArg>
                            <buildArg>-H:+ReportExceptionStackTraces</buildArg>
                            <buildArg>-H:ReflectionConfigurationFiles=../reflect-config.json</buildArg>
                            <buildArg>-H:ResourceConfigurationFiles=../resource-config.json</buildArg>
                        </buildArgs>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

## 需要的反射配置文件

路径：`src/main/resources/META-INF/native-image/com.njydsz/ydsz-common-json/reflect-config.json`

```json
[
  {
    "name": "com.njydsz.common.json.asm.AsmBeanCodecGenerator",
    "allDeclaredConstructors": true,
    "allDeclaredFields": true
  },
  {
    "name": "com.njydsz.common.json.provider.SerializationProvider",
    "allDeclaredMethods": true
  },
  {
    "name": "com.njydsz.common.json.provider.DeserializationProvider",
    "allDeclaredMethods": true
  },
  {
    "name": "com.njydsz.common.json.serializer.SerializerRegistry",
    "allDeclaredMethods": true
  }
]
```

## CI 集成脚本

```yaml
# .github/workflows/native-image-check.yml
name: GraalVM Native Image Build Check
on:
  push:
    paths:
      - 'ydsz-common/ydsz-common-json/**'
      - '.github/workflows/native-image-check.yml'

jobs:
  native-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21'
          distribution: 'graalvm'
          components: 'native-image'
          github-token: ${{ secrets.GITHUB_TOKEN }}
      - name: Build Native Image
        run: |
          cd ydsz-common/ydsz-common-json
          mvn native:compile -Pnative -DskipTests
```

## 风险与回滚

| 风险 | 缓解措施 |
|---|---|
| Native Image 构建时间过长（10-30 分钟） | CI 并行构建；失败时回退 JVM 模式 |
| 反射配置遗漏导致启动 `NullPointerException` | 用 ` native-image-agent` 自动收集 |
| 性能回退（反射模式） | 关键路径保留 ASM；仅 fallback 路径降级 |
| 维护双份代码的复杂度 | 明确标记 `@VisibleForTesting` 路径 |

## 自动收集反射配置

```bash
# 使用 native-image-agent 在 JVM 测试中收集
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/com.njydsz/ydsz-common-json \
     -jar target/ydsz-common-json.jar
```

## 验收标准

- [ ] 单元测试通过（Native Image 模式）
- [ ] Spring Boot 应用成功启动（Native Image）
- [ ] 启动时间 < 100ms
- [ ] 内存占用减少 50%+
- [ ] JSON 序列化/反序列化基准性能不低于 JVM 模式的 60%

---

**创建时间**：2026-08-04  
**维护者**：ydsz-team  
**当前状态**：方案阶段（待 Phase 1 启动）
