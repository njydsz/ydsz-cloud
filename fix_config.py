import re
from pathlib import Path

BASE = Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-excel\src\main\java\com\njydsz\pmis\common\excel")

def rf(p):
    with open(p, "r", encoding="utf-8") as f:
        return f.read()

def wf(p, c):
    with open(p, "w", encoding="utf-8") as f:
        f.write(c)

# P2-8: Remove unused fields from ExcelConfig
config_path = str(BASE / "core" / "config" / "ExcelConfig.java")
c = rf(config_path)

# Remove field declarations
fields_to_remove = [
    "useScientificNotation",
    "keepRichTextFormat",
    "writeHiddenSheet",
    "maxSheetCacheSize",
    "mandatoryUseInputStream",
    "maxReadCacheSize",
]

for field in fields_to_remove:
    # Remove field declaration with Javadoc
    pattern = re.compile(r'    /\*\*.*?\*/\n    private \w+ \w+' + field + r'.*?;\n\n?', re.DOTALL)
    c = pattern.sub("", c)
    # Remove getter
    pattern_get = re.compile(r'    public \w+ [gG]et\w*' + field + r'\(\).*?\n    \}\n\n?', re.DOTALL)
    c = pattern_get.sub("", c)
    # Remove setter
    pattern_set = re.compile(r'    public void [sS]et\w*' + field + r'\(.*?\n    \}\n\n?', re.DOTALL)
    c = pattern_set.sub("", c)
    # Remove is-getter for boolean
    pattern_is = re.compile(r'    public boolean is\w*' + field + r'\(\).*?\n    \}\n\n?', re.DOTALL)
    c = pattern_is.sub("", c)
    print(f"  removed {field}")

wf(config_path, c)
print("P2-8 DONE")

# P1-7+P1-8: README update
readme_path = Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend\ydsz-pmis-common\ydsz-pmis-common-excel\README.md")
readme_content = """# ydsz-pmis-common-excel

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
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-excel</artifactId>
</dependency>
```
"""
readme_path.write_text(readme_content, encoding="utf-8")
print("README DONE")

# P2-4: Fix ExcelFacade ThreadLocal (if not already done)
facade_path = str(BASE / "core" / "ExcelFacade.java")
c = rf(facade_path)
if "DOWNLOAD_CONTENT_TYPE" in c:
    # Still has ThreadLocal - remove it
    import re
    # Remove ThreadLocal declarations and accessor methods
    pattern = re.compile(
        r'    private static final ThreadLocal<String> DOWNLOAD_\w+.*?(?=\n    [/\*]|\n    public|\n    \})',
        re.DOTALL
    )
    c = pattern.sub("", c)
    # Remove getDownloadContentType, getDownloadFileName, clearDownloadContext
    for method in ["getDownloadContentType", "getDownloadFileName", "clearDownloadContext"]:
        pat = re.compile(r'    public static \w+ ' + method + r'\(\).*?\n    \}\n\n?', re.DOTALL)
        c = pat.sub("", c)
    wf(facade_path, c)
    print("P2-4 ThreadLocal cleaned")
else:
    print("P2-4 already done")

print("ALL DONE")
