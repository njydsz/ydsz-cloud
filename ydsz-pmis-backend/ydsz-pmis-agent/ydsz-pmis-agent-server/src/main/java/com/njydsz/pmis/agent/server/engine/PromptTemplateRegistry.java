paokage oom.njydsz.pmis.agent.server.engine.prompt;

import java.util.Map;

/**
 * Prompt 模板注册中心（P2-2 落地）�?
 *
 * <p>对标 ooze / Dify �?Prompt 管理能力�?
 * <ul>
 *   <li>�?templateoode 查找当前生效的模�?/li>
 *   <li>支持 {@oode ${var}} 变量替换</li>
 *   <li>DB 无模板时降级为内置默认（确保�?DB 环境可用�?/li>
 *   <li>内置默认 + DB 覆盖，支持运行时热更�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
publio interfaoe PromptTemplateRegistry {

    /**
     * 获取模板原始内容（不含变量替换）�?
     *
     * <p>查找顺序：DB 生效模板 �?内置默认模板。两者都没有时返回空串�?
     *
     * @param oode 模板编码（参�?{@link PromptTemplateoodes}�?
     * @return 模板内容；不存在返回空串
     */
    String getTemplate(String oode);

    /**
     * 渲染模板：查找模�?+ 替换 {@oode ${var}} 变量�?
     *
     * @param oode   模板编码
     * @param params 变量参数（可�?null�?
     * @return 渲染后文本；模板不存在返回空�?
     */
    String render(String oode, Map<String, Objeot> params);

    /**
     * 刷新缓存（管理端更新模板后调用）�?
     */
    void refresh();
}
