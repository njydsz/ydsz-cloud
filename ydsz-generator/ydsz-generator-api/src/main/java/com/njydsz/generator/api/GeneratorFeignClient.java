package com.njydsz.generator.api;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.api.fallback.GeneratorClientFallbackFactory;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.vo.CodePreviewVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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
    path = "/api/v1/generator",
    fallbackFactory = GeneratorClientFallbackFactory.class)
public interface GeneratorFeignClient {

  // ════════════════════════════════════════════════════════════
  // 数据源管理
  // ════════════════════════════════════════════════════════════

  /**
   * 查询全部数据源。
   *
   * @return 数据源列表
   */
  @GetMapping("/datasources")
  YdszResponse<List<GenDatasource>> listDatasources();

  /**
   * 获取默认数据源。
   *
   * @return 默认数据源
   */
  @GetMapping("/datasources/default")
  YdszResponse<GenDatasource> getDefaultDatasource();

  /**
   * 测试数据源连接。
   *
   * @param datasource 数据源配置
   * @return 连接是否成功
   */
  @PostMapping("/datasources/test")
  YdszResponse<Boolean> testDatasource(@RequestBody GenDatasource datasource);

  // ════════════════════════════════════════════════════════════
  // 模板分组管理
  // ════════════════════════════════════════════════════════════

  /**
   * 查询全部分组。
   *
   * @return 分组列表
   */
  @GetMapping("/groups")
  YdszResponse<List<GenTemplateGroup>> listGroups();

  /**
   * 激活指定分组。
   *
   * @param groupId 分组 ID
   * @return 操作结果
   */
  @PostMapping("/groups/{groupId}/activate")
  YdszResponse<Void> activateGroup(@PathVariable("groupId") Long groupId);

  // ════════════════════════════════════════════════════════════
  // 表元数据管理
  // ════════════════════════════════════════════════════════════

  /**
   * 查询数据源下全部表。
   *
   * @param datasourceId 数据源 ID
   * @return 表元数据列表
   */
  @GetMapping("/tables")
  YdszResponse<List<GenTableMeta>> listTables(@RequestParam("datasourceId") Long datasourceId);

  /**
   * 刷新数据源表缓存。
   *
   * @param datasourceId 数据源 ID
   * @return 刷新后列表
   */
  @PostMapping("/tables/refresh")
  YdszResponse<List<GenTableMeta>> refreshTables(@RequestParam("datasourceId") Long datasourceId);

  /**
   * 查询表的列元数据。
   *
   * @param tableMetaId 表元数据 ID
   * @return 列元数据列表
   */
  @GetMapping("/tables/columns")
  YdszResponse<List<GenColumnMeta>> getColumns(@RequestParam("tableMetaId") Long tableMetaId);

  // ════════════════════════════════════════════════════════════
  // 代码生成
  // ════════════════════════════════════════════════════════════

  /**
   * 预览代码。
   *
   * @param datasourceId    数据源 ID
   * @param templateGroupId 模板分组 ID
   * @param tableName       表名
   * @return 预览结果列表
   */
  @GetMapping("/code/preview")
  YdszResponse<List<CodePreviewVO>> preview(
      @RequestParam("datasourceId") Long datasourceId,
      @RequestParam("templateGroupId") Long templateGroupId,
      @RequestParam("tableName") String tableName);

  /**
   * 正式生成代码。
   *
   * @param datasourceId      数据源 ID
   * @param templateGroupId   模板分组 ID
   * @param tableName         表名
   * @param outputDir         输出目录
   * @param conflictStrategy  冲突策略（SKIP/OVERRIDE/MERGE）
   * @param triggeredBy       触发人
   * @return 生成结果
   */
  @PostMapping("/code/generate")
  YdszResponse<String> generate(
      @RequestParam("datasourceId") Long datasourceId,
      @RequestParam("templateGroupId") Long templateGroupId,
      @RequestParam("tableName") String tableName,
      @RequestParam("outputDir") String outputDir,
      @RequestParam(value = "conflictStrategy", required = false) String conflictStrategy,
      @RequestParam(value = "triggeredBy", required = false) String triggeredBy);

  // ════════════════════════════════════════════════════════════
  // 回滚与历史
  // ════════════════════════════════════════════════════════════

  /**
   * 查询生成历史。
   *
   * @param limit 数量上限
   * @return 历史列表
   */
  @GetMapping("/history")
  YdszResponse<List<GenHistory>> listHistory(@RequestParam(value = "limit", defaultValue = "20") int limit);

  /**
   * 回滚任务。
   *
   * @param historyId 任务 ID
   * @return 操作结果
   */
  @PostMapping("/history/{historyId}/rollback")
  YdszResponse<Void> rollback(@PathVariable("historyId") Long historyId);
}
