package com.njydsz.nextwiki.server.service.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.nextwiki.domain.service.QuotaDomainService;

/**
 * 配额校验步骤：校验用户是否有足够存储空间。
 *
 * <p>校验失败时抛 {@code QUOTA_INSUFFICIENT} 或 {@code QUOTA_FILE_LIMIT} 业务异常，管道终止。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaCheckStep implements UploadStep {

  private final QuotaDomainService quotaDomainService;

  @Override
  public boolean execute(UploadContext context) {
    quotaDomainService.checkQuota("user", context.getUserId(), context.getFileSize());
    log.debug("[QuotaCheckStep] 配额校验通过: userId={}, size={}", context.getUserId(), context.getFileSize());
    return true;
  }

  @Override
  public String getName() {
    return "quota_check";
  }
}
