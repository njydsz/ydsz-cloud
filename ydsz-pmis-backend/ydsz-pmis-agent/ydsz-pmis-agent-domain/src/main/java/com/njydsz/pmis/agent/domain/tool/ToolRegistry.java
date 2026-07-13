package com.njydsz.pmis.agent.domain.tool;

import java.util.List;

import com.njydsz.pmis.agent.domain.model.ToolCall;
import com.njydsz.pmis.agent.domain.model.ToolDefinition;

/**
 * 工具注册中心接口
 *
 * <p>管理工具的注册、查询和执行。实现可选择：
 * <ul>
 *   <li>注解扫描（{@link Tool} 注解自动注册）</li>
 *   <li>编程式注册（手动注册 ToolExecutor）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface ToolRegistry {

    /**
     * 注册一个工具执行器
     *
     * @param name     工具名称
     * @param executor 工具执行器
     */
    void register(String name, ToolExecutor executor);

    /**
     * 注销工具
     *
     * @param name 工具名称
     */
    void unregister(String name);

    /**
     * 执行工具调用
     *
     * @param toolCall 工具调用请求
     * @return 工具执行结果（JSON 字符串）
     */
    String execute(ToolCall toolCall);

    /**
     * 获取所有已注册工具的定义
     *
     * @return 工具定义列表
     */
    List<ToolDefinition> getToolDefinitions();

    /**
     * 获取已注册工具数
     */
    int size();

    /**
     * 判断是否已注册指定工具
     *
     * @param name 工具名称
     * @return true=已注册
     */
    boolean contains(String name);
}
