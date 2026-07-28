package com.njydsz.message.server.service.config;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.domain.dto.config.RouteRuleUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgRouteRule;

/**
 * 消息路由规则 Service
 *
 * <p>基于 SpEL 表达式的「消息 → 通道/模板」路由能力。在多通道、多模板并存时,
 * 路由规则按 {@code priority} 升序匹配,首个 SpEL 求值命中的规则决定最终通道和模板,
 * 实现"什么样的消息走哪个通道发什么内容"的灵活配置。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #create} / {@link #update} / {@link #delete} / {@link #getById} / {@link #page}</li>
 *   <li><b>启用规则查询</b>：{@link #listEnabled} — 内存中按 priority 升序缓存</li>
 *   <li><b>匹配</b>：{@link #match} — SpEL 求值命中即返回,未命中返回 null</li>
 * </ul>
 *
 * <p><b>匹配算法：</b>按 priority 升序遍历 {@code enabled} 规则,对每条规则的
 * {@code conditionExpr}（SpEL 表达式）求值,首次 {@code true} 即返回。
 * 上下文变量包括 {@code request.bizType / bizId / receiver / channel / priority / locale}
 * 等 {@link MessageRequest} 字段。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.message.domain.entity.config.MsgRouteRule 路由规则实体
 * @see com.njydsz.message.server.service.core.MessageService 消息发送主流程(调用 match 选择通道/模板)
 */
public interface RouteRuleService {

    /**
     * 创建路由规则
     *
     * @param dto 规则参数
     * @return 已创建的规则
     */
    MsgRouteRule create(RouteRuleUpsertDTO dto);

    /**
     * 更新路由规则
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     */
    MsgRouteRule update(String id, RouteRuleUpsertDTO dto);

    /**
     * 删除路由规则(逻辑删除)
     *
     * @param id 规则 ID
     */
    void delete(String id);

    /**
     * 根据 ID 查询路由规则
     *
     * @param id 规则 ID
     * @return 规则实体
     */
    MsgRouteRule getById(String id);

    /**
     * 分页查询路由规则
     *
     * @param query 分页参数
     * @return 分页结果
     */
    Page<MsgRouteRule> page(PageQuery query);

    /**
     * 查询所有启用的路由规则
     *
     * @return 启用规则列表
     */
    List<MsgRouteRule> listEnabled();

    /**
     * 按 priority 升序遍历 enabled 规则,SpEL 求值命中即返回
     *
     * @param request 消息请求
     * @return 命中的路由规则,未命中返回 null
     */
    MsgRouteRule match(MessageRequest request);
}
