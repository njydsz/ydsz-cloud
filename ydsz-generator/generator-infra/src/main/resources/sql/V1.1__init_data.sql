-- =====================================================
-- ydzs-generator 数据初始化脚本
-- 创建时间：2026-09-05
-- 说明：初始化默认模板分组（实际模板内容由 DataInitializer 从 classpath 加载）
-- =====================================================

-- 默认 DDD 模板分组
INSERT INTO gen_template_group (name, description, is_system, sort_order, is_active)
VALUES ('default', '标准 DDD 分层模板（entity/service/controller/repository...）', 1, 1, 1)
ON DUPLICATE KEY UPDATE is_system = 1;

-- Mybatis-Plus 模板分组（预留）
INSERT INTO gen_template_group (name, description, is_system, sort_order, is_active)
VALUES ('mybatis-plus', 'Mybatis-Plus 增强版模板（含 Wrapper/通用 Service）', 1, 2, 0)
ON DUPLICATE KEY UPDATE is_system = 1;

-- 示例数据源（仅开发环境使用，生产环境请通过管理接口配置）
INSERT INTO gen_datasource (name, jdbc_url, username, password, dialect, is_default, description)
VALUES ('local-mysql', 'jdbc:mysql://localhost:3306/ydsz_cloud?useUnicode=true&characterEncoding=utf8', 'root', 'ENC(encrypted_password_here)', 'MYSQL', 1, '本地开发数据库')
ON DUPLICATE KEY UPDATE is_default = 1;
