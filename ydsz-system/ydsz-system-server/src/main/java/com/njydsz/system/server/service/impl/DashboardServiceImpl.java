package com.njydsz.system.server.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.util.SecurityUtils;
import com.njydsz.system.domain.vo.DashboardOverviewItemVO;
import com.njydsz.system.domain.vo.DashboardWorkspaceVO;
import com.njydsz.system.domain.vo.TenantVO;
import com.njydsz.system.server.service.AppInfoService;
import com.njydsz.system.server.service.DashboardService;
import com.njydsz.system.server.service.DictService;
import com.njydsz.system.server.service.TenantService;
import com.njydsz.system.server.service.VariableService;

/**
 * 工作台聚合 Service 实现
 *
 * <p>聚合 system 域内可真实计数的统计数据（租户/字典类型/系统变量/注册应用），
 * 不做任何估算或造假数据；「今日新增」仅对携带创建时间的维度计算。
 *
 * <p>问候语按时段生成（凌晨/早安/上午好/中午好/下午好/晚上好），拼接当前登录用户名。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @see DashboardService 接口定义
 * @see com.njydsz.system.web.controller.DashboardController 对外端点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

  /** 问候时段分界：凌晨结束（5 点起为早安） */
  private static final int HOUR_DAWN_END = 5;

  /** 问候时段分界：上午结束（12 点起为中午好） */
  private static final int HOUR_MORNING_END = 12;

  /** 问候时段分界：中午结束（14 点起为下午好） */
  private static final int HOUR_NOON_END = 14;

  /** 问候时段分界：下午结束（18 点起为晚上好） */
  private static final int HOUR_AFTERNOON_END = 18;

  /** 问候时段分界：深夜开始（22 点后为夜深了） */
  private static final int HOUR_NIGHT_START = 22;

  /** 租户 Service */
  private final TenantService tenantService;

  /** 字典 Service */
  private final DictService dictService;

  /** 系统变量 Service */
  private final VariableService variableService;

  /** 注册应用 Service */
  private final AppInfoService appInfoService;

  @Override
  public List<DashboardOverviewItemVO> overview() {
    List<TenantVO> tenants = tenantService.listAccessibleTenants();
    List<DashboardOverviewItemVO> items = new ArrayList<>(4);
    items.add(buildItem("租户总数", "累计租户", tenants.size(),
        countCreatedToday(tenants.stream().map(TenantVO::getCreatedAt).toList())));
    items.add(buildItem("字典类型", "累计字典", dictService.listAll().size(), 0L));
    items.add(buildItem("系统变量", "累计变量", variableService.list().size(), 0L));
    items.add(buildItem("注册应用", "累计应用", appInfoService.list().size(), 0L));
    return items;
  }

  @Override
  public DashboardWorkspaceVO workspace() {
    DashboardWorkspaceVO vo = new DashboardWorkspaceVO();
    vo.setGreeting(buildGreeting());
    return vo;
  }

  /**
   * 构建概览统计项。
   *
   * @param title 统计项标题
   * @param totalTitle 累计总值标题
   * @param totalValue 累计总值
   * @param value 今日/增量值
   * @return 概览统计项 VO
   */
  private DashboardOverviewItemVO buildItem(
      String title, String totalTitle, int totalValue, long value) {
    DashboardOverviewItemVO item = new DashboardOverviewItemVO();
    item.setTitle(title);
    item.setTotalTitle(totalTitle);
    item.setTotalValue(totalValue);
    item.setValue(value);
    return item;
  }

  /** 统计创建时间在今天的数量。 */
  private long countCreatedToday(List<LocalDateTime> createdAtList) {
    LocalDate today = LocalDate.now();
    return createdAtList.stream()
        .filter(createdAt -> createdAt != null && today.equals(createdAt.toLocalDate()))
        .count();
  }

  /**
   * 按时段 + 当前登录用户名构建问候文案。
   *
   * <p>时段划分：凌晨（0-4）/ 早安（5-11）/ 中午好（12-13）/ 下午好（14-17）/
   * 晚上好（18-21）/ 夜深了（22-23）。
   *
   * @return 问候文案（如「下午好，admin」）
   */
  private String buildGreeting() {
    int hour = LocalTime.now().getHour();
    String salutation;
    if (hour < HOUR_DAWN_END) {
      salutation = "夜深了";
    } else if (hour < HOUR_MORNING_END) {
      salutation = "早安";
    } else if (hour < HOUR_NOON_END) {
      salutation = "中午好";
    } else if (hour < HOUR_AFTERNOON_END) {
      salutation = "下午好";
    } else if (hour < HOUR_NIGHT_START) {
      salutation = "晚上好";
    } else {
      salutation = "夜深了";
    }
    String username = SecurityUtils.getCurrentUserName();
    return (username == null || username.isBlank())
        ? salutation
        : salutation + "，" + username;
  }
}
