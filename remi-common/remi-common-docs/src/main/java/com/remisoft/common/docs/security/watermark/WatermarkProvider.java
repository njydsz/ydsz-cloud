package com.remisoft.common.docs.security.watermark;

import java.io.InputStream;

import com.remisoft.common.docs.enums.DocumentFormat;

/**
 * 文档水印提供者接口
 * <p>
 * 为文档添加水印（文本/图片），用于版权保护和泄露追溯。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface WatermarkProvider {

    /**
     * 为文档添加水印
     *
     * @param inputStream 原始文档输入流
     * @param fileName     原始文件名
     * @param format       文档格式
     * @param watermarkText 水印文本
     * @return 添加水印后的文档字节流
     */
    byte[] addWatermark(InputStream inputStream, String fileName, DocumentFormat format, String watermarkText);

    /**
     * 检查是否支持指定格式
     *
     * @param format 文档格式
     * @return 如果支持返回 true
     */
    boolean supports(DocumentFormat format);
}
