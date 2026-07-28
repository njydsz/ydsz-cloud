package com.njydsz.workflow.server.service;

import java.util.List;
import java.util.Map;

/**
 * 流程国际化服务。
 * <p>多语言标题/意见/通知。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


public interface FlowI18nService {

    /**
     * 获取指定枚举类和语言的全部枚举描述。
     *
     * @param enumType 枚举类型（如 FlowTaskStatus / FlowInstanceStatus）
     * @param locale   语言（zh_CN / en_US），为空则默认 zh_CN
     * @return 枚举值与描述的映射列表
     */
    List<Map<String, String>> getEnumDescriptions(String enumType, String locale);

    /**
     * 获取指定枚举值的描述。
     *
     * @param enumType 枚举类型
     * @param enumName 枚举值名称
     * @param locale   语言
     * @return 描述文本
     */
    String getEnumDescription(String enumType, String enumName, String locale);

    /**
     * 获取所有支持的语言列表。
     *
     * @return 语言列表
     */
    List<Map<String, String>> getSupportedLocales();
}
