# remi-common-excel

> 高性能 Excel 读写引擎（L5 业务服务层）— 双引擎架构（零 POI 快速路径 + POI 兼容路径）、SAX 流式读、流式写、并发写、模板填充、ASM 字节码加速、列式存储（Parquet/ORC）、Tabular 统一 API、公式注入防护。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 80+ |
| **版本** | 1.0.0 |
| **依赖** | remi-common-exception、Apache POI、ASM、SLF4J、Micrometer（可选）、Spring Boot（可选） |

## 核心能力

### 1. 门面 API

| 类 | 说明 |
|---|---|
| `ExcelFacade` | 统一入口（读 / 写 / 模板 / 多 Sheet / Web 下载），构建器 + 快捷方法双风格 |
| `ExcelReader` | Excel 读取器（支持监听器模式、批量回调、进度回调） |
| `ExcelWriter` | Excel 写入器（链式 sheet / headRowNumber / doWrite） |
| `ExcelSheetInfo` | Sheet 元信息查询（名称、数量、信息列表） |
| `ExcelTemplateWriter` | 模板填充（保留模板样式、公式，可指定数据起始行） |
| `ConcurrentExcelWriter` | 并发写入（多线程分片预序列化 + 顺序写入 ZIP，OOXML 顺序约束下接近线性加速） |

### 2. SAX 流式读（大文件不 OOM）

| 类 | 说明 |
|---|---|
| `SuperFastExcelReader` | 零 POI 路径读取器，ZipInputStream + 手工 XML 解析，100K 行 ~300ms / ~50MB |
| `SheetXmlReader` | Sheet XML 流式解析器（大 sheet 自动切换临时文件管道） |
| `SharedStringsReader` | 共享字符串表（SST）流式按需加载 |
| `ChunkedSSTTable` | 分块 SST 表，控制内存占用 |
| `HeaderAnalyzer` | 表头识别与列元数据构建 |
| `RowParser` | 行解析器（结合列元数据生成对象） |
| `ExcelXmlParser` | XML 底层解析工具 |

性能对比（100K 行）：

| 读取方式 | 耗时 | 内存占用 |
|---|---|---|
| POI XSSFWorkbook 用户模式 | ~1500ms | ~500MB |
| SAX + POI 组件 | ~800ms | ~200MB |
| 本库 XML 手工解析 | ~300ms | ~50MB |

### 3. 流式写

| 类 | 说明 |
|---|---|
| `SuperFastExcelWriter` | 零 POI 路径写入器，直接生成 OOXML 字节流写入 ZIP 包 |
| `UltraFastCellWriter` | 超快单元格写入器（行级 1MB 缓冲） |
| `StyleManager` | 样式管理器（LRU 缓存 + StyleKey 复用） |
| `WriteStyleHandler` | 写入样式处理器（表头 / 数据样式） |
| `ValueFormatter` | 值格式化器（DateTimeFormatter 缓存 + 公式注入防护） |
| `WorkbookFactory` | Workbook 工厂（按数据量选择 SXSSF / XSSF） |
| `PrecomputedColumnProperties` | 列属性预计算（宽度、顺序） |

### 4. 注解驱动

| 注解 | 作用域 | 说明 |
|---|---|---|
| `@ExcelProperty` | 字段 / 类型 | 列映射（value / index / order / dateFormat / numberFormat / width / formula / maxLength / minValue / maxValue / pattern / converterClass） |
| `@ExcelHead` | 字段 | 表头样式（名称、宽度、加粗、字体颜色、背景色） |
| `@ExcelSheet` | 类型 | Sheet 配置（name / sheetNo / headRowNumber / freezePane / mergedRegions） |
| `@ExcelIgnore` | 字段 | 忽略字段（不参与映射） |
| `@ExcelStyle` | 字段 | 单元格样式（表头加粗、字体颜色、背景填充、数据样式类） |
| `@ContentStyle` | 字段 | 内容样式（hidden / locked / 对齐 / 背景色 / dataFormat / wrapText / shrinkToFit） |
| `@ContentFont` | 字段 | 内容字体（fontName / fontSize / bold / italic / color） |

### 5. 类型转换

