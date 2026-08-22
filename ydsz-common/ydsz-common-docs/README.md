# ydsz-common-docs

> 文档解析与安全扫描框架（L5 业务服务层）

提供 8 种格式解析（PDF / Word / Excel / PPT / HTML / Markdown / TXT / CSV）、预处理 Pipeline、安全扫描（宏 / PDF JS）、PII 检测（7 种敏感信息）、OCR 集成能力，是 YDSZ 项目文档内容处理的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多格式文档解析、安全扫描、PII 检测等基础能力 |
| **依赖** | 直接依赖 common-core、common-util、common-exception、common-json、tika-core、ydsz-common-excel、ydsz-common-safe；可选依赖 pdfbox、pdfbox-io、poi-ooxml、jsoup、commons-csv、spring-boot-actuator、spring-boot-health、micrometer-core、jakarta.validation-api |
| **版本** | 2.0.1 |

## v2.0.0 变更摘要

本次重构对标过度设计评估结论，核心变更：

- **删除** `DocumentSummarizer`：摘要/关键词/分类不属于 common 层职责，业务方应接入 LLM 服务
- **删除** `WatermarkProvider` / `TextWatermarkProvider`：仅有 SPI 定义无内置实现，属于 YAGNI 反模式
- **删除** `DocumentRedactor` / `TextRedactor`：脱敏逻辑过于简单，不应占据 common 模块位置
- **简化** Composite 模式：`PiiDetectorComposite` 和 `DocumentSecurityScannerComposite` 逻辑内联到 `DocumentService`
- **统一** 临时文件管理：复用 `ydsz-common-util` 的 `TempFileManager` 集中管理
- **精简** 配置项：从 16 项缩减为 10 项，业务特定参数下沉到业务模块
- **消除** 代码重复：`DocumentConverter` 优先委托已注册 Parser，仅保留 Excel 降级实现
- **增强** `PiiFinding`：增加二进制定位模型（`BinaryLocation`）支持 PDF/DOCX/XLSX 场景
- **新增** `ParseProfile` 枚举：约束输出轮廓，调用方可按需选择结构化程度
- **新增** PDF 流式解析：`PdfDocumentParser#parseStreaming` 支持大文件增量处理
- **重构** `AsyncDocumentParser`：线程池改为 Spring 注入，移除手写 `@PreDestroy`

## 核心能力

### 1. 文档解析

| 类 | 格式 | 说明 |
|---|---|---|
| `PdfDocumentParser` | PDF | 基于 Apache PDFBox，提取文本 / 表格 / 图片 / 元数据；支持流式逐页解析（`pdfbox-io` 可选） |
| `WordDocumentParser` | DOCX | 基于 Apache POI，.docx 解析（.doc 旧格式拒绝并提示转换） |
| `ExcelDocumentParser` | XLSX | 基于 `ydsz-common-excel`（统一 Excel 引擎），.xlsx 解析（含表格结构） |
| `PptDocumentParser` | PPTX | 基于 Apache POI，.pptx 解析 |
| `HtmlDocumentParser` | HTML | 基于 Jsoup，HTML 解析与标签清洗 |
| `MarkdownDocumentParser` | MARKDOWN | Markdown 解析（保留标题层级与代码块） |
| `TxtDocumentParser` | TXT | 纯文本解析（自动识别编码） |
| `CsvDocumentParser` | CSV | 基于 Apache Commons CSV，CSV 解析 |

| 类 | 说明 |
|---|---|
| `DocumentParser` | 解析器 SPI 接口，定义 `parse(InputStream, String, ParseOptions)` 与 `getSupportedFormat()` |
| `DocumentParserRegistry` | 解析器注册表，Spring 自动注入所有 `DocumentParser` Bean，按格式路由 |
| `DocumentFormat` | 格式枚举，含 16 种格式，支持扩展名与 Tika MIME 双重检测 |
| `ParseMode` | 解析模式枚举：`FAST`（纯文本）/ `FULL`（完整提取）/ `METADATA_ONLY`（仅元数据） |
| `ParseProfile` | 解析输出轮廓：`TEXT_ONLY` / `STRUCTURED` / `FULL` |

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
| `MacroDetector` | Office 宏检测（.docm / .xlsm / .pptm） |
| `PdfJsDetector` | PDF JavaScript 检测（PDF 内嵌 JS 脚本） |
| `SecurityScanResult` | 扫描结果（含安全等级、findings 列表） |
| `SecurityLevel` | 安全等级枚举：`SAFE` / `LOW` / `MEDIUM` / `HIGH` / `CRITICAL` |

### 4. PII 检测

