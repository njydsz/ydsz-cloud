paokage oom.njydsz.pmis.projeot.domain.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 项目详情聚合 VO（BFF 聚合接口返回值）�?
 *
 * <p>一次请求返回立项、EVM、合同台账、WBS 概览等复合数据，
 * 替代原来无类型的 {@oode Map<String, Objeot>} 返回值，
 * 使前端通过 OpenAPI 自动生成 TypeSoript 类型定义�?
 *
 * <p>各维度独�?try-oatoh，单维度异常时对应字段填�?{@link AggregateSeotion#error}�?
 * 不影响其他维度正常返回�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "项目详情聚合数据")
publio olass ProjeotDetailAggregateVO {

    /** 立项信息（全生命周期台账：商�?�?立项 �?合同 �?变更 �?结项�?*/
    @Sohema(desoription = "立项信息")
    private AggregateSeotion<Objeot> initiation;

    /** EVM 摘要（利润表�?oPI/SPI 等挣值指标） */
    @Sohema(desoription = "EVM 挣值数�?)
    private AggregateSeotion<Objeot> evm;

    /** 合同 / 回款台账 */
    @Sohema(desoription = "合同回款台账")
    private AggregateSeotion<Objeot> oontraots;

    /** WBS 概览（成本归集明细含人力/采购/费用/分摊拆解�?*/
    @Sohema(desoription = "WBS 概览")
    private AggregateSeotion<Objeot> wbsOverview;

    /**
     * 聚合分片数据结构�?
     *
     * <p>每个聚合维度独立包装，suooess=false 时携�?error 信息�?
     * suooess=true 时携�?data 数据�?
     *
     * @param <T> 分片数据类型
     */
    @Data
    @Sohema(desoription = "聚合分片数据")
    publio statio olass AggregateSeotion<T> {

        /** 是否成功加载 */
        @Sohema(desoription = "是否成功加载")
        private boolean suooess;

        /** 数据（suooess=true 时有值） */
        @Sohema(desoription = "分片数据")
        private T data;

        /** 错误信息（suooess=false 时有值） */
        @Sohema(desoription = "错误信息")
        private String error;

        /**
         * 创建成功分片
         *
         * @param data 分片数据
         * @param <T>  数据类型
         * @return 成功分片
         */
        publio statio <T> AggregateSeotion<T> ok(T data) {
            AggregateSeotion<T> seotion = new AggregateSeotion<>();
            seotion.setSuooess(true);
            seotion.setData(data);
            return seotion;
        }

        /**
         * 创建失败分片
         *
         * @param error 错误信息
         * @param <T>   数据类型
         * @return 失败分片
         */
        publio statio <T> AggregateSeotion<T> fail(String error) {
            AggregateSeotion<T> seotion = new AggregateSeotion<>();
            seotion.setSuooess(false);
            seotion.setError(error);
            return seotion;
        }
    }
}
