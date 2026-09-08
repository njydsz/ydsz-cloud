package com.njydsz.system.domain.vo;

import lombok.Data;

/**
 * 工作台聚合数据 VO
 *
 * <p>供 {@code GET /api/dashboard/workspace} 返回，对齐前端 {@code WorkspaceData} 契约。
 * 当前版本提供后端可真实计算的「时段化问候语」；项目/待办/动态等列表字段由前端
 * 按字段粒度回退本地默认值（前端 useWorkspaceData 已内置该合并逻辑），
 * 待各业务模块沉淀出真实的待办/动态聚合能力后再逐字段接入。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see com.njydsz.system.server.service.DashboardService 数据来源
 */
@Data
public class DashboardWorkspaceVO {

  /** 问候文案（按时段 + 当前用户名生成，如「下午好，admin」） */
  private String greeting;
}
