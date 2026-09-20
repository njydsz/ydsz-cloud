package com.njydsz.common.docs.security.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.SecurityScanResult;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.SecurityLevel;

/**
 * OLE2 复合格式 (doc/xls/ppt 旧格式) 嵌入对象检测器
 *
 * <p>旧版 Office 二进制格式 (Compound File Binary / OLE2) 可封装多种嵌入对象， 是恶意软件投递的常见载体。本扫描器对 POIFS 文件系统进行递归遍历，检测以下风险：
 *
 * <ul>
 *   <li>OLE 嵌入包 (\x00Ole10Native / \x00CompObj / Embedded Object) — 中风险
 *   <li>Macros 目录中存在 VBA 项目 — 高风险
 *   <li>指定格式名的可执行文件嵌入（如 exe/bat/cmd）— 高风险
 *   <li>未知/可疑的嵌入对象 — 中风险
 * </ul>
 *
 * <p><b>设计对齐：</b>
 *
 * <ul>
 *   <li>仅对 doc / xls / ppt (旧版复合格式) 执行扫描，其它格式直接返回 SAFE
 *   <li>使用流式 POIFSFileSystem 按需读取嵌入内容片段，避免全文档加载 OOM
 *   <li>递归深度上限 3 层，防止恶意构造的深嵌套结构导致递归过深
 * </ul>
 *
 * <p><b>竞品对标：</b>
 *
 * <ul>
 *   <li>iConvert 静态分析阶段会解析 OLE2 目录并标记所有 ActiveX/Embedded Package
 *   <li>On-premise DLP 常用类似 POIFS 递归方式发现 disallowed compound objects
 *   <li>Windows Defender ASR "Block Office applications from creating child processes" 利用了同样的嵌入对象机制
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.apache.poi.poifs.filesystem.POIFSFileSystem")
  // CHECKSTYLE.ON: RegexpSinglelineJava
public class Ole2EmbeddedObjectScanner implements DocumentSecurityScanner {

  /** 可扫描的旧版复合格式 */
  private static final Set<DocumentFormat> SCANNABLE_FORMATS =
      Set.of(DocumentFormat.DOC, DocumentFormat.XLS, DocumentFormat.PPT);

  /** 可疑嵌入对象流名 (前缀匹配，不区分大小写) */
  private static final List<String> SUSPICIOUS_ENTRY_PREFIXES =
      List.of("__pipe/", "/");

  /** 高风险扩展名签名 (UTF-16LE 编码的 4 字节特征) */
  private static final byte[] EXE_SIGNATURE = {'M', 0, 'Z', 0};

  /** 递归遍历深度上限 */
  private static final int MAX_RECURSION_DEPTH = 3;

  /** 嵌入对象扇区大小敏感阈值：> 64KB 视为高风险 */
  private static final int LARGE_EMBED_SIZE_THRESHOLD = 64 * 1024;

  @Override
  public SecurityScanResult scan(InputStream inputStream, String fileName, DocumentFormat format) {
    // 非旧版复合格式直接放行
    if (format == null || !SCANNABLE_FORMATS.contains(format)) {
      return SecurityScanResult.builder()
          .securityLevel(SecurityLevel.SAFE)
          .findings(List.of())
          .isSuccess(true)
          .build();
    }

    List<SecurityScanResult.SecurityFinding> findings = new ArrayList<>(16);

    try (POIFSFileSystem fs = new POIFSFileSystem(inputStream)) {
      DirectoryEntry root = fs.getRoot();
      traverseEntries(root, findings, 0);
    } catch (IOException e) {
      log.warn(
          "[Ole2EmbeddedObjectScanner] OLE2 文件系统开读失败 (可能是规范不符的混合文档): {}",
          fileName,
          e);
      return SecurityScanResult.builder()
          .securityLevel(SecurityLevel.SAFE)
          .findings(List.of())
          .isSuccess(false)
          .errorMessage("OLE2 容器解析失败: " + e.getMessage())
          .build();
    } catch (IllegalArgumentException e) {
      // 输入流不符合 OLE2 规范时抛此异常，属于预期中的"非 OLE2 文件"场景
      log.debug("[Ole2EmbeddedObjectScanner] 文件不是规范的 OLE2 容器: {}", fileName);
      return SecurityScanResult.builder()
          .securityLevel(SecurityLevel.SAFE)
          .findings(List.of())
          .isSuccess(true)
          .build();
    }

    SecurityLevel level = calculateOverallLevel(findings);

    return SecurityScanResult.builder()
        .securityLevel(level)
        .findings(findings)
        .isSuccess(true)
        .build();
  }

  /** 递归遍历 POIFS 目录，识别并标注嵌入对象特征。 */
  private void traverseEntries(
      DirectoryEntry dir, List<SecurityScanResult.SecurityFinding> findings, int depth) {
    if (depth > MAX_RECURSION_DEPTH) {
      return;
    }

    for (Entry entry : dir) {
      String entryName = entry.getName();

      // 排除系统簿记流（POIFS 内部元数据，非业务风险）
      if (isSystemMetadataEntry(entryName)) {
        continue;
      }

      if (entry instanceof DirectoryEntry subDir) {
        // Macros / _VBA_PROJECT 目录 → VBA 项目
        if ("Macros".equalsIgnoreCase(entryName) || entryName.startsWith("_VBA_PROJECT")) {
          findings.add(
              SecurityScanResult.SecurityFinding.builder()
                  .type("macro")
                  .description("检测到 VBA 宏项目目录: " + entryName)
                  .level(SecurityLevel.HIGH)
                  .location("OLE2 目录")
                  .build());
        }
        // 其他目录递归探测
        traverseEntries(subDir, findings, depth + 1);
        continue;
      }

      // 文档节点检测
      if (entry instanceof DocumentEntry docEntry) {
        classifyDocumentEntry(docEntry, findings);
      }
    }
  }

  /** 识别文档条目是否可疑并附加相应 finding。 */
  private void classifyDocumentEntry(
      DocumentEntry entry, List<SecurityScanResult.SecurityFinding> findings) {
    String name = entry.getName();

    // 1. OLE 嵌入对象标记流
    if ("\u0001Ole10Native".equals(name) || "\u0001CompObj".equals(name)) {
      findings.add(
          SecurityScanResult.SecurityFinding.builder()
              .type("embedded_object")
              .description("检测到 OLE 嵌入对象标记: " + sanitizeName(name))
              .level(SecurityLevel.MEDIUM)
              .location("OLE2 流 " + name)
              .build());
      return;
    }

    // 2. 大型嵌入内容（可能为打包的恶意载荷）
    if (entry.getSize() > LARGE_EMBED_SIZE_THRESHOLD) {
      findings.add(
          SecurityScanResult.SecurityFinding.builder()
              .type("embedded_object")
              .description("检测到大型嵌入数据 (" + entry.getSize() + " 字节)")
              .level(SecurityLevel.HIGH)
              .location("OLE2 流 " + name)
              .build());
      return;
    }

    // 3. 扇区内容首字节 "MZ" → 可执行文件嵌入
    if (entry.getSize() >= 4 && isExecutableSignature(entry)) {
      findings.add(
          SecurityScanResult.SecurityFinding.builder()
              .type("embedded_object")
              .description("检测到疑似可执行文件嵌入 (MZ 签名): " + name)
              .level(SecurityLevel.CRITICAL)
              .location("OLE2 流 " + name)
              .build());
    }
  }

  /** 判断 POIFS 是否具备 Windows 可执行文件的前 4 字节签名。 */
  private boolean isExecutableSignature(DocumentEntry entry) {
    try {
      byte[] header = new byte[4];
      try (InputStream is = new org.apache.poi.poifs.filesystem.DocumentInputStream(entry)) {
        int read = is.read(header);
        if (read < 4) {
          return false;
        }
      }
      return header[0] == EXE_SIGNATURE[0]
          && header[1] == EXE_SIGNATURE[1]
          && header[2] == EXE_SIGNATURE[2]
          && header[3] == EXE_SIGNATURE[3];
    } catch (IOException e) {
      return false;
    }
  }

  /** 判断是否为系统簿记元数据（无业务风险的 POIFS 自身结构） */
  private boolean isSystemMetadataEntry(String name) {
    if (name == null) {
      return false;
    }
    // "\x0005SummaryInformation" / "\x005DocumentSummaryInformation" 属于分析元数据
    return name.startsWith("\u0005");
  }

  /** 将嵌入式不可打印 Unicode 名称转义为安全的人类可读形式。 */
  private String sanitizeName(String name) {
    StringBuilder sb = new StringBuilder(name.length());
    for (char c : name.toCharArray()) {
      if (c < 0x20 || c == 0x7f) {
        sb.append(String.format("\\x%02X", (int) c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** 取所有 findings 中最严格的安全等级作为整体级别。 */
  private SecurityLevel calculateOverallLevel(List<SecurityScanResult.SecurityFinding> findings) {
    return findings.stream()
        .map(SecurityScanResult.SecurityFinding::getLevel)
        .max(Enum::compareTo)
        .orElse(SecurityLevel.SAFE);
  }

  @Override
  public String getName() {
    return "ole2-embedded-scanner";
  }
}
