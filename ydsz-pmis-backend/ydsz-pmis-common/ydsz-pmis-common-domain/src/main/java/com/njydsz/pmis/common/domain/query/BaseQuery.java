package com.njydsz.pmis.common.domain.query;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

/**
 * 查询对象基类。
 *
 * <p>所有查询参数对象的顶层基类，提供序列化支持和通用查询字段。
 * 子类可通过 {@link SuperBuilder} 继承 Builder 能力，实现链式构建查询参数。
 *
 * <p><b>继承体系：</b>
 * <pre>
 * BaseQuery (基类)
 *     ├── PageQuery (分页查询)
 *     └── DateRangeQuery (时间范围查询)
 * </pre>
 *
 * <p><b>通用字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>searchKey</td><td>String</td><td>模糊搜索关键词</td></tr>
 *   <tr><td>status</td><td>Integer</td><td>状态过滤（0-禁用，1-启用）</td></tr>
 *   <tr><td>startTime</td><td>LocalDateTime</td><td>开始时间</td></tr>
 *   <tr><td>endTime</td><td>LocalDateTime</td><td>结束时间</td></tr>
 *   <tr><td>keyword</td><td>String</td><td>关键字（用于多字段搜索）</td></tr>
 * </table>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
     * 模糊搜索关键词
     *
     * <p>用于对多个字段进行模糊匹配搜索。
     * 具体搜索字段由业务子类或 MyBatis XML 定义。
     */
    private String searchKey;

    /**
     * 状态过滤
     *
     * <p>用于过滤数据状态：
     * <ul>
     *   <li>null - 不过滤状态</li>
     *   <li>0 - 禁用/停用</li>
     *   <li>1 - 启用/正常</li>
     *   <li>其他值 - 业务自定义状态</li>
     * </ul>
     */
    private Integer status;

    /**
     * 开始时间
     *
     * <p>用于时间范围查询的起始时间。
     * 通常与 endTime 配合使用，查询 [startTime, endTime] 区间的数据。
     */
    private transient LocalDateTime startTime;

    /**
     * 结束时间
     *
     * <p>用于时间范围查询的结束时间。
     * 通常与 startTime 配合使用，查询 [startTime, endTime] 区间的数据。
     */
    private transient LocalDateTime endTime;

    /**
     * 关键字
     *
     * <p>用于多字段搜索的关键字，与 searchKey 类似但语义更明确。
     * 适用于需要区分"模糊搜索"和"精确搜索"的场景。
     */
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
     * <p><b>注意：</b>直接设置此字段可能存在 SQL 注入风险，
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
        return startTime != null || endTime != null;
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