| 类 / 接口 | 说明 |
|---|---|
| `CellValueConverter` | 单元格值转换器 SPI（`supports` + `convert` + `priority`，数值越小优先级越高，默认 100） |
| `ConverterRegistry` | 转换器注册中心（双重检查锁定懒初始化默认链，支持 `registerCustomConverter`） |
| `ConverterChain` | 责任链（CopyOnWriteArrayList + 按 priority 排序） |
| `ConvertContext` | 转换上下文（携带日期格式、是否严格数字等） |
| `BigDecimalConverter` | BigDecimal 转换器（priority=40） |
| `DateConverter` | Date 转换器（priority=50） |
| `LocalDateTimeConverter` | LocalDateTime 转换器（priority=60） |
| `LocalDateConverter` / `LocalTimeConverter` / `YearMonthConverter` / `TimestampConverter` | 时间类型转换器 |
| `EnumConverter` | 枚举转换器（priority=110） |
| `StringConverter` / `NumberConverter` / `BooleanConverter` | 基础类型转换器 |

### 6. ASM 字节码加速

| 类 | 说明 |
|---|---|
| `ASMFieldAccessor` | ASM 字段访问器（动态生成 Getter / Setter / Instantiator 字节码） |
| `FieldGetter` / `FieldSetter` / `ObjectInstantiator` | 字段访问接口（直接 getfield / putfiled / new，跳过反射） |
| `ClassMetadataCache` | 类元数据缓存（读写分离缓存 `@ExcelProperty` 解析结果） |
| `ReflectCache` | 反射缓存（Field / MethodHandle / ASM Getter / Setter / Instantiator 复合缓存） |

性能对比（百万次访问）：

| 访问方式 | 耗时 | 性能倍数 |
|---|---|---|
| Native Reflection | ~3000ms | 1x |
| MethodHandle | ~500ms | ~6x |
| ASM Bytecode | ~100ms | ~30x |

安全机制：

- 单独 `GeneratedClassLoader` 加载生成类
- 生成类数量阈值 `MAX_GENERATED_CLASS_COUNT = 5000`，超限自动降级到 MethodHandle / 反射，防止 Metaspace OOM
- ASM 生成失败时无缝回退到 MethodHandle，再回退到原生反射

### 7. 性能对象池

| 类 | 说明 |
|---|---|
| `ObjectPool<T>` | 通用对象池（ConcurrentLinkedQueue 无锁 borrow / release + 校验器 + 泄漏检测） |
| `StylePool` | 单元格样式池（LRU 缓存 + StyleKey Builder 模式，默认容量 1000） |
| `GlobalObjectPool` | 全局对象池单例（多类型池统一管理 + StringPool / DatePool intern 优化） |
| `LRUCache<K,V>` | LRU 缓存（LinkedHashMap accessOrder + 命中率统计 + 懒加载 `getOrLoad`） |

对象池特性：

- `ObjectPool` 支持 `PooledObject` 自动关闭包装器（try-with-resources）
- 泄漏检测超时默认 5 分钟，超时记录 WARN 日志
- 100K 行场景下减少 50-70% 对象创建，降低 GC 压力 30-40%

### 8. 列式存储

| 类 / 枚举 | 说明 |
|---|---|
| `ColumnarType` | 列式类型枚举（BOOLEAN / INT32 / INT64 / FLOAT / DOUBLE / STRING / BINARY / DATE / TIMESTAMP / DECIMAL，屏蔽 Parquet/ORC 底层差异） |
| `ColumnarField` | 列式字段元数据（name / type / nullable / precision / scale / comment，Builder 模式） |
| `ColumnarSchema` | 列式表结构（有序字段列表 + 索引映射 + headerNames） |
| `ColumnarRowMapper<T>` | 列式行映射器（`Object[]` 输入输出，避免 String 反复转换） |
| `ColumnarConfig` | 列式通用配置基类（batchSize / compression / enableDictionary / withHeader） |
| `ParquetConfig` | Parquet 配置（rowGroupSize=128MB / pageSize=1MB / WriteMode=CREATE\|OVERWRITE） |
| `OrcConfig` | ORC 配置（stripeSize=64MB / indexStride=10000 / WriteStrategy=COMPRESSION\|SPEED） |
| `ColumnarCompression` | 压缩编解码器（NONE / SNAPPY / GZIP / LZ4 / ZSTD，默认 SNAPPY） |

Parquet/ORC 类型映射详见 `ColumnarType` 类内表格。

### 9. Tabular 统一 API

| 类 / 接口 | 说明 |
|---|---|
| `TabularFormat` | 统一格式枚举（EXCEL_XLS / EXCEL_XLSX / CSV / TSV / PARQUET / ORC，支持扩展名与 Content-Type 识别） |
| `TabularReader<T>` | 统一读取接口（`open` → `readAll` → `close`，实现 Closeable） |
| `TabularWriter<T>` | 统一写入接口（`open` → `write` / `writeAll` → `flush` → `close`） |
| `TabularRowMapper<T>` | 统一行映射器（headers + toRow(String[]) + fromRow(T)） |
| `TabularReadContext` | 读取上下文（format / headers / currentRowNumber / processedCount / errorCount / cancel） |
| `TabularWriteContext` | 写入上下文（format / writtenCount / elapsed） |
| `TabularReadListener<T>` | 读取监听器（onOpen / onRow / onBatch / onError / onClose） |
| `TabularWriteListener` | 写入监听器 |
| `DefaultAnnotationRowMapper<T>` | 基于注解的默认行映射器实现 |

