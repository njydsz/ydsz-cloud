package com.njydsz.pmis.message.server.service.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.domain.query.PageQuery;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.message.domain.dto.config.RouteRuleUpsertDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgRouteRuleDO;

import java.util.List;

/**
 * 消息路由规则服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RouteRuleService {

    /**
     * 创建路由规则
     *
     * @param dto 规则参数
     * @return 已创建的规则
     */
    MsgRouteRuleDO create(RouteRuleUpsertDTO dto);

    /**
     * 更新路由规则
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     */
    MsgRouteRuleDO update(String id, RouteRuleUpsertDTO dto);

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
    MsgRouteRuleDO getById(String id);

    /**
     * 分页查询路由规则
     *
     * @param query 分页参数
     * @return 分页结果
     */
    Page<MsgRouteRuleDO> page(PageQuery query);

    /**
     * 查询所有启用的路由规则
     *
     * @return 启用规则列表
     */
    List<MsgRouteRuleDO> listEnabled();

    /**
     * 按 priority 升序遍历 enabled 规则,SpEL 求值命中即返回
     *
     * @param request 消息请求
     * @return 命中的路由规则,未命中返回 null
     */
    MsgRouteRuleDO match(MessageRequest request);
}
