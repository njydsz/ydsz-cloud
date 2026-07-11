package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.njydsz.pmis.common.security.SecurityContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 审计字段自动填充器。
 * <p>
 * 自动填充 createdAt、updatedAt、createdBy、updatedBy 字段。
 * 从 {@link SecurityContext} 获取当前操作人。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class AuditFieldFiller implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String currentUser = getCurrentUser();

        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createdBy", String.class, currentUser);
        this.strictInsertFill(metaObject, "updatedBy", String.class, currentUser);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updatedBy", String.class, getCurrentUser());
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID，未登录返回 "system"
     */
    private String getCurrentUser() {
        try {
            String userId = SecurityContext.getCurrentUserId();
            return userId != null ? userId : "system";
        } catch (Exception e) {
            return "system";
        }
    }
}
