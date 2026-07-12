package com.njydsz.pmis.common.datasource;

/**
 * 动态数据源常量（P2-3 读写分离）
 *
 * <p>统一管理 baomidou dynamic-datasource 的数据源名称，避免硬编码字符串散落各处。
 * 与 {@code @DS} 注解配合使用，实现查询走从库、写入走主库的读写分离。
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>查询类方法：{@code @DS(DataSourceConstants.SLAVE)} 走从库</li>
 *   <li>写入/更新/删除方法：默认走主库（primary=master），无需添加注解</li>
 *   <li>读写混合方法（方法内既有读又有写）：不添加 {@code @DS} 注解，走主库保证一致性</li>
 * </ul>
 *
 * <h3>配置对应</h3>
 * <p>常量值需与 Nacos 配置 {@code spring.datasource.dynamic.datasource.<name>} 中的 key 一致：
 * <ul>
 *   <li>{@link #MASTER} 对应 {@code datasource.master}</li>
 *   <li>{@link #SLAVE} 对应 {@code datasource.slave}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class DataSourceConstants {

    private DataSourceConstants() {
    }

    /** 主库数据源名称：写入/更新/删除默认走此库 */
    public static final String MASTER = "master";

    /** 从库数据源名称：纯查询方法通过 @DS("slave") 走此库 */
    public static final String SLAVE = "slave";
}
