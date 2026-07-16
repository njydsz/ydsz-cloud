# ydsz-common-excel

PMIS high-performance Excel read/write framework - SAX streaming read (memory-friendly), SXSSF large file write, concurrent write, template fill, ASM field acceleration, formula injection protection, type converter chain, Spring Web integration.

## Module

| Attribute | Value |
|---|---|
| **Layer** | L5 Business Service |
| **Type** | Common library (not independently deployed) |
| **Source files** | ~70 |

## Core

### Entry point

| Class | Description |
|---|---|
| `ExcelFacade` | Unified entry (read / write / template) |
| `ExcelReader` | Excel reader |
| `ExcelWriter` | Excel writer |
| `ExcelTemplateWriter` | Template writer |
| `ConcurrentExcelWriter` | Concurrent writer (multi-sheet parallel) |

### High-performance read (SAX streaming)

| Class | Description |
|---|---|
| `SuperFastExcelReader` | SAX streaming reader (memory-friendly, supports GB-level files) |
| `SheetXmlReader` | Sheet XML reader |
| `SharedStringsReader` | Shared strings table reader |
| `ChunkedSSTTable` | Chunked SST table (memory-controlled) |

### High-performance write

| Class | Description |
|---|---|
| `SuperFastExcelWriter` | Fast writer (zero-POI path) |
| `UltraFastCellWriter` | Ultra-fast cell writer |
| `WorkbookFactory` | Workbook factory |
| `ValueFormatter` | Value formatter |
| `StyleManager` | Style manager |

### Type converter

| Class | Description |
|---|---|
| `CellValueConverter` | Cell value converter SPI interface |
| `ConverterChain` | Converter chain |
| `ConverterRegistry` | Converter registry |
| `impl/*Converter` | Built-in converters (String/Number/Boolean/BigDecimal/Date etc.) |

### Annotations

| Annotation | Description |
|---|---|
| `@ExcelProperty` | Field mapping (column index / name / date format / validation) |
| `@ExcelSheet` | Sheet definition (freeze pane / merged regions) |
| `@ExcelIgnore` | Ignore field |

### Security

| Class | Description |
|---|---|
| `FormulaInjectionGuard` | Formula injection protection (all write paths) |

### Data validation

| Class | Description |
|---|---|
| `DataValidator` | Auto validation on read (required / max length / regex / range) |

### Observability

| Class | Description |
|---|---|
| `ExcelMetrics` | Micrometer metrics (read/write duration / rows / success rate) |
| `ExcelHealthIndicator` | Spring Boot Actuator health check |

### Spring integration

| Class | Description |
|---|---|
| `ExcelWebSupport` | Web download support (Content-Length + UTF-8 filename) |
| `DownloadContext` | Download context |
| `ExcelAutoConfiguration` / `ExcelProperties` | Auto-configuration |

## Usage

```java
// Read (listener mode)
ExcelFacade.read("demo.xlsx", User.class)
    .sheet("Users")
    .doRead(new ReadListener<User>() {
        @Override
        public void onStart(AnalysisContext context) {}
        @Override
        public void onData(AnalysisContext context, User data) {
            System.out.println(data);
        }
        @Override
        public void onEnd(AnalysisContext context) {}
    });

// Read (shortcut)
ExcelFacade.read("demo.xlsx", User.class, (context, user) -> {
    System.out.println(user);
});

// Read all
List<User> users = ExcelFacade.read("demo.xlsx", User.class).doReadAll();

// Write
ExcelFacade.write("output.xlsx", User.class)
    .sheet("Users")
    .doWrite(userList);

// Write (shortcut)
ExcelFacade.write("output.xlsx", User.class, userList);

// Template fill
ExcelFacade.writeWithTemplate("template.xlsx", "output.xlsx", User.class)
    .sheet(0)
    .dataStartRow(3)
    .doWrite(userList);

// Concurrent write (large data)
ConcurrentExcelWriter.write("output.xlsx", User.class, largeDataList)
    .parallelism(4)
    .chunkSize(10000)
    .doWrite();

// Web download
ExcelWebSupport.download(response, "users", User.class, userList, "Sheet1");
```

## Configuration

```yaml
ydsz:
  excel:
    read-buffer-size: 8192
    write-buffer-size: 8192
    default-date-format: "yyyy-MM-dd HH:mm:ss"
    default-number-format: "#,##0.00"
    automatic-trim: true
    use-fast-reader: true
    use-fast-writer: true
    streaming-parse-threshold-mb: 10
    max-read-file-size-mb: 100
    max-write-file-size-mb: 50
    compression-level: 1
    formula-injection-protection: true
    strict-number-conversion: false
    head-row-number: 1
    write-cache-size: 100
```

## Dependency

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-excel</artifactId>
</dependency>
```
