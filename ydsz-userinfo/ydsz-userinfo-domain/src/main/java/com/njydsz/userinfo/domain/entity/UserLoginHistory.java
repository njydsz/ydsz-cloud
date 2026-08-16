package com.njydsz.userinfo.domain.entity;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户登录历史实体
 *
 * <p>对应数据库表 {@code ydsz_user_login_history}，记录每次登录尝试的详细信息， 用于安全审计、异常登录检测、登录追溯。
 *
 * <p><b>设计说明：</b>
 *
 * <ul>
 *   <li>记录所有登录尝试（成功和失败）
 *   <li>字段包含：用户 ID、用户名、登录 IP、登录结果、失败原因、用户代理
 *   <li>定期清理历史数据（建议保留 90 天）
 * </ul>
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>{@code idx_user_id_created_at} — 用户 ID + 创建时间复合索引
 *   <li>{@code idx_ip} — 登录 IP 索引（IP 封禁查询）
 *   <li>{@code idx_created_at} — 创建时间索引（定期清理）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@TableName("ydsz_user_login_history")
public class UserLoginHistory {

  /** 主键 ID（雪花算法） */
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  /** 用户 ID（关联 ydsz_user_account.id） */
  private String userId;

  /** 用户名（冗余存储，即使用户被删除也可追溯） */
  private String username;

  /** 登录 IP 地址 */
  private String loginIp;

  /** 登录结果：SUCCESS / FAILED */
  private String loginResult;

  /** 失败原因（成功时为 null） */
  private String failReason;

  /** 用户代理（浏览器/设备信息） */
  private String userAgent;

  /** 登录时间 */
  private LocalDateTime createdAt;
}
