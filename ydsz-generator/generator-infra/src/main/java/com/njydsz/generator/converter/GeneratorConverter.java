package com.njydsz.generator.converter;

import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenHistoryFile;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.entity.GenTemplate;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.enums.ConflictStrategyEnum;
import com.njydsz.generator.enums.DbDialectEnum;
import com.njydsz.generator.enums.GenStatusEnum;
import com.njydsz.generator.enums.TemplateFileTypeEnum;
import com.njydsz.generator.po.GenColumnMetaPO;
import com.njydsz.generator.po.GenDatasourcePO;
import com.njydsz.generator.po.GenHistoryPO;
import com.njydsz.generator.po.GenHistoryFilePO;
import com.njydsz.generator.po.GenTableMetaPO;
import com.njydsz.generator.po.GenTemplatePO;
import com.njydsz.generator.po.GenTemplateGroupPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 代码生成器领域对象与持久化对象转换器。
 *
 * <p>统一负责 Entity ↔ PO 的双向转换。
 *
 * <p><b>放置位置说明：</b>Converter 属于 DDD 基础设施层职责，
 * MapStruct 注解处理器仅在 infra 模块 pom 声明。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Mapper(componentModel = "spring")
public interface GeneratorConverter {

  // ── 数据源 ──

  /**
   * PO 转 Entity（枚举映射）。
   *
   * @param po 持久化对象
   * @return 领域实体
   */
  @Mapping(target = "dialect", source = "dialect", qualifiedByName = "stringToDbDialect")
  GenDatasource toEntity(GenDatasourcePO po);

  /**
   * Entity 转 PO（枚举映射）。
   *
   * @param entity 领域实体
   * @return 持久化对象
   */
  @Mapping(target = "dialect", source = "dialect", qualifiedByName = "dbDialectToString")
  GenDatasourcePO toPO(GenDatasource entity);

  // ── 模板分组 ──

  /**
   * PO 转 Entity。
   *
   * @param po 持久化对象
   * @return 领域实体
   */
  GenTemplateGroup toEntity(GenTemplateGroupPO po);

  /**
   * Entity 转 PO。
   *
   * @param entity 领域实体
   * @return 持久化对象
   */
  GenTemplateGroupPO toPO(GenTemplateGroup entity);

  /**
   * PO 列表转 Entity 列表。
   *
   * @param list PO 列表
   * @return Entity 列表
   */
  List<GenTemplateGroup> toGroupEntityList(List<GenTemplateGroupPO> list);

  // ── 模板 ──

  /**
   * PO 转 Entity（枚举映射）。
   *
   * @param po 持久化对象
   * @return 领域实体
   */
  @Mapping(target = "fileType", source = "fileType", qualifiedByName = "stringToFileType")
  GenTemplate toEntity(GenTemplatePO po);

  /**
   * Entity 转 PO（枚举映射）。
   *
   * @param entity 领域实体
   * @return 持久化对象
   */
  @Mapping(target = "fileType", source = "fileType", qualifiedByName = "fileTypeToString")
  GenTemplatePO toPO(GenTemplate entity);

  /**
   * PO 列表转 Entity 列表。
   *
   * @param list PO 列表
   * @return Entity 列表
   */
  List<GenTemplate> toTemplateEntityList(List<GenTemplatePO> list);

  // ── 历史 ──

  /**
   * PO 转 Entity（枚举映射）。
   *
   * @param po 持久化对象
   * @return 领域实体
   */
  @Mapping(target = "status", source = "status", qualifiedByName = "stringToGenStatus")
  GenHistory toEntity(GenHistoryPO po);

  /**
   * Entity 转 PO（枚举映射）。
   *
   * @param entity 领域实体
   * @return 持久化对象
   */
  @Mapping(target = "status", source = "status", qualifiedByName = "genStatusToString")
  GenHistoryPO toPO(GenHistory entity);

  /**
   * PO 列表转 Entity 列表。
   *
   * @param list PO 列表
   * @return Entity 列表
   */
  List<GenHistory> toHistoryEntityList(List<GenHistoryPO> list);

  // ── 文件明细 ──

