package com.njydsz.workflow.server.service.impl.definition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.infra.entity.FlowDefinitionDO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowSkipDO;
import com.njydsz.workflow.infra.mapper.FlowDefinitionMapper;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.workflow.infra.mapper.FlowSkipMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 流程定义查询服务
 *
 * <p>承担流程定义<b>只读查询</b>全部职责：按编码/版本查询、最新版本查询、分页查询、
 * 详情查询（含节点+跳转）、版本历史列表。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>已发布版本查询</b>：按 {@code flowCode + version + tenantId} 三元组唯一定位，带 {@link Cacheable} 缓存
 *   <li><b>最新版本查询</b>：按 {@code flowCode + tenantId} 查询最新版本定义，带 {@link Cacheable} 缓存
 *   <li><b>分页查询</b>：按分类/编码过滤的分页列表查询
 *   <li><b>详情查询</b>：组装 {@code definition + nodes + skips} 三元组供设计器回显
 *   <li><b>版本历史</b>：列出同 flowCode 的全部历史版本
 * </ul>
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>{@code flow:def:published:{code}} TTL 10min（高频读取）
 *   <li>{@code flow:def:latest:{code}} TTL 5min（更新频率较高）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class FlowDefinitionQueryService {

  /** 流程定义 Mapper */
  private final FlowDefinitionMapper definitionMapper;

  /** 流程节点 Mapper */
  private final FlowNodeMapper nodeMapper;

  /** 流程跳转 Mapper */
  private final FlowSkipMapper skipMapper;

  public FlowDefinitionQueryService(
      FlowDefinitionMapper definitionMapper,
      FlowNodeMapper nodeMapper,
      FlowSkipMapper skipMapper) {
    this.definitionMapper = definitionMapper;
    this.nodeMapper = nodeMapper;
    this.skipMapper = skipMapper;
  }

  /**
   * 查询已发布的流程定义（带缓存）
   *
   * <p>按 {@code flowCode + version + tenantId} 三元组唯一定位，缓存至
   * {@code ydsz:flow:def:published:{code}:{version}:{tenantId}}，TTL 10min。
   *
   * @param flowCode 流程编码
   * @param version 版本号（为空时取 {@code "1.0"}）
   * @param tenantId 租户 ID（为空时取 {@code SecurityContext}，默认 {@code "1"}）
   * @return 流程定义；不存在返回 {@code null}
   */
  @Transactional(readOnly = true)
  @Cacheable(
      value = CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
      key = "#flowCode + ':' + #version + ':' + #tenantId",
      unless = "#result == null")
  public FlowDefinitionDO getPublished(String flowCode, String version, String tenantId) {
    if (!StringUtils.hasText(version)) {
      version = "1.0";
    }
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return definitionMapper.selectPublished(flowCode, version, tid);
  }

  /**
   * 查询指定流程编码的最新版本定义（带缓存）
   *
   * <p>按 {@code flowCode + tenantId} 唯一定位，缓存至
   * {@code ydsz:flow:def:latest:{code}:{tenantId}}，TTL 5min。
   *
   * @param flowCode 流程编码
   * @param tenantId 租户 ID（为空时取 {@code SecurityContext}，默认 {@code "1"}）
   * @return 最新版本定义；不存在返回 {@code null}
   */
  @Transactional(readOnly = true)
  @Cacheable(
      value = CacheConstants.FLOW_DEF_LATEST_CACHE,
      key = "#flowCode + ':' + #tenantId",
      unless = "#result == null")
  public FlowDefinitionDO getLatestByCode(String flowCode, String tenantId) {
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
    return definitionMapper.selectLatestByCode(flowCode, tid);
  }

  /**
   * 分页查询流程定义列表
   *
   * <p>仅返回 {@code activityStatus=1}（启用）且未逻辑删除的记录，
   * 按 {@code created_at} 倒序排列。支持按 {@code category}（精确）和 {@code flowCode}（模糊）过滤。
   *
   * @param pageNo 页码（从 1 开始）
   * @param pageSize 每页大小
   * @param category 分类编码过滤（可选）
   * @param flowCode 流程编码模糊过滤（可选）
   * @return 流程定义列表
   */
  @Transactional(readOnly = true)
  public List<FlowDefinitionDO> page(int pageNo, int pageSize, String category, String flowCode) {
    Page<FlowDefinitionDO> page = new Page<>(pageNo, pageSize);
    LambdaQueryWrapper<FlowDefinitionDO> w = new LambdaQueryWrapper<>();
    w.eq(StringUtils.hasText(category), FlowDefinitionDO::getCategory, category)
        .like(StringUtils.hasText(flowCode), FlowDefinitionDO::getFlowCode, flowCode)
        .eq(FlowDefinitionDO::getActivityStatus, 1)
        .eq(FlowDefinitionDO::getDeleted, 0)
        .orderByDesc(FlowDefinitionDO::getCreatedAt);
    return definitionMapper.selectPage(page, w).getRecords();
  }

  /**
   * 查询流程定义详情（含节点 + 跳转）
   *
   * <p>组装 {@code definition + nodes + skips} 三元组供设计器回显用。
   *
   * @param definitionId 流程定义 ID
   * @return 详情 Map（{@code definition/nodes/skips}）；不存在返回 {@code null}
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getDetail(String definitionId) {
    FlowDefinitionDO definition = definitionMapper.selectById(definitionId);
    if (definition == null) {
      return null;
    }
    List<FlowNodeDO> nodes = nodeMapper.selectByDefinitionId(definitionId);
    List<FlowSkipDO> skips = skipMapper.selectByDefinitionId(definitionId);
    Map<String, Object> result = new HashMap<>();
    result.put("definition", definition);
    result.put("nodes", nodes);
    result.put("skips", skips);
    return result;
  }

  /**
   * 查询流程版本历史
   *
   * <p>按 {@code flowCode+tenantId} 维度查询该流程的全部历史版本，
   * 组装为扁平 Map 列表供设计器版本时间线展示。
   *
   * @param definitionId 当前版本定义 ID（用于回溯 {@code flowCode/tenantId}）
   * @return 版本列表（按定义顺序）
   * @throws SysException {@code NOT_FOUND} — 流程定义不存在
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listVersions(String definitionId) {
    FlowDefinitionDO def = definitionMapper.selectById(definitionId);
    if (def == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("流程定义不存在: " + definitionId)
          .build();
    }
    String tenantId = def.getTenantId() != null ? def.getTenantId() : "1";
    List<FlowDefinitionDO> versions = definitionMapper.selectByFlowCode(def.getFlowCode(), tenantId);
    List<Map<String, Object>> result = new ArrayList<>();
    for (FlowDefinitionDO v : versions) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("id", v.getId());
      map.put("version", v.getFlowVersion());
      map.put("flowName", v.getFlowName());
      map.put("isPublish", v.getIsPublish());
      map.put("activityStatus", v.getActivityStatus());
      map.put("category", v.getCategory());
      map.put("description", v.getDescription());
      map.put("createdAt", v.getCreatedAt());
      map.put("updatedAt", v.getUpdatedAt());
      result.add(map);
    }
    log.info("[Flow] 查询版本历史: flowCode={} count={}", def.getFlowCode(), result.size());
    return result;
  }
}
