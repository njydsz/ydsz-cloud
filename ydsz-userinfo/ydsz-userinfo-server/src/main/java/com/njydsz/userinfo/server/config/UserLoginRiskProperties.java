package com.njydsz.userinfo.server.config;

import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录风控与 MFA 配置属性（从 {@link UserInfoProperties} 拆分的子配置）。
 *
 * <p>聚焦风险评分权重、异常时段、频率阈值、可信代理等登录风控参数。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.risk}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     risk:
 *       window-seconds: 300
 *       mfa-risk-threshold: 60
 *       ip-weight: 30
 *       time-weight: 20
 *       device-weight: 25
 *       frequency-weight: 25
 *       anomaly-start-hour: 0
 *       anomaly-end-hour: 6
 *       frequency-window-minutes: 5
 *       frequency-threshold: 3
 *       trusted-proxies:
 *         - "10.0.0.0/8"
 *         - "172.16.0.0/12"
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.risk")
public class UserLoginRiskProperties {

  /** 默认windowSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_WINDOW_SECONDS = 300;

  /** 默认mfaRiskThreshold值（可被配置文件覆盖） */
  private static final int DEFAULT_MFA_RISK_THRESHOLD = 60;

  /** 默认ipWeight值（可被配置文件覆盖） */
  private static final int DEFAULT_IP_WEIGHT = 30;

  /** 默认timeWeight值（可被配置文件覆盖） */
  private static final int DEFAULT_TIME_WEIGHT = 20;

  /** 默认deviceWeight值（可被配置文件覆盖） */
  private static final int DEFAULT_DEVICE_WEIGHT = 25;

  /** 默认frequencyWeight值（可被配置文件覆盖） */
  private static final int DEFAULT_FREQUENCY_WEIGHT = 25;

  /** 默认anomalyStartHour值（可被配置文件覆盖） */
  private static final int DEFAULT_ANOMALY_START_HOUR = 0;

  /** 默认anomalyEndHour值（可被配置文件覆盖） */
  private static final int DEFAULT_ANOMALY_END_HOUR = 6;

  /** 默认frequencyWindowMinutes值（可被配置文件覆盖） */
  private static final int DEFAULT_FREQUENCY_WINDOW_MINUTES = 5;

  /** 默认frequencyThreshold值（可被配置文件覆盖） */
  private static final int DEFAULT_FREQUENCY_THRESHOLD = 3;

  /** 风险因子采集窗口（秒），默认 5 分钟。 */
  private long windowSeconds = DEFAULT_WINDOW_SECONDS;

  /** 触发 MFA 的风险评分阈值（含），默认 60。 */
  private int mfaRiskThreshold = DEFAULT_MFA_RISK_THRESHOLD;

  /** IP 风险权重，默认 30。 */
  private int ipWeight = DEFAULT_IP_WEIGHT;

  /** 时间异常权重，默认 20。 */
  private int timeWeight = DEFAULT_TIME_WEIGHT;

  /** 设备异常权重，默认 25。 */
  private int deviceWeight = DEFAULT_DEVICE_WEIGHT;

  /** 频率异常权重，默认 25。 */
  private int frequencyWeight = DEFAULT_FREQUENCY_WEIGHT;

  /** 异常时段起始小时（默认 0）。 */
  private int anomalyStartHour = DEFAULT_ANOMALY_START_HOUR;

  /** 异常时段结束小时（默认 6）。 */
  private int anomalyEndHour = DEFAULT_ANOMALY_END_HOUR;

  /** 频率异常窗口（分钟），默认 5 分钟。 */
  private int frequencyWindowMinutes = DEFAULT_FREQUENCY_WINDOW_MINUTES;

  /** 频率异常阈值（窗口内尝试次数），默认 3 次。 */
  private int frequencyThreshold = DEFAULT_FREQUENCY_THRESHOLD;

  /** 可信代理 IP 列表，为空时不信任任何代理头。 */
  private List<String> trustedProxies = List.of();
}
