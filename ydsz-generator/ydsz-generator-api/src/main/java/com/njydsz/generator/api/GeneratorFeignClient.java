package com.njydsz.generator.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.api.fallback.GeneratorClientFallbackFactory;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.query.GenCodeGenerateQuery;
import com.njydsz.generator.vo.CodePreviewVO;

/**
 * 代码生成器 Feign 远程调用接口。
 *
 * <p>其他微服务通过此接口调用代码生成器，无需直接依赖 server 层。
 * 接口契约应保持稳定，变更需同步通知下游服务。
 *
 * <p><b>DDD 分层位置：</b>api 模块，仅依赖 domain（Entity/VO/Enum 等）。
 * api 模块禁止自建 dto/vo/query 子包，所有数据类型引用自 domain 模块。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@FeignClient(
    name = "ydsz-generator-service",
    contextId = "generatorFeignClient",
    path = "/api/generator",
    fallbackFactory = GeneratorClientFallbackFactory.class)
public interface GeneratorFeignClient {

  // ════════════════════════════════════════════════════════════
  // 数据源管理
  // ════════════════════════════════════════════════════════════

  /**
   * 查询全部已配置的数据源（Feign 远程调用）。
   *
   * <p>支持 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库。
   *
   * @return 数据源列表，每个元素包含 id、name、jdbcUrl、username、dialect 等；
   *         调用失败时由 {@code GeneratorClientFallbackFactory} 处理
   */
  @GetMapping("/datasources")
  YdszResponse<List<GenDatasource>> listDatasources();

  /**
   * 获取标记为默认的数据源（Feign 远程调用）。
   *
   * @return 默认数据源配置；未配置时返回 null
   */
  @GetMapping("/datasources/default")
  YdszResponse<GenDatasource> getDefaultDatasource();

  /**
   * 测试数据源 JDBC 连接是否可用（Feign 远程调用）。
   *
   * <p>不保存配置，仅使用传入参数尝试建立数据库连接。
   * 支持 MySQL、PostgreSQL、Oracle、SQL Server 等。
   *
   * @param datasource 数据源配置，至少需包含 jdbcUrl、username、password
   * @return true 表示连接成功，false 表示连接失败
   */
  @PostMapping("/datasources/test")
  YdszResponse<Boolean> testDatasource(@RequestBody GenDatasource datasource);

  // ════════════════════════════════════════════════════════════
  // 模板分组管理
  // ════════════════════════════════════════════════════════════

  /**
   * 查询全部模板分组（Feign 远程调用）。
   *
   * @return 全部分组列表，每个元素包含 id、name、description、isActive 等
   */
  @GetMapping("/groups")
  YdszResponse<List<GenTemplateGroup>> listGroups();

  /**
   * 激活指定的模板分组（Feign 远程调用）。
   *
   * <p>同一时刻只有一个分组处于激活状态。
   *
   * @param groupId 分组 ID
   * @return 操作结果，成功时 data 为 null
   */
  @PostMapping("/groups/{groupId}/activate")
  YdszResponse<Void> activateGroup(@PathVariable("groupId") Long groupId);

  // ════════════════════════════════════════════════════════════
  // 表元数据管理
  // ════════════════════════════════════════════════════════════

  /**
   * 查询数据源下全部表结构元数据（Feign 远程调用）。
   *
   * <p>兼容 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库。
   *
   * @param datasourceId 数据源 ID
   * @return 表元数据列表，包含 tableName、comment、aliasName、moduleName 等
   */
  @GetMapping("/tables")
  YdszResponse<List<GenTableMeta>> listTables(@RequestParam("datasourceId") Long datasourceId);

  /**
   * 重新连接数据库刷新表元数据缓存（Feign 远程调用）。
   *
   * <p>从目标数据库实时读取表结构信息替换本地缓存。
   * 兼容 MySQL、PostgreSQL、Oracle、SQL Server 等。
   *
   * @param datasourceId 数据源 ID
   * @return 刷新后的表元数据列表
   */
  @PostMapping("/tables/refresh")
  YdszResponse<List<GenTableMeta>> refreshTables(@RequestParam("datasourceId") Long datasourceId);

  /**
   * 查询指定表的列元数据（Feign 远程调用）。
   *
   * <p>兼容 MySQL、PostgreSQL、Oracle、SQL Server 等主流关系型数据库字段类型映射。
   *
   * @param tableMetaId 表元数据 ID
   * @return 列元数据列表，包含 columnName、columnType、javaType、columnComment、isPrimaryKey 等
   */
  @GetMapping("/tables/columns")
  YdszResponse<List<GenColumnMeta>> getColumns(@RequestParam("tableMetaId") Long tableMetaId);

  // ════════════════════════════════════════════════════════════
  // 代码生成
  // ════════════════════════════════════════════════════════════

  /**
   * 预览代码生成结果（Feign 远程调用，展示不写入）。
   *
   * <p>预览返回渲染后的代码文本，每项包含 fileName、filePath、content（代码文本）、
   * isConflict（是否与已有文件冲突）。
   * 支持 MySQL、PostgreSQL、Oracle、SQL Server 等作为数据源。
   *
   * @param datasourceId    数据源 ID
   * @param templateGroupId 模板分组 ID
   * @param tableName       物理表名
   * @return 预览结果列表
   */
  @GetMapping("/code/preview")
  YdszResponse<List<CodePreviewVO>> preview(
      @RequestParam("datasourceId") Long datasourceId,
      @RequestParam("templateGroupId") Long templateGroupId,
      @RequestParam("tableName") String tableName);

  /**
   * 根据表结构正式生成代码到指定目录（Feign 远程调用）。
   *
   * <p>生成产物包含 Entity/VO/DTO、Mapper 接口与 XML、Service、Controller、
   * 前端页面等全栈文件。
   * 冲突策略（conflictStrategy）：SKIP（默认跳过）/ OVERRIDE（覆盖并备份）/ APPEND（追加）。
   *
   * @param query 生成请求参数，包含 datasourceId、templateGroupId、tableName、
   *              outputDir、conflictStrategy、triggeredBy
   * @return 生成结果摘要（JSON 格式字符串），包含 historyId、fileCount 等
   */
  @PostMapping("/code/generate")
  YdszResponse<String> generate(@RequestBody GenCodeGenerateQuery query);

  // ════════════════════════════════════════════════════════════
  // 回滚与历史
  // ════════════════════════════════════════════════════════════

  /**
   * 查询最近的生成历史记录（Feign 远程调用）。
   *
   * @param limit 返回数量上限，默认 20
   * @return 历史任务列表，按时间倒序排列
   */
  @GetMapping("/history")
  YdszResponse<List<GenHistory>> listHistory(@RequestParam(value = "limit", defaultValue = "20") int limit);

  /**
   * 回滚指定的代码生成任务（Feign 远程调用）。
   *
   * <p>恢复生成前的文件状态：新生成的文件被删除，被覆盖的文件从备份中恢复。
   *
   * @param historyId 任务 ID
   * @return 操作结果，成功时 data 为 null
   */
  @PostMapping("/history/{historyId}/rollback")
  YdszResponse<Void> rollback(@PathVariable("historyId") Long historyId);
}
