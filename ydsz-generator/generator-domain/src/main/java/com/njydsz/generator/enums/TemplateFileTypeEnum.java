package com.njydsz.generator.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模板文件类型枚举。
 *
 * <p>模板按用途分为后端 Java 代码和前端 Vue 代码两类。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Getter
@AllArgsConstructor
public enum TemplateFileTypeEnum {

  /** 后端 Java 源代码模板（controller/service/...）。 */
  BACKEND("BACKEND", "后端"),
  /** 前端 Vue 模板（api.ts, index.vue）。 */
  FRONTEND("FRONTEND", "前端");

  /** 类型码。 */
  private final String code;
  /** 类型描述。 */
  private final String description;
}
