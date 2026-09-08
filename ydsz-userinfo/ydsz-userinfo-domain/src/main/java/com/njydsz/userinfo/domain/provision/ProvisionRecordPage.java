package com.njydsz.userinfo.domain.provision;

import java.io.Serializable;
import java.util.List;

/**
 * 供给记录分页结果（P0-1 Identity Provisioning 管道）。
 *
 * <p>封装从外部身份源拉取的一页用户记录，携带增量同步状态令牌。
 * {@code nextSyncToken} 由连接器实现方维护（如 LDAP 的 {@code usnChanged}、
 * JDBC 的 {@code max(modified_at)} 等），供下次增量拉取使用。
 *
 * @author ydsz-team
 * @since 26.09.08
 * @param records 当前页用户记录列表（不可为 null）
 * @param nextSyncToken 下次增量拉取的令牌；为 null 表示已到最后页或不支持分页
 * @param totalCount 总记录数（-1 表示未知）
 */
public record ProvisionRecordPage(
    List<ProvisionRecord> records,
    String nextSyncToken,
    long totalCount)
    implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 构造供给记录分页（无分页场景）。
   *
   * @param records 全部记录
   */
  public ProvisionRecordPage(List<ProvisionRecord> records) {
    this(records, null, records.size());
  }

  /**
   * 判断是否还有更多页。
   *
   * @return true 表示 nextSyncToken 存在且非空
   */
  public boolean hasNext() {
    return nextSyncToken != null && !nextSyncToken.isBlank();
  }
}
