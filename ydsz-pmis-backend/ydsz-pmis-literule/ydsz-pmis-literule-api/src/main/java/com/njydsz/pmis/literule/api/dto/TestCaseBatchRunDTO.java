package com.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 测试用例批量执行请求体 DTO
 *
 * <p>用于 {@code /rules/test-cases/batch-run} 接口，对指定测试用例执行回归测试。
 * {@code ids} 为空时执行全部测试用例。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Schema(description = "测试用例批量执行请求体")
public class TestCaseBatchRunDTO {

    /**
     * 测试用例 ID 列表（为空则执行全部测试用例）
     */
    @Schema(description = "测试用例 ID 列表（为空则执行全部）")
    private List<Long> ids;
}
