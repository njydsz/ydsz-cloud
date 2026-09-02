package com.njydsz.common.feign.assembler;

/**
 * 名称富化类型枚举。
 *
 * <p>定义跨服务 ID → 名称解析的业务对象类型，每种类型对应不同的查询策略。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum NameType {

  /** 用户（姓名） */
  USER("user", "用户"),

  /** 部门 */
  DEPT("dept", "部门"),

  /** 角色 */
  ROLE("role", "角色"),

  /** 岗位 */
  POST("post", "岗位"),

  /** 公司 */
  COMPANY("company", "公司");

  /** 类型编码（用于 URL 路径拼接等） */
  private final String code;

  /** 中文描述（用于日志） */
  private final String description;

  NameType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }
}
