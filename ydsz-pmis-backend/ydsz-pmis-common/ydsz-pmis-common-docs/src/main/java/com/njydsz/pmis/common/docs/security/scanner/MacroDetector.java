package com.njydsz.pmis.common.docs.security.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.SecurityScanResult;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.enums.SecurityLevel;

import lombok.extern.slf4j.Slf4j;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.apache.poi.xwpf.usermodel.XWPFDocument")
public class MacroDetector implements DocumentSecurityScanner {

    /** 含宏的 Office 文件后缀 */
    private static final List<String> MACRO_EXTENSIONS = List.of("docm", "xlsm", "pptm", "xlsb");

    /** VBA 宏在 ZIP 容器中的路径 */
    private static final String VBA_PROJECT_ENTRY = "vbaProject.bin";

    @Override
    public SecurityScanResult scan(InputStream inputStream, String fileName, DocumentFormat format) {
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

        // ZIP 容器内容检测
        try {
            Path tempFile = Files.createTempFile("pmis-docs-macro-", ".tmp");
            inputStream.transferTo(Files.newOutputStream(tempFile));
            byte[] fileBytes = Files.readAllBytes(tempFile);
            Files.deleteIfExists(tempFile);

            // 检测 vbaProject.bin（OOXML 中的 VBA 宏标记）
            if (containsVbaProject(fileBytes)) {
                findings.add(SecurityScanResult.SecurityFinding.builder()
                        .type("macro")
                        .description("检测到 VBA 宏项目 (vbaProject.bin)")
                        .level(SecurityLevel.HIGH)
                        .location("OOXML 容器")
                        .build());
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

    /**
     * 检测 ZIP 容器中是否包含 vbaProject.bin
     */
    private boolean containsVbaProject(byte[] fileBytes) {
        // 简单字节搜索
        String target = VBA_PROJECT_ENTRY;
        byte[] targetBytes = target.getBytes();
        if (fileBytes.length < targetBytes.length) {
            return false;
        }
        for (int i = 0; i <= fileBytes.length - targetBytes.length; i++) {
            boolean match = true;
            for (int j = 0; j < targetBytes.length; j++) {
                if (fileBytes[i + j] != targetBytes[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
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
