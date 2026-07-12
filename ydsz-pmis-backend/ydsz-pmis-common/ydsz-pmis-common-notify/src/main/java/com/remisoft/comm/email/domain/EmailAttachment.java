package com.njydsz.pmis.common.notify.email.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.File;
/**
 * 邮件附件封装类
 *
 * <p>用于封装邮件附件的完整信息，包括文件路径、文件名和 File 对象。
 * 支持两种构造方式：直接指定文件路径，或提供完整的 File 对象。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 方式1：通过路径构造
 * EmailAttachment attachment = EmailAttachment.builder()
 *         .filePath("D:/tmp/report.xlsx")
 *         .build();
 *
 * // 方式2：通过 File 对象构造
 * EmailAttachment attachment = EmailAttachment.builder()
 *         .file(new File("D:/tmp/report.xlsx"))
 *         .build();
 *
 * // 方式3：指定自定义文件名
 * EmailAttachment attachment = EmailAttachment.builder()
 *         .filePath("D:/tmp/report.xlsx")
 *         .fileName("自定义名称.xlsx")
 *         .build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachment {

    /**
     * 附件文件路径，与 {@link #file} 二选一
     */
    private String filePath;

    /**
     * 附件 File 对象，与 {@link #filePath} 二选一
     */
    private File file;

    /**
     * 附件显示文件名，为空时使用 File 原始名称
     */
    private String fileName;

    /**
     * MIME 类型，例如 application/pdf，留空时由 FileNameMap 推断
     */
    private String contentType;

    public File getFile() {
        if (this.file != null) {
            return this.file;
        }
        if (this.filePath != null && !this.filePath.isBlank()) {
            return new File(this.filePath);
        }
        throw new IllegalStateException("附件文件未设置，请指定 filePath 或 file");
    }

    public String getDisplayName() {
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return getFile().getName();
    }

    /**
     * 判断附件是否有效（至少有 filePath 或 file）
     *
     * @return 有效返回 true，否则返回 false
     */
    public boolean isValid() {
        return (filePath != null && !filePath.isBlank()) || file != null;
    }
}