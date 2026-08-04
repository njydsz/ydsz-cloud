package com.njydsz.workflow.domain.vo;

/**
 * 流程定义视图层级（P2-1: 配合 @JsonView 实现静态视图过滤）。
 *
 * <p>使用方式：
 * <pre>
 * // 列表接口 — 仅返回 Summary 字段（id, flowCode, flowName, category, version, status）
 * String json = YdszJson.toJson(def, FlowViews.Summary.class);
 *
 * // 详情接口 — 返回 Summary + Detail 字段（含 ext, listener, canary 等）
 * String json = YdszJson.toJson(def, FlowViews.Detail.class);
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class FlowViews {

    private FlowViews() {
    }

    /**
     * 列表视图：仅包含列表页需要的核心字段。
     */
    public interface Summary {
    }

    /**
     * 详情视图：继承 Summary，额外包含扩展字段、监听器配置、灰度配置等。
     */
    public interface Detail extends Summary {
    }
}
