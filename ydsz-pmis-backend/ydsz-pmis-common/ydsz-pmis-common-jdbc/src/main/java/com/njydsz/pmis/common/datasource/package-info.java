/**
 * 动态数据源层（读写分离）。
 *
 * <p>封装 baomidou dynamic-datasource 的数据源名称常量。业务模块通过
 * {@code @DS(DataSourceConstants.SLAVE)} 显式声明走从库的方法（纯查询场景），
 * 其余方法默认走主库（{@code master}）。
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>查询类方法：{@code @DS(DataSourceConstants.SLAVE)}</li>
 *   <li>写入 / 更新 / 删除：默认走主库，无需添加注解</li>
 *   <li>读写混合方法：不加 {@code @DS}，走主库保证一致性</li>
 *   <li>事务方法：必须在 {@code @Transactional} 的主入口加 {@code @DS(MASTER)}，防止从库只读失败</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.datasource;
