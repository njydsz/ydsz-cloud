package com.njydsz.pmis.common.docs.security.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.SecurityScanResult;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.enums.SecurityLevel;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档安全扫描器组合实现
 * <p>
 * 聚合所有 {@link DocumentSecurityScanner} 实现，对文档进行全量安全扫描。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Component
public class DocumentSecurityScannerComposite implements DocumentSecurityScanner {

    private final List<DocumentSecurityScanner> scanners;

    public DocumentSecurityScannerComposite(List<DocumentSecurityScanner> scanners) {
        // 过滤自身，避免递归
        this.scanners = scanners.stream()
                .filter(s -> s != this)
                .toList();
        log.info("[DocumentSecurityScannerComposite] 已注册 {} 个安全扫描器", this.scanners.size());
    }

    @Override
    public SecurityScanResult scan(InputStream inputStream, String fileName, DocumentFormat format) {
        if (inputStream == null) {
            return SecurityScanResult.builder()
                    .securityLevel(SecurityLevel.SAFE)
                    .findings(List.of())
                    .success(false)
                    .errorMessage("输入流为空")
                    .build();
        }

        // 写入临时文件，因为多个扫描器需要重复读取
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pmis-docs-scan-", ".tmp");
            inputStream.transferTo(Files.newOutputStream(tempFile));

            List<SecurityScanResult.SecurityFinding> allFindings = new ArrayList<>();
            boolean allSuccess = true;
            String lastError = null;

            for (DocumentSecurityScanner scanner : scanners) {
                try (InputStream fis = Files.newInputStream(tempFile)) {
                    SecurityScanResult result = scanner.scan(fis, fileName, format);
                    if (result.isSuccess()) {
                        if (result.getFindings() != null) {
                            allFindings.addAll(result.getFindings());
                        }
                    } else {
                        allSuccess = false;
                        lastError = result.getErrorMessage();
                        log.warn("[DocumentSecurityScannerComposite] 扫描器 {} 执行失败: {}",
                                scanner.getName(), lastError);
                    }
                } catch (IOException e) {
                    allSuccess = false;
                    log.error("[DocumentSecurityScannerComposite] 扫描器 {} IO 异常", scanner.getName(), e);
                }
            }

            return SecurityScanResult.builder()
                    .securityLevel(determineLevel(allFindings))
                    .findings(allFindings)
                    .success(allSuccess)
                    .errorMessage(lastError)
                    .build();

        } catch (IOException e) {
            log.error("[DocumentSecurityScannerComposite] 临时文件写入失败", e);
            return SecurityScanResult.builder()
                    .securityLevel(SecurityLevel.SAFE)
                    .findings(List.of())
                    .success(false)
                    .errorMessage("IO 错误: " + e.getMessage())
                    .build();
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // 临时文件删除失败不影响主流程
                }
            }
        }
    }

    /**
     * 根据风险项列表确定最终安全等级
     */
    private SecurityLevel determineLevel(List<SecurityScanResult.SecurityFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return SecurityLevel.SAFE;
        }
        return findings.stream()
                .map(SecurityScanResult.SecurityFinding::getLevel)
                .max(Enum::compareTo)
                .orElse(SecurityLevel.SAFE);
    }

    @Override
    public String getName() {
        return "composite";
    }
}