  /**
   * PO 转 Entity。
   *
   * @param po 持久化对象
   * @return 领域实体
   */
  GenHistoryFile toEntity(GenHistoryFilePO po);

  /**
   * Entity 转 PO。
   *
   * @param entity 领域实体
   * @return 持久化对象
   */
  GenHistoryFilePO toPO(GenHistoryFile entity);

  /**
   * PO 列表转 Entity 列表。
   *
   * @param list PO 列表
   * @return Entity 列表
   */
  List<GenHistoryFile> toFileEntityList(List<GenHistoryFilePO> list);

  // ── 表元数据 ──

  /**
   * PO 转 Entity。
   *
   * @param po 持久化对象
   * @return 领域实体
   */
  GenTableMeta toEntity(GenTableMetaPO po);

  /**
   * Entity 转 PO。
   *
   * @param entity 领域实体
   * @return 持久化对象
   */
  GenTableMetaPO toPO(GenTableMeta entity);

  /**
   * PO 列表转 Entity 列表。
   *
   * @param list PO 列表
   * @return Entity 列表
   */
  List<GenTableMeta> toTableEntityList(List<GenTableMetaPO> list);

  // ── 列元数据 ──

  /**
   * PO 转 Entity。
   *
   * @param po 持久化对象
   * @return 领域实体
   */
  GenColumnMeta toEntity(GenColumnMetaPO po);

  /**
   * Entity 转 PO。
   *
   * @param entity 领域实体
   * @return 持久化对象
   */
  GenColumnMetaPO toPO(GenColumnMeta entity);

  /**
   * PO 列表转 Entity 列表。
   *
   * @param list PO 列表
   * @return Entity 列表
   */
  List<GenColumnMeta> toColumnEntityList(List<GenColumnMetaPO> list);

  // ── 枚举命名转换方法 ──

  /** 字符串转数据库方言枚举。 */
  @Named("stringToDbDialect")
  default DbDialectEnum stringToDbDialect(String value) {
    if (value == null) {
      return DbDialectEnum.MYSQL;
    }
    for (DbDialectEnum dialect : DbDialectEnum.values()) {
      if (dialect.getDialect().equalsIgnoreCase(value)) {
        return dialect;
      }
    }
    return DbDialectEnum.MYSQL;
  }

  /** 数据库方言枚举转字符串。 */
  @Named("dbDialectToString")
  default String dbDialectToString(DbDialectEnum dialect) {
    return dialect == null ? DbDialectEnum.MYSQL.getDialect() : dialect.getDialect();
  }

  /** 字符串转任务状态枚举。 */
  @Named("stringToGenStatus")
  default GenStatusEnum stringToGenStatus(String value) {
    if (value == null) {
      return GenStatusEnum.FAILED;
    }
    for (GenStatusEnum s : GenStatusEnum.values()) {
      if (s.getCode().equalsIgnoreCase(value)) {
        return s;
      }
    }
    return GenStatusEnum.FAILED;
  }

  /** 任务状态枚举转字符串。 */
  @Named("genStatusToString")
  default String genStatusToString(GenStatusEnum status) {
    return status == null ? GenStatusEnum.FAILED.getCode() : status.getCode();
  }

  /** 字符串转模板类型枚举。 */
  @Named("stringToFileType")
  default TemplateFileTypeEnum stringToFileType(String value) {
    if (value == null) {
      return TemplateFileTypeEnum.BACKEND;
    }
    for (TemplateFileTypeEnum type : TemplateFileTypeEnum.values()) {
      if (type.getCode().equalsIgnoreCase(value)) {
        return type;
      }
    }
    return TemplateFileTypeEnum.BACKEND;
  }

  /** 模板类型枚举转字符串。 */
  @Named("fileTypeToString")
  default String fileTypeToString(TemplateFileTypeEnum type) {
    return type == null ? TemplateFileTypeEnum.BACKEND.getCode() : type.getCode();
  }

  /**
   * 转换 ConflictStrategyEnum 为字符串（暂不使用，预留）。
   *
   * @param strategy 冲突策略
   * @return 字符串表示
   */
  default String conflictStrategyToString(ConflictStrategyEnum strategy) {
    return strategy == null ? ConflictStrategyEnum.SKIP.getCode() : strategy.getCode();
  }
}
