package com.njydsz.generator.web.security;

/**
 * 代码生成器模块 — 接口权限码常量。
 *
 * <p>统一使用 YDSZ 三段式命名规范：{@code generator:{resource}:{operation}}。
 * 所有 Controller 方法级鉴权注解 {@code @AuthApiPermission} 引用本常量类，
 * 实现与平台 RBAC 体系统一。
 *
 * @author ydsz-team
 * @since 26.09.27
 */
public final class PermissionCodes {

  private PermissionCodes() {
    // 工具类禁止实例化
  }

  // ══════════════ 角色级入口权限（类级别注解使用） ══════════════

  /** 代码生成器通用用户权限（查询/预览/生成/历史/模板读取等）。 */
  public static final String GENERATOR_USER = "generator:user";

  /** 代码生成器管理员权限（数据源写操作/分组写操作等管理接口）。 */
  public static final String GENERATOR_ADMIN = "generator:admin";

  // ══════════════ 模板分组管理 ══════════════

  /** 查询分组列表。 */
  public static final String GENERATOR_GROUP_READ = "generator:group:read";

  /** 创建/激活/删除分组（写操作）。 */
  public static final String GENERATOR_GROUP_WRITE = "generator:group:write";

  // ══════════════ 模板管理 ══════════════

  /** 查询/搜索/校验/对比模板。 */
  public static final String GENERATOR_TEMPLATE_READ = "generator:template:read";

  /** 更新模板内容（写操作）。 */
  public static final String GENERATOR_TEMPLATE_WRITE = "generator:template:write";

  // ══════════════ 模板导入导出 ══════════════

  /** 导出模板为 zip。 */
  public static final String GENERATOR_TEMPLATE_EXPORT = "generator:template:export";

  /** 从 zip 导入模板。 */
  public static final String GENERATOR_TEMPLATE_IMPORT = "generator:template:import";

  // ══════════════ 表元数据管理 ══════════════

  /** 查询/刷新表元数据及列元数据。 */
  public static final String GENERATOR_TABLE_READ = "generator:table:read";

  // ══════════════ 代码生成 ══════════════

  /** 预览代码（不写文件系统）。 */
  public static final String GENERATOR_CODEGEN_PREVIEW = "generator:codegen:preview";

  /** 正式生成/批量生成代码。 */
  public static final String GENERATOR_CODEGEN_GENERATE = "generator:codegen:generate";

  /** 下载代码 zip。 */
  public static final String GENERATOR_CODEGEN_DOWNLOAD = "generator:codegen:download";

  // ══════════════ 反向生成 ══════════════

  /** 反向分析 Java 源文件。 */
  public static final String GENERATOR_REVERSE_ANALYZE = "generator:reverse:analyze";

  // ══════════════ 生成历史 ══════════════

  /** 查询生成历史列表与详情。 */
  public static final String GENERATOR_HISTORY_READ = "generator:history:read";

  /** 回滚/删除生成历史。 */
  public static final String GENERATOR_HISTORY_WRITE = "generator:history:write";

  // ══════════════ 数据源管理 ══════════════

  /** 查询数据源列表与详情。 */
  public static final String GENERATOR_DATASOURCE_READ = "generator:datasource:read";

  /** 创建/更新/删除数据源、测试连接。 */
  public static final String GENERATOR_DATASOURCE_WRITE = "generator:datasource:write";
}
