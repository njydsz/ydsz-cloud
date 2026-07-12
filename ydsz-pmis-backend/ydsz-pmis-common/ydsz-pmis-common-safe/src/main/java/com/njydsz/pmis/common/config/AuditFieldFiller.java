package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 审计字段自动填充器。
 *
 * <p>自动填充 createdAt、updatedAt、createdBy、updatedBy 字段。
 * 从请求头 X-User-Id 获取当前操作人，未登录返回 "system"。
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class AuditFieldFiller implements MetaObjectHandler {

    private static final String USER_ID_HEADER = "X-User-Id";

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
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String userId = req.getHeader(USER_ID_HEADER);
                return userId != null ? userId : "system";
            }
        } catch (Exception ignored) {
            // 非 Web 上下文
        }
        return "system";
    }
}
