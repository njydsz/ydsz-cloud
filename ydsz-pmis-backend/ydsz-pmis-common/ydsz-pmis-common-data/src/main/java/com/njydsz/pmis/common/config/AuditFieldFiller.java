package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.njydsz.pmis.common.constant.SystemConstants;
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
 * <p>当前线程未登录用户时，createdBy/updatedBy 默认为
 * {@link SystemConstants#SYSTEM_USER_ID}。
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

    /**
     * 获取当前线程登录用户 ID，未登录或异常时回退到系统占位值
     *
     * @return 当前用户 ID 或 {@link SystemConstants#SYSTEM_USER_ID}
     */
    private String currentUserId() {
        try {
            String uid = SecurityContext.getUserId();
            return uid == null || uid.isEmpty() ? SystemConstants.SYSTEM_USER_ID : uid;
        } catch (Exception e) {
            log.debug("[AuditFieldFiller] 当前线程无登录用户，审计字段使用系统占位值 {}: {}",
                    SystemConstants.SYSTEM_USER_ID, e.getMessage());
            return SystemConstants.SYSTEM_USER_ID;
        }
    }
}
