package com.njydsz.pmis.common.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;

/**
 * JDBC 异常翻译器
 *
 * <p>将数据库异常翻译为业务异常，便于统一处理。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class JdbcExceptionTranslator {

    private JdbcExceptionTranslator() {
    }

    /**
     * 翻译 SQL 异常
     *
     * @param ex SQL 异常
     * @return 业务异常
     */
    public static BizException translate(SQLException ex) {
        if (ex == null) {
            return new BizException("500", "Unknown database error");
        }

        // 唯一键冲突
        if (ex instanceof SQLIntegrityConstraintViolationException) {
            String message = ex.getMessage();
            if (message != null && message.contains("Duplicate entry")) {
                return new BizException("409", "数据已存在，请勿重复操作");
            }
            if (message != null && message.contains("foreign key")) {
                return new BizException("409", "存在关联数据，无法删除");
            }
            return new BizException("409", "数据约束冲突");
        }

        // 连接异常
        if (ex instanceof SQLRecoverableException) {
            return new BizException("503", "数据库连接异常，请稍后重试");
        }

        // 超时
        if (ex instanceof SQLTimeoutException) {
            return new BizException("504", "数据库操作超时");
        }

        // 死锁
        String message = ex.getMessage();
        if (message != null && message.toLowerCase().contains("deadlock")) {
            return new BizException("409", "操作冲突，请重试");
        }

        // 其他
        return new BizException("500", "数据库操作异常: " + ex.getSQLState());
    }

    /**
     * 翻译 DataAccessException
     *
     * @param ex DataAccessException
     * @return 业务异常
     */
    public static BizException translate(DataAccessException ex) {
        if (ex == null) {
            return new BizException("500", "Unknown data access error");
        }

        // 唯一键冲突
        if (ex instanceof DuplicateKeyException) {
            return new BizException("409", "数据已存在，请勿重复操作");
        }

        // 乐观锁
        if (ex instanceof ObjectOptimisticLockingFailureException) {
            return new BizException("409", "数据已被修改，请刷新后重试");
        }

        // 超时
        if (ex instanceof QueryTimeoutException) {
            return new BizException("504", "查询超时");
        }

        // 连接
        if (ex.getCause() instanceof SQLException) {
            return translate((SQLException) ex.getCause());
        }

        return new BizException("500", ex.getMessage());
    }
}
