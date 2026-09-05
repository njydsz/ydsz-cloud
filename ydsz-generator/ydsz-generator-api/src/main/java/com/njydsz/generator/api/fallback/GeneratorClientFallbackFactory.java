package com.njydsz.generator.api.fallback;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.generator.api.GeneratorFeignClient;
import com.njydsz.generator.api.dto.CodeGenRequestDTO;
import com.njydsz.generator.api.dto.CodeGenResultDTO;
import com.njydsz.generator.api.dto.TableMetaDTO;
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
    log.error("GeneratorFeignClient 远程调用降级: {}", cause.getMessage());
    return new GeneratorFeignClient() {

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<Object>> listDatasources() {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<Object> getDefaultDatasource() {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<TableMetaDTO>> listTables(Long datasourceId) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<TableMetaDTO>> refreshTables(Long datasourceId) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<CodeGenResultDTO>> preview(CodeGenRequestDTO request) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<Object> generate(CodeGenRequestDTO request) {
        return YdszResponse.error("代码生成服务暂不可用");
      }

      /** {@inheritDoc} */
      @Override
      public YdszResponse<List<Object>> listHistory(int limit) {
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
