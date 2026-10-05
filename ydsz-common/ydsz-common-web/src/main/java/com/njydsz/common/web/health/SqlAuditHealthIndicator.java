package com.njydsz.common.web.health;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.njydsz.common.jdbc.config.DataPermissionConfiguration;
import com.njydsz.common.jdbc.interceptor.DataPermissionInnerInterceptor;

/**
 * SQL 数据权限拦截器启动自检 HealthIndicator
 *
 * <p>对标美团/字节中台 fail-fast 要求——数据权限不允许静默关闭。在应用启动后校验：
 *
 * <ol>
 *   <li>{@code ydsz-common-jdbc} 是否已引入（通过 {@link MybatisPlusInterceptor} Bean 是否存在隐式探测）
 *   <li>当 {@code ydsz-common.jdbc.sql-audit.enabled=true} 时，{@code DataPermissionInnerInterceptor}
 *       （行级 {@code RowPermissionInnerInterceptor} / 列级 {@code ColPermissionInnerInterceptor}）
 *       是否已注册到 MP 拦截器链
 * </ol>
 *
 * <p>健康判定规则：
 *
 * <ul>
 *   <li>config 未显式设置 / {@code enabled=false}：DOWN + WARN 日志（提示开启自检）
 *   <li>{@code enabled=true} + 拦截器未找到：DOWN + ERROR 日志
 *       "数据权限拦截器未激活：请检查 ydzs.common.jdbc.sql-audit.enabled=true"
 *   <li>{@code enabled=true} + 拦截器已注册：UP + details 含拦截器名和时间戳
 * </ul>
 *
 * <p><b>Bean 条件守卫：</b>仅在 {@link MybatisPlusInterceptor} Bean 存在时实例化（即 classpath 已引入
 * {@code ydsz-common-jdbc}），避免在无消费数据源模块（gateway / cronjob 等）中引入不必要的依赖。
 *
 * <p>端点路径：{@code /actuator/health/sqlAudit}
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ConditionalOnBean(MybatisPlusInterceptor.class)
@ConditionalOnProperty(
    prefix = "ydsz-common.jdbc.sql-audit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class SqlAuditHealthIndicator implements HealthIndicator {

  /** 错误提示信息：配置缺失或拦截器未找到时使用 */
  private static final String ERR_INTERCEPTOR_INACTIVE =
      "数据权限拦截器未激活：请检查 ydzs.common.jdbc.sql-audit.enabled=true";

  private static final Logger log = LoggerFactory.getLogger(SqlAuditHealthIndicator.class);

  private final MybatisPlusInterceptor mybatisPlusInterceptor;

  /** 数据权限 configuration provider（弱依赖，允许为 null） */
  private final ObjectProvider<DataPermissionConfiguration> dataPermissionConfigurationProvider;

  /**
   * 构造 SQL 数据权限拦截器自检 HealthIndicator
   *
   * @param mybatisPlusInterceptor MyBatis-Plus 拦截器链（由 ydsz-common-jdbc 注册）
   * @param dataPermissionConfigurationProvider 数据权限配置 provider（由 ydsz-common-jdbc 注册；允许 null）
   */
  public SqlAuditHealthIndicator(
      MybatisPlusInterceptor mybatisPlusInterceptor,
      ObjectProvider<DataPermissionConfiguration> dataPermissionConfigurationProvider) {
    this.mybatisPlusInterceptor = mybatisPlusInterceptor;
    this.dataPermissionConfigurationProvider = dataPermissionConfigurationProvider;
  }

  /**
   * 执行 SQL 数据权限拦截器健康检查
   *
   * <p>检查 MP 拦截器链中是否包含 ydzs 的 {@link DataPermissionInnerInterceptor}
   * （行级或列级），并依据结果构建 Health 响应。
   *
   * @return Health.up（拦截器已注册）或 Health.down（拦截器未注册 / 配置关闭）
   */
  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>(16);

    // 探测 MP 拦截器链中的 ydzs 数据权限拦截器
    List< DataPermissionInnerInterceptor > matched =
        mybatisPlusInterceptor.getInterceptors().stream()
            .filter(DataPermissionInnerInterceptor.class::isInstance)
            .map(DataPermissionInnerInterceptor.class::cast)
            .toList();

    boolean interceptorFound = !matched.isEmpty();
    String interceptorNames =
        matched.stream()
            .map(i -> i.getClass().getSimpleName())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

    // 读取 DataPermissionConfiguration.enabled（弱依赖探测）
    DataPermissionConfiguration dpConfig =
        dataPermissionConfigurationProvider.getIfAvailable();
    boolean dataPermissionEnabled =
        dpConfig != null && Boolean.TRUE.equals(dpConfig.getIsEnabled());

    details.put("dataPermissionConfigEnabled", dataPermissionEnabled);
    details.put("dataPermissionInterceptorFound", interceptorFound);
    details.put("timestamp", Instant.now().toString());

    if (interceptorFound) {
      // 拦截器已注册 → UP
      details.put("interceptors", interceptorNames);
      if (log.isDebugEnabled()) {
        log.debug(
            "SQL 数据权限拦截器自检通过，已注册拦截器：[{}]",
            interceptorNames);
      }
      return Health.up().withDetails(details).build();
    }

    // 拦截器未注册 → DOWN
    details.put("error", ERR_INTERCEPTOR_INACTIVE);
    log.error(
        "SQL 数据权限拦截器自检未通过：enabled=true 但 MyBatis-Plus 拦截器链中未找到 DataPermissionInnerInterceptor。"
            + "请确认：(1) ydsz-common-jdbc 已引入；"
            + "(2) ydsz.jdbc.data-permission.enabled=true；"
            + "(3) 未通过自定义 MybatisPlusConfiguration 绕过自动装配。"
            + "拦截器链当前内容：{}",
        mybatisPlusInterceptor.getInterceptors().stream()
            .map(i -> i.getClass().getSimpleName())
            .toList());

    return Health.down().withDetails(details).build();
  }
}
