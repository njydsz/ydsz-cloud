-- ====================================================================
-- 预警阈值配置（pmis_config，group=alert）
--
--  说明：EVM / Bench / 预算 等模块的告警阈值从此处读取，
--       业务模块通过 ConfigClient Feign 调用 ydsz-pmis-config 读取。
-- ====================================================================

INSERT INTO pmis_config (config_group, config_key, config_value, value_type, description, is_public, created_by)
VALUES
    -- EVM 阈值
    ('alert', 'alert.cpi.yellow', '0.95', 'NUMBER', 'CPI 黄色预警阈值（低于即黄灯）', 0, 0),
    ('alert', 'alert.cpi.red',    '0.85', 'NUMBER', 'CPI 红色预警阈值（低于即红灯）', 0, 0),
    ('alert', 'alert.spi.yellow', '0.90', 'NUMBER', 'SPI 黄色预警阈值', 0, 0),
    ('alert', 'alert.spi.red',    '0.80', 'NUMBER', 'SPI 红色预警阈值', 0, 0),
    -- Bench 阈值
    ('alert', 'alert.bench.days.yellow', '7',  'NUMBER', 'Bench 黄色预警天数', 0, 0),
    ('alert', 'alert.bench.days.red',    '15', 'NUMBER', 'Bench 红色预警天数', 0, 0),
    ('alert', 'alert.bench.cost.ratio',  '0.08', 'NUMBER', 'Bench 成本占比预警阈值（占总人力成本）', 0, 0)
ON CONFLICT (config_group, config_key, deleted) DO UPDATE
    SET config_value = EXCLUDED.config_value,
        description   = EXCLUDED.description,
        updated_at    = CURRENT_TIMESTAMP;
