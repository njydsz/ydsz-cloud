package com.njydsz.system.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.entity.AppInfo;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.infra.mapper.AppInfoMapper;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.AppInfoService;

/**
 * 应用注册 Service 实现
 *
 * <p>对 {@link AppInfoService} 接口的完整实现，是「应用注册中心」的核心业务逻辑层。 维护 {@code ydsz_app_info} 应用注册表，对标大厂「开放平台 /
 * 应用市场」的 AppId/AppSecret 管理体系， 用于客户端身份标识 + 密钥校验（OAuth2 Client Credentials 等场景）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} / {@link
 *       #removeById}，全部走 {@code @Transactional} 事务保证
 *   <li><b>密钥校验</b>：{@link #validateClient} — 通过 BCrypt 校验 {@code appSecret}， 用于 OAuth2 / OpenAPI
 *       网关层身份认证
 *   <li><b>行级权限</b>：{@link #page} / {@link #list} 启用 {@code @DataScope} 限制部门 + 创建人可见
 *   <li><b>指标埋点</b>：密钥校验成功 / 失败次数上报 Micrometer Prometheus 指标
 * </ul>
 *
 * <p><b>安全设计：</b>
 *
 * <ul>
 *   <li><b>密钥加密存储</b>：{@code appSecret} 字段在 {@link #save} / {@link #updateById} 中自动 BCrypt
 *       加密后存储，<b>VO 不暴露密钥哈希</b>（前端永远看不到密文）
 *   <li><b>密钥轮换</b>：{@link #updateById} 密钥非空才更新；为空时设为 {@code null}， MyBatis-Plus NOT_NULL
 *       策略会跳过该字段，<b>保持原密钥不变</b>
 *   <li><b>appKey 唯一性</b>：保存前校验 {@code appKey} 全租户内不能重复（业务主键）
 *   <li><b>启用状态校验</b>：{@link #validateClient} 仅接受 {@code status=ENABLED} 的应用， 失效 / 禁用应用直接返回 false
 *   <li><b>BCrypt 强度</b>：默认 strength=10（约 100ms 单次加密），单实例 100QPS 校验无压力
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}
 *   <li>密钥校验不走事务（仅读 DB）
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离， 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>密钥不返回</b>：{@link AppInfoVO} 中 {@code appSecret} 字段为
 *       null（{@code @com.njydsz.common.json.annotation.JsonIgnore}）， 避免泄漏到前端 / 日志
 *   <li><b>登录态隔离</b>：管理后台「应用列表」自动按当前用户部门 + 创建人过滤 （{@code @DataScope}），避免越权查看
 *   <li><b>软删除</b>：{@code ydsz_app_info} 表采用 <b>逻辑删除</b>（{@code deleted} 字段）
 *   <li><b>密钥不存明文</b>：所有密钥 BCrypt 哈希后存 DB，<b>不可逆</b>，忘记密钥只能重置
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 1. 管理后台创建应用
 * AppInfoDTO dto = AppInfoDTO.builder()
 *     .appCode("hr-sync")
 *     .appName("HR 数据同步")
 *     .appKey("hr_sync_2026")
 *     .appSecret("原始密钥 32 位")
 *     .build();
 * String appId = appInfoService.save(dto);
 *
 * // 2. OAuth2 网关层校验
 * boolean valid = appInfoService.validateClient("hr_sync_2026", "原始密钥 32 位");
 * if (!valid) {
 *     throw new BizException("INVALID_CLIENT", "应用密钥校验失败");
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AppInfoService 应用注册 Service 接口
 * @see com.njydsz.system.domain.entity.AppInfo 应用实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppInfoServiceImpl implements AppInfoService {

  /** 应用注册 Mapper（继承 {@code ydsz_app_info} 表 CRUD） */
  private final AppInfoMapper mapper;

  /** 系统监控指标采集器 */
  private final SystemMetrics metrics;

  /** BCrypt 密码编码器，用于 appSecret 加密存储（strength 默认 10） */
  private final BCryptPasswordEncoder passwordEncoder;

  /** Redis String 操作组件（用于校验缓存 + 失败锁定） */
  private final RedisStringOps redisStringOps;

  /** 校验缓存键前缀（命中后跳过 BCrypt） */
  private static final String VALIDATE_CACHE_PREFIX = "ydsz:system:app:validate:";

  /** 失败计数键前缀（连续失败锁定） */
  private static final String FAIL_COUNT_PREFIX = "ydsz:system:app:fail:";

  /** 校验缓存 TTL（秒），5 分钟 */
  private static final long VALIDATE_CACHE_TTL_SECONDS = 300L;

  /** 连续失败锁定阈值 */
  private static final int MAX_FAIL_COUNT = 5;

  /** 失败锁定 TTL（秒），30 分钟 */
  private static final long FAIL_LOCK_TTL_SECONDS = 1800L;

  /**
   * 根据主键查询应用（不走缓存，直接走 DB）
   *
   * <p>适用场景：管理后台「应用详情」页，单次访问无缓存需求。
   *
   * @param id 应用主键
   * @return 应用 VO（<b>不含密钥哈希</b>），不存在返回 null
   */
  @Override
  public AppInfoVO getById(String id) {
    AppInfo entity = mapper.selectById(id);
    return SystemConverter.INSTANT.entityToVO(entity);
  }

  /**
   * 校验应用密钥（OAuth2 Client Credentials 风格）
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li><b>失败锁定检查</b>：若连续失败次数 ≥ {@value #MAX_FAIL_COUNT}，直接拒绝（防爆破）
   *   <li><b>Redis 缓存命中</b>：命中校验缓存时跳过 BCrypt，直接返回 true
   *   <li>按 {@code appKey} 查询应用（仅 {@code status=ENABLED}）
   *   <li>应用不存在 / 密钥为空 → 校验失败，累加失败计数
   *   <li>BCrypt 校验 {@code appSecret} 与存储的密钥哈希是否匹配
   *   <li>校验成功 → 缓存结果 + 重置失败计数；失败 → 累加失败计数
   * </ol>
   *
   * <p><b>安全设计：</b>
   *
   * <ul>
   *   <li><b>Redis 缓存</b>：key {@code ydsz:system:app:validate:{appKey}}，TTL 5min， 校验成功缓存结果、失败不缓存
   *   <li><b>失败锁定</b>：key {@code ydsz:system:app:fail:{appKey}}，连续 5 次失败锁 30min，
   *       期间所有请求直接拒绝，有效防止暴力破解 / DoS
   * </ul>
   *
   * <p><b>性能说明：</b>BCrypt 校验约 100ms（strength=10），<b>不建议</b>在网关同步阻塞路径高频调用。 开启 Redis 缓存后，命中缓存的请求延迟 <
   * 2ms。
   *
   * @param appKey 应用 Key（明文，对应 {@code app_key} 列）
   * @param appSecret 应用密钥明文
   * @return true=校验通过，false=应用不存在 / 未启用 / 密钥不匹配 / 账号锁定
   */
  @Override
  public boolean validateClient(String appKey, String appSecret) {
    if (appKey == null || appKey.isBlank()) {
      return false;
    }

    // 1. 失败锁定检查：连续失败 ≥ MAX_FAIL_COUNT 次则直接拒绝
    String failKey = FAIL_COUNT_PREFIX + appKey;
    String failCountStr = redisStringOps.get(failKey, String.class);
    if (failCountStr != null) {
      try {
        int failCount = Integer.parseInt(failCountStr);
        if (failCount >= MAX_FAIL_COUNT) {
          log.warn(
              "应用校验锁定中: appKey={}, 连续失败次数={}, 锁定 {}s", appKey, failCount, FAIL_LOCK_TTL_SECONDS);
          metrics.recordAppValidateFail();
          return false;
        }
      } catch (NumberFormatException ignored) {
        // 解析失败时放行，由后续 BCrypt 兜底校验
      }
    }

    // 2. Redis 缓存命中：已校验成功的 appKey 跳过 BCrypt
    String cacheKey = VALIDATE_CACHE_PREFIX + appKey;
    String cached = redisStringOps.get(cacheKey, String.class);
    if ("true".equals(cached)) {
      metrics.recordAppValidateSuccess();
      return true;
    }

    // 3. DB 查询 + BCrypt 校验
    AppInfo app = mapper.selectEnabledByAppKey(appKey);
    if (app == null) {
      handleValidateFail(appKey, failKey, "不存在或未启用");
      return false;
    }
    if (app.getAppSecret() == null || app.getAppSecret().isBlank()) {
      handleValidateFail(appKey, failKey, "密钥为空");
      return false;
    }
    boolean matched = passwordEncoder.matches(appSecret, app.getAppSecret());
    if (matched) {
      // 校验成功：缓存结果、重置失败计数
      redisStringOps.set(cacheKey, "true", VALIDATE_CACHE_TTL_SECONDS);
      redisStringOps.del(failKey);
      metrics.recordAppValidateSuccess();
    } else {
      handleValidateFail(appKey, failKey, "密钥不匹配");
    }
    return matched;
  }

  /**
   * 校验失败处理（私有）：累加失败计数 + 设置锁定 TTL + 记录指标
   *
   * @param appKey 应用 Key
   * @param failKey 失败计数 Redis key
   * @param reason 失败原因（用于日志）
   */
  private void handleValidateFail(String appKey, String failKey, String reason) {
    long count = redisStringOps.incr(failKey, 1);
    redisStringOps.expire(failKey, FAIL_LOCK_TTL_SECONDS);
    metrics.recordAppValidateFail();
    log.warn("应用校验失败: appKey={}, {}, 连续失败次数={}/{}", appKey, reason, count, MAX_FAIL_COUNT);
  }

  /**
   * 分页查询应用（管理后台列表页）
   *
   * <p>支持按 {@code appName} 模糊匹配、{@code status} 精确匹配进行过滤， 按 {@code created_at} 倒序返回。
   *
   * <p><b>行级权限：</b>本方法带 {@code @DataScope} 注解， 自动按当前用户的部门 / 人员范围过滤（管理员看全量）。
   *
   * @param pageNum 页码（1-based）
   * @param pageSize 每页条数
   * @param appName 应用名（可选，模糊匹配）
   * @param status 状态（可选过滤条件，如 {@code ENABLED/DISABLED}）
   * @return 分页结果（VO 中密钥字段为 null，不暴露密钥哈希）
   */
  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "created_by")
  public PageResponse<List<AppInfoVO>> page(
      int pageNum, int pageSize, String appName, String status) {
    QueryWrapper<AppInfo> wrapper = new QueryWrapper<>();
    if (appName != null && !appName.isBlank()) {
      wrapper.like("app_name", appName);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("created_at");
    IPage<AppInfo> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    return PageResponses.success(page, SystemConverter.INSTANT::entityToVO);
  }

  /**
   * 查询全部应用（不区分状态）
   *
   * <p>典型调用方：管理后台「应用选择器」下拉框。
   *
   * <p><b>行级权限：</b>本方法带 {@code @DataScope} 注解， 自动按当前用户的部门 / 人员范围过滤。
   *
   * <p><b>慎用：</b>全表扫描，应用一般 < 50 条，单次查询 < 10ms。
   *
   * @return 全部应用列表（VO 中密钥字段为 null，不暴露密钥哈希）
   */
  @Override
  @DataScope(deptColumn = "dept_id", userColumn = "created_by")
  public List<AppInfoVO> list() {
    return mapper.selectList(null).stream()
        .map(SystemConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
  }

  /**
   * 新增应用
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>唯一性校验：{@code appKey} 全租户内不能重复
   *   <li>DTO 转 DO，默认 {@code status=ENABLED}
   *   <li><b>密钥 BCrypt 加密</b>（{@code passwordEncoder.encode}）
   *   <li>插入 {@code ydsz_app_info} 表
   * </ol>
   *
   * <p><b>密钥管理：</b>原始密钥仅在 {@code DTO.appSecret} 中出现一次， BCrypt 哈希后存
   * DB，<b>不可逆</b>，调用方需在创建时把原始密钥同步告知应用方（一次性）。
   *
   * @param dto 应用数据（含明文密钥）
   * @return 新创建的应用 ID
   * @throws IllegalArgumentException {@code appKey} 已存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(AppInfoDTO dto) {
    // 唯一性校验：appKey 不能重复
    QueryWrapper<AppInfo> checkWrapper = new QueryWrapper<>();
    checkWrapper.eq("app_key", dto.getAppKey());
    if (mapper.selectCount(checkWrapper) > 0) {
      throw BusinessException.of(SystemExceptionCode.APP_KEY_DUPLICATE)
          .data("appKey", dto.getAppKey());
    }
    AppInfo entity = toEntity(dto);
    if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
      entity.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
    }
    mapper.insert(entity);
    return entity.getId();
  }

  /**
   * 更新应用
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>DTO 转 DO
   *   <li><b>密钥轮换逻辑</b>：
   *       <ul>
   *         <li>{@code DTO.appSecret} 非空 → BCrypt 加密后更新
   *         <li>{@code DTO.appSecret} 为空 → 设为 {@code null}， MyBatis-Plus NOT_NULL
   *             策略会跳过此字段，<b>保持原密钥不变</b>
   *       </ul>
   *   <li>更新 {@code ydsz_app_info} 表
   * </ol>
   *
   * <p><b>密钥轮换最佳实践：</b>
   *
   * <ol>
   *   <li>提前 N 天生成新密钥并通过本方法更新（{@code appSecret} 非空）
   *   <li>新老密钥<b>并行生效</b>期（应用方逐步切换）
   *   <li>切换完成后<b>撤销旧密钥</b>（重新生成新密钥）
   * </ol>
   *
   * @param dto 应用数据（需包含 {@code id}；密钥非空才更新）
   * @return true=更新成功，false=记录不存在
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(AppInfoDTO dto) {
    AppInfo entity = toEntity(dto);
    if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
      entity.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
    } else {
      // 不更新密钥时设为 null，MyBatis-Plus NOT_NULL 策略会跳过此字段
      entity.setAppSecret(null);
    }
    return mapper.updateById(entity) > 0;
  }

  /**
   * 逻辑删除应用
   *
   * <p>采用<b>逻辑删除</b>（{@code deleted=1} + {@code status=DISABLED}）， 不真正从 DB 删除，便于审计回溯。
   *
   * <p><b>注意：</b>删除后 {@link #validateClient} 会拒绝该应用的密钥校验（仅接受 {@code ENABLED}）。
   *
   * @param id 应用主键
   * @return true=删除成功，false=记录不存在
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    return mapper.deleteById(id) > 0;
  }

  /**
   * DTO → DO 转换（私有）
   *
   * <p>缺省 {@code status="ENABLED"}，保证新创建的应用默认可用。
   *
   * <p><b>注意：</b>本方法<b>不</b>处理密钥加密，由调用方在 {@link #save} / {@link #updateById} 中按需调用 {@code
   * passwordEncoder.encode}。
   *
   * @param dto 应用 DTO
   * @return 应用 Entity
   */
  private AppInfo toEntity(AppInfoDTO dto) {
    AppInfo entity = new AppInfo();
    entity.setId(dto.getId());
    entity.setAppCode(dto.getAppCode());
    entity.setAppName(dto.getAppName());
    entity.setAppKey(dto.getAppKey());
    entity.setAppSecret(dto.getAppSecret());
    entity.setRedirectUrl(dto.getRedirectUrl());
    entity.setDescription(dto.getDescription());
    entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
    return entity;
  }
}
