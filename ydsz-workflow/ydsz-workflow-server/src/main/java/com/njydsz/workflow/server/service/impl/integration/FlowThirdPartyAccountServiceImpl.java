package com.njydsz.workflow.server.service.impl.integration;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.workflow.domain.entity.FlowThirdPartyAccount;
import com.njydsz.workflow.infra.mapper.FlowThirdPartyAccountMapper;
import com.njydsz.workflow.server.service.FlowThirdPartyAccountService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 第三方审批账号服务实现。
 *
 * <p>管理钉钉/飞书/企业微信审批账号的绑定关系 ({@code ydsz_flow_thirdparty_account})：
 *
 * <p>用户在 IM 端发起审批后，通过此服务映射到本系统用户。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowThirdPartyAccountServiceImpl implements FlowThirdPartyAccountService {

  /** 三方账号 Mapper，管理 ydsz_flow_third_party_account 表 */
  private final FlowThirdPartyAccountMapper thirdPartyAccountMapper;

  // ============================== 查询 ==============================

  /**
   * 按「本系统用户 + IM 平台」查询三方账号绑定关系。
   *
   * <p><b>缓存：</b>{@link CacheConstants#FLOW_THIRDPARTY_BY_USER_CACHE}， 键为 {@code
   * userId:platform}。{@code unless = "#result == null"} 使空结果不入缓存——
   * 未绑定用户属常态，缓存空值会占用大量条目；代价是未绑定用户每次都会穿透到 DB， 因该场景 QPS 极低，取舍上可接受。
   *
   * <p><b>事务：</b>只读事务，仅用于走从库/只读优化，无写副作用。
   *
   * <p><b>异常处理：</b>DB 异常被<b>捕获并转为返回 {@code null}</b>，不向上抛出。 设计意图是三方账号缺失只应导致「无法推送到 IM」，而不能让审批主流程失败。
   * 因此调用方<b>无法区分</b>「未绑定」与「查询故障」，需要强一致判断的场景不可依赖本方法。
   *
   * @param userId 本系统用户 ID，为 {@code null} 时直接返回 {@code null}
   * @param platform IM 平台标识（如 {@code dingtalk}、{@code feishu}、{@code wecom}），空白时返回 {@code null}
   * @return 绑定记录；未绑定、参数非法或查询异常时均返回 {@code null}
   */
  @Override
  @Transactional(readOnly = true)
  @Cacheable(
      value = CacheConstants.FLOW_THIRDPARTY_BY_USER_CACHE,
      key = "#userId + ':' + #platform",
      unless = "#result == null")
  public FlowThirdPartyAccount getByUserIdAndPlatform(String userId, String platform) {
    try {
      if (userId == null || !StringUtils.hasText(platform)) {
        return null;
      }
      return thirdPartyAccountMapper.selectByUserIdAndPlatform(userId, platform);
    } catch (Exception e) {
      log.error(
          "[ThirdPartyAccount] 按用户查询异常: userId={} platform={} err={}",
          userId,
          platform,
          e.getMessage(),
          e);
      return null;
    }
  }

  /**
   * 按 IM 平台的 openId 反查本系统用户绑定关系。
   *
   * <p>与 {@link #getByUserIdAndPlatform} 方向相反，用于<b>IM 侧回调入口</b>： 用户在钉钉/飞书卡片上点「同意」时，回调只带
   * openId，需借此还原成本系统用户身份 才能校验其审批权限。因此本方法是权限链路的起点，返回 {@code null} 时调用方 <b>必须拒绝该次操作</b>，不得回退为匿名或默认用户。
   *
   * <p><b>缓存：</b>{@link CacheConstants#FLOW_THIRDPARTY_BY_OPENID_CACHE}， 键为 {@code
   * platform:openId}，同样不缓存空值。
   *
   * <p><b>异常处理：</b>查询异常被吞掉并返回 {@code null}，仅记录 error 日志。
   *
   * @param platform IM 平台标识，空白时返回 {@code null}
   * @param openId 平台侧用户唯一标识，空白时返回 {@code null}
   * @return 绑定记录；未绑定、参数非法或查询异常时均返回 {@code null}
   */
  @Override
  @Transactional(readOnly = true)
  @Cacheable(
      value = CacheConstants.FLOW_THIRDPARTY_BY_OPENID_CACHE,
      key = "#platform + ':' + #openId",
      unless = "#result == null")
  public FlowThirdPartyAccount getByOpenId(String platform, String openId) {
    try {
      if (!StringUtils.hasText(platform) || !StringUtils.hasText(openId)) {
        return null;
      }
      return thirdPartyAccountMapper.selectByOpenId(platform, openId);
    } catch (Exception e) {
      log.error(
          "[ThirdPartyAccount] 按 openId 查询异常: platform={} openId={} err={}",
          platform,
          openId,
          e.getMessage(),
          e);
      return null;
    }
  }

  @Override
  public FlowThirdPartyAccount getActiveByPlatform(String platform) {
    try {
      if (!StringUtils.hasText(platform)) {
        return null;
      }
      return thirdPartyAccountMapper.selectActiveByPlatform(platform);
    } catch (Exception e) {
      log.error("[ThirdPartyAccount] 按平台查询激活账号异常: platform={} err={}", platform, e.getMessage(), e);
      return null;
    }
  }

  // ============================== 保存 / 更新 ==============================

  /**
   * 新增或更新三方账号绑定记录（upsert）。
   *
   * <p><b>幂等判定：</b>入参无 {@code id} 时，先按 {@code userId + platform} 反查已有记录， 命中则回填 {@code id}
   * 转为更新。这保证同一用户在同一平台<b>只会有一条绑定</b>， 重复调用不会产生脏数据。新建时默认 {@code status=ACTIVE}。
   *
   * <p><b>缓存：</b>成功后清空两个三方账号缓存的<b>全部</b>条目。之所以用 {@code allEntries = true} 而非精确删除，是因为一次写入会同时影响
   * userId 和 openId 两个维度的键，精确失效易遗漏；该表写入频率极低，全清代价可接受。
   *
   * <p><b>事务边界与重要缺陷提示：</b>方法虽标注 {@code @Transactional(rollbackFor = Exception.class)}，但内部 {@code
   * try-catch} 吞掉了所有异常，异常不会传播到事务拦截器，因此<b>回滚实际不会触发</b>， 且调用方（返回 {@code
   * void}）也<b>无从得知失败</b>。业务上属「绑定失败可容忍、 不阻断主流程」的取舍，但需要确认绑定结果的场景必须回查 {@link #getByUserIdAndPlatform}
   * 校验，不能假定本方法一定成功。
   *
   * @param account 待保存的绑定记录；为 {@code null} 时仅记录 warn 后静默返回
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(
      value = {
        CacheConstants.FLOW_THIRDPARTY_BY_OPENID_CACHE,
        CacheConstants.FLOW_THIRDPARTY_BY_USER_CACHE
      },
      allEntries = true)
  public void saveOrUpdate(FlowThirdPartyAccount account) {
    try {
      if (account == null) {
        log.warn("[ThirdPartyAccount] saveOrUpdate 参数为空");
        return;
      }
      LocalDateTime now = LocalDateTime.now();
      // 无 id 时按 userId+platform 命中已有记录转为更新
      if (account.getId() == null
          && account.getUserId() != null
          && StringUtils.hasText(account.getPlatform())) {
        FlowThirdPartyAccount existing =
            thirdPartyAccountMapper.selectByUserIdAndPlatform(
                account.getUserId(), account.getPlatform());
        if (existing != null) {
          account.setId(existing.getId());
        }
      }
      if (account.getId() == null) {
        if (account.getStatus() == null) {
          account.setStatus("ACTIVE");
        }
        if (account.getCreatedAt() == null) {
          account.setCreatedAt(now);
        }
        account.setUpdatedAt(now);
        thirdPartyAccountMapper.insert(account);
      } else {
        account.setUpdatedAt(now);
        thirdPartyAccountMapper.updateById(account);
      }
    } catch (Exception e) {
      log.error(
          "[ThirdPartyAccount] saveOrUpdate 异常: userId={} platform={} err={}",
          account != null ? account.getUserId() : null,
          account != null ? account.getPlatform() : null,
          e.getMessage(),
          e);
    }
  }

  // ============================== 绑定账号 ==============================

  /**
   * 绑定本系统用户与 IM 平台账号。
   *
   * <p>相对 {@link #saveOrUpdate} 的差异：本方法面向<b>扫码/授权绑定</b>场景， 只接收标识字段，由内部组装实体。已存在绑定时<b>覆盖</b> {@code
   * openId} 与 {@code unionId}，用于支持用户更换 IM 账号后重新绑定；不存在则新建并置为 {@code ACTIVE}。因此对同一 {@code (userId,
   * platform)} 重复调用是幂等的。
   *
   * <p><b>缓存：</b>成功后全量清空两个三方账号缓存，理由同 {@link #saveOrUpdate}。
   *
   * <p><b>事务边界与失败可见性：</b>同 {@link #saveOrUpdate} —— 内部吞异常导致 {@code rollbackFor} 不生效，且返回 {@code
   * void} 使调用方无法感知失败。 绑定是否真正生效需回查 {@link #getByOpenId} 确认。
   *
   * <p><b>安全提示：</b>本方法不校验 openId 的归属真实性，调用方<b>必须</b>先完成 IM 侧的授权码换取流程，确认 openId 确属当前登录用户后再调用，
   * 否则存在把他人 IM 账号绑定到本账号、进而冒名审批的风险。
   *
   * @param userId 本系统用户 ID，为 {@code null} 时静默跳过
   * @param platform IM 平台标识，空白时静默跳过
   * @param openId 平台侧用户唯一标识，空白时静默跳过
   * @param unionId 平台侧跨应用唯一标识，可为 {@code null}（部分平台不提供）
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(
      value = {
        CacheConstants.FLOW_THIRDPARTY_BY_OPENID_CACHE,
        CacheConstants.FLOW_THIRDPARTY_BY_USER_CACHE
      },
      allEntries = true)
  public void bindAccount(String userId, String platform, String openId, String unionId) {
    try {
      if (userId == null || !StringUtils.hasText(platform) || !StringUtils.hasText(openId)) {
        log.warn(
            "[ThirdPartyAccount] 绑定参数为空: userId={} platform={} openId={}",
            userId,
            platform,
            openId);
        return;
      }
      FlowThirdPartyAccount account =
          thirdPartyAccountMapper.selectByUserIdAndPlatform(userId, platform);
      if (account == null) {
        account = new FlowThirdPartyAccount();
        account.setUserId(userId);
        account.setPlatform(platform);
        account.setStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
      } else {
      }
      account.setOpenId(openId);
      account.setUnionId(unionId);
      if (account.getId() == null) {
        thirdPartyAccountMapper.insert(account);
      } else {
        thirdPartyAccountMapper.updateById(account);
      }
      log.info(
          "[ThirdPartyAccount] 绑定成功: userId={} platform={} openId={}", userId, platform, openId);
    } catch (Exception e) {
      log.error(
          "[ThirdPartyAccount] 绑定异常: userId={} platform={} err={}",
          userId,
          platform,
          e.getMessage(),
          e);
    }
  }
}
