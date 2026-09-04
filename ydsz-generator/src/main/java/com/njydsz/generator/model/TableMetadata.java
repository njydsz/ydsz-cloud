package com.njydsz.generator.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 数据库表元数据。
 *
 * <p>封装从 JDBC {@link java.sql.DatabaseMetaData} 读取的表结构描述，作为模板引擎的上下文根对象。
 *
 * <p><b>上下文传递：</b>通过 {@link CodeGeneratorService#generateAll(TableMetadata)} 传递给 Velocity 模板，
 * 模板中通过 {@code $table.xxx} 语法访问各属性。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class TableMetadata {

  /** 原始表名（如 {@code ydsz_sys_tenant}） */
  private String tableName;

  /** 去除前缀后的裸表名（如 {@code sys_tenant}） */
  private String rawTableName;

  /** 实体类名（PascalCase，如 {@code Tenant}） */
  private String entityName;

  /** Repository 接口名（如 {@code TenantRepository}） */
  private String repositoryName;

  /** Service 接口名（如 {@code TenantService}） */
  private String serviceName;

  /** Service 实现类名（如 {@code TenantServiceImpl}） */
  private String serviceImplName;

  /** Controller 类名（如 {@code TenantController}） */
  private String controllerName;

  /** VO/DTO/Query 类名（如 {@code TenantVO}、{@code TenantDTO}、{@code TenantPageQuery}） */
  private String voName;
  private String dtoName;
  private String queryName;

  /** MapStruct Converter 接口名（如 {@code SystemConverter}） */
  private String converterName;

  /** 表注释（COMMENT） */
  private String tableComment;

  /** 列列表（排除审计字段后的业务字段） */
  private List<ColumnMetadata> columns = new ArrayList<>(16);

  /** 全部列（含审计字段） */
  private List<ColumnMetadata> allColumns = new ArrayList<>(16);

  /** 主键列 */
  private ColumnMetadata primaryKey;

  /** Controller 路径前缀（如 {@code /api/v1/tenant}） */
  private String apiPath;

  /** 权限前缀（如 {@code sys:tenant}） */
  private String permissionPrefix;

  /** Mapper 接口名（如 {@code TenantMapper}） */
  private String mapperName;

  /** FeignClient 接口名（如 {@code TenantFeignClient}） */
  private String feignClientName;

  /** 枚举定义列表（从字段注释中解析） */
  private java.util.List<com.njydsz.generator.model.EnumDefinition> enumDefinitions;

  /** 是否包含枚举字段（用于模板条件渲染） */
  private boolean hasEnums;

  /** 包路径段：domain */
  private String domainPackage;
  /** 包路径段：infra */
  private String infraPackage;
  /** 包路径段：server */
  private String serverPackage;
  /** 包路径段：web */
  private String webPackage;
  /** 包路径段：api */
  private String apiPackage;
}
