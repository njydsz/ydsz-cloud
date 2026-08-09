package com.njydsz.common.docs.domain;

import java.time.Duration;

import lombok.Builder;
import lombok.Data;

/**
 * 文档解析结果
 * <p>
 * 封装解析操作的完整输出，包括文档内容、解析耗时和解析状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class DocumentParseResult {

    /** 文档内容 */
    private DocumentContent content;

    /** 解析耗时 */
    private Duration elapsed;

    /** 解析是否成功 */
    private boolean success;

    /** 错误消息（解析失败时） */
    private String errorMessage;

    /** 源文件名 */
    private String fileName;

    /** 源文件大小（字节） */
    private long fileSize;
}
