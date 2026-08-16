# ydsz-common-docs

> 文档智能处理框架（L5 业务服务层）

提供 8 种格式解析（PDF / Word / Excel / PPT / HTML / Markdown / TXT / CSV）、预处理 Pipeline、安全扫描（宏 / PDF JS / 嵌入对象）、PII 检测（5 种敏感信息）、文本水印、PDF 脱敏、异步解析、OCR 集成能力，是 YDSZ 项目文档内容处理的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多格式文档解析、安全扫描、PII 检测、脱敏、水印、OCR 集成等能力 |
| **依赖** | common-core、common-util、common-exception、common-json、tika-core；可选依赖 pdfbox、poi-ooxml、jsoup、commons-csv、spring-boot-actuator、spring-boot-health、micrometer-core |
| **版本** | 1.0.0 |

## 核心能力

### 1. 文档解析

| 类 | 格式 | 说明 |
|---|---|---|
| `PdfDocumentParser` | PDF | 基于 Apache PDFBox，提取文本 / 表格 / 图片 / 元数据 |
| `WordDocumentParser` | DOCX | 基于 Apache POI，.docx 解析（.doc 旧格式拒绝并提示转换） |
| `ExcelDocumentParser` | XLSX | 基于 Apache POI，.xlsx 解析（含表格结构） |
| `PptDocumentParser` | PPTX | 基于 Apache POI，.pptx 解析 |
| `HtmlDocumentParser` | HTML | 基于 Jsoup，HTML 解析与标签清洗 |
| `MarkdownDocumentParser` | MARKDOWN | Markdown 解析（保留标题层级与代码块） |
| `TxtDocumentParser` | TXT | 纯文本解析（自动识别编码） |
| `CsvDocumentParser` | CSV | 基于 Apache Commons CSV，CSV 解析 |

| 类 | 说明 |
|---|---|
| `DocumentParser` | 解析器 SPI 接口，定义 `parse(InputStream, String, ParseOptions)` 与 `getSupportedFormat()` |
| `DocumentParserRegistry` | 解析器注册表，Spring 自动注入所有 `DocumentParser` Bean，按格式路由 |
| `DocumentFormat` | 格式枚举，含 16 种格式（PDF / DOCX / DOC / XLSX / XLS / PPTX / PPT / DOCM / XLSM / PPTM / HTML / MARKDOWN / TXT / CSV / XML / RTF），支持扩展名与 Tika MIME 双重检测 |
| `ParseMode` | 解析模式枚举：`FAST`（纯文本）/ `FULL`（完整提取）/ `METADATA_ONLY`（仅元数据） |

### 2. 文档预处理

| 类 | 说明 |
|---|---|
| `DocumentPreprocessor` | 预处理器 SPI 接口 |
| `PreprocessPipeline` | 预处理管道（责任链模式，按顺序执行多个预处理器） |
| `TextNormalizer` | 文本标准化（全角→半角 / Unicode NFC 规范化） |
| `TextCleaner` | 文本清洗（去除不可见字符 / 多余空白 / BOM） |
| `TextChunker` | 文本分块（按字符数拆分，支持 overlap 重叠） |

### 3. 安全扫描

| 类 | 说明 |
|---|---|
| `DocumentSecurityScanner` | 安全扫描 SPI 接口 |
| `DocumentSecurityScannerComposite` | 组合扫描器，按顺序执行所有子扫描器并聚合结果 |
| `MacroDetector` | Office 宏检测（.docm / .xlsm / .pptm） |
| `PdfJsDetector` | PDF JavaScript 检测（PDF 内嵌 JS 脚本） |
| `SecurityScanResult` | 扫描结果（含安全等级、findings 列表） |
| `SecurityLevel` | 安全等级枚举：`SAFE` / `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` |

### 4. PII 检测

