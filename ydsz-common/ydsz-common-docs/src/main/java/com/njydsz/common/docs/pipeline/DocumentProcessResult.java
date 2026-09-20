package com.njydsz.common.docs.pipeline;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.common.docs.domain.DocumentParseResult;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.domain.SecurityScanResult;

/**
 * 文档管道处理结果 —— 聚合解析、安全扫描与 PII 检测三阶段输出。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessResult {

  /** 解析结果（必然存在） */
  private DocumentParseResult parseResult;

  /** 安全扫描结果（仅当启用扫描时填充） */
  private SecurityScanResult securityScanResult;

  /** PII 发现列表（仅当启用 PII 检测时填充） */
  private List<PiiFinding> piiFindings;

  /**
   * 判断整条管道是否成功（解析成功 且 未触发高风险阻止）。
   *
   * @return 成功时返回 {@code true}
   */
  public boolean isSuccess() {
    return parseResult != null && parseResult.isSuccess();
  }

  /**
   * 便捷方法：获取解析后的文档内容。
   *
   * @return 文档内容；解析失败时返回 {@code null}
   */
  public com.njydsz.common.docs.domain.DocumentContent getContent() {
    return parseResult != null ? parseResult.getContent() : null;
  }
}
