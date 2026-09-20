package com.njydsz.common.seata.validator;

import com.njydsz.common.exception.BusinessException;
import com.njydsz.common.exception.code.CoreErrorCode;
import com.njydsz.common.seata.config.SeataProperties;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * undo_log 表 Schema 启动期校验器。
 *
 * <p>AT 模式下，Seata 依赖 undo_log 表记录分支事务的前后镜像，用于反向补偿。
 * 本校验器在应用启动时检查 undo_log 表是否存在（Metadata 级校验），
 * 表不存在时根据配置决定是抛出异常（阻断启动）还是记录 WARN（仅警告）。
 *
 * <p><b>启用方式：</b>
 * <pre>{@code
 * ydsz.seata.undo-log.validate-schema=true   # 默认 false（能力储备阶段不强制）
 * ydsz.seata.undo-log.table-name=undo_log     # 默认 undo_log
 * }</pre>
 *
 * <p><b>设计状态：</b>能力储备 — 建议生产环境启用此校验（validateSchema=true），
 * 开发/测试环境可关闭以减少启动依赖。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public class UndoLogSchemaValidator {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(UndoLogSchemaValidator.class);

    /** Seata 配置属性 */
    private final SeataProperties properties;

    /** 应用主数据源 */
    private final DataSource dataSource;

    /**
     * 构造校验器。
     *
     * @param properties Seata 配置属性
     * @param dataSource 主数据源（用于查询 Metadata）
     */
    public UndoLogSchemaValidator(SeataProperties properties, DataSource dataSource) {
        this.properties = properties;
        this.dataSource = dataSource;
    }

    /**
     * 执行 undo_log 表存在性校验。
     *
     * <p>仅当 {@code undo-log.validate-schema=true} 时执行校验。
     * 表不存在时抛出 {@link BusinessException} 阻断启动（Fail Fast 策略）。
     */
    @PostConstruct
    public void validate() {
        if (!properties.isEnabled()) {
            return;
        }

        if (!properties.getUndoLog().isValidateSchema()) {
            LOG.debug("undo_log schema validation skipped (validateSchema=false)");
            return;
        }

        String tableName = properties.getUndoLog().getTableName();
        LOG.info("Validating undo_log table schema: {}", tableName);

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            // 尝试多种表名格式（考虑大小写敏感）
            if (tableExists(metaData, catalog, schema, tableName)
                || tableExists(metaData, catalog, schema, tableName.toUpperCase())
                || tableExists(metaData, catalog, schema, tableName.toLowerCase())) {
                LOG.info("undo_log table validated successfully: {}", tableName);
            } else {
                String message = String.format(
                    "undo_log table '%s' not found in database. "
                    + "Please run SQL migration script V26.09.14__seata_undo_log_for_poc.sql "
                    + "or set ydzs.seata.undo-log.validate-schema=false to skip.",
                    tableName);
                LOG.error(message);
                throw BusinessException.of(
                    CoreErrorCode.DATABASE_ERROR,
                    message);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Failed to validate undo_log schema: {}", e.getMessage());
            // 校验异常不阻断启动（网络抖动、临时不可用时降级）
        }
    }

    /**
     * 检查指定表名是否存在。
     *
     * @param metaData 数据库元数据
     * @param catalog 目录（可为 null）
     * @param schema Schema（可为 null）
     * @param tableName 表名
     * @return true = 表存在
     */
    private boolean tableExists(DatabaseMetaData metaData, String catalog,
                               String schema, String tableName) {
        try (ResultSet tables = metaData.getTables(catalog, schema, tableName,
            new String[]{"TABLE"})) {
            return tables.next();
        } catch (Exception e) {
            LOG.debug("Error checking table existence for {}: {}", tableName, e.getMessage());
            return false;
        }
    }
}
