package com.njydsz.pmis.common.email.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.File;
/**
 * 邮件内嵌资源封装�?
 *
 * <p>用于封装邮件中内嵌资源（如图片）的完整信息�?
 * 内嵌资源通过 CID（Content-ID）在 HTML 正文中引用�?/p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // �?HTML 正文中引用：
 * // <img src="cid:company-logo" />
 *
 * EmailInlineResource inlineResource = EmailInlineResource.builder()
 *         .rscId("company-logo")
 *         .filePath("D:/images/logo.png")
 *         .build();
 *
 * Email email = Email.builder()
 *         .to("user@example.com")
 *         .subject("带Logo的邮�?)
 *         .content("<html><body><img src='cid:company-logo' /></body></html>")
 *         .inlineResources(List.of(inlineResource))
 *         .build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailInlineResource {

    /**
     * 内嵌资源 ID（即 Content-ID），HTML 中通过 cid:xxx 引用
     */
    private String rscId;

    /**
     * 内嵌资源文件路径，与 {@link #file} 二选一
     */
    private String filePath;

    /**
     * 内嵌资源 File 对象，与 {@link #filePath} 二选一
     */
    private File file;

    /**
     * MIME 类型，例�?image/png
     */
    private String contentType;

    /**
     * 资源名称
     */
    private String name;

    public File getFile() {
        if (this.file != null) {
            return this.file;
        }
        if (this.filePath != null && !this.filePath.isBlank()) {
            return new File(this.filePath);
        }
        throw new IllegalStateException("内嵌资源文件未设置，请指�?filePath �?file");
    }

    public String getResourceId() {
        return rscId;
    }

    /**
     * 判断内嵌资源是否有效（必须同时有 rscId 和文件来源）
     *
     * @return 有效返回 true，否则返�?false
     */
    public boolean isValid() {
        return rscId != null && !rscId.isBlank() && ((filePath != null && !filePath.isBlank()) || file != null);
    }
}