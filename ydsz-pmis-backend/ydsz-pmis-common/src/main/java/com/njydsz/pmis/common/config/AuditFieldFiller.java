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

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();

        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createdBy", Long.class, userId);
        strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = currentUserId();
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
    }

    private Long currentUserId() {
        try {
            return SecurityContext.getUserId();
        } catch (Exception e) {
            return 0L;
        }
    }
}
