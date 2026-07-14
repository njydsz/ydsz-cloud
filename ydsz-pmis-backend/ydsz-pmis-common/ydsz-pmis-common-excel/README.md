# ydsz-pmis-common-excel

PMIS 高性能 Excel 读写框架 — SAX 流式读取（内存友好）、SXSSF 大文件写入、并发写入、模板填充、ASM 字段加速、公式注入防护、类型转换器链、Spring Web 集成。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 87 |

## 核心能力

### 统一入口

| 类 | 说明 |
|---|---|
| `ExcelFacade` | 统一入口（读 / 写 / 模板） |
| `ExcelReader` | Excel 读取器 |
| `ExcelWriter` | Excel 写入器 |
| `ExcelTemplateWriter` | 模板写入器 |
| `ConcurrentExcelWriter` | 并发写入器（多 Sheet 并行写入） |

### 高性能读取（SAX 流式）

| 类 | 说明 |
|---|---|
| `SuperFastExcelReader` | SAX 流式读取器（内存友好，支持 GB 级文件） |
| `SheetXmlReader` | Sheet XML 读取器 |
| `SharedStringsReader` | 共享字符串表读取器 |
| `ChunkedSSTTable` | 分块 SST 表（内存可控） |
| `ExcelXmlParser` | Excel XML 解析器 |
| `RowParser` | 行解析器 |
| `HeaderAnalyzer` | 表头分析器 |
| `InputSourceDetector` | 输入源检测 |
| `ColumnMetadata` / `SimpleCell` | 列元数据 / 简单单元格 |

### 高性能写入

| 类 | 说明 |
|---|---|
| `SuperFastExcelWriter` | 高速写入器 |
| `UltraFastCellWriter` | 超快单元格写入器 |
| `SxssfWriteStrategy` | SXSSF 策略（磁盘溢出写入） |
| `WorkbookFactory` | Workbook 工厂 |
| `ValueFormatter` | 值格式化器 |
| `StyleManager` | 样式管理器 |
| `PrecomputedColumnProperties` | 预计算列属性 |

### 策略模式

| 类 | 说明 |
|---|---|
| `ReadStrategy` / `WriteStrategy` | 读 / 写策略接口 |
| `UserModelReadStrategy` | UserModel 读取策略（兼容模式） |

### 类型转换器

| 类 | 说明 |
|---|---|
| `Converter` / `ConverterChain` / `ConverterRegistry` | 转换器接口 / 链 / 注册表 |
| `CellConverter` / `CellValueConverter` | 单元格转换器 |
| `ConvertContext` | 转换上下文 |
| `StringConverter` / `NumberConverter` / `BooleanConverter` | 基础类型转换器 |
| `BigDecimalConverter` / `DateConverter` / `LocalDateConverter` / `LocalDateTimeConverter` / `LocalTimeConverter` | 日期 / 数字转换器 |
| `EnumConverter` / `YearMonthConverter` / `TimestampConverter` | 枚举 / 年月 / 时间戳转换器 |

### 注解驱动

| 注解 | 说明 |
|---|---|
| `@ExcelProperty` | 字段映射（列索引 / 列名） |
| `@ExcelSheet` | Sheet 定义 |
| `@ExcelHead` | 表头样式 |
| `@ExcelStyle` | 单元格样式 |
| `@ExcelIgnore` | 忽略字段 |
| `@ContentStyle` / `@ContentFont` | 内容样式 / 字体 |

### 安全防护

| 类 | 说明 |
|---|---|
| `FormulaInjectionGuard` | 公式注入防护（拦截 `= / + / - / @` 开头单元格） |

### 缓存与对象池

| 类 | 说明 |
|---|---|
| `ReflectCache` | 反射缓存 |
| `LRUCache` | LRU 缓存 |
| `ClassMetadataCache` | 类元数据缓存 |
| `ObjectPool` / `StylePool` / `GlobalObjectPool` | 对象池（样式 / Workbook 复用） |
| `ASMFieldAccessor` | ASM 字段访问器 |

### Spring Web 集成

| 类 | 说明 |
|---|---|
| `ExcelWebSupport` | Web 下载支持 |
| `ExcelTemplate` | 模板注解 |
| `DownloadContext` | 下载上下文 |
| `ExcelAutoConfiguration` / `ExcelProperties` | 自动配置 |

### 元数据与上下文

| 类 | 说明 |
|---|---|
| `WriteMetadata` / `ReadMetadata` / `MetadataCache` | 写入 / 读取 / 元数据缓存 |
| `WriteMetadataBuilder` | 元数据构建器 |
| `WriteContext` / `AnalysisContext` | 写入 / 分析上下文 |
| `SheetData` | Sheet 数据 |
| `WriteHandler` / `ReadHandler` / `ReadListener` | 读写处理器 / 监听器 |

### 结果与校验

| 类 | 说明 |
|---|---|
| `ExcelReadResult` | 读取结果 |
| `DataValidator` | 数据校验器 |
| `ExcelConfig` | 全局配置 |

### 异常

| 类 | 说明 |
|---|---|
| `ExcelException` / `ExcelExceptionCode` | Excel 异常 / 错误码 |
| `ExcelReadException` / `ExcelWriteException` | 读 / 写异常 |

## 使用示例

```java
// 读取
List<User> users = ExcelFacade.read(inputStream, User.class);

// 写入
ExcelFacade.write(outputStream, User.class).sheet(users).doWrite();

// 模板填充
ExcelFacade.fillTemplate(templateStream, dataMap).write(outputStream);
```

## 配置项

```yaml
pmis:
  excel:
    read:
      buffer-size: 4096             # 读取缓冲区
      head-row-number: 1            # 表头行数
    write:
      batch-size: 1000              # 批量写入大小
      memory-window-size: 100       # SXSSF 内存窗口
    security:
      formula-injection-guard: true # 公式注入防护
```

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-excel</artifactId>
</dependency>
```
