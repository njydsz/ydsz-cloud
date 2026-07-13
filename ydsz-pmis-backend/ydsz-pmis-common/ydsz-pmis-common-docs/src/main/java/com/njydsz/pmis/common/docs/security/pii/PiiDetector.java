package com.njydsz.pmis.common.docs.security.pii;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.PiiFinding;
import com.njydsz.pmis.common.docs.enums.PiiType;

import java.util.List;

/**
 * PII 检测器接口
 * <p>
 * 定义个人身份信息检测的标准规范，每种 PII 类型对应一个实现。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
public interface PiiDetector {

    /**
     * 检测文档内容中的 PII
     *
     * @param content 文档内容
     * @return PII 发现列表
     */
    List<PiiFinding> detect(DocumentContent content);

    /**
     * 获取此检测器支持的 PII 类型
     *
     * @return PII 类型
     */
    PiiType getSupportedType();

    /**
     * 将匹配到的原文脱敏
     *
     * @param matchedText 匹配到的原文
     * @return 脱敏后的文本
     */
    String mask(String matchedText);
}
