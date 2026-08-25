package com.njydsz.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.workflow.infra.entity.FlowQuickComment;

/**
 * 审批常用语 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_quick_comment</code>，存储审批常用语（快捷回复）。
 *
 * <p>常用语按用户维度配置（个人常用/部门常用/全局常用），支持排序与启用/禁用。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_user_comment — (userId+content) 唯一索引
 *   <li>idx_user_scope — 用户/范围过滤索引（PERSONAL/DEPT/GLOBAL）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.infra.entity.FlowQuickComment 常用语实体
 * @see com.njydsz.workflow.server.service.FlowCommentService 评论服务（含常用语能力）
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowQuickCommentMapper extends BaseMapper<FlowQuickComment> {}
