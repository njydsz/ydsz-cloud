package com.njydsz.workflow.server.service.impl.integration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.feign.assembler.NameType;
import com.njydsz.workflow.domain.gateway.NameServiceClient;

/**
 * 名称查询网关适配器（GAP-A2 装配缺口修复）。
 *
 * <p>为 domain 层 {@link NameServiceClient} 网关接口提供落地实现：
 * 内部委托 common-feign 的 {@link NameAssembler}（ID → 名称富化组件，带缓存与
 * N+1 防护），由 {@code FlowAutoConfiguration} 以
 * {@code @ConditionalOnMissingBean(NameServiceClient.class)} 注册。
 *
 * <p><b>装配链路说明：</b>
 *
 * <ul>
 *   <li>部署环境若提供了真实的 {@code NameAssembler} 实现（如 userinfo-api 覆盖平台 NoOp 兜底），
 *       本适配器即获得真实名称解析能力</li>
 *   <li>若仅存在平台兜底 {@code NoOpNameAssembler}（返回空映射），本适配器降级为"返回 null"，
 *       与 {@code FlowUserCacheService} 既有 catch-and-warn 行为一致，不阻塞审批主链路，
 *       且保证应用上下文始终可启动（修复此前无任何 Bean 导致的启动失败风险）</li>
 * </ul>
 *
 * <p><b>nameType 支持范围：</b>当前用户中心富化契约仅覆盖真实姓名维度，
 * {@code getUserNameByType} 的 SHORT / FULL / ENGLISH 等细分类型暂按 USER 维度返回，
 * 待 userinfo 暴露多形态名称端点后按类型分派（见 TODO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class NameServiceClientAdapter implements NameServiceClient {

  /** ID → 名称富化组件（common-feign 平台契约，含缓存；平台兜底被显式禁用时可能为 null） */
  private final NameAssembler nameAssembler;

  /**
   * 构造适配器（装配安全：{@code nameAssembler} 允许为 null，此时所有解析降级返回空）。
   *
   * @param nameAssembler ID → 名称富化组件（可为 null）
   */
  public NameServiceClientAdapter(NameAssembler nameAssembler) {
    this.nameAssembler = nameAssembler;
  }

  /**
   * 根据用户 ID 查询用户名称。
   *
   * @param userId 用户 ID
   * @return 用户名称，未找到返回 null
   */
  @Override
  public String getUserName(String userId) {
    if (userId == null || userId.isBlank() || nameAssembler == null) {
      return null;
    }
    try {
      return nameAssembler.resolveName(NameType.USER, userId);
    } catch (Exception e) {
      // 降级不阻塞主链路：FlowUserCacheService 已有告警日志，此处静默返回 null
      log.warn("[FlowNameAdapter] 解析用户名称失败 userId={}: {}", userId, e.getMessage());
      return null;
    }
  }

  /**
   * 批量查询用户名称。
   *
   * @param userIds 用户 ID 列表
   * @return 用户 ID → 用户名称映射（未解析到的 ID 不包含在结果中）
   */
  @Override
  public Map<String, String> getUserNames(List<String> userIds) {
    if (userIds == null || userIds.isEmpty() || nameAssembler == null) {
      return Collections.emptyMap();
    }
    try {
      Map<String, String> resolved = nameAssembler.batchResolveNames(NameType.USER, userIds);
      return resolved == null ? Collections.emptyMap() : new HashMap<>(resolved);
    } catch (Exception e) {
      log.warn("[FlowNameAdapter] 批量解析用户名称失败 size={}: {}", userIds.size(), e.getMessage());
      return Collections.emptyMap();
    }
  }

  /**
   * 根据用户 ID + 名称类型获取展示名称。
   *
   * <p>TODO: 待用户中心暴露多形态名称端点（SHORT / FULL / ENGLISH）后按类型分派；
   * 当前统一走 USER 维度解析。
   *
   * @param userId 用户 ID
   * @param nameType 名称类型（SHORT / FULL / ENGLISH 等）
   * @return 名称，未找到返回 null
   */
  @Override
  public String getUserNameByType(String userId, String nameType) {
    return getUserName(userId);
  }
}
