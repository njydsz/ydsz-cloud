package com.njydsz.message.infra.identity;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.identity.IdGenerator;

/**
 * 基于 MyBatis-Plus IdWorker（Snowflake）的唯一 ID 生成器实现。
 *
 * <p>封装 domain 层 {@link IdGenerator} 接口的具体实现，将第三方库的 ID 生成能力接入 DDD 体系。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class MpIdGenerator implements IdGenerator {

  @Override
  public String nextId() {
    return IdWorker.getIdStr();
  }

  @Override
  public long nextLong() {
    return IdWorker.getId();
  }
}
