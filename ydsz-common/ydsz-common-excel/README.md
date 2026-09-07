# ydsz-common-excel

> 高性能 Excel 读写引擎（L1 工具模块层）— 双引擎读写 + 模板导出 + 公式注入防护

提供 SuperFast 零 POI 快速路径 + POI 兼容路径的双引擎读写、12 种类型 ConverterChain、MethodHandle/ASM 字段访问、FormulaInjectionGuard 公式注入防护、MetadataCache 元数据缓存、Micrometer 可观测性、模板导出等企业级能力，是所有业务模块 Excel 处理统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供高性能 Excel 读写、模板导出、公式注入防护、类型安全转换 |
| **依赖** | poi 5.x、poi-ooxml、jakarta.validation-api、jackson-annotations、slf4j-api、micrometer-core；可选 spring-boot-autoconfigure、spring-boot-actuator、spring-web、lombok |
| **版本** | 2.1.0 |

## 核心能力

### 1. 双引擎读写架构

| 引擎 | 适用场景 | 性能特征 |
|---|---|---|
| **SuperFastExcelReader / SuperFastExcelWriter** | 大数据量纯数据读写（10w+ 行） | 零 POI 对象模型，流式 SAX 解析，内存占用 < 100MB |
| **POI 兼容路径（ExcelReader / ExcelWriter）** | 复杂格式（公式/样式/图表） | 基于 SXSSF/SAX 事件模型 |

**自动选择**：`ExcelFacade` 根据文件大小 + 灰度配置自动路由引擎。文件 > 10MB 默认走 SuperFast，可通过配置强制路由。

### 2. 核心读写器

| 类 | 说明 |
|---|---|
| `ExcelFacade` | Excel 门面入口，封装引擎选择 + 公共参数 |
| `ExcelReader` | 读取器入口（统一 API，内部路由 SuperFast / POI） |
| `ExcelWriter` | 写入器入口 |
| `ExcelTemplateWriter` | 模板填充写入器（替换模板占位符） |
| `ExcelSheetInfo` | Sheet 元数据（名称、索引、行数） |
| `RawSheetData` | 原始 Sheet 数据（无类型，全 Sheet 一次读取） |

### 3. 注解声明式映射

| 注解 | 说明 |
|---|---|
| `@ExcelProperty` | 字段 → 列索引/名称映射 |
| `@ExcelHead` | 表头行数声明 |
| `@ExcelIgnore` | 忽略字段 |
| `@ExcelSheet` | Sheet 名称/索引绑定 |
| `@ExcelStyle` | 单元格样式（字体/边框/背景） |
| `@ContentStyle` | 内容区域样式 |
| `@ContentFont` | 内容字体配置 |

### 4. 类型转换器链

| 类 | 说明 |
|---|---|
| `CellValueConverter` **SPI** | 单元格值转换器接口（支持 `priority()` 排序） |
| `ConverterChain` | 转换器链（责任链模式，按 priority 顺序匹配） |
| `ConverterRegistry` | 转换器注册表 |
| `ConvertContext` | 转换上下文（含 Workbook / Locale 等） |

内置 12 种类型转换器：

| 转换器 | 目标类型 |
|---|---|
| `BigDecimalCellConverter` | BigDecimal |
| `BooleanCellConverter` | Boolean |
| `DateCellConverter` | java.util.Date |
| `LocalDateCellConverter` | LocalDate |
| `LocalDateTimeCellConverter` | LocalDateTime |
| `LocalTimeCellConverter` | LocalTime |
| `NumberCellConverter` | Number (Integer/Long/Double) |
| `StringCellConverter` | String |
| `TimestampCellConverter` | Timestamp |
| `YearMonthCellConverter` | YearMonth |

### 5. 公式注入防护

| 类 | 说明 |
|---|---|
| `FormulaInjectionGuard` | 公式注入防护（`=` / `+` / `-` / `@` 开头的单元格值添加单引号前缀，防止 CSV 公式注入） |

### 6. 元数据缓存与性能优化

| 类 | 说明 |
|---|---|
| `MetadataCache` | 读写元数据缓存（字段映射、格式信息） |
| `ReadMetadata` / `WriteMetadata` | 读/写元数据封装 |
| `WriteMetadataBuilder` | 写元数据构建器 |
| `ClassMetadataCache`（support.cache） | 类级别元数据缓存（字段/注解） |
| `LRUCache`（support.cache） | 辅助 LRU 缓存 |
| `ReflectCache`（support.cache） | 反射缓存（MethodHandle） |
| `ASMFieldAccessor`（support.asm） | ASM 字节码字段访问器（替代反射，提升 3-5 倍） |

### 7. 监听器与上下文

| 类 | 说明 |
|---|---|
| `ReadListener` / `ReadHandler` | 读取监听器 / 处理器（行级回调） |
| `WriteHandler` / `WriteLifecycleHandler` | 写入监听器 / 生命周期回调 |
| `AnalysisContext`（config.context） | 读分析上下文 |
| `WriteContext`（config.context） | 写上下文 |

### 8. SAX 底层解析

