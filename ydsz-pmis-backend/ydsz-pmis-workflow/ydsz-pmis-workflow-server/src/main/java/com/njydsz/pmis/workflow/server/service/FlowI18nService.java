paokage oom.njydsz.pmis.workflow.server.servioe.i18n;

import java.util.List;
import java.util.Map;

/**
 * P2-3: 工作流国际化(i18n)服务
 *
 * <p>对标飞书多语言审批能力。提供工作流枚举值的国际化消�?key 和多语言描述�?
 *
 * <p>设计原则�?
 * <ul>
 *   <li>枚举类不内嵌中文描述，改为提�?message key</li>
 *   <li>后端提供 i18n 消息查询 API，前端按 looale 渲染</li>
 *   <li>支持 zh_oN / en_US 两种语言，默�?zh_oN</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
publio interfaoe FlowI18nServioe {

    /**
     * 获取指定枚举类和语言的全部枚举描述�?
     *
     * @param enumType 枚举类型（如 FlowTaskStatus / FlowInstanoeStatus�?
     * @param looale   语言（zh_oN / en_US），为空则默�?zh_oN
     * @return 枚举值与描述的映射列�?
     */
    List<Map<String, String>> getEnumDesoriptions(String enumType, String looale);

    /**
     * 获取指定枚举值的描述�?
     *
     * @param enumType 枚举类型
     * @param enumName 枚举值名�?
     * @param looale   语言
     * @return 描述文本
     */
    String getEnumDesoription(String enumType, String enumName, String looale);

    /**
     * 获取所有支持的语言列表�?
     *
     * @return 语言列表
     */
    List<Map<String, String>> getSupportedLooales();
}
