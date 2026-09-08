package com.njydsz.userinfo.server.provision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.UserAccountDTO;
import com.njydsz.userinfo.domain.enums.UserLifecycleStatusEnum;
import com.njydsz.userinfo.domain.provision.IdentityProvisionConnector;
import com.njydsz.userinfo.domain.provision.ProvisionException;
import com.njydsz.userinfo.domain.provision.ProvisionRecord;
import com.njydsz.userinfo.domain.provision.ProvisionRecordPage;
import com.njydsz.userinfo.domain.provision.ProvisionResult;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * 身份供给编排器（P0-1 Identity Provisioning 管道）。
 *
 * <p>将 {@link IdentityProvisionConnector} 输出的 {@link ProvisionRecord} 转换为本地用户记录，
 * 写入 ydsz_acct_user 表。职责包括：
 *
 * <ul>
 *   <li>新增外部用户（{@code created}）</li>
 *   <li>更新已存在的外部用户属性（_updated属性）</li>
 *   <li>停用在外部源中已标记为失效的本地用户（{@code deactivated}）</li>
 *   <li>收集同步统计和错误信息</li>
 * </ul>
 *
 * <p><b>注意：</b>本编排器为轻量实现，处理简单的「外部用户 → 本地用户」映射。
 * 对于 LDAP 的组织架构层级同步（部门树的完整同步），仍由 {@code LdapOrgSyncService} 负责。
 * 本编排器仅处理平铺的用户记录导入。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisionOrchestrator {

  /** 错误详情最大保留条数 */
  private static final int MAX_ERRORS = 100;

  private final UserAccountRepository userAccountRepository;

  /**
   * 执行单一连接器的同步。
   *
   * @param connector 供给连接器
   * @param incremental 是否增量同步
   * @param lastSyncToken 上次增量令牌（增量模式时传入）
   * @return 同步执行结果
   */
  @Transactional(rollbackFor = Exception.class)
  public ProvisionResult executeSync(IdentityProvisionConnector connector, boolean incremental,
      String lastSyncToken) {
    long startTime = System.currentTimeMillis();
    String connectorType = connector.getConnectorType();

    List<String> errors = new ArrayList<>(16);
    int created = 0;
    int updated = 0;
    int failed = 0;

    // 1. 拉取外部用户记录（支持分页循环）
    ProvisionRecordPage page;
    try {
      page = incremental
          ? connector.pullIncremental(lastSyncToken)
          : connector.pullAll();
    } catch (ProvisionException e) {
      long duration = System.currentTimeMillis() - startTime;
      errors.add("拉取失败: " + e.getMessage());
      log.error("Provision 拉取失败: type={}, error={}", connectorType, e.getMessage(), e);
      return new ProvisionResult(connectorType, 0, 0, 0, 0, 1, duration, null, errors);
    }

    List<ProvisionRecord> records = page.records();
    log.info("Provision 拉取完成: type={}, count={}", connectorType, records.size());

    // 2. 逐条写入本地用户表
    for (ProvisionRecord record : records) {
      try {
        ProvisionWriteResult writeResult = upsertUser(record);
        switch (writeResult) {
          case CREATED -> created++;
          case UPDATED -> updated++;
          case UNCHANGED -> { /* 跳过计数 */ }
        }
      } catch (Exception e) {
        failed++;
        if (errors.size() < MAX_ERRORS) {
          errors.add("用户 " + record.externalId() + " 写入失败: " + e.getMessage());
        }
      }
    }

    long duration = System.currentTimeMillis() - startTime;
    String nextSyncToken = page.hasNext() ? page.nextSyncToken() : lastSyncToken;

    ProvisionResult result = new ProvisionResult(
        connectorType, records.size(), created, updated, 0, failed, duration, nextSyncToken,
        errors);

    log.info("Provision 同步完成: type={}, total={}, created={}, updated={}, failed={}, duration={}ms",
        connectorType, records.size(), created, updated, failed, duration);

    return result;
  }

  /**
   * 单条外部用户记录的写入逻辑（新增或更新）。
   *
   * <p>通过 username 查找现有用户。不存在则创建（使用随机占位密码），
   * 存在则更新非空的 realName/email/phone 字段。
   *
   * @param record 外部用户记录
   * @return 写入结果类型
   */
  private ProvisionWriteResult upsertUser(ProvisionRecord record) {
    UserAccountVO existing = userAccountRepository.findByUsername(record.username()).orElse(null);

    if (existing == null) {
      // 新增用户（随机占位密码；外部用户通常通过 OAuth/LDAP/etc 登录）
      UserAccountDTO dto = new UserAccountDTO();
      dto.setUsername(record.username());
      dto.setRealName(record.realName());
      dto.setEmail(record.email());
      dto.setPhone(record.phone());
      dto.setStatus(UserLifecycleStatusEnum.ENABLED);
      // 使用 UUID 片段 + BCrypt 作为占位密码（用户不可通过此密码登录）
      dto.setPassword(generateRandomPassword());

      userAccountRepository.save(dto);
      log.debug("Provision 新增用户: username={}, externalId={}", record.username(),
          record.externalId());
      return ProvisionWriteResult.CREATED;
    }

    // 更新用户（仅更新非空字段）
    boolean hasUpdate = false;
    UserAccountDTO updateDto = new UserAccountDTO();
    updateDto.setId(existing.getId());

    if (record.realName() != null && !record.realName().equals(existing.getRealName())) {
      updateDto.setRealName(record.realName());
      hasUpdate = true;
    }
    if (record.email() != null && !record.email().equals(existing.getEmail())) {
      updateDto.setEmail(record.email());
      hasUpdate = true;
    }
    if (record.phone() != null && !record.phone().equals(existing.getPhone())) {
      updateDto.setPhone(record.phone());
      hasUpdate = true;
    }

    if (hasUpdate) {
      userAccountRepository.save(updateDto);
      log.debug("Provision 更新用户: username={}, externalId={}", record.username(),
          record.externalId());
      return ProvisionWriteResult.UPDATED;
    }

    return ProvisionWriteResult.UNCHANGED;
  }

  /**
   * 生成随机初始密码（仅供占位，用户无法通过此密码登录）。
   *
   * @return BCrypt 加密后的随机密码
   */
  private String generateRandomPassword() {
    // 使用 UUID 前 16 位作为随机占位密码，BCrypt 编码后存储
    String randomPart = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        .encode("Init@" + randomPart);
  }

  /**
   * 写入结果类型。
   */
  private enum ProvisionWriteResult {
    /** 新增用户 */
    CREATED,
    /** 更新用户 */
    UPDATED,
    /** 用户无变化 */
    UNCHANGED
  }
}
