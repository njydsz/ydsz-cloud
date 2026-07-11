package com.njydsz.pmis.project.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 项目详情聚合 VO（BFF 聚合接口返回值）。
 *
 * <p>一次请求返回立项、EVM、合同台账、WBS 概览等复合数据，
 * 替代原来无类型的 {@code Map<String, Object>} 返回值，
 * 使前端通过 OpenAPI 自动生成 TypeScript 类型定义。
 *
 * <p>各维度独立 try-catch，单维度异常时对应字段填充 {@link AggregateSection#error}，
 * 不影响其他维度正常返回。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Schema(description = "项目详情聚合数据")
public class ProjectDetailAggregateVO {

    /** 立项信息（全生命周期台账：商机 → 立项 → 合同 → 变更 → 结项） */
    @Schema(description = "立项信息")
    private AggregateSection<Object> initiation;

    /** EVM 摘要（利润表含 CPI/SPI 等挣值指标） */
    @Schema(description = "EVM 挣值数据")
    private AggregateSection<Object> evm;

    /** 合同 / 回款台账 */
    @Schema(description = "合同回款台账")
    private AggregateSection<Object> contracts;

    /** WBS 概览（成本归集明细含人力/采购/费用/分摊拆解） */
    @Schema(description = "WBS 概览")
    private AggregateSection<Object> wbsOverview;

    /**
     * 聚合分片数据结构。
     *
     * <p>每个聚合维度独立包装，success=false 时携带 error 信息，
     * success=true 时携带 data 数据。
     *
     * @param <T> 分片数据类型
     */
    @Data
    @Schema(description = "聚合分片数据")
    public static class AggregateSection<T> {

        /** 是否成功加载 */
        @Schema(description = "是否成功加载")
        private boolean success;

        /** 数据（success=true 时有值） */
        @Schema(description = "分片数据")
        private T data;

        /** 错误信息（success=false 时有值） */
        @Schema(description = "错误信息")
        private String error;

        /**
         * 创建成功分片
         *
         * @param data 分片数据
         * @param <T>  数据类型
         * @return 成功分片
         */
        public static <T> AggregateSection<T> ok(T data) {
            AggregateSection<T> section = new AggregateSection<>();
            section.setSuccess(true);
            section.setData(data);
            return section;
        }

        /**
         * 创建失败分片
         *
         * @param error 错误信息
         * @param <T>   数据类型
         * @return 失败分片
         */
        public static <T> AggregateSection<T> fail(String error) {
            AggregateSection<T> section = new AggregateSection<>();
            section.setSuccess(false);
            section.setError(error);
            return section;
        }
    }
}
