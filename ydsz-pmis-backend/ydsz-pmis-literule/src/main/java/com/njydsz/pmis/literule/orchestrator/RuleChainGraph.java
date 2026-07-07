package com.njydsz.pmis.literule.orchestrator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 可视化规则链编排画布图 DTO（P2-1）
 *
 * <p>规则链画布的完整元数据模型，由 {@link ChainNodeDTO} 节点集合、
 * {@link ChainEdgeDTO} 连线集合以及画布视口元数据组成。
 * 支撑前端可视化规则编排画布的"画布持久化"能力：
 * <ul>
 *   <li>规则链可视化编辑（拖拽节点、连线、布局自动对齐）</li>
 *   <li>规则链版本回放（按 graphId 拉取历史画布快照）</li>
 *   <li>规则链导入导出（导出为 JSON，跨环境同步）</li>
 * </ul>
 *
 * <p>该 DTO 与 {@link RuleChain} 的关系：
 * <ul>
 *   <li>RuleChain：运行时执行模型，承载规则编排语义（THEN/WHEN/IF...），不含布局信息</li>
 *   <li>RuleChainGraph：可视化元数据模型，承载画布节点位置和连线，不参与运行时执行</li>
 * </ul>
 * 通过 {@link ChainGraphConverter} 可在 RuleChain 与 RuleChainGraph 之间双向转换：
 * RuleChain → Graph 提取结构骨架（不包含位置），Graph → RuleChain 还原可执行编排。
 *
 * <p>典型用法：
 * <pre>
 *   RuleChainGraph graph = RuleChainGraph.builder()
 *       .graphId("graph-1")
 *       .name("CPI 预警链")
 *       .scenario("EVM")
 *       .nodes(List.of(node1, node2))
 *       .edges(List.of(edge1))
 *       .viewport(new RuleChainGraph.Viewport(0, 0, 1.0))
 *       .build();
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleChainGraph implements Serializable {

    private static final String serialVersionUID = "1";

    /** 画布 ID（全局唯一） */
    private String graphId;

    /** 画布名称（如"CPI 预警链-2024Q1"） */
    private String name;

    /** 关联规则编码（一对一，P0-1 增强：作为画布查询的 key） */
    private String ruleCode;

    /** 画布描述 */
    private String description;

    /** 适用场景（与 RuleContext.scenario 对应） */
    private String scenario;

    /** 租户 ID（多租户隔离，P1-3） */
    private String tenantId;

    /** 画布版本号（语义化版本，如 1.0.0、1.1.0-SNAPSHOT） */
    private String version;

    /** 画布状态：DRAFT / PUBLISHED / ARCHIVED（与 {@link com.njydsz.pmis.literule.api.RuleStatus} 对齐） */
    @Builder.Default
    private String status = "DRAFT";

    /** 节点列表 */
    @Builder.Default
    private List<ChainNodeDTO> nodes = new ArrayList<>();

    /** 连线列表 */
    @Builder.Default
    private List<ChainEdgeDTO> edges = new ArrayList<>();

    /** 画布视口（前端缩放和平移状态） */
    private Viewport viewport;

    /** 画布元数据扩展（如作者、标签、自定义属性） */
    private Map<String, Object> metadata;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /** 创建人 */
    private String createdBy;

    /** 最后更新人 */
    private String updatedBy;

    /**
     * 画布视口（前端画布的缩放和平移状态）
     *
     * @param x      视口左上角横坐标
     * @param y      视口左上角纵坐标
     * @param zoom   缩放比例（1.0 = 100%）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Viewport implements Serializable {
        private static final String serialVersionUID = "1";
        private double x;
        private double y;
        private double zoom = 1.0;
    }
}
