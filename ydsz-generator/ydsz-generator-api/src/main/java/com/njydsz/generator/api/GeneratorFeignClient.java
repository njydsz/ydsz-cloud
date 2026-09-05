package com.njydsz.generator.api;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.api.dto.CodeGenRequestDTO;
import com.njydsz.generator.api.dto.CodeGenResultDTO;
import com.njydsz.generator.api.dto.TableMetaDTO;
import com.njydsz.generator.api.fallback.GeneratorClientFallbackFactory;
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
 * <p><b>DDD 分层位置：</b>api 模块，不依赖任何 infra/server 层。
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

  /**
   * 查询全部数据源。
   *
   * @return 数据源列表
   */
  @GetMapping("/datasources")
  YdszResponse<List<Object>> listDatasources();

  /**
   * 获取默认数据源。
   *
   * @return 默认数据源
   */
  @GetMapping("/datasources/default")
  YdszResponse<Object> getDefaultDatasource();

  /**
   * 查询数据源下全部表。
   *
   * @param datasourceId 数据源 ID
   * @return 表元数据列表
   */
  @GetMapping("/tables")
  YdszResponse<List<TableMetaDTO>> listTables(@RequestParam("datasourceId") Long datasourceId);

  /**
   * 刷新表缓存。
   *
   * @param datasourceId 数据源 ID
   * @return 刷新后的表列表
   */
  @PostMapping("/tables/refresh")
  YdszResponse<List<TableMetaDTO>> refreshTables(@RequestParam("datasourceId") Long datasourceId);

  /**
   * 预览代码。
   *
   * @param request 预览请求
   * @return 预览结果
   */
  @PostMapping("/code/preview")
  YdszResponse<List<CodeGenResultDTO>> preview(@RequestBody CodeGenRequestDTO request);

  /**
   * 正式生成代码。
   *
   * @param request 生成请求
   * @return 生成结果
   */
  @PostMapping("/code/generate")
  YdszResponse<Object> generate(@RequestBody CodeGenRequestDTO request);

  /**
   * 查询生成历史。
   *
   * @param limit 数量上限
   * @return 历史列表
   */
  @GetMapping("/history")
  YdszResponse<List<Object>> listHistory(@RequestParam(defaultValue = "20") int limit);

  /**
   * 回滚任务。
   *
   * @param historyId 任务 ID
   * @return 操作结果
   */
  @PostMapping("/history/{historyId}/rollback")
  YdszResponse<Void> rollback(@PathVariable Long historyId);
}
