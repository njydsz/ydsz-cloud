package com.njydsz.pmis.common.domain.query;

import static lombok.AccessLevel.PROTECTED;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 查询对象基类型
 *
 * <p>所有查询参数对象的顶层基类，提供序列化支持和通用查询字段。
 * 子类可通过 {@link SuperBuilder} 继承 Builder 能力，实现链式构建查询参数量
 *
 * <p><b>继承体系：</b>
 * <pre>
 * BaseQuery (基类)
 *     └── PageQuery (分页查询)
 * </pre>
 *
 * <p><b>通用字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>searchKey</td><td>String</td><td>模糊搜索关键字</td></tr>
 *   <tr><td>status</td><td>String</td><td>状态过滤</td></tr>
 *   <tr><td>startTime</td><td>String</td><td>开始时间（字符串格式）</td></tr>
 *   <tr><td>endTime</td><td>String</td><td>结束时间（字符串格式）</td></tr>
 *   <tr><td>startDateTime</td><td>LocalDateTime</td><td>开始时间（类型安全版本）</td></tr>
 *   <tr><td>endDateTime</td><td>LocalDateTime</td><td>结束时间（类型安全版本）</td></tr>
 *   <tr><td>tenantId</td><td>String</td><td>租户ID</td></tr>
 *   <tr><td>orderBy</td><td>String</td><td>排序字段</td></tr>
 *   <tr><td>ascending</td><td>Boolean</td><td>是否升序</td></tr>
 * </table>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 */
@Data
@SuperBuilder
@NoArgsConstructor(access = PROTECTED)
public class BaseQuery implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 模糊搜索关键字
     *
     * <p>用于对多个字段进行模糊匹配搜索。
     * 具体搜索字段由业务子类或 MyBatis XML 定义。
     */
    private String searchKey;

    /**
     * 状态过滤
     *
     * <p>用于过滤数据状态，子类可按需覆盖为具体业务状态枚举值。
     * 默认值为空，由各子类根据业务语义自行定义。
     */
    private String status;

    /**
     * 开始时间（字符串格式）
     *
     * <p>用于时间范围查询的起始时间，字符串格式。
     * 通常与 endTime 配合使用。
     *
     * @deprecated 建议使用 {@link #startDateTime}（LocalDateTime 类型）替代
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    private transient String startTime;

    /**
     * 结束时间（字符串格式）
     *
     * <p>用于时间范围查询的结束时间，字符串格式。
     * 通常与 startTime 配合使用。
     *
     * @deprecated 建议使用 {@link #endDateTime}（LocalDateTime 类型）替代
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    private transient String endTime;

    /**
     * 开始时间（类型安全版本）
     *
     * <p>用于时间范围查询的起始时间，使用 {@link LocalDateTime} 类型
     * 替代 {@link #startTime} 字符串版本，避免手动解析和格式问题。
     */
    private transient LocalDateTime startDateTime;

    /**
     * 结束时间（类型安全版本）
     *
     * <p>用于时间范围查询的结束时间，使用 {@link LocalDateTime} 类型
     * 替代 {@link #endTime} 字符串版本，避免手动解析和格式问题。
     */
    private transient LocalDateTime endDateTime;

    /**
     * 关键字（已废弃）
     *
     * <p>用于多字段搜索的关键字，与 searchKey 语义高度重叠。
     *
     * @deprecated 使用 {@link #searchKey} 替代
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    private String keyword;

    /**
     * 租户ID
     *
     * <p>用于多租户场景下的数据隔离。
     * 通常在 SaaS 应用中使用，确保不同租户的数据互不干扰。
     */
    private String tenantId;

    /**
     * 排序字段
     *
     * <p>用于指定排序字段，多个字段用逗号分隔。
     * 格式：field1 ASC, field2 DESC
     *
     * <p><b>注意：</b>直接设置此字段可能存在 SQL 注入风险。
     * 建议使用 {@link PageQuery#addOrder(String, boolean)} 方法。
     */
    private String orderBy;

    /**
     * 是否升序
     *
     * <p>配合 orderBy 使用，控制排序方向。
     * <ul>
     *   <li>true - 升序（ASC）</li>
     *   <li>false - 降序（DESC）</li>
     * </ul>
     */
    @Builder.Default
    private Boolean ascending = true;

    /**
     * 判断是否有时间范围条件
     *
     * @return 有任一时间边界返回 true
     */
    public boolean hasTimeRange() {
        return startTime != null || endTime != null
                || startDateTime != null || endDateTime != null;
    }

    /**
     * 判断是否有搜索关键字
     *
     * @return 有搜索关键字返回 true
     */
    public boolean hasSearchKey() {
        return searchKey != null && !searchKey.isBlank();
    }

    /**
     * 判断是否有状态过滤
     *
     * @return 有状态过滤返回 true
     */
    public boolean hasStatus() {
        return status != null;
    }
}
