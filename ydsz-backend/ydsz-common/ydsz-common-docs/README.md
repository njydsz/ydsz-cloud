# ydsz-common-docs

YDSZ 文档智能处理框架 — 8 种格式解析（PDF / Word / Excel / PPT / HTML / Markdown / TXT / CSV）、预处理 Pipeline、安全扫描（宏 / PDF JS / 嵌入对象）、PII 检测（5 种）、文本水印、PDF 脱敏、异步解析、OCR 集成。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 54 |

## 核心能力

### 文档解析

| 类 | 格式 | 说明 |
|---|---|---|
| `PdfDocumentParser` | PDF | PDF 文本/表格/图片提取 |
| `WordDocumentParser` | Word | .doc / .docx 解析 |
| `ExcelDocumentParser` | Excel | .xls / .xlsx 解析 |
| `PptDocumentParser` | PPT | .ppt / .pptx 解析 |
| `HtmlDocumentParser` | HTML | HTML 解析（Jsoup 清洗） |
| `MarkdownDocumentParser` | Markdown | Markdown 解析 |
| `TxtDocumentParser` | TXT | 纯文本解析 |
| `CsvDocumentParser` | CSV | CSV 解析 |

- `DocumentParser` — 解析器接口
- `DocumentParserRegistry` — 解析器注册表（自动探测格式）
- `DocumentFormat` — 格式枚举

### 文档预处理

| 类 | 说明 |
|---|---|
| `DocumentPreprocessor` | 预处理器接口 |
| `PreprocessPipeline` | 预处理管道（责任链模式） |
| `TextNormalizer` | 文本标准化（全角→半角 / Unicode 规范化） |
| `TextCleaner` | 文本清洗（去除不可见字符 / 多余空白） |
| `TextChunker` | 文本分块（按 token 数拆分） |

### 安全扫描

| 类 | 说明 |
|---|---|
| `DocumentSecurityScanner` | 安全扫描接口 |
| `DocumentSecurityScannerComposite` | 组合扫描器 |
| `MacroDetector` | 宏检测（Word / Excel / PPT 宏病毒） |
| `PdfJsDetector` | PDF JavaScript 检测 |
| `SecurityScanResult` / `SecurityLevel` | 扫描结果 / 安全等级 |

### PII 检测

| 类 | 说明 |
|---|---|
| `PiiDetector` | PII 检测接口 |
| `PiiDetectorComposite` | 组合检测器 |
| `PhoneDetector` | 手机号检测 |
| `IdCardDetector` | 身份证号检测 |
| `EmailDetector` | 邮箱检测 |
| `BankCardDetector` | 银行卡号检测 |
| `ApiKeyDetector` | API Key 检测 |
| `PiiFinding` / `PiiType` | PII 发现 / 类型枚举 |

### 水印与脱敏

| 类 | 说明 |
|---|---|
| `WatermarkProvider` | 水印提供者接口 |
| `TextWatermarkProvider` | 文本水印提供者 |
| `TextRedactor` | 文本脱敏器 |
| `DocumentRedactor` | 文档脱敏器（PDF 内容替换） |

### OCR 集成

| 类 | 说明 |
|---|---|
| `OcrEngine` | OCR 引擎接口 |
| `OcrProvider` | OCR 提供者（Tesseract / 云 OCR） |

### 文档服务

| 类 | 说明 |
|---|---|
| `DocumentService` | 文档服务接口（统一入口） |
| `AsyncDocumentParser` | 异步文档解析器 |
| `DocumentConverter` | 文档格式转换器 |
| `DocumentSummarizer` | 文档摘要生成器 |

### 领域模型

| 类 | 说明 |
|---|---|
| `DocumentParseResult` | 解析结果（文本 + 表格 + 图片 + 元数据） |
| `DocumentContent` / `DocumentSection` | 文档内容 / 章节 |
| `DocumentTable` / `DocumentImage` | 表格 / 图片 |
| `DocumentMetadata` | 元数据（标题 / 作者 / 页数 / 创建时间） |
| `ParseOptions` / `ParseMode` | 解析选项 / 模式 |

## 配置项

```yaml
ydsz:
  docs:
    enabled: true
    max-file-size-mb: 50
    parse-timeout-seconds: 60
    security-scan-enabled: true
    pii-detection-enabled: true
    preprocess-enabled: true
    watermark-enabled: true
    redact-enabled: true
    async-pool-size: 4
    async-queue-capacity: 100
    max-chunk-size: 2000
    chunk-overlap: 200
    security-max-scan-pages: 50
    block-on-high-risk: false
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `DocsAutoConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-docs</artifactId>
</dependency>
```
