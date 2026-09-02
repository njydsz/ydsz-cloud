/**
 * 查询入参包（DDD domain 层）
 *
 * <p>存放查询参数对象（以 {@code Query} 结尾），封装查询条件。
 *
 * <p><b>命名规范：</b>
 *
 * <ul>
 *   <li>分页查询参数：{@code XxxPageQuery}，继承 {@code PageQuery}</li>
 *   <li>自定义查询参数：{@code XxxQuery}</li>
 * </ul>
 *
 * <p>当前模块使用 {@code ydsz-common-domain} 的 {@code PageQuery} 作为基类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.literule.domain.query;
