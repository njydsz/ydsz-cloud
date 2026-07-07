package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.njydsz.pmis.common.security.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 审计字段自动填充
 *
 * <p>INSERT 时填充 createdBy/createdAt/updatedBy/updatedAt；
 * UPDATE 时填充 updatedBy/updatedAt。
 *
 * <p>当前线程未登录用户时，createdBy/updatedBy 默认为 0。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class AuditFieldFiller implements MetaObjectHandler {

    /**
     * INSERT 时自动填充审计字段
     *
     * @param metaObject MyBatis-Plus 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String userId = currentUserId();

        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createdBy", String.class, userId);
        strictInsertFill(metaObject, "updatedBy", String.class, userId);
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    /**
     * UPDATE 时自动填充审计字段
     *
     * @param metaObject MyBatis-Plus 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        String userId = currentUserId();
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updatedBy", String.class, userId);
    }

    private String currentUserId() {
        try {
            String uid = SecurityContext.getUserId();
            return uid == null || uid.isEmpty() ? "0" : uid;
        } catch (Exception e) {
            log.debug("[AuditFieldFiller] 当前线程无登录用户，审计字段使用默认值 0: {}", e.getMessage());
            return "0";
        }
    }
}