### 10. 事件回调

| 接口 | 说明 |
|---|---|
| `ReadListener<T>` | 读取监听器（onStart / onData / onEnd / onError / onProgress / onBatchData） |
| `ReadHandler` | 读取处理器（行级钩子） |
| `WriteHandler` | 写入处理器（表头 / 行 / 单元格级钩子） |
| `AnalysisContext` | 读取分析上下文（当前行号、总行数、Sheet 信息） |
| `WriteContext` | 写入上下文（已写行数、Sheet 信息） |

### 11. 公式注入防护

| 类 | 说明 |
|---|---|
| `FormulaInjectionGuard` | 公式注入防护工具类（检测 `=` / `+` / `-` / `@` 前缀，自动添加 `'` 前缀转义） |

默认开启（`remi.excel.formula-injection-protection=true`），覆盖所有写入路径（`SuperFastExcelWriter` / `ValueFormatter` / `ConcurrentExcelWriter`）。

### 12. Spring 集成

| 类 | 说明 |
|---|---|
| `ExcelAutoConfiguration` | 自动配置（`@AutoConfiguration`，`remi.excel.enabled=true` 默认激活） |
| `ExcelProperties` | 配置属性（`@ConfigurationProperties(prefix = "remi.excel")`，JSR-303 校验） |
| `ExcelConfig` | 全局配置单例（双重检查锁定，所有读写共用） |
| `ExcelTemplate` | Spring 模板类（封装 `ExcelFacade`，可注入使用） |
| `ExcelWebSupport` | Web 下载支持（Content-Type / Content-Disposition / UTF-8 文件名编码） |
| `DownloadContext` | 下载 ThreadLocal 上下文（文件名 / Sheet 名，请求结束自动清理） |
| `ExcelExportHelper` | 导出辅助工具 |

### 13. 健康检查与指标

| 类 | 说明 |
|---|---|
| `ExcelHealthIndicator` | Spring Boot Actuator 健康检查（fastReader / fastWriter / 公式防护 / 文件大小限制 / 临时目录可写） |
| `ExcelMetrics` | Micrometer 指标采集（读写耗时 / 行数 / 失败次数 / 缓存命中） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-excel</artifactId>
</dependency>
```

Spring Boot 应用会通过 `spring.factories` / `AutoConfiguration.imports` 自动激活 `ExcelAutoConfiguration`。

### 2. 配置启用

```yaml
remi:
  excel:
    enabled: true                       # 默认激活
    use-fast-reader: true               # 启用零 POI 快速读取路径
    use-fast-writer: true               # 启用零 POI 快速写入路径
    formula-injection-protection: true  # 启用公式注入防护
```

### 3. 注入使用

```java
import java.io.OutputStream;
import java.util.List;

