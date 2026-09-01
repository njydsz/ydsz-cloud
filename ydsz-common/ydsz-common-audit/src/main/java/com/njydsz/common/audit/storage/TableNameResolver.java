package com.njydsz.common.audit.storage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 审计日志分表名解析器
 *
 * <p>根据配置的分表类型（monthly/daily/yearly）和时间动态计算目标表名。 替代原有的 {@code TableShardingStrategy}
 * 策略接口，通过配置驱动而非多态实现。
 *
 * <p>分表命名规则：
 *
 * <ul>
 *   <li>monthly：{@code baseTableName_yyyyMM}，如 {@code sys_audit_log_202601}
 *   <li>daily：{@code baseTableName_yyyyMMdd}，如 {@code sys_audit_log_20260101}
 *   <li>yearly：{@code baseTableName_yyyy}，如 {@code sys_audit_log_2026}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TableNameResolver {

  /** 按月格式化器 */
  private static final DateTimeFormatter MONTHLY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

  /** 按天格式化器 */
  private static final DateTimeFormatter DAILY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

  /** 按年格式化器 */
  private static final DateTimeFormatter YEARLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy");

  /** 分表类型 */
  private final ShardType shardType;

  /** 基础表名 */
  private final String baseTableName;

  /**
   * 构造表名解析器
   *
   * @param shardType 分表类型（monthly/daily/yearly），为 null 或空表示不分表
   * @param baseTableName 基础表名
   */
  public TableNameResolver(String shardType, String baseTableName) {
    this.shardType = ShardType.fromName(shardType);
    this.baseTableName = baseTableName;
  }

  /**
   * 判断是否启用分表
   *
   * @return 启用分表返回 true
   */
  public boolean isShardingEnabled() {
    return shardType != null;
  }

  /**
   * 根据时间计算分表名
   *
   * @param time 时间，为 null 时使用当前时间
   * @return 分表后的完整表名；未启用分表时返回基础表名
   */
  public String resolve(LocalDateTime time) {
    if (shardType == null) {
      return baseTableName;
    }
    LocalDateTime actualTime = time != null ? time : LocalDateTime.now();
    return switch (shardType) {
      case MONTHLY -> baseTableName + "_" + actualTime.format(MONTHLY_FORMATTER);
      case DAILY -> baseTableName + "_" + actualTime.format(DAILY_FORMATTER);
      case YEARLY -> baseTableName + "_" + actualTime.format(YEARLY_FORMATTER);
    };
  }

  /**
   * 根据时间范围计算涉及的所有分表名
   *
   * @param startTime 开始时间
   * @param endTime 结束时间
   * @return 时间范围内涉及的分表名集合（去重、正序）；未启用分表时仅返回基础表名
   */
  public Set<String> resolveInRange(LocalDateTime startTime, LocalDateTime endTime) {
    Set<String> tables = new LinkedHashSet<>();
    if (shardType == null) {
      tables.add(baseTableName);
      return tables;
    }
    if (startTime == null || endTime == null) {
      tables.add(resolve(null));
      return tables;
    }
    LocalDateTime current =
        switch (shardType) {
          case MONTHLY -> startTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
          case DAILY -> startTime.withHour(0).withMinute(0).withSecond(0);
          case YEARLY ->
              startTime.withMonth(1).withDayOfYear(1).withHour(0).withMinute(0).withSecond(0);
        };
    while (!current.isAfter(endTime)) {
      tables.add(resolve(current));
      current =
          switch (shardType) {
            case MONTHLY -> current.plusMonths(1).withDayOfMonth(1);
            case DAILY -> current.plusDays(1);
            case YEARLY -> current.plusYears(1).withDayOfYear(1);
          };
    }
    return tables;
  }

  /** 分表类型枚举 */
  public enum ShardType {
    /** 按月分表 */
    MONTHLY,
    /** 按天分表 */
    DAILY,
    /** 按年分表 */
    YEARLY;

    /**
     * 根据名称解析分表类型
     *
     * @param name 类型名称（不区分大小写），为 null 或空返回 null
     * @return 分表类型枚举，未匹配返回 null
     */
    public static ShardType fromName(String name) {
      if (name == null || name.isEmpty()) {
        return null;
      }
      for (ShardType type : values()) {
        if (type.name().equalsIgnoreCase(name)) {
          return type;
        }
      }
      return null;
    }
  }
}
