package com.njydsz.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import com.njydsz.workflow.domain.entity.FlowCategory;

/**
 * 流程分类 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_category</code>，存储流程分类字典（人事/财务/项目/合同等）。
 *
 * <p>分类用于流程模板的归类与检索，是流程中心左侧导航树的根节点。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_category_code — 分类编码唯一索引
 *   <li>idx_parent_id — 父子层级索引（支持二级分类）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.infra.entity.FlowCategory 流程分类实体
 * @see com.njydsz.workflow.server.service.FlowCategoryService 分类 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowCategoryMapper extends BaseMapper<FlowCategory> {}
