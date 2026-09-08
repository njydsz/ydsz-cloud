package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.vo.DashboardOverviewItemVO;
import com.njydsz.system.domain.vo.DashboardWorkspaceVO;

/**
 * 工作台聚合 Service 接口
 *
 * <p>为主框架工作台（analytics/workspace 页面）提供可真实计算的聚合数据：
 * 概览统计项（租户/字典/变量/应用规模）与时段化问候语。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public interface DashboardService {

  /**
   * 查询概览统计项。
   *
   * <p>统计 system 域内可真实计数的数据规模：租户、字典类型、系统变量、注册应用。
   * 「今日新增」仅对携带创建时间的维度（租户）计算，其余维度恒为 0（不做估算）。
   *
   * @return 概览统计项列表
   */
  List<DashboardOverviewItemVO> overview();

  /**
   * 查询工作台聚合数据（当前版本：时段化问候语）。
   *
   * @return 工作台聚合 VO
   */
  DashboardWorkspaceVO workspace();
}