| 类 | 说明 |
|---|---|
| `SheetXmlReader`（reader.sax） | Sheet XML SAX 解析器 |
| `SharedStringsReader`（reader.sax） | 共享字符串表解析器 |
| `StylesReader`（reader.sax） | 样式表解析器 |
| `ExcelXmlParser`（reader） | Excel XML 入口解析器 |
| `HeaderAnalyzer`（reader） | 表头分析器（自动检测表头行） |
| `ColumnMetadata`（reader） | 列元数据 |
| `RowParser`（reader） | 行解析器 |
| `SimpleCell`（reader） | 简易单元格封装 |
| `InputSourceDetector`（reader） | 输入源自动检测（InputStream / File / byte[]） |
| `ChunkedSSTTable`（reader） | 分块共享字符串表（处理超大型 SST） |

### 9. Spring 集成

| 类 | 说明 |
|---|---|
| `ExcelAutoConfiguration` | 自动配置入口 |
| `ExcelProperties` | Excel 配置属性（`ydsz.excel.*`） |
| `ExcelHealthIndicator` | 健康检查 |
| `ExcelTemplate` | Spring 模板工具 |
| `ExcelWebSupport` | Web 集成（自动处理下载响应头） |
| `ExcelExportHelper` | 导出辅助工具 |

### 10. 可观测性

| 类 | 说明 |
|---|---|
| `ExcelMetrics` | Micrometer 指标（read/write 计数、耗时分位数） |

### 11. CSV 支持

| 类 | 说明 |
|---|---|
| `TabularRowMapper` | 表格行映射器（CSV / 二维数据） |
| `DefaultAnnotationRowMapper` | 基于注解的默认行映射器 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-excel</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  excel:
    engine: AUTO             # AUTO / SUPER_FAST / POI
    default-sheet-name: Sheet1
    max-rows-per-sheet: 1048576
    formula-injection-guard: true
    buffer-size: 8192
    cache-metadata: true
    health-check-enabled: true
```

### 3. 直接使用

```java
import com.njydsz.common.core.excel.core.ExcelFacade;
import com.njydsz.common.core.excel.annotation.ExcelProperty;

// 数据读取（注解映射）
@Data
public class UserRow {
    @ExcelProperty(index = 0) private String name;
    @ExcelProperty(index = 1) private Integer age;
    @ExcelProperty(index = 2) private LocalDate birthday;
}

List<UserRow> rows = ExcelFacade.read(inputStream, UserRow.class);

// 模板导出
Map<String, Object> params = Map.of("title", "用户报表", "rows", userRows);
ExcelFacade.writeTemplate(templateStream, outputStream, params);

// 原始数据读取（无类型）
RawSheetData rawSheet = ExcelFacade.readRaw(inputStream, 0);
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.excel.engine` | AUTO | 引擎选择（AUTO / SUPER_FAST / POI） |
| `ydsz.excel.default-sheet-name` | Sheet1 | 默认 Sheet 名称 |
| `ydsz.excel.max-rows-per-sheet` | 1048576 | 每 Sheet 最大行数 |
| `ydsz.excel.formula-injection-guard` | true | 公式注入防护开关 |
| `ydsz.excel.buffer-size` | 8192 | 读缓冲区大小 |
| `ydsz.excel.cache-metadata` | true | 元数据缓存开关 |
| `ydsz.excel.date-format` | yyyy-MM-dd | 日期格式 |
| `ydsz.excel.datetime-format` | yyyy-MM-dd HH:mm:ss | 日期时间格式 |
| `ydsz.excel.health-check-enabled` | true | 健康检查开关 |

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `CellValueConverter` **SPI** | 单元格值转换器（支持 `priority()` 控制顺序） | `ConverterRegistry.registerCustomConverter()` |
| `ExcelMetrics` | Excel 指标回调 | `@ConditionalOnMissingBean` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/excel` | Excel 引擎健康检查 | `spring-boot-health` 在 classpath + `ydsz.excel.health-check-enabled=true` |

`ExcelHealthIndicator` 暴露信息：

- `engine` — 当前引擎类型（SUPER_FAST / POI / AUTO）
- `cache_hit_rate` — 元数据缓存命中率
- `status` — UP / DOWN

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `ExcelAutoConfiguration` | POI 在 classpath 时激活 |

## 注意事项

1. **引擎选择**：大数据量纯数据读写用 SuperFast；复杂格式（公式/样式/图表）用 POI。AUTO 模式根据文件大小自动选择。
2. **内存控制**：SuperFast 引擎逐行流式解析，内存占用恒定；POI 路径大文件建议配置 SXSSF 窗口。
3. **公式注入**：公式注入防护默认开启，`=+@-` 开头的值自动添加单引号前缀。
4. **日期格式**：Excel 日期类型依赖 JVM 时区，建议在 `@ExcelProperty` 显式声明日期格式。
5. **字段访问**：默认使用 ASM 字节码访问（首次加载生成 accessor），性能为反射 3-5 倍。

## 变更记录

- **2.1.0**（2026-09-01）：FormulaInjectionGuard 公式注入防护默认开启；新增 ExcelWebSupport 自动处理下载响应头；优化 SuperFast 字符串驻留策略；新增 Micrometer 指标。
- **26.09.01**（2026-08-02）：初始版本，双引擎架构（SuperFast + POI）。
