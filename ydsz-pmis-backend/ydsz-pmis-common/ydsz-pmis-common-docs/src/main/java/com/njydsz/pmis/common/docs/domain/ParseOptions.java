package com.njydsz.pmis.common.docs.domain;

import com.njydsz.pmis.common.docs.enums.ParseMode;

import lombok.Builder;
import lombok.Data;

/**
 * 文档解析选项
 * <p>
 * 控制解析行为的可配置参数。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
public class ParseOptions {

    /** 解析模式，默认完整模式 */
    @Builder.Default
    private ParseMode mode = ParseMode.FULL;

    /** 最大文件大小（字节），超过则拒绝解析，默认 50MB */
    @Builder.Default
    private long maxFileSize = 50L * 1024 * 1024;

    /** 解析超时时间（毫秒），默认 60 秒 */
    @Builder.Default
    private long timeoutMs = 60_000L;

    /** 是否提取表格 */
    @Builder.Default
    private boolean extractTables = true;

    /** 是否提取图片元数据 */
    @Builder.Default
    private boolean extractImages = true;

    /** 是否提取元数据 */
    @Builder.Default
    private boolean extractMetadata = true;

    /** 最大页数限制（超过则截断），0 表示不限制 */
    @Builder.Default
    private int maxPages = 0;

    /** 编码（仅文本文件有效），null 表示自动检测 */
    private String charset;
}