| 类 | 说明 |
|---|---|
| `PiiDetector` | PII 检测 SPI 接口，定义 `detect` / `getSupportedType` / `mask` |
| `PiiDetectorComposite` | 组合检测器，聚合所有子检测器结果 |
| `PhoneDetector` | 手机号检测（11 位国内手机号） |
| `IdCardDetector` | 身份证号检测（18 位，含校验位验证） |
| `EmailDetector` | 邮箱地址检测 |
| `BankCardDetector` | 银行卡号检测（16-19 位，Luhn 校验） |
| `ApiKeyDetector` | API Key / Token 检测（常见格式：`sk-` / `AK` / Bearer 等） |
| `PiiFinding` | PII 发现实体（含类型、原文、脱敏文本、位置） |
| `PiiType` | PII 类型枚举：`ID_CARD` / `PHONE` / `BANK_CARD` / `EMAIL` / `API_KEY` / `IP_ADDRESS` / `PASSPORT` |

### 5. 水印与脱敏

| 类 | 说明 |
|---|---|
| `WatermarkProvider` | 水印提供者 SPI 接口 |
| `TextWatermarkProvider` | 文本水印实现（PDF 页面水印） |
| `TextRedactor` | 文本脱敏器（基于 PII 发现替换为掩码） |
| `DocumentRedactor` | 文档脱敏器（PDF 内容替换，生成脱敏后的字节流） |

### 6. OCR 与摘要

| 类 | 说明 |
|---|---|
| `OcrEngine` | OCR 引擎枚举（Tesseract / 云 OCR） |
| `OcrProvider` | OCR 提供者 SPI 接口 |
| `DocumentSummarizer` | 文档摘要生成器（含 `summarize` 与 `extractKeywords`） |
| `DocumentConverter` | 文档格式转换器 SPI 接口 |

### 7. 文档服务门面

| 类 | 说明 |
|---|---|
| `DocumentService` | 文档处理统一服务门面，整合解析 / 预处理 / 安全扫描 / PII 检测 / 脱敏 / 水印 / OCR / 摘要能力 |
| `AsyncDocumentParser` | 异步文档解析器，基于 `CompletableFuture` + 线程池，支持超时取消与回调 |

`DocumentService` 核心方法：

| 方法 | 说明 |
|---|---|
| `parse(InputStream, String, ParseOptions)` | 解析文档 |
| `preprocess(DocumentContent)` | 预处理文档内容 |
| `parseAndPreprocess(...)` | 解析 + 预处理一体化 |
| `scanSecurity(InputStream, String)` | 安全扫描 |
| `detectPii(DocumentContent)` | PII 检测 |
| `convert(InputStream, String, DocumentFormat)` | 文档格式转换 |
| `addWatermark(InputStream, String, String)` | 添加水印 |
| `redact(InputStream, String, List<PiiFinding>)` | PII 脱敏 |
| `ocrScan(InputStream, String, OcrEngine)` | OCR 识别 |
| `summarize(DocumentContent)` | 文档摘要 |
| `extractKeywords(DocumentContent)` | 关键词提取 |
| `parseWithSecurityCheck(...)` | 解析 + 安全扫描一体化（高风险可阻止） |
| `parseAndRedact(...)` | 解析 + PII 检测 + 脱敏一体化 |

### 8. 领域模型

| 类 | 说明 |
|---|---|
| `DocumentParseResult` | 解析结果（含 content / elapsed / success / errorMessage / fileName） |
| `DocumentContent` | 文档内容（含 sections / tables / images / metadata / rawText） |
| `DocumentSection` | 文档章节（标题 / 层级 / 内容） |
| `DocumentTable` | 表格（行列表结构） |
| `DocumentImage` | 图片（字节流 + 格式 + 位置） |
| `DocumentMetadata` | 元数据（标题 / 作者 / 页数 / 创建时间 / 修改时间） |
| `ParseOptions` | 解析选项（页码范围 / 是否提取图片 / 最大文件大小） |

### 9. 监控与健康检查

| 类 | 说明 |
|---|---|
| `DocsMetrics` | Micrometer 指标采集（解析耗时 / 安全扫描 / PII 检测计数） |
| `DocsHealthIndicator` | 健康检查（暴露已注册解析器、PII 检测器、异步队列状态） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-docs</artifactId>
</dependency>
```

按需引入格式解析依赖（均为 optional，未引入对应解析器自动降级）：

```xml
<!-- PDF 解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
</dependency>

