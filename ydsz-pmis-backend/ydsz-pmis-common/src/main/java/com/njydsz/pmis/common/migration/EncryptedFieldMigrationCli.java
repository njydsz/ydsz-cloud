package com.njydsz.pmis.common.migration;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * EncryptedField 迁移命令行入口
 *
 * <p>脱离 Spring 容器, 直接通过 JDBC URL 执行:
 * <pre>
 *   java -cp ydsz-pmis-common.jar com.njydsz.pmis.common.migration.EncryptedFieldMigrationCli \
 *     --phase=ENCRYPT \
 *     --jdbcUrl=jdbc:postgresql://localhost:5432/pmis \
 *     --username=pmis \
 *     --password=*** \
 *     --batch=V1.0.0_018_ENCRYPTED_FIELD \
 *     --key=ENC(AES256,...) \
 *     --batchSize=500
 * </pre>
 *
 * <p>支持 phase:
 * <ul>
 *   <li>ENCRYPT: 加密所有默认列</li>
 *   <li>VERIFY : 抽样校验 (默认 100 行/列)</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class EncryptedFieldMigrationCli {

    /**
     * 命令行入口：解析参数后按 phase 执行加密或校验，并通过退出码反映执行结果
     *
     * @param args 命令行参数，格式 {@code --key=value}，必填项 {@code --jdbcUrl}
     */
    public static void main(String[] args) {
        Map<String, String> opts = parseArgs(args);
        String phase = opts.getOrDefault("phase", "ENCRYPT").toUpperCase();
        String jdbcUrl = opts.getOrDefault("jdbcUrl", "");
        String username = opts.getOrDefault("username", "pmis");
        String password = opts.getOrDefault("password", "");
        String batch = opts.getOrDefault("batch", "V1.0.0_018_ENCRYPTED_FIELD");
        String key = opts.get("key");
        int batchSize = Integer.parseInt(opts.getOrDefault("batchSize", "500"));
        int sampleSize = Integer.parseInt(opts.getOrDefault("sampleSize", "100"));

        if (jdbcUrl.isEmpty()) {
            System.err.println("错误: --jdbcUrl 必填");
            printUsage();
            System.exit(1);
        }

        log.info("[EncryptedField-CLI] 启动 phase={} batch={} jdbcUrl={}", phase, batch, jdbcUrl);
        EncryptedFieldMigrationService svc = EncryptedFieldMigrationService.fromJdbcUrl(jdbcUrl, username, password);

        try {
            switch (phase) {
                case "ENCRYPT": {
                    EncryptedFieldMigrationService.MigrationOptions opt = new EncryptedFieldMigrationService.MigrationOptions();
                    opt.batchCode = batch;
                    opt.batchSize = batchSize;
                    opt.aesKeyBase64 = key;
                    List<EncryptedFieldMigrationService.MigrationResult> results = svc.encryptAll(opt);
                    printEncryptSummary(results);
                    break;
                }
                case "VERIFY": {
                    List<EncryptedFieldMigrationService.VerifyResult> results = svc.verifyAll(batch, sampleSize);
                    printVerifySummary(results);
                    boolean allOk = results.stream().allMatch(EncryptedFieldMigrationService.VerifyResult::isAllOk);
                    System.exit(allOk ? 0 : 2);
                    break;
                }
                default:
                    System.err.println("错误: 未知 phase: " + phase);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            log.error("[EncryptedField-CLI] 执行失败 phase={} err={}", phase, e.getMessage(), e);
            System.exit(3);
        }
    }

    private static void printEncryptSummary(List<EncryptedFieldMigrationService.MigrationResult> results) {
        System.out.println();
        System.out.println("==================== ENCRYPT 汇总 ====================");
        long totalSuccess = 0, totalSkipped = 0, totalFailed = 0;
        for (EncryptedFieldMigrationService.MigrationResult r : results) {
            System.out.printf("  %s.%-15s success=%-6d skipped=%-6d failed=%-6d cost=%dms%n",
                    r.column().table(), r.column().cipherColumn(),
                    r.success(), r.skipped(), Result.failed(), r.costMs());
            totalSuccess += r.success();
            totalSkipped += r.skipped();
            totalFailed += Result.failed();
        }
        System.out.println("-------------------------------------------------------");
        System.out.printf("  TOTAL: success=%d skipped=%d failed=%d%n", totalSuccess, totalSkipped, totalFailed);
        System.out.println("=======================================================");
        System.out.println();
    }

    private static void printVerifySummary(List<EncryptedFieldMigrationService.VerifyResult> results) {
        System.out.println();
        System.out.println("==================== VERIFY 汇总 ======================");
        for (EncryptedFieldMigrationService.VerifyResult r : results) {
            System.out.printf("  %s.%-15s sample=%-4d match=%-4d mismatch=%-4d rate=%.2f%%%n",
                    r.column().table(), r.column().cipherColumn(),
                    r.sample(), r.match(), r.mismatch(), r.matchRate() * 100);
        }
        System.out.println("=======================================================");
        System.out.println();
    }

    private static Map<String, String> parseArgs(String[] args) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String a : args) {
            if (a == null || a.isEmpty() || !a.startsWith("--")) continue;
            int eq = a.indexOf('=');
            if (eq < 0) {
                m.put(a.substring(2), "true");
            } else {
                m.put(a.substring(2, eq), a.substring(eq + 1));
            }
        }
        return m;
    }

    private static void printUsage() {
        System.err.println();
        System.err.println("Usage: java -cp ydsz-pmis-common.jar com.njydsz.pmis.common.migration.EncryptedFieldMigrationCli \\");
        System.err.println("         --phase=ENCRYPT|VERIFY \\");
        System.err.println("         --jdbcUrl=jdbc:postgresql://host:port/db \\");
        System.err.println("         --username=pmis --password=*** \\");
        System.err.println("         --batch=V1.0.0_018_ENCRYPTED_FIELD \\");
        System.err.println("         --key=base64-32字节密钥 \\");
        System.err.println("         [--batchSize=500] [--sampleSize=100]");
        System.err.println();
    }
}
