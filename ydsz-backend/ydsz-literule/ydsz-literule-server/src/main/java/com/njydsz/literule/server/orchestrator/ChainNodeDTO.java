package com.njydsz.literule.server.orchestrator;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可视化规则链编排画布节点 DTO（P2-1）
 *
 * <p>描述规则链画布上的一个节点，包含节点在画布上的位置坐标、引用的规则或子链、
 * 节点形态（单规则 / 子链 / 规则组）以及前端渲染所需的扩展元数据。
 *
 * <p>该 DTO 仅承载可视化元数据，与 {@link RuleNode} 的运行时编排节点分离，
 * 避免可视化布局信息污染运行时执行模型。
 *
 * <p>典型用法：
 * <pre>
 *   ChainNodeDTO node = ChainNodeDTO.builder()
 *       .nodeId("node-1")
 *       .nodeType("SINGLE")
 *       .label("CPI 预警")
 *       .ruleCode("CPI_WARN")
 *       .position(new ChainNodeDTO.Position(120, 80))
 *       .build();
 * </pre>
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainNodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点 ID（画布内唯一，前端生成的 uuid 或后端分配的有序 id） */
    private String nodeId;

    /** 节点形态：SINGLE / CHAIN / GROUP（对应 {@link RuleNode.NodeType}） */
    private String nodeType;

    /** 节点显示标签（默认取规则名称） */
    private String label;

    /** 引用的规则编码（nodeType=SINGLE 时必填） */
    private String ruleCode;

    /** 引用的规则名称（便于画布展示，避免每次反查规则定义） */
    private String ruleName;

    /** 引用的规则类别（EVM / COST / BENCH 等，用于前端按类别着色） */
    private String category;

    /** 子链类型（nodeType=CHAIN 时有效，对应 {@link RuleChainType}：THEN/WHEN/IF/ELIF/SWITCH/FOR/WHILE/BREAK） */
    private String chainType;

    /** 父节点 ID（嵌套链时使用，根节点为 null） */
    private String parentNodeId;

    /** 节点位置坐标（画布坐标系，左上角为原点） */
    private Position position;

    /** 节点尺寸（可选，前端可按默认尺寸渲染） */
    private Size size;

    /** 节点样式扩展（颜色、图标等，前端自定义） */
    private Map<String, Object> style;

    /** 业务扩展字段（如分支条件、循环变量名等，按 chainType 解释） */
    private Map<String, Object> metadata;

    /** 是否启用断点（P2-3 断点调试，前端可勾选） */
    @Builder.Default
    private boolean breakpoint = false;

    /**
     * 节点位置坐标
     *
     * @param x 横坐标（像素）
     * @param y 纵坐标（像素）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position implements Serializable {
        private static final long serialVersionUID = 1L;
        private double x;
        private double y;
    }

    /**
     * 节点尺寸
     *
     * @param width  宽度（像素）
     * @param height 高度（像素）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Size implements Serializable {
        private static final long serialVersionUID = 1L;
        private double width;
        private double height;
    }
}
