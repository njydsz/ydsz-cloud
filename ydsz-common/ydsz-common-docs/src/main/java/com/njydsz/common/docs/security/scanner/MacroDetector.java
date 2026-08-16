package com.njydsz.common.docs.security.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import com.njydsz.common.docs.domain.SecurityScanResult;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.SecurityLevel;

/**
 * Office 宏检测器
 * <p>
 * 检测 Office 文档中是否包含 VBA 宏代码，识别高风险文件。
 *
 * <p><b>检测策略：</b>
 * <ul>
 *   <li>.docm/.xlsm/.pptm 后缀 = 高风险（含宏的 Office 格式）</li>
 *   <li>.docx/.xlsx/.pptx 中检测是否存在 vbaProject.bin</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
public class MacroDetector implements DocumentSecurityScanner {

    /** 含宏的 Office 文件后缀 */
    private static final List<String> MACRO_EXTENSIONS = List.of("docm", "xlsm", "pptm", "xlsb");

    /** VBA 宏在 ZIP 容器中的路径 */
    private static final String VBA_PROJECT_ENTRY = "vbaProject.bin";

    /** 需要 ZIP 扫描的 Office 格式 */
    private static final Set<DocumentFormat> OFFICE_FORMATS = Set.of(
            DocumentFormat.DOCX, DocumentFormat.XLSX, DocumentFormat.PPTX,
            DocumentFormat.DOCM, DocumentFormat.XLSM, DocumentFormat.PPTM,
            DocumentFormat.XLS);

    @Override
    public SecurityScanResult scan(InputStream inputStream, String fileName, DocumentFormat format) {
        // 非 Office 格式跳过 ZIP 扫描
        if (format != null && !OFFICE_FORMATS.contains(format)) {
            return SecurityScanResult.builder()
                    .securityLevel(SecurityLevel.SAFE)
                    .findings(List.of())
                    .success(true)
                    .build();
        }
        List<SecurityScanResult.SecurityFinding> findings = new ArrayList<>();

        // 后缀检测
        String ext = extractExtension(fileName);
        if (MACRO_EXTENSIONS.contains(ext)) {
            findings.add(SecurityScanResult.SecurityFinding.builder()
                    .type("macro")
                    .description("文件扩展名 '" + ext + "' 表明此文件包含 VBA 宏代码")
                    .level(SecurityLevel.HIGH)
                    .location("文件名")
                    .build());
        }

        // ZIP 容器内容检测（使用 ZipInputStream 流式遍历，避免全量读取 OOM）
        try (var zis = new ZipInputStream(inputStream)) {
            var entry = zis.getNextEntry();
            while (entry != null) {
                if (VBA_PROJECT_ENTRY.equals(entry.getName())) {
                    findings.add(SecurityScanResult.SecurityFinding.builder()
                            .type("macro")
                            .description("检测到 VBA 宏项目 (vbaProject.bin)")
                            .level(SecurityLevel.HIGH)
                            .location("OOXML 容器")
                            .build());
                    break;
                }
                entry = zis.getNextEntry();
            }
        } catch (IOException e) {
            log.warn("[MacroDetector] 读取文件失败: {}", fileName, e);
        }

        return SecurityScanResult.builder()
                .securityLevel(findings.isEmpty() ? SecurityLevel.SAFE : SecurityLevel.HIGH)
                .findings(findings)
                .success(true)
                .build();
    }


    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    @Override
    public String getName() {
        return "macro-detector";
    }
}
