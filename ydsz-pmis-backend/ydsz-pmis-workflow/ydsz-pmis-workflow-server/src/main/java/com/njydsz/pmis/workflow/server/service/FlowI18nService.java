package com.njydsz.pmis.workflow.server.service.i18n;

import java.util.List;
import java.util.Map;

/**
 * P2-3: 工作流国际化(i18n)服务
 *
 * <p>对标飞书多语言审批能力。提供工作流枚举值的国际化消息 key 和多语言描述。
 *
 * <p>设计原则：
 * <ul>
 *   <li>枚举类不内嵌中文描述，改为提供 message key</li>
 *   <li>后端提供 i18n 消息查询 API，前端按 locale 渲染</li>
 *   <li>支持 zh_CN / en_US 两种语言，默认 zh_CN</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
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
