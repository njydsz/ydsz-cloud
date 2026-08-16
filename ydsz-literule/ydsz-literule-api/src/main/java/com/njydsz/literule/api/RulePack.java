package com.njydsz.literule.api;

import java.io.Serializable;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

/**
 * 规则集（RulePack）元数据
 *
 * <p>将一组相关规则打包发布到规则集市场（Rule Pack Market）。
 * 与单个 {@link RuleDefinition} 不同，{@code RulePack} 关注：
 * <ul>
 *   <li><b>聚合</b>：将多条规则按业务场景打包，用户可一键导入整包</li>
 *   <li><b>版本</b>：规则集本身有独立版本号，支持升级与回滚</li>
 *   <li><b>市场属性</b>：作者、下载量、评分等市场检索维度</li>
 *   <li><b>行业适用性</b>：标注适用行业，便于按行业筛选</li>
 * </ul>
 *
 * <p>典型场景：
 * <ul>
 *   <li>新项目快速初始化（导入"金融行业风险预警规则集"）</li>
 *   <li>跨租户共享最佳实践（运营团队发布官方规则集）</li>
 *   <li>版本化升级（从 v1.0 升级到 v2.0，回滚到 v1.0）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class RulePack implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则集编码（唯一） */
    @NotBlank(message = "规则集编码不能为空")
    private String packCode;

    /** 规则集名称 */
    @NotBlank(message = "规则集名称不能为空")
    private String packName;

    /** 规则集版本号（语义化版本，如 1.0.0） */
    @NotBlank(message = "规则集版本号不能为空")
    private String packVersion;

    /** 规则集描述 */
    private String description;

    /** 适用行业编码 */
    private String industry;

    /** 标签列表（用于市场筛选与检索） */
    private List<String> tags;

    /** 包含的规则编码列表（引用 RuleDefinition.code） */
    private List<String> ruleCodes;

    /**
     * 规则定义快照列表（P2-8 知识包版本管理）
     *
     * <p>发布版本时固化的规则定义 JSON 列表，用于版本内容复现与回滚。
     * 格式为 {@code List<RuleDefinition>} 反序列化后的对象。
     */
    private List<RuleDefinition> ruleSnapshots;

    /** 升级来源版本号（便于审计链路追踪） */
    private String previousVersion;

    /** 作者（发布方） */
    private String author;

    /** 下载次数（市场热度排序依据） */
    private long downloadCount;

    /** 评分（0.0 ~ 5.0，市场质量排序依据） */
    private double rating;
}