import com.remisoft.common.excel.spring.ExcelTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private ExcelTemplate excelTemplate;

    public void export(OutputStream out, List<User> data) {
        excelTemplate.write(out, User.class, data, "用户列表");
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.excel.enabled` | `true` | 是否启用 Excel 模块自动配置 |
| `remi.excel.read-buffer-size` | `8192` | 读取缓冲区大小（字节，最小 1024） |
| `remi.excel.write-buffer-size` | `8192` | 写入缓冲区大小（字节，最小 1024） |
| `remi.excel.default-date-format` | `yyyy-MM-dd HH:mm:ss` | 默认日期格式 |
| `remi.excel.default-number-format` | `#,##0.00` | 默认数字格式 |
| `remi.excel.automatic-trim` | `true` | 是否自动去除字符串首尾空格 |
| `remi.excel.use-fast-reader` | `true` | 是否启用快速读取引擎（SuperFastExcelReader） |
| `remi.excel.use-fast-writer` | `true` | 是否启用快速写入引擎（SuperFastExcelWriter） |
| `remi.excel.streaming-parse-threshold-mb` | `10` | 流式解析文件大小阈值（MB，1-500） |
| `remi.excel.max-read-file-size-mb` | `100` | 最大读取文件大小（MB，1-1024） |
| `remi.excel.max-write-file-size-mb` | `50` | 最大写入文件大小（MB，1-512） |
| `remi.excel.compression-level` | `1`（BEST_SPEED） | ZIP 压缩级别（-1~9） |
| `remi.excel.formula-injection-protection` | `true` | 是否启用公式注入防护 |
| `remi.excel.strict-number-conversion` | `false` | 是否启用严格数字转换（失败抛异常） |
| `remi.excel.use-1904-windowing` | `false` | 是否使用 1904 日期窗口（Mac 版 Excel 兼容） |
| `remi.excel.head-row-number` | `1` | 默认表头行号 |
| `remi.excel.write-cache-size` | `100` | SXSSF 写入缓存行数 |

## 使用示例

### 1. 简单读取

```java
import com.remisoft.common.excel.core.ExcelFacade;
import java.util.List;

List<User> users = ExcelFacade.read("demo.xlsx", User.class).doReadAll();
```

### 2. 大文件流式读取（SAX）

```java
import java.util.List;

import com.remisoft.common.excel.core.ExcelFacade;
import com.remisoft.common.excel.core.context.AnalysisContext;
import com.remisoft.common.excel.core.listener.ReadListener;

ExcelFacade.read("large.xlsx", User.class)
    .sheet("用户数据")
    .batchSize(1000)
    .doRead(new ReadListener<List<User>>() {
        @Override
        public void onStart(AnalysisContext context) {}

        @Override
        public void onData(AnalysisContext context, List<User> batch) {
            batchService.saveBatch(batch);  // 批量入库
        }

        @Override
        public void onEnd(AnalysisContext context) {}
    });
```

### 3. 模板填充

```java
import com.remisoft.common.excel.core.ExcelFacade;

ExcelFacade.writeWithTemplate("template.xlsx", "output.xlsx", User.class)
    .sheet(0)
    .dataStartRow(3)
    .doWrite(userList);
```

### 4. 并发写入

```java
import com.remisoft.common.excel.core.ConcurrentExcelWriter;

ConcurrentExcelWriter.write("output.xlsx", User.class, largeDataList)
    .parallelism(4)
    .chunkSize(10000)
    .doWrite();
```

### 5. 列式存储导出（Parquet）

```java
import com.remisoft.common.excel.columnar.ColumnarCompression;
import com.remisoft.common.excel.columnar.ColumnarField;
import com.remisoft.common.excel.columnar.ColumnarSchema;
import com.remisoft.common.excel.columnar.ColumnarType;
import com.remisoft.common.excel.columnar.ParquetConfig;

ColumnarSchema schema = ColumnarSchema.builder()
    .addField(ColumnarField.of("id", ColumnarType.INT64, false))
    .addField(ColumnarField.of("name", ColumnarType.STRING, true))
    .addField(ColumnarField.builder("amount", ColumnarType.DECIMAL)
        .precision(18).scale(2).build())
    .build();

ParquetConfig config = ParquetConfig.builder()
    .compression(ColumnarCompression.SNAPPY)
    .rowGroupSize(128L * 1024L * 1024L)
    .pageSize(1024 * 1024)
    .build();
```

### 6. Tabular 统一 API

```java
import com.remisoft.common.excel.tabular.TabularFormat;
import com.remisoft.common.excel.tabular.TabularReader;
import com.remisoft.common.excel.tabular.TabularReadListener;

TabularFormat format = TabularFormat.fromExtension("users.csv").orElseThrow();
try (TabularReader<User> reader = csvReaderBuilder.format(format).build()) {
    reader.open();
    reader.readAll((ctx, user) -> System.out.println(user));
}
```

### 7. 自定义类型转换器

```java
import com.remisoft.common.excel.converter.CellValueConverter;
import com.remisoft.common.excel.converter.ConverterRegistry;
import com.remisoft.common.excel.converter.ConvertContext;

public class MyTypeConverter implements CellValueConverter {
    @Override
    public boolean supports(Class<?> targetType) {
        return MyType.class.isAssignableFrom(targetType);
    }

    @Override
    public Object convert(Object rawValue, Class<?> targetType, ConvertContext context) {
        return new MyType(String.valueOf(rawValue));
    }

    @Override
    public int priority() {
        return 50;  // 高于默认 100，优先于内置转换器
    }
}

ConverterRegistry.registerCustomConverter(new MyTypeConverter());
```

## SPI 扩展点

| 接口 | 说明 |
|---|---|
| `CellValueConverter` | 单元格值转换器（`supports` + `priority` 控制优先级） |
| `ReadListener<T>` | 读取监听器（流式回调 + 批量回调 + 进度回调） |
| `ReadHandler` / `WriteHandler` | 读写处理器（行 / 单元格级钩子） |
| `TabularRowMapper<T>` | 统一行映射器（Excel / CSV / TSV / Parquet / ORC 通用） |
| `ColumnarRowMapper<T>` | 列式行映射器（基于 `ColumnarSchema`，`Object[]` 传递） |
| `TabularReadListener<T>` / `TabularWriteListener` | Tabular 统一监听器 |
| `ObjectPool.ObjectFactory<T>` / `ObjectPool.Resetter<T>` | 对象池工厂与重置器 |

## 性能优化建议

| 场景 | 推荐配置 | 原因 |
|---|---|---|
| 大文件读（>10MB） | `use-fast-reader: true` + `streaming-parse-threshold-mb: 10` | SuperFastExcelReader 内存占用仅 ~50MB，POI 用户模式 ~500MB |
| 大文件写（>10万行） | `use-fast-writer: true` | SuperFastExcelWriter 绕过 POI 对象模型，1MB 行级缓冲 |
| 超大数据写（>100万行） | `ConcurrentExcelWriter.parallelism(CPU核数)` | 多线程分片预序列化 + 顺序写入，接近线性加速 |
| 高频小对象 | 注入 `ObjectPool` / `GlobalObjectPool` | 减少 GC 压力 30-40% |
| 字段反射热点 | 默认启用 ASM（无需配置） | ASM Getter 比反射快 30 倍 |
| 低基数列存储 | `ColumnarCompression.SNAPPY` + `enableDictionary: true` | 字典编码显著减小文件体积 |
| 临时对象复用 | `GlobalObjectPool.borrowObject` / `returnObject` | 池化复用，try-with-resources 自动归还 |

ASM 加速启用条件：

- 默认启用，无需配置
- 当生成类数量超过 5000 时自动降级到 MethodHandle（防止 Metaspace OOM）
- 通过 `ASMFieldAccessor.isFallbackToReflection()` 可查询当前状态

## 健康检查

访问 Spring Boot Actuator `/actuator/health` 端点，`excel` 健康检查项展示：

```json
{
  "status": "UP",
  "components": {
    "excel": {
      "status": "UP",
      "details": {
        "fastReader": true,
        "fastWriter": true,
        "formulaInjectionProtection": true,
        "maxReadFileSizeMB": 100,
        "maxWriteFileSizeMB": 50,
        "streamingParseThresholdMB": 10,
        "tempDirWritable": true,
        "compressionLevel": 1
      }
    }
  }
}
```

### Micrometer 指标

| 指标 | 类型 | 标签 | 说明 |
|---|---|---|---|
| `excel.write.duration` | Timer | engine, result | 写入耗时（P50/P90/P99） |
| `excel.read.duration` | Timer | engine, result | 读取耗时（P50/P90/P99） |
| `excel.rows.written` | Counter | engine | 写入行数 |
| `excel.rows.read` | Counter | engine | 读取行数 |
| `excel.write.failures` | Counter | engine | 写入失败次数 |
| `excel.read.failures` | Counter | engine | 读取失败次数 |
| `excel.cache.hits` | Counter | - | 缓存命中次数 |
| `excel.cache.misses` | Counter | - | 缓存未命中次数 |

## 注意事项

- **公式注入防护**：默认开启，覆盖所有写入路径。如业务确实需要写入公式（`@ExcelProperty.formula()`），公式本身不会被防护，但用户数据中的 `=` / `+` / `-` / `@` 前缀会被自动转义
- **大文件内存配置**：读取大于 `streamingParseThresholdMB`（默认 10MB）的文件时自动切换临时文件管道，确保 `java.io.tmpdir` 可写（健康检查会监控）
- **样式池容量**：`StylePool` 默认容量 1000，超过 LRU 淘汰。如自定义样式种类极多，可通过构造器调整 `new StylePool(maxSize)`
- **ASM 降级**：生成类超过 5000 自动降级，可通过 `ASMFieldAccessor.clearCache()` 手动重置
- **对象池泄漏**：`ObjectPool.borrow()` 必须配对 `release()`，推荐使用 `borrowAutoClose()` + try-with-resources
- **SXSSF 缓存**：`write-cache-size` 默认 100 行，写超大数据时建议调大（如 1000），但会增加内存占用
- **1904 日期窗口**：Mac 版 Excel 默认使用 1904 日期系统，读取此类文件需开启 `use-1904-windowing: true`

## 变更记录

- **v1.0.0**（2026-08-02）：中文化 README + 补全 ASM 字节码加速、列式存储（Parquet/ORC）、Tabular 统一 API、注解（`@ExcelHead` / `@ContentStyle` / `@ContentFont`）、性能对象池（`ObjectPool` / `StylePool` / `GlobalObjectPool` / `LRUCache`）章节
