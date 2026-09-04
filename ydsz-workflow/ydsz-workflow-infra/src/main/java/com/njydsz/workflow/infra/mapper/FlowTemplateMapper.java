package com.njydsz.workflow.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.workflow.domain.entity.FlowTemplate;

/**
 * 流程模板 Mapper
 *
 * <p>对应数据表 <code>ydsz_flow_template</code>，存储可复用的流程模板（带版本化与继承关系）。
 *
 * <p>模板是「流程定义的母版」，按分类与编码组织，支持版本升级与父子继承。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_template_code — 模板编码唯一索引
 *   <li>idx_category_id — 分类过滤索引
 *   <li>idx_is_latest — 最新版本过滤索引（默认仅返回 is_latest=1）
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.domain.entity.FlowTemplate 流程模板实体
 * @see com.njydsz.workflow.server.service.FlowTemplateService 流程模板 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FlowTemplateMapper extends BaseMapper<FlowTemplate> {

  /**
   * 按分类查询模板列表（按 sort_order 升序）
   *
   * <p>P2-9: 仅返回 {@code is_latest=1} 的最新版本。
   *
   * @param category 分类（可空，为空查全部）
   * @return 模板列表
   */
  List<FlowTemplate> selectByCategory(@Param("category") String category);

  /**
   * 按模板编码查询最新版本
   *
   * <p>P2-9: 仅返回 {@code is_latest=1} 的记录，保持与旧调用方语义一致。
   *
   * @param templateCode 模板编码
   * @return 模板实体（最新版本），不存在返回 null
   */
  FlowTemplate selectByTemplateCode(@Param("templateCode") String templateCode);

  /**
   * 增加模板使用次数
   *
   * @param templateCode 模板编码
   * @return 受影响行数
   */
  int incrementUseCount(@Param("templateCode") String templateCode);

  /**
   * P2-9: 查询某 template_code 的全部历史版本（按 version 降序）。
   *
   * @param templateCode 模板编码
   * @return 全部版本列表（最新版本在首位）
   */
  List<FlowTemplate> selectVersionsByTemplateCode(@Param("templateCode") String templateCode);

  /**
   * P2-9: 按父模板 ID 查询继承关系列表。
   *
   * @param parentTemplateId 父模板主键 ID
   * @return 继承自父模板的子模板列表
   */
  List<FlowTemplate> selectByParentTemplateId(@Param("parentTemplateId") String parentTemplateId);

  /**
   * P2-9: 将指定 template_code 的所有版本标记为非最新（is_latest=0）。
   *
   * <p>用于创建新版本前，把旧版本统一降级。
   *
   * @param templateCode 模板编码
   * @return 受影响行数
   */
  int markAsNotLatest(@Param("templateCode") String templateCode);

  /**
   * P2-9: 查询某 template_code 当前的最大版本号。
   *
   * @param templateCode 模板编码
   * @return 最大版本号；不存在任何版本时返回 null
   */
  Integer selectMaxVersion(@Param("templateCode") String templateCode);
}