| 类 | 说明 |
|---|---|
| `PiiDetector` | PII 检测 SPI 接口，定义 `detect` / `getSupportedType` / `mask` |
| `PhoneDetector` | 手机号检测（11 位国内手机号） |
| `IdCardDetector` | 身份证号检测（18 位含校验位验证 + 15 位） |
| `EmailDetector` | 邮箱地址检测 |
| `BankCardDetector` | 银行卡号检测（16-19 位，Luhn 校验） |
| `ApiKeyDetector` | API Key / Token 检测 |
| `IpAddressDetector` | IP 地址检测 |
| `PassportDetector` | 护照号检测 |
| `PiiFinding` | PII 发现实体（含类型、原文、脱敏文本、位置、二进制定位） |
| `PiiType` | PII 类型枚举 |

### 5. OCR 集成

| 类 | 说明 |
|---|---|
| `OcrEngine` | OCR 引擎枚举（Tesseract / 云 OCR） |
| `OcrProvider` | OCR 服务实现（页面渲染 + 识别回调） |

### 6. 文档服务门面

| 类 | 说明 |
|---|---|
| `DocumentService` | 文档处理统一服务门面，整合解析 / 预处理 / 安全扫描 / PII 检测能力 |
| `AsyncDocumentParser` | 异步文档解析器，基于 Spring 托管的线程池 |

`DocumentService` 核心方法：

| 方法 | 说明 |
|---|---|
| `parse(InputStream, String, ParseOptions)` | 解析文档 |
| `preprocess(DocumentContent)` | 预处理文档内容 |
| `parseAndPreprocess(...)` | 解析 + 预处理一体化 |
| `scanSecurity(InputStream, String)` | 安全扫描 |
| `detectPii(DocumentContent)` | PII 检测 |
| `convert(InputStream, String, DocumentFormat)` | 文档格式转换（委托已注册 Parser） |
| `ocrScan(InputStream, String, OcrEngine)` | OCR 识别 |
| `parseWithSecurityCheck(...)` | 解析 + 安全扫描一体化（高风险可阻止） |

### 7. 领域模型

| 类 | 说明 |
|---|---|
| `DocumentParseResult` | 解析结果（含 content / elapsed / success / errorMessage / fileName） |
| `DocumentContent` | 文档内容（含 sections / tables / images / metadata / text） |
| `DocumentSection` | 文档章节（标题 / 层级 / 内容） |
| `DocumentTable` | 表格（行列表结构） |
| `DocumentImage` | 图片（字节流 + 格式 + 位置） |
| `DocumentMetadata` | 元数据（标题 / 作者 / 页数 / 创建时间 / 修改时间） |
| `ParseOptions` | 解析选项（页码范围 / 输出轮廓 / 是否提取图片 / 最大文件大小） |

### 8. 异常处理

| 类 | 说明 |
|---|---|
| `DocumentException` | 文档处理统一异常（继承 `AbstractYdszException`），携带 `DocumentExceptionCode` 错误码 |
| `DocumentExceptionCode` | 文档异常错误码枚举（PARSE_ERROR / UNSUPPORTED_FORMAT / FILE_TOO_LARGE / SECURITY_HIGH_RISK / OCR_ERROR 等） |

### 9. 监控与健康检查

| 类 | 说明 |
|---|---|
| `DocsMetrics` | Micrometer 指标采集（解析耗时 / 安全扫描 / PII 检测计数） |
| `DocsHealthIndicator` | 健康检查（暴露已注册解析器、PII 检测器、异步队列状态） |

> 临时文件管理复用 `ydsz-common-util` 的 `TempFileManager`（跟踪 / 清理 / ShutdownHook 兜底），本模块不重复实现。

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

### 4. 异步线程池配置

由于 `AsyncDocumentParser` 改为使用 Spring 托管的线程池，使用方需声明名为 `docsAsyncExecutor` 的 Bean：

```java
@Configuration
public class DocsAsyncConfig {

    @Bean(name = "docsAsyncExecutor", destroyMethod = "shutdown")
    public ExecutorService docsAsyncExecutor(
            @Value("${ydsz.docs.async-pool-size:4}") int poolSize,
            @Value("${ydsz.docs.async-queue-capacity:100}") int queueCapacity) {
        return new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> { Thread t = new Thread(r, "ydsz-docs-async-parser"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy());
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
| `ydsz.docs.async-pool-size` | 4 | 异步解析线程池大小（1-64） |
| `ydsz.docs.async-queue-capacity` | 100 | 异步解析队列容量（1-10000） |
| `ydsz.docs.block-on-high-risk` | false | 高风险时是否阻止解析 |

## 使用示例

### 1. 解析 + 预处理一体化

```java
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.ParseMode;
import com.njydsz.common.docs.enums.ParseProfile;

ParseOptions options = ParseOptions.builder()
        .mode(ParseMode.FULL)
        .profile(ParseProfile.STRUCTURED)
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

### 3. PII 检测

