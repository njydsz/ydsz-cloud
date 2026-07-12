package com.njydsz.pmis.common.jdbc.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.njydsz.pmis.common.util.auth.AuthInfoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 元对象处理器
 *
 * <p>实现 MyBatis-Plus 的 {@link MetaObjectHandler} 接口，
 * 用于自动填充实体类审计字段（创建人、创建时间、更新人、更新时间）。</p>
 *
 * <h2>功能说明</h2>
 * <ul>
 *   <li>INSERT 操作：自动填充 createdBy、createdAt、updatedBy、updatedAt</li>
 *   <li>UPDATE 操作：自动填充 updatedBy、updatedAt</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <p>当使用 MyBatis-Plus 的实体类进行数据库操作时，
 * 该处理器会自动为标记了 @TableField 注解的审计字段填充值。</p>
 *
 * <h2>配置要求</h2>
 * <pre>
 * // 实体类字段配置
 * {@code @TableField(fill = FieldFill.INSERT)}
 * private String createdBy;
 *
 * {@code @TableField(fill = FieldFill.INSERT)}
 * private LocalDateTime createdAt;
 *
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)}
 * private String updatedBy;
 *
 * {@code @TableField(fill = FieldFill.INSERT_UPDATE)}
 * private LocalDateTime updatedAt;
 * </pre>
 *
 * <h2>与 FieldFillInterceptor 的区别</h2>
 * <ul>
 *   <li>MyMetaObjectHandler：基于 MyBatis-Plus 实体类填充，适合使用实体类进行 CRUD 的场景</li>
 *   <li>FieldFillInterceptor：基于 SQL 拦截器填充，适合使用 Map/DTO 进行更新的场景</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see MetaObjectHandler
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * INSERT 操作时的字段填充
     *
     * @param metaObject MyBatis 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始 INSERT 字段填充...");
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "createdBy", String.class, AuthInfoUtils.getUniqueId());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedBy", String.class, AuthInfoUtils.getUniqueId());
    }

    /**
     * UPDATE 操作时的字段填充
     *
     * @param metaObject MyBatis 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始 UPDATE 字段填充...");
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updatedBy", String.class, AuthInfoUtils.getUniqueId());
    }
}