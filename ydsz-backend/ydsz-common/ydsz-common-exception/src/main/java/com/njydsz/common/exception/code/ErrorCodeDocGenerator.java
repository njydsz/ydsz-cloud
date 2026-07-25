package com.njydsz.common.exception.code;

import java.util.Map;
import java.util.TreeMap;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 错误码文档生成器
 *
 * <p>将全局注册的错误码自动生成 Markdown 格式的字典文档。
 * 可用于：
 * <ul>
 *   <li>API 文档站点自动同步（CI 阶段生成 docs/error-codes.md）</li>
 *   <li>前后端协作共享（导出后供前端 ts 类型生成）</li>
 *   <li>运维排障手册（每个错误码对应处理建议）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * String markdown = ErrorCodeDocGenerator.generate();
 * Files.writeString(Paths.get("docs/error-codes.md"), markdown);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ErrorCodeDocGenerator {

    private ErrorCodeDocGenerator() {
        // 工具类
    }

    /**
     * 生成完整 Markdown 字典
     *
     * @return Markdown 字符串
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 错误码字典\n\n");
        sb.append("> 本文档由 `ErrorCodeDocGenerator` 自动生成，请勿手动编辑。\n");
        sb.append("> 最后更新：").append(java.time.LocalDateTime.now()).append("\n\n");
        sb.append("- 总错误码数：**").append(ErrorCodeFactory.count()).append("**\n");
        sb.append("- 总子错误码数：**").append(ErrorCodeFactory.countSubCodes()).append("**\n\n");
        sb.append("---\n\n");

        // 按分类生成
        for (ExceptionCategory category : ExceptionCategory.values()) {
            if (!category.isPrimary()) {
                continue;
            }
            appendCategory(sb, category);
        }

        return sb.toString();
    }

    /**
     * 生成分类的错误码表格
     */
    private static void appendCategory(StringBuilder sb, ExceptionCategory category) {
        Map<String, ExceptionCode> codes = ErrorCodeFactory.findByCategory(category);
        if (codes.isEmpty()) {
            return;
        }
        // 按 code 排序
        Map<String, ExceptionCode> sorted = new TreeMap<>(codes);

        sb.append("## ").append(category.getCode()).append(" - ")
                .append(category.getDescription()).append("\n\n");
        sb.append("| 错误码 | 国际化 Key | HTTP状态码 | 子错误码 | 描述 |\n");
        sb.append("|--------|------------|------------|----------|------|\n");

        for (Map.Entry<String, ExceptionCode> entry : sorted.entrySet()) {
            ExceptionCode ec = entry.getValue();
            String mainCode = entry.getKey();
            Map<String, String> subCodes = ErrorCodeFactory.getSubCodes(mainCode);
            int httpStatus = ec.getHttpStatus();
            String i18nKey = ec.getKey();
            ExceptionLevel level = inferLevel(category);

            if (subCodes.isEmpty()) {
                sb.append("| `").append(mainCode).append("` | `")
                        .append(i18nKey).append("` | ").append(httpStatus)
                        .append(" | - | ").append(level.name()).append(" |\n");
            } else {
                int idx = 0;
                for (Map.Entry<String, String> subEntry : new TreeMap<>(subCodes).entrySet()) {
                    String subCode = subEntry.getKey();
                    String description = ErrorCodeFactory.getSubCodeDescription(mainCode, subCode);
                    if (idx == 0) {
                        sb.append("| `").append(mainCode).append("` | `")
                                .append(i18nKey).append("` | ").append(httpStatus)
                                .append(" | `").append(subCode).append("` | ")
                                .append(description != null ? description : level.name())
                                .append(" |\n");
                    } else {
                        sb.append("|  |  |  | `").append(subCode)
                                .append("` | ")
                                .append(description != null ? description : "")
                                .append(" |\n");
                    }
                    idx++;
                }
            }
        }
        sb.append("\n");
    }

    /**
     * 推断异常级别（基于分类）
     */
    private static ExceptionLevel inferLevel(ExceptionCategory category) {
        switch (category) {
            case SYSTEM:
            case INFRASTRUCTURE:
                return ExceptionLevel.ERROR;
            case SECURITY:
            case RATE_LIMIT:
                return ExceptionLevel.WARN;
            case EXTERNAL:
                return ExceptionLevel.WARN;
            default:
                return ExceptionLevel.INFO;
        }
    }

    /**
     * 生成 JSON 格式（供前端类型生成）
     *
     * @return JSON 字符串
     */
    public static String generateJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generatedAt\": \"").append(java.time.LocalDateTime.now()).append("\",\n");
        sb.append("  \"totalCodes\": ").append(ErrorCodeFactory.count()).append(",\n");
        sb.append("  \"codes\": [\n");

        Map<String, ExceptionCode> all = ExceptionCodeRegistry.allRegistered();
        java.util.List<String> sortedKeys = new java.util.ArrayList<>(all.keySet());
        java.util.Collections.sort(sortedKeys);

        boolean first = true;
        for (String code : sortedKeys) {
            ExceptionCode ec = all.get(code);
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("    {\n");
            sb.append("      \"code\": \"").append(code).append("\",\n");
            sb.append("      \"key\": \"").append(ec.getKey()).append("\",\n");
            sb.append("      \"httpStatus\": ").append(ec.getHttpStatus()).append(",\n");
            sb.append("      \"category\": \"").append(ec.getCategory().name()).append("\",\n");
            Map<String, String> subCodes = ErrorCodeFactory.getSubCodes(code);
            if (!subCodes.isEmpty()) {
                sb.append("      \"subCodes\": [\n");
                int idx = 0;
                for (Map.Entry<String, String> subEntry : new TreeMap<>(subCodes).entrySet()) {
                    if (idx > 0) sb.append(",\n");
                    sb.append("        { \"code\": \"").append(subEntry.getKey())
                            .append("\", \"key\": \"").append(subEntry.getValue()).append("\" }");
                    idx++;
                }
                sb.append("\n      ],\n");
            }
            sb.append("    }");
        }

        sb.append("\n  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 生成 TypeScript 类型定义（供前端使用）
     *
     * @return TypeScript 字符串
     */
    public static String generateTypeScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by ErrorCodeDocGenerator. DO NOT EDIT.\n");
        sb.append("// Generated at: ").append(java.time.LocalDateTime.now()).append("\n\n");

        Map<String, ExceptionCode> all = ExceptionCodeRegistry.allRegistered();
        java.util.List<String> sortedKeys = new java.util.ArrayList<>(all.keySet());
        java.util.Collections.sort(sortedKeys);

        // 枚举定义
        sb.append("/** 错误码枚举（按主错误码） */\n");
        sb.append("export enum ErrorCode {\n");
        for (String code : sortedKeys) {
            String constantName = code.replace("-", "_");
            sb.append("  /** ").append(all.get(code).getKey()).append(" */\n");
            sb.append("  ").append(constantName).append(" = '").append(code).append("',\n");
        }
        sb.append("}\n\n");

        // 类型定义
        sb.append("/** 错误码元信息 */\n");
        sb.append("export interface ErrorCodeInfo {\n");
        sb.append("  code: string;\n");
        sb.append("  key: string;\n");
        sb.append("  httpStatus: number;\n");
        sb.append("  category: 'BUSINESS' | 'SYSTEM' | 'SECURITY' | 'RATE_LIMIT' | 'EXTERNAL'\n");
        sb.append("    | 'VALIDATION' | 'INFRASTRUCTURE' | 'TIMEOUT' | 'CONCURRENCY' | 'DUPLICATE';\n");
        sb.append("}\n\n");

        // 元信息表
        sb.append("/** 错误码元信息表（按主错误码索引） */\n");
        sb.append("export const ERROR_CODE_INFO: Record<string, ErrorCodeInfo> = {\n");
        for (String code : sortedKeys) {
            ExceptionCode ec = all.get(code);
            sb.append("  '").append(code).append("': {\n");
            sb.append("    code: '").append(code).append("',\n");
            sb.append("    key: '").append(ec.getKey()).append("',\n");
            sb.append("    httpStatus: ").append(ec.getHttpStatus()).append(",\n");
            sb.append("    category: '").append(ec.getCategory().name()).append("',\n");
            sb.append("  },\n");
        }
        sb.append("};\n");

        return sb.toString();
    }
}