```java
import com.njydsz.common.docs.domain.PiiFinding;

DocumentParseResult parseResult = documentService.parse(inputStream, "doc.pdf", options);
List<PiiFinding> findings = documentService.detectPii(parseResult.getContent());
findings.forEach(f -> log.info("发现 {}: 位置[{}] 脱敏值={}",
        f.getType(), f.getStartIndex(), f.getMaskedValue()));
```

### 4. 异步解析

```java
import com.njydsz.common.docs.service.AsyncDocumentParser;

asyncDocumentParser.parseAsync(inputStream, "big.pdf", options)
        .thenAccept(result -> {
            if (result.isSuccess()) {
                log.info("异步解析完成: {}", result.getFileName());
            }
        });
```

### 5. PDF 流式解析（大文件增量处理）

```java
import com.njydsz.common.docs.parser.impl.PdfDocumentParser;

pdfDocumentParser.parseStreaming(inputStream, "large.pdf", pageContent -> {
    // 逐页消费，无需等待全文加载
    indexer.indexPage(pageContent.pageNumber(), pageContent.text());
});
```

## 已接入模块清单

| 模块 | 接入能力 | 接入方式 | 依赖声明 | 接入时间 |
|------|---------|---------|---------|---------|
| ydsz-nextwiki | 文档解析（PDF/Office/HTML → 纯文本提取） | 注入 `DocumentService#parseAndPreprocess` | 显式声明 | v2.1.0 |
| ydsz-agent | 文档解析 + RAG 知识库摄入 | 注入 `DocumentService` + `DocumentIngestionService` | 显式声明 | v2.1.0 |
| ydsz-message | PII 脱敏（日志打印场景） | `SensitiveUtil#scanAndMask`（common-safe 传递） | 传递引入 | v2.1.0 |

> **幽灵依赖检查**：本表用于 Pre-PR 审查时核对。`pom.xml` 中声明了 `ydsz-common-docs` 但无任何 Java 代码引用该模块的，视为幽灵依赖，需移除声明。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `DocumentParser` | 文档解析器，业务可自定义新格式实现 | 框架内置 8 种格式解析器 |
| `DocumentPreprocessor` | 文档预处理器，业务可插入自定义清洗逻辑 | 框架内置 3 种预处理器 |
| `DocumentSecurityScanner` | 安全扫描器，业务可扩展新的安全检测项 | 框架内置宏检测 + PDF JS 检测 |
| `PiiDetector` | PII 检测器，业务可扩展新的敏感信息识别 | 框架内置 7 种 PII 检测器 |
| `OcrEngine` | OCR 引擎枚举 | 业务模块实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/docs` | 文档处理模块健康检查 | `spring-boot-health` 在类路径，`ydsz.docs.enabled=true` |

## 注意事项

1. **格式降级**：未引入 `pdfbox` / `poi-ooxml` / `jsoup` / `commons-csv` 对应依赖时，相关解析器不注册，调用对应格式抛 `UNSUPPORTED_FORMAT` 异常。
2. **旧格式拒绝**：`.doc` / `.ppt` / `.xls` 旧格式直接抛 `UNSUPPORTED_FORMAT`，建议业务层提示用户转换为新格式。
3. **宏文档警告**：`.docm` / `.xlsm` / `.pptm` 含宏文档会被 `MacroDetector` 标记为 `HIGH` 风险，配合 `block-on-high-risk=true` 可阻止解析。
4. **大文件控制**：`max-file-size-mb=50` 默认上限，超出应在上游网关拦截；解析器内部不重复校验。
5. **异步线程池**：v2.0.0 起线程池由 Spring 托管，使用方需声明 `docsAsyncExecutor` Bean。
6. **临时文件清理**：所有临时文件由 `ydsz-common-util` 的 `TempFileManager` 统一跟踪管理，JVM 退出时有 ShutdownHook 兜底清理。
7. **PII 检测精度**：基于正则表达式，存在误报与漏报可能；身份证号、银行卡号有校验位验证，准确率较高。
8. **输出轮廓选择**：通过 `ParseOptions.profile` 控制输出结构化程度，`TEXT_ONLY` 模式性能最优，`FULL` 模式最耗资源。

## 变更记录

- **v2.0.1**（2026-08-17）：
  - 更新依赖说明：标注 `ydsz-common-excel` / `ydsz-common-safe` 为直接依赖，添加 `pdfbox-io`（optional）、`jakarta.validation-api`（optional）
  - 补全 `DocumentationException` / `DocumentationExceptionCode` 异常处理文档
  - 修正 `ExcelDocumentParser` 依赖为 `ydsz-common-excel`（统一 Excel 引擎），`PdfDocumentParser` 标注 `pdfbox-io` 可选
- **v2.0.0**（2026-08-16）：基于过度设计评估全面重构，详见 [v2.0.0 变更摘要](#v200-变更摘要)
- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
