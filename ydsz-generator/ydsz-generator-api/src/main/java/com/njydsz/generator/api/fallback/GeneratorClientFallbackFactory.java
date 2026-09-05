package com.njydsz.generator.api.fallback;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.api.GeneratorFeignClient;
import com.njydsz.generator.entity.GenColumnMeta;
import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.entity.GenTableMeta;
import com.njydsz.generator.entity.GenTemplateGroup;
import com.njydsz.generator.vo.CodePreviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 代码生成器 Feign 降级工厂。
 *
 * <p>当 ydzsz-generator-service 不可用时，提供降级响应，避免调用链路中断。
 *
 * <p><b>DDD 分层位置：</b>api 模块，Feign 客户端的降级逻辑。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Component
public class GeneratorClientFallbackFactory implements FallbackFactory<GeneratorFeignClient> {

  /** {@inheritDoc} */
  @Override
  public GeneratorFeignClient create(Throwable cause) {
    log.warn("GeneratorFeignClient 远程调用降级: {}", cause.getMessage());
    return new GeneratorFeignClient() {

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<GenDatasource>> listDatasources() {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<GenDatasource> getDefaultDatasource() {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<Boolean> testDatasource(GenDatasource datasource) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<GenTemplateGroup>> listGroups() {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<Void> activateGroup(Long groupId) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<GenTableMeta>> listTables(Long datasourceId) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<GenTableMeta>> refreshTables(Long datasourceId) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<GenColumnMeta>> getColumns(Long tableMetaId) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<CodePreviewVO>> preview(
          Long datasourceId, Long templateGroupId, String tableName) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<String> generate(
          Long datasourceId, Long templateGroupId, String tableName,
          String outputDir, String conflictStrategy, String triggeredBy) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<GenHistory>> listHistory(int limit) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<Void> rollback(Long historyId) {
        return YdszResponse.error("代码生成服务暂不可用");
      }
    };
  }
}
