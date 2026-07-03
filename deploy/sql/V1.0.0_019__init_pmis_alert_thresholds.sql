-- ====================================================================
-- 预警阈值配置（pmis_config，group=alert）
--
--  说明：EVM / Bench / 预算 / 毛利率 / 利用率 等模块的告警阈值从此处读取，
--       业务模块通过 ConfigClient Feign 调用 ydsz-pmis-system 读取。
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
    ('alert', 'alert.bench.cost.ratio',  '0.08', 'NUMBER', 'Bench 成本占比预警阈值（占总人力成本）', 0, 0),
    -- EVM 红色项目数
    ('alert', 'alert.evm.red.count',     '3',        'NUMBER', 'EVM 红色项目数预警阈值', 0, 0),
    -- 毛利率
    ('alert', 'alert.margin.yellow',     '0.10',     'NUMBER', '毛利率黄色预警阈值', 0, 0),
    ('alert', 'alert.margin.red',        '0.05',     'NUMBER', '毛利率红色预警阈值', 0, 0),
    -- Bench 闲置成本
    ('alert', 'alert.bench.yellow.cost', '500000',   'NUMBER', 'Bench 闲置成本黄色预警阈值（元）', 0, 0),
    ('alert', 'alert.bench.red.cost',    '1000000',  'NUMBER', 'Bench 闲置成本红色预警阈值（元）', 0, 0),
    -- 可计费利用率
    ('alert', 'alert.utilization.yellow', '0.70',    'NUMBER', '可计费利用率黄色预警阈值', 0, 0),
    ('alert', 'alert.utilization.red',    '0.50',    'NUMBER', '可计费利用率红色预警阈值', 0, 0),
    -- 预算使用率
    ('alert', 'alert.budget.yellow',     '0.80',     'NUMBER', '预算使用率黄色预警阈值', 0, 0),
    ('alert', 'alert.budget.red',        '0.95',     'NUMBER', '预算使用率红色预警阈值', 0, 0)
ON CONFLICT (config_group, config_key, deleted) DO UPDATE
    SET config_value = EXCLUDED.config_value,
        description   = EXCLUDED.description,
        updated_at    = CURRENT_TIMESTAMP;
