package com.njydsz.agent.domain.model.a2a;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A2A 协议文件段落模型。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2aFilePart {

  /** 文件名称 */
  private String name;

  /** MIME 类型 */
  private String mimeType;

  /** 文件内容 Base64 编码 */
  private String bytes;

  /** 文件 URI（引用外部文件时使用） */
  private String uri;
}