<!-- Word/Excel/PPT 解析 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
</dependency>

<!-- HTML 解析 -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
</dependency>

<!-- CSV 解析 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-csv</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  docs:
    enabled: true
    security-scan-enabled: true
    pii-detection-enabled: true
    preprocess-enabled: true
    watermark-enabled: true
    redact-enabled: true
```

### 3. 注入并使用

```java
import com.njydsz.common.docs.service.DocumentService;
import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.ParseMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class ContractParseService {

    @Autowired
    private DocumentService documentService;

    public DocumentParseResult parseContract(InputStream input, String fileName) {
        ParseOptions options = ParseOptions.builder()
                .mode(ParseMode.FULL)
                .build();
        return documentService.parse(input, fileName, options);
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.docs.enabled` | true | 是否启用文档处理模块 |
| `ydsz.docs.max-file-size-mb` | 50 | 文件大小上限（MB，1-500） |
| `ydsz.docs.parse-timeout-seconds` | 60 | 解析超时时间（秒，1-600） |
| `ydsz.docs.security-scan-enabled` | true | 安全扫描开关 |
| `ydsz.docs.pii-detection-enabled` | true | PII 检测开关 |
| `ydsz.docs.preprocess-enabled` | true | 预处理开关 |
| `ydsz.docs.watermark-enabled` | true | 水印开关 |
| `ydsz.docs.redact-enabled` | true | 脱敏开关 |
| `ydsz.docs.async-pool-size` | 4 | 异步解析线程池大小（1-64） |
| `ydsz.docs.async-queue-capacity` | 100 | 异步解析队列容量（1-10000） |
| `ydsz.docs.max-chunk-size` | 2000 | 文本分块最大字符数（100-100000） |
| `ydsz.docs.chunk-overlap` | 200 | 文本分块重叠量（0-10000） |
| `ydsz.docs.security-max-scan-pages` | 50 | 安全扫描最大页数（0-500） |
| `ydsz.docs.block-on-high-risk` | false | 高风险时是否阻止解析 |
| `ydsz.docs.watermark-font-path` | - | 水印自定义字体路径（配置后优先使用） |
| `ydsz.docs.classifier-rules` | - | 文档分类规则（JSON 格式） |

## 使用示例

### 1. 解析 + 预处理一体化

```java
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.ParseMode;

ParseOptions options = ParseOptions.builder()
        .mode(ParseMode.FULL)
        .extractImages(true)
        .build();

DocumentParseResult result = documentService.parseAndPreprocess(inputStream, "contract.pdf", options);
if (result.isSuccess()) {
    String text = result.getContent().getText();
    log.info("解析成功，耗时: {}ms", result.getElapsed().toMillis());
}
```

### 2. 安全扫描 + 高风险阻止

```java
import com.njydsz.common.docs.enums.SecurityLevel;

SecurityScanResult scan = documentService.scanSecurity(inputStream, "report.xlsm");
if (scan.getSecurityLevel() == SecurityLevel.HIGH
        || scan.getSecurityLevel() == SecurityLevel.CRITICAL) {
    throw new RuntimeException("文档存在高风险，拒绝处理");
}
```

### 3. PII 检测 + 脱敏一体化

```java
// 一键完成解析 + PII 检测 + 脱敏，返回脱敏后的字节流
byte[] redactedBytes = documentService.parseAndRedact(
        inputStream, "resume.pdf", ParseOptions.builder().build());
```

### 4. 单独 PII 检测与文本脱敏

```java
import com.njydsz.common.docs.domain.PiiFinding;

DocumentParseResult parseResult = documentService.parse(inputStream, "doc.pdf", options);
List<PiiFinding> findings = documentService.detectPii(parseResult.getContent());
findings.forEach(f -> log.info("发现 {}: 位置[{}] 脱敏值={}",
        f.getType(), f.getStartIndex(), f.getMaskedValue()));
```

### 5. 异步解析

```java
import com.njydsz.common.docs.service.AsyncDocumentParser;

asyncDocumentParser.parseAsync(inputStream, "big.pdf", options)
        .thenAccept(result -> {
            if (result.isSuccess()) {
                log.info("异步解析完成: {}", result.getFileName());
            }
        });

// 批量异步解析
List<AsyncDocumentParser.BatchFile> files = List.of(
        new AsyncDocumentParser.BatchFile(in1, "a.pdf"),
        new AsyncDocumentParser.BatchFile(in2, "b.pdf"));
List<CompletableFuture<DocumentParseResult>> futures =
        asyncDocumentParser.parseBatch(files, options);
```

### 6. 添加水印

```java
byte[] watermarked = documentService.addWatermark(
        inputStream, "secret.pdf", "内部资料 禁止外传");
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `DocumentParser` | 文档解析器，业务可自定义新格式实现 | 框架内置 8 种格式解析器 |
| `DocumentPreprocessor` | 文档预处理器，业务可插入自定义清洗逻辑 | 框架内置 3 种预处理器 |
| `DocumentSecurityScanner` | 安全扫描器，业务可扩展新的安全检测项 | 框架内置宏检测 + PDF JS 检测 |
| `PiiDetector` | PII 检测器，业务可扩展新的敏感信息识别（如护照、IP） | 框架内置 5 种 PII 检测器 |
| `WatermarkProvider` | 水印提供者，业务可自定义水印样式 | 框架内置文本水印 |
| `DocumentRedactor` | 文档脱敏器，业务可扩展新格式的脱敏实现 | 框架内置 PDF 脱敏 |
| `OcrProvider` | OCR 提供者，业务可对接 Tesseract / 云 OCR | 业务模块实现 |
| `DocumentConverter` | 文档格式转换器 | 业务模块实现 |
| `DocumentSummarizer` | 文档摘要生成器 | 业务模块实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/docs` | 文档处理模块健康检查 | `spring-boot-health` 在类路径，`ydsz.docs.enabled=true` |

暴露信息：

- `enabled` — 模块启用状态
- `maxFileSizeMb` — 文件大小上限
- `supportedFormats` — 已注册的文档格式列表
- `piiDetectors` — 已注册的 PII 检测器类型列表
- `asyncQueueSize` — 异步解析队列当前任务数
- `asyncActiveCount` — 异步解析线程活跃数

## 注意事项

1. **格式降级**：未引入 `pdfbox` / `poi-ooxml` / `jsoup` / `commons-csv` 对应依赖时，相关解析器不注册，调用对应格式抛 `UNSUPPORTED_FORMAT` 异常。
2. **旧格式拒绝**：`.doc` / `.ppt` / `.xls` 旧格式直接抛 `UNSUPPORTED_FORMAT`，建议业务层提示用户转换为新格式（.docx / .pptx / .xlsx）。
3. **宏文档警告**：`.docm` / `.xlsm` / `.pptm` 含宏文档会被 `MacroDetector` 标记为 `HIGH` 风险，配合 `block-on-high-risk=true` 可阻止解析。
4. **大文件控制**：`max-file-size-mb=50` 默认上限，超出应在上游网关拦截；解析器内部不重复校验。
5. **异步线程池**：`AsyncDocumentParser` 使用 `CallerRunsPolicy` 拒绝策略，队列满时由调用线程执行，可能阻塞调用方。
6. **临时文件清理**：`parseWithSecurityCheck` / `parseAndRedact` / `parseAsync` 会创建临时文件，使用 `try-finally` 确保删除；删除失败不影响主流程。
7. **PII 检测精度**：基于正则表达式，存在误报与漏报可能；身份证号、银行卡号有校验位验证，准确率较高。
8. **Tika 复用**：`DocumentFormat.fromContent` 复用静态 `Tika` 实例，避免重复加载开销。
9. **水印字体**：未配置 `watermark-font-path` 时使用 PDFBox 默认字体，中文可能显示为方块，需配置支持中文的 TTF 字体路径。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节；完善配置项表、SPI 扩展点、健康检查、注意事项
