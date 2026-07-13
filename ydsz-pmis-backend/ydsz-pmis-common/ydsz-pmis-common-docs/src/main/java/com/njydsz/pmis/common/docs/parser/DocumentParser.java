package com.njydsz.pmis.common.docs.parser;

import java.io.InputStream;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.ParseOptions;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;

/**
 * 文档解析器 SPI 接口
 * <p>
 * 定义文档解析的标准规范。每种文档格式对应一个实现类，
 * 通过 {@link com.njydsz.pmis.common.docs.parser.registry.DocumentParserRegistry} 注册和路由。
 *
 * <p><b>实现要求：</b>
 * <ul>
 *   <li>实现类应保证线程安全，建议使用无状态设计</li>
 *   <li>{@link #parse(InputStream, String, ParseOptions)} 中传入的 InputStream 由调用方负责关闭</li>
 *   <li>大文件解析时应注意内存控制，遵循 {@link ParseOptions#getMaxFileSize()} 限制</li>
 *   <li>解析失败时应抛出 {@link com.njydsz.pmis.common.docs.exception.DocumentException}</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
public interface DocumentParser {

    /**
     * 解析文档
     *
     * @param inputStream 文档输入流（由调用方负责关闭）
     * @param fileName    文件名（用于格式推断和日志记录）
     * @param options     解析选项
     * @return 文档内容
     */
    DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options);

    /**
     * 获取此解析器支持的文档格式
     *
     * @return 支持的文档格式
     */
    DocumentFormat getSupportedFormat();

    /**
     * 检查此解析器是否支持指定格式
     *
     * @param format 文档格式
     * @return 如果支持返回 true
     */
    default boolean supports(DocumentFormat format) {
        return getSupportedFormat() == format;
    }
}
