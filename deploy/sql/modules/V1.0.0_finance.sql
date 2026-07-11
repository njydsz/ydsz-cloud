-- ============================================================
-- PMIS finance module SQL
-- 璐㈠姟浼氳鏈嶅姟 (ydsz-pmis-finance, port 9011)
-- ============================================================
-- 鏈剼鏈?DDL 瀵瑰簲鍚庣 finance 鏈嶅姟鐨?Mapper / DO,
--   鐗╃悊 Mapper 瀹為檯鎵€鍦ㄦā鍧楀嵆琛ㄥ綊灞炪€傝法鏈嶅姟寮曠敤绂佹鐩磋繛,缁熶竴璧?
--   Feign Client (FinanceDataClient / SalesDataClient)銆?
--
-- 琛ㄥ綊灞炰緷鎹? ydsz-pmis-finance/src/main/java/.../infra/mapper/
-- 琛ㄦ暟閲? 8 寮?
-- --------------------------------------------------------------------

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_cost_expense(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    expense_code        VARCHAR(64)    NOT NULL,
    initiation_id       VARCHAR(20),
    employee_id         VARCHAR(20)         NOT NULL,
    employee_name       VARCHAR(64),
    expense_type        VARCHAR(32)    NOT NULL,
    amount              NUMERIC(18,2)  NOT NULL DEFAULT 0,
    expense_date        DATE           NOT NULL,
    description         TEXT,
    receipt_url         VARCHAR(512),
    status              VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    approver_id         VARCHAR(20),
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMPTZ,
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 鏁版嵁瀹屾暣鎬х害鏉?
    CONSTRAINT uk_pce_code           UNIQUE (expense_code, deleted),
    CONSTRAINT ck_pce_type_enum      CHECK (expense_type IN ('TRAVEL', 'CATERING', 'MEETING', 'SUPPLIES', 'COMMUNICATION', 'OTHER')),
    CONSTRAINT ck_pce_status_enum    CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID')),
    CONSTRAINT ck_pce_amount_nonneg  CHECK (amount >= 0),
    CONSTRAINT ck_pce_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_pce_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_cost_expense IS '璐圭敤鎶ラ攢琛? 宸梾/鍥㈠缓/浼氳/鍔炲叕绛夎垂鐢ㄦ姤閿€,鍙叧鑱旈」鐩?褰卞搷椤圭洰棰勭畻)';

COMMENT ON COLUMN pmis_cost_expense.id IS '涓婚敭 ID';

COMMENT ON COLUMN pmis_cost_expense.expense_code IS '鎶ラ攢鍗曠紪鐮?鍏ㄥ眬鍞竴)';

COMMENT ON COLUMN pmis_cost_expense.initiation_id IS '鍏宠仈绔嬮」 ID(椤圭洰绾ц垂鐢ㄥ繀濉?鍏徃鍏叡璐圭敤鍙┖)';

COMMENT ON COLUMN pmis_cost_expense.employee_id IS '鎶ラ攢浜?ID';

COMMENT ON COLUMN pmis_cost_expense.employee_name IS '鎶ラ攢浜哄鍚?;

COMMENT ON COLUMN pmis_cost_expense.expense_type IS '璐圭敤绫诲瀷: TRAVEL 宸梾 / CATERING 椁愰ギ / MEETING 浼氳 / SUPPLIES 鍔炲叕 / COMMUNICATION 閫氳 / OTHER 鍏朵粬';

COMMENT ON COLUMN pmis_cost_expense.amount IS '鎶ラ攢閲戦(鍏?';

COMMENT ON COLUMN pmis_cost_expense.expense_date IS '璐圭敤鍙戠敓鏃ユ湡';

COMMENT ON COLUMN pmis_cost_expense.description IS '璐圭敤璇存槑';

COMMENT ON COLUMN pmis_cost_expense.receipt_url IS '鍙戠エ/鍑瘉 URL';

COMMENT ON COLUMN pmis_cost_expense.status IS '瀹℃壒鐘舵€? DRAFT 鑽夌 / SUBMITTED 宸叉彁浜?/ APPROVED 宸叉壒鍑?/ REJECTED 宸查┏鍥?/ PAID 宸叉墦娆?;

COMMENT ON COLUMN pmis_cost_expense.approver_id IS '瀹℃壒浜?ID';

COMMENT ON COLUMN pmis_cost_expense.approver_name IS '瀹℃壒浜哄鍚?;

COMMENT ON COLUMN pmis_cost_expense.approved_at IS '瀹℃壒鏃堕棿';

COMMENT ON COLUMN pmis_cost_expense.provider_trace_id IS '閾捐矾杩借釜 ID';

COMMENT ON COLUMN pmis_cost_expense.created_by IS '鍒涘缓浜?ID';

COMMENT ON COLUMN pmis_cost_expense.created_at IS '鍒涘缓鏃堕棿';

COMMENT ON COLUMN pmis_cost_expense.updated_by IS '鏈€鍚庝慨鏀逛汉 ID';

COMMENT ON COLUMN pmis_cost_expense.updated_at IS '鏈€鍚庝慨鏀规椂闂?;

COMMENT ON COLUMN pmis_cost_expense.deleted IS '閫昏緫鍒犻櫎鏍囪: 0 鏈垹闄?/ 1 宸插垹闄?;

COMMENT ON COLUMN pmis_cost_expense.tenant_id IS '绉熸埛 ID(鍗曠鎴烽儴缃查粯璁?1)';

COMMENT ON COLUMN pmis_cost_expense.version IS '涔愯閿佺増鏈彿';

CREATE INDEX IF NOT EXISTS idx_pce_initiation
    ON pmis_cost_expense (initiation_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pce_employee
    ON pmis_cost_expense (employee_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pce_status
    ON pmis_cost_expense (status) WHERE deleted = 0;

-- [INLINE-OPT] 鍛樺伐 + 鐘舵€?鍛樺伐鎶ラ攢鍙拌处)
CREATE INDEX IF NOT EXISTS idx_pce_employee_status
    ON pmis_cost_expense (employee_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 璐圭敤鏃ユ湡(鎶ラ攢涓績鏃堕棿绛涢€?
CREATE INDEX IF NOT EXISTS idx_pce_tenant_date
    ON pmis_cost_expense (tenant_id, expense_date DESC) WHERE deleted = 0;

-- [INLINE-OPT] 閾捐矾杩借釜 ID 绱㈠紩
CREATE INDEX IF NOT EXISTS idx_pce_trace
    ON pmis_cost_expense (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 6. 鏀跺叆纭琛?pmis_profit_revenue

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_profit_revenue(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    contract_id         VARCHAR(20)         NOT NULL,
    initiation_id       VARCHAR(20)         NOT NULL,
    revenue_code        VARCHAR(64)    NOT NULL,
    recognition_method  VARCHAR(32)    NOT NULL,
    period              VARCHAR(7)     NOT NULL,
    amount              NUMERIC(18,2)  NOT NULL DEFAULT 0,
    recognition_date    DATE           NOT NULL,
    milestone           VARCHAR(128),
    percent_complete    NUMERIC(5,2),
    invoice_id          VARCHAR(20),
    status              VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    confirmed_by        VARCHAR(20),
    confirmed_at        TIMESTAMPTZ,
    description         TEXT,
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 鏁版嵁瀹屾暣鎬х害鏉?
    CONSTRAINT uk_ppr_code              UNIQUE (revenue_code, deleted),
    CONSTRAINT ck_ppr_method_enum       CHECK (recognition_method IN ('MILESTONE', 'PERCENTAGE', 'PERCENT_COMPLETE', 'POINTS', 'MANUAL')),
    CONSTRAINT ck_ppr_status_enum       CHECK (status IN ('DRAFT', 'CONFIRMED', 'REVERSED')),
    CONSTRAINT ck_ppr_amount_nonneg     CHECK (amount >= 0),
    CONSTRAINT ck_ppr_pct_range         CHECK (percent_complete IS NULL OR (percent_complete >= 0 AND percent_complete <= 100)),
    CONSTRAINT ck_ppr_version_nonneg    CHECK (version >= 0),
    CONSTRAINT ck_ppr_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_profit_revenue IS '鏀跺叆纭琛? 鎸夐噷绋嬬/鐧惧垎姣?瀹屽伐娉?鎵嬪姩娉曠瓑澶氱淮搴︾‘璁ら」鐩敹鍏?;

COMMENT ON COLUMN pmis_profit_revenue.id IS '涓婚敭 ID';

COMMENT ON COLUMN pmis_profit_revenue.contract_id IS '鍚堝悓 ID(鍏宠仈 pmis_project_contract.id)';

COMMENT ON COLUMN pmis_profit_revenue.initiation_id IS '绔嬮」 ID(鍏宠仈 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_profit_revenue.revenue_code IS '鏀跺叆纭鍗曠紪鐮?鍏ㄥ眬鍞竴)';

COMMENT ON COLUMN pmis_profit_revenue.recognition_method IS '纭鏂规硶: MILESTONE 閲岀▼纰戞硶 / PERCENTAGE 姣斾緥娉?/ PERCENT_COMPLETE 瀹屽伐娉?/ POINTS 宸ュ垎娉?/ MANUAL 鎵嬪姩';

COMMENT ON COLUMN pmis_profit_revenue.period IS '鎵€灞炴湡闂?YYYY-MM)';

COMMENT ON COLUMN pmis_profit_revenue.amount IS '纭閲戦(鍏?';

COMMENT ON COLUMN pmis_profit_revenue.recognition_date IS '纭鏃ユ湡';

COMMENT ON COLUMN pmis_profit_revenue.milestone IS '閲岀▼纰戞弿杩?;

COMMENT ON COLUMN pmis_profit_revenue.percent_complete IS '瀹屽伐鐧惧垎姣?0-100,瀹屽伐娉?';

COMMENT ON COLUMN pmis_profit_revenue.invoice_id IS '鍏宠仈寮€绁ㄧ敵璇?ID';

COMMENT ON COLUMN pmis_profit_revenue.status IS '鐘舵€? DRAFT 鑽夌 / CONFIRMED 宸茬‘璁?/ REVERSED 宸插啿閿€';

COMMENT ON COLUMN pmis_profit_revenue.confirmed_by IS '纭浜?ID';

COMMENT ON COLUMN pmis_profit_revenue.confirmed_at IS '纭鏃堕棿';

COMMENT ON COLUMN pmis_profit_revenue.description IS '鏀跺叆纭璇存槑';

COMMENT ON COLUMN pmis_profit_revenue.provider_trace_id IS '閾捐矾杩借釜 ID';

COMMENT ON COLUMN pmis_profit_revenue.created_by IS '鍒涘缓浜?ID';

COMMENT ON COLUMN pmis_profit_revenue.created_at IS '鍒涘缓鏃堕棿';

COMMENT ON COLUMN pmis_profit_revenue.updated_by IS '鏈€鍚庝慨鏀逛汉 ID';

COMMENT ON COLUMN pmis_profit_revenue.updated_at IS '鏈€鍚庝慨鏀规椂闂?;

COMMENT ON COLUMN pmis_profit_revenue.deleted IS '閫昏緫鍒犻櫎鏍囪: 0 鏈垹闄?/ 1 宸插垹闄?;

COMMENT ON COLUMN pmis_profit_revenue.tenant_id IS '绉熸埛 ID(鍗曠鎴烽儴缃查粯璁?1)';

COMMENT ON COLUMN pmis_profit_revenue.version IS '涔愯閿佺増鏈彿';

CREATE INDEX IF NOT EXISTS idx_ppr_contract
    ON pmis_profit_revenue (contract_id) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绔嬮」 + 鏈熼棿(椤圭洰鏈堝害鏀跺叆璧板娍)
CREATE INDEX IF NOT EXISTS idx_ppr_initiation
    ON pmis_profit_revenue (initiation_id, period) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppr_status
    ON pmis_profit_revenue (status) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 鏈熼棿(鍏ㄥ叕鍙告敹鍏ユ湀鎶?
CREATE INDEX IF NOT EXISTS idx_ppr_tenant_period
    ON pmis_profit_revenue (tenant_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 鍏宠仈寮€绁ㄧ敵璇?ID
CREATE INDEX IF NOT EXISTS idx_ppr_invoice
    ON pmis_profit_revenue (invoice_id) WHERE deleted = 0 AND invoice_id IS NOT NULL;

-- [INLINE-OPT] 閾捐矾杩借釜 ID 绱㈠紩
CREATE INDEX IF NOT EXISTS idx_ppr_trace
    ON pmis_profit_revenue (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 7. 椤圭洰鍒╂鼎蹇収琛?pmis_profit_snapshot

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_profit_snapshot(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    initiation_id       VARCHAR(20)         NOT NULL,
    period              VARCHAR(7)     NOT NULL,
    contract_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    recognized_revenue  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    billed_amount       NUMERIC(18,2)  NOT NULL DEFAULT 0,
    received_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    labor_cost          NUMERIC(18,2)  NOT NULL DEFAULT 0,
    purchase_cost       NUMERIC(18,2)  NOT NULL DEFAULT 0,
    expense_cost        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    outsource_cost      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    allocation_cost     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    total_cost          NUMERIC(18,2)  NOT NULL DEFAULT 0,
    gross_profit        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    gross_margin        NUMERIC(5,4)   NOT NULL DEFAULT 0,
    progress_pct        NUMERIC(5,2)   NOT NULL DEFAULT 0,
    billable_hours      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    non_billable_hours  NUMERIC(10,2)  NOT NULL DEFAULT 0,
    snapshot_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 鏁版嵁瀹屾暣鎬х害鏉?
    CONSTRAINT uk_pps_init_period     UNIQUE (initiation_id, period, deleted),
    CONSTRAINT ck_pps_amount_nonneg   CHECK (contract_amount >= 0 AND recognized_revenue >= 0 AND billed_amount >= 0
                                              AND received_amount >= 0 AND labor_cost >= 0 AND purchase_cost >= 0
                                              AND expense_cost >= 0 AND outsource_cost >= 0 AND allocation_cost >= 0
                                              AND total_cost >= 0 AND gross_profit >= 0),
    CONSTRAINT ck_pps_margin_range    CHECK (gross_margin >= 0 AND gross_margin <= 1),
    CONSTRAINT ck_pps_progress_range  CHECK (progress_pct >= 0 AND progress_pct <= 100),
    CONSTRAINT ck_pps_hours_nonneg    CHECK (billable_hours >= 0 AND non_billable_hours >= 0),
    CONSTRAINT ck_pps_version_nonneg  CHECK (version >= 0),
    CONSTRAINT ck_pps_deleted_enum    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_profit_snapshot IS '椤圭洰鍒╂鼎蹇収(鎸夋湀): 绔嬮」 脳 鏈熼棿 鍞竴绾︽潫,鍛ㄦ湡鎬ф粴鍔ㄧ敓鎴?;

COMMENT ON COLUMN pmis_profit_snapshot.id IS '涓婚敭 ID';

COMMENT ON COLUMN pmis_profit_snapshot.initiation_id IS '绔嬮」 ID(鍏宠仈 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_profit_snapshot.period IS '蹇収鍛ㄦ湡(YYYY-MM)';

COMMENT ON COLUMN pmis_profit_snapshot.contract_amount IS '鍚堝悓鎬婚(鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.recognized_revenue IS '宸茬‘璁ゆ敹鍏?鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.billed_amount IS '宸插紑绁ㄩ噾棰?鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.received_amount IS '宸插洖娆鹃噾棰?鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.labor_cost IS '浜哄姏鎴愭湰(鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.purchase_cost IS '閲囪喘鎴愭湰(鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.expense_cost IS '璐圭敤(鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.outsource_cost IS '澶栧寘(鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.allocation_cost IS '鍒嗘憡璐圭敤(鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.total_cost IS '鎬绘垚鏈?鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.gross_profit IS '姣涘埄(鍏?';

COMMENT ON COLUMN pmis_profit_snapshot.gross_margin IS '姣涘埄鐜?0.0000-1.0000';

COMMENT ON COLUMN pmis_profit_snapshot.progress_pct IS '瀹屽伐杩涘害(0-100)';

COMMENT ON COLUMN pmis_profit_snapshot.billable_hours IS '鍙璐瑰伐鏃?灏忔椂)';

COMMENT ON COLUMN pmis_profit_snapshot.non_billable_hours IS '涓嶅彲璁¤垂宸ユ椂(灏忔椂)';

COMMENT ON COLUMN pmis_profit_snapshot.snapshot_at IS '蹇収鐢熸垚鏃堕棿';

COMMENT ON COLUMN pmis_profit_snapshot.provider_trace_id IS '閾捐矾杩借釜 ID';

COMMENT ON COLUMN pmis_profit_snapshot.deleted IS '閫昏緫鍒犻櫎鏍囪: 0 鏈垹闄?/ 1 宸插垹闄?;

COMMENT ON COLUMN pmis_profit_snapshot.tenant_id IS '绉熸埛 ID(鍗曠鎴烽儴缃查粯璁?1)';

COMMENT ON COLUMN pmis_profit_snapshot.version IS '涔愯閿佺増鏈彿';

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绔嬮」 + 鏈熼棿(椤圭洰鍒╂鼎璧板娍)
CREATE INDEX IF NOT EXISTS idx_pps_initiation
    ON pmis_profit_snapshot (initiation_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 鏈熼棿(鍏ㄥ叕鍙告湀搴﹀埄娑﹂┚椹惰埍)
CREATE INDEX IF NOT EXISTS idx_pps_tenant_period
    ON pmis_profit_snapshot (tenant_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 閾捐矾杩借釜 ID 绱㈠紩
CREATE INDEX IF NOT EXISTS idx_pps_trace
    ON pmis_profit_snapshot (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 8. 椤圭洰椋庨櫓鐧昏琛?pmis_execution_risk

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_finance_invoice(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    invoice_no          VARCHAR(64),                              -- 璐㈠姟鍙戠エ鍙?
    invoice_code        VARCHAR(64)  NOT NULL,                    -- 涓氬姟缂栧彿锛堢郴缁熺敓鎴愶級
    invoice_type        VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',   -- NORMAL/RED_REVERSE
    contract_id         VARCHAR(20)       NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
    customer_id         VARCHAR(20)       NOT NULL,
    customer_name       VARCHAR(256),
    invoice_basis       VARCHAR(32)  NOT NULL,                    -- MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER
    amount              NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 鍚◣閲戦
    tax_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    net_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 涓嶅惈绋庨噾棰?
    tax_rate            NUMERIC(5,4) NOT NULL DEFAULT 0.06,
    currency            VARCHAR(16)  NOT NULL DEFAULT 'CNY',
    invoice_date        DATE,
    tax_period          VARCHAR(16),                              -- YYYY-MM
    title               VARCHAR(256),                             -- 鍙戠エ鎶ご
    tax_no              VARCHAR(64),                              -- 绾崇◣浜鸿瘑鍒彿
    bank_info           VARCHAR(256),                             -- 寮€鎴疯+璐﹀彿
    address             VARCHAR(256),
    phone               VARCHAR(64),
    remark              TEXT,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',     -- InvoiceStatus
    reversed_by_id      VARCHAR(20),                                   -- 琚孩鍐茬殑鍙戠エID
    attachment_id       VARCHAR(64),                              -- 鍙戠エ鎵弿浠?
    approval_comment    TEXT,
    applied_by          VARCHAR(20),
    approved_at         TIMESTAMPTZ,
    approved_by         VARCHAR(20),
    issued_at           TIMESTAMPTZ,
    issued_by           VARCHAR(20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfi_code              UNIQUE (invoice_code, deleted),
    CONSTRAINT ck_pfi_invoice_type      CHECK (invoice_type IN ('NORMAL','RED_REVERSE')),
    CONSTRAINT ck_pfi_invoice_basis     CHECK (invoice_basis IN ('MILESTONE','OUTSOURCING','MONTHLY','FINAL','OTHER')),
    CONSTRAINT ck_pfi_status_enum       CHECK (status IN ('DRAFT','SUBMITTED','ISSUED','RED_REVERSED','CANCELLED')),
    CONSTRAINT ck_pfi_tax_period_fmt    CHECK (tax_period IS NULL OR tax_period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_pfi_tax_rate_range    CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_pfi_amount_nonneg     CHECK (amount >= 0 AND tax_amount >= 0 AND net_amount >= 0),
    CONSTRAINT ck_pfi_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_finance_invoice IS '鍙戠エ涓昏〃: 鏀寔姝ｅ父寮€绁ㄤ笌绾㈠啿锛圧ED_REVERSED锛?鎵ц InvoiceStatus 鐘舵€佹満鏍￠獙,invoice_code 鍞竴,invoice_no 鍦?ISSUED 鏃跺垎閰?;

COMMENT ON COLUMN pmis_finance_invoice.invoice_no IS '璐㈠姟鍙戠エ鍙? 绋庡姟灞€鍒嗛厤鐨勭焊璐?鐢靛瓙鍙戠エ鍙?ISSUED 鐘舵€佹椂鍒嗛厤';

COMMENT ON COLUMN pmis_finance_invoice.invoice_code IS '涓氬姟缂栧彿: 绯荤粺鐢熸垚鐨勫敮涓€缂栫爜,濡?INV-2026-001';

COMMENT ON COLUMN pmis_finance_invoice.invoice_type IS '鍙戠エ绫诲瀷: NORMAL 姝ｅ父寮€绁?/ RED_REVERSE 绾㈠啿鍙戠エ';

COMMENT ON COLUMN pmis_finance_invoice.contract_id IS '鎵€灞炲悎鍚?ID';

COMMENT ON COLUMN pmis_finance_invoice.initiation_id IS '鎵€灞炵珛椤?ID';

COMMENT ON COLUMN pmis_finance_invoice.customer_id IS '瀹㈡埛 ID';

COMMENT ON COLUMN pmis_finance_invoice.customer_name IS '瀹㈡埛鍚嶇О锛堝啑浣欙級';

COMMENT ON COLUMN pmis_finance_invoice.invoice_basis IS '寮€绁ㄤ緷鎹? MILESTONE 閲岀▼纰?/ OUTSOURCING 澶栧寘浜哄ぉ / MONTHLY 鏈堝害缁撶畻 / FINAL 缁堥獙 / OTHER 鍏朵粬';

COMMENT ON COLUMN pmis_finance_invoice.amount IS '鍚◣閲戦(鍏?';

COMMENT ON COLUMN pmis_finance_invoice.tax_amount IS '绋庨(鍏?';

COMMENT ON COLUMN pmis_finance_invoice.net_amount IS '涓嶅惈绋庨噾棰?鍏?';

COMMENT ON COLUMN pmis_finance_invoice.tax_rate IS '绋庣巼: 0.06=6%,0.13=13%';

COMMENT ON COLUMN pmis_finance_invoice.currency IS '甯佺: CNY/USD/EUR,榛樿 CNY';

COMMENT ON COLUMN pmis_finance_invoice.invoice_date IS '寮€绁ㄦ棩鏈?;

COMMENT ON COLUMN pmis_finance_invoice.tax_period IS '绋庡姟鎵€灞炴湡: 鏍煎紡 YYYY-MM,鐢ㄤ簬绋庡姟鐢虫姤';

COMMENT ON COLUMN pmis_finance_invoice.title IS '鍙戠エ鎶ご';

COMMENT ON COLUMN pmis_finance_invoice.tax_no IS '绾崇◣浜鸿瘑鍒彿: 瀹㈡埛绋庡彿';

COMMENT ON COLUMN pmis_finance_invoice.bank_info IS '寮€鎴疯+璐﹀彿: 瀹㈡埛鏀剁エ鏂归摱琛屼俊鎭?;

COMMENT ON COLUMN pmis_finance_invoice.address IS '瀹㈡埛鍦板潃';

COMMENT ON COLUMN pmis_finance_invoice.phone IS '瀹㈡埛鐢佃瘽';

COMMENT ON COLUMN pmis_finance_invoice.remark IS '澶囨敞';

COMMENT ON COLUMN pmis_finance_invoice.status IS '鍙戠エ鐘舵€? DRAFT 鑽夌 / SUBMITTED 宸叉彁浜?/ ISSUED 宸插紑绁?/ RED_REVERSED 宸茬孩鍐?/ CANCELLED 宸插彇娑?涓ユ牸鐘舵€佹満';

COMMENT ON COLUMN pmis_finance_invoice.reversed_by_id IS '绾㈠啿鏉ユ簮鍙戠エ ID: 绾㈠啿鍙戠エ鎸囧悜琚孩鍐茬殑鍘熷鍙戠エ';

COMMENT ON COLUMN pmis_finance_invoice.attachment_id IS '鍙戠エ鎵弿浠? 寮曠敤 pmis_file_metadata.id';

COMMENT ON COLUMN pmis_finance_invoice.approval_comment IS '瀹℃壒鎰忚';

COMMENT ON COLUMN pmis_finance_invoice.applied_by IS '鐢宠浜?ID';

COMMENT ON COLUMN pmis_finance_invoice.approved_by IS '瀹℃壒浜?ID';

COMMENT ON COLUMN pmis_finance_invoice.approved_at IS '瀹℃壒鏃堕棿';

COMMENT ON COLUMN pmis_finance_invoice.issued_by IS '寮€绁ㄤ汉 ID';

COMMENT ON COLUMN pmis_finance_invoice.issued_at IS '寮€绁ㄦ椂闂?;

COMMENT ON COLUMN pmis_finance_invoice.tenant_id IS '绉熸埛 ID';

COMMENT ON COLUMN pmis_finance_invoice.provider_trace_id IS '閾捐矾杩借釜 ID';

COMMENT ON COLUMN pmis_finance_invoice.deleted IS '閫昏緫鍒犻櫎: 0=鏈垹闄?1=宸插垹闄?;

-- 澶嶅悎/閮ㄥ垎绱㈠紩(鏇夸唬闆舵暎鐨?idx_pfi_*)
CREATE INDEX IF NOT EXISTS idx_pfi_tenant_contract
    ON pmis_finance_invoice(tenant_id, contract_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_initiation
    ON pmis_finance_invoice(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_customer
    ON pmis_finance_invoice(tenant_id, customer_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_status_type
    ON pmis_finance_invoice(tenant_id, status, invoice_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_invoice_date
    ON pmis_finance_invoice(tenant_id, invoice_date)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_tax_period
    ON pmis_finance_invoice(tenant_id, tax_period)
    WHERE deleted = 0;

-- =====================================================
-- 2. 鍥炴涓昏〃 pmis_finance_payment

-- =====================================================
-- P1-6: 宸插簾寮?鏃犻渶 DROP
CREATE TABLE IF NOT EXISTS pmis_finance_payment(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    payment_no          VARCHAR(64),                              -- 閾惰娴佹按鍙?绯荤粺娴佹按
    payment_code        VARCHAR(64)  NOT NULL,                    -- 涓氬姟缂栧彿
    contract_id         VARCHAR(20)       NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
    customer_id         VARCHAR(20)       NOT NULL,
    customer_name       VARCHAR(256),
    amount              NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 鍥炴鎬婚噾棰?
    currency            VARCHAR(16)  NOT NULL DEFAULT 'CNY',
    payment_method      VARCHAR(32)  NOT NULL DEFAULT 'BANK_TRANSFER', -- BANK_TRANSFER/CHECK/CASH/OTHER
    payment_date        DATE         NOT NULL,                    -- 鍒拌处鏃ユ湡
    bank_account        VARCHAR(64),                              -- 瀹㈡埛浠樻璐﹀彿
    our_bank_account    VARCHAR(64),                              -- 鎴戞柟鏀舵璐﹀彿
    bank_reference      VARCHAR(128),                             -- 閾惰娴佹按鍙?
    invoice_allocation  TEXT,                                     -- 宸插垎閰嶅彂绁↖D锛堥€楀彿鍒嗛殧锛?
    allocated_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 宸叉牳閿€閲戦
    unallocated_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 鏈牳閿€閲戦
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- PaymentStatus
    remark              TEXT,
    confirmed_by        VARCHAR(20),
    confirmed_at        TIMESTAMPTZ,
    recorded_by         VARCHAR(20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfp_code              UNIQUE (payment_code, deleted),
    CONSTRAINT ck_pfp_payment_method    CHECK (payment_method IN ('BANK_TRANSFER','CHECK','CASH','OTHER')),
    CONSTRAINT ck_pfp_status_enum       CHECK (status IN ('PENDING','RECEIVED','PARTIAL','ALLOCATED','CANCELLED')),
    CONSTRAINT ck_pfp_amount_nonneg     CHECK (amount >= 0 AND allocated_amount >= 0 AND unallocated_amount >= 0),
    CONSTRAINT ck_pfp_alloc_le_amount   CHECK (allocated_amount <= amount),
    CONSTRAINT ck_pfp_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_finance_payment IS '鍥炴涓昏〃: 瀹㈡埛鍥炴璁板綍,鏀寔鏍搁攢鍙戠エ锛坅llocated_amount/unallocated_amount锛?unallocatedAmount=0 鏃惰嚜鍔ㄨ浆 ALLOCATED';

COMMENT ON COLUMN pmis_finance_payment.payment_no IS '鍥炴娴佹按鍙? 閾惰娴佹按鍙锋垨绯荤粺鐢熸垚';

COMMENT ON COLUMN pmis_finance_payment.payment_code IS '涓氬姟缂栧彿: 绯荤粺鐢熸垚鐨勫敮涓€缂栫爜,濡?PAY-2026-001';

COMMENT ON COLUMN pmis_finance_payment.contract_id IS '鎵€灞炲悎鍚?ID';

COMMENT ON COLUMN pmis_finance_payment.initiation_id IS '鎵€灞炵珛椤?ID';

COMMENT ON COLUMN pmis_finance_payment.customer_id IS '瀹㈡埛 ID';

COMMENT ON COLUMN pmis_finance_payment.customer_name IS '瀹㈡埛鍚嶇О锛堝啑浣欙級';

COMMENT ON COLUMN pmis_finance_payment.amount IS '鍥炴鎬婚噾棰?鍏?';

COMMENT ON COLUMN pmis_finance_payment.currency IS '甯佺: 榛樿 CNY';

COMMENT ON COLUMN pmis_finance_payment.payment_method IS '鏀粯鏂瑰紡: BANK_TRANSFER 閾惰杞处 / CHECK 鏀エ / CASH 鐜伴噾 / OTHER 鍏朵粬';

COMMENT ON COLUMN pmis_finance_payment.payment_date IS '鍒拌处鏃ユ湡';

COMMENT ON COLUMN pmis_finance_payment.bank_account IS '瀹㈡埛浠樻璐﹀彿';

COMMENT ON COLUMN pmis_finance_payment.our_bank_account IS '鎴戞柟鏀舵璐﹀彿';

COMMENT ON COLUMN pmis_finance_payment.bank_reference IS '閾惰娴佹按鍙? 閾惰绔殑娴佹按鏍囪瘑';

COMMENT ON COLUMN pmis_finance_payment.invoice_allocation IS '宸插垎閰嶅彂绁?ID 鍒楄〃: 閫楀彿鍒嗛殧';

COMMENT ON COLUMN pmis_finance_payment.allocated_amount IS '宸叉牳閿€閲戦(鍏?: 鍏宠仈鍒板彂绁?;

COMMENT ON COLUMN pmis_finance_payment.unallocated_amount IS '鏈牳閿€閲戦(鍏?: amount - allocatedAmount';

COMMENT ON COLUMN pmis_finance_payment.status IS '鍥炴鐘舵€? PENDING 寰呯‘璁?/ RECEIVED 宸插埌璐?/ PARTIAL 閮ㄥ垎鏍搁攢 / ALLOCATED 宸叉牳閿€瀹?/ CANCELLED 宸插彇娑?;

COMMENT ON COLUMN pmis_finance_payment.remark IS '澶囨敞';

COMMENT ON COLUMN pmis_finance_payment.confirmed_by IS '纭浜?ID: 璐㈠姟纭鍒拌处';

COMMENT ON COLUMN pmis_finance_payment.confirmed_at IS '纭鏃堕棿';

COMMENT ON COLUMN pmis_finance_payment.recorded_by IS '褰曞叆浜?ID';

COMMENT ON COLUMN pmis_finance_payment.tenant_id IS '绉熸埛 ID';

COMMENT ON COLUMN pmis_finance_payment.provider_trace_id IS '閾捐矾杩借釜 ID';

COMMENT ON COLUMN pmis_finance_payment.deleted IS '閫昏緫鍒犻櫎: 0=鏈垹闄?1=宸插垹闄?;

-- 澶嶅悎/閮ㄥ垎绱㈠紩(鏇夸唬闆舵暎鐨?idx_pfp_*)
CREATE INDEX IF NOT EXISTS idx_pfp_tenant_contract
    ON pmis_finance_payment(tenant_id, contract_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_initiation
    ON pmis_finance_payment(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_customer_status
    ON pmis_finance_payment(tenant_id, customer_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_payment_date
    ON pmis_finance_payment(tenant_id, payment_date)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_customer_unalloc
    ON pmis_finance_payment(tenant_id, customer_id, unallocated_amount)
    WHERE deleted = 0 AND status IN ('PENDING','RECEIVED','PARTIAL');

-- =====================================================
-- 3. 瀹㈡埛淇＄敤琛?pmis_finance_customer_credit

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_finance_customer_credit(
    id                    VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    customer_id           VARCHAR(20)       NOT NULL,
    customer_name         VARCHAR(256),
    credit_level          VARCHAR(8)   NOT NULL DEFAULT 'D',       -- A/B/C/D
    credit_score          INTEGER      NOT NULL DEFAULT 0,        -- 0-100
    total_contract_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_invoiced_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_received_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    on_time_rate          NUMERIC(5,4) NOT NULL DEFAULT 0,        -- 鍙婃椂鍥炴鐜?
    contract_count        INTEGER      NOT NULL DEFAULT 0,
    overdue_count         INTEGER      NOT NULL DEFAULT 0,
    last_evaluation_at    TIMESTAMPTZ,
    evaluator             VARCHAR(64),
    remark                TEXT,
    tenant_id             VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfcc_customer         UNIQUE (customer_id, deleted),
    CONSTRAINT ck_pfcc_credit_level     CHECK (credit_level IN ('A','B','C','D')),
    CONSTRAINT ck_pfcc_credit_score      CHECK (credit_score >= 0 AND credit_score <= 100),
    CONSTRAINT ck_pfcc_on_time_rate      CHECK (on_time_rate >= 0 AND on_time_rate <= 1),
    CONSTRAINT ck_pfcc_amount_nonneg     CHECK (total_contract_amount >= 0 AND total_invoiced_amount >= 0 AND total_received_amount >= 0),
    CONSTRAINT ck_pfcc_count_nonneg      CHECK (contract_count >= 0 AND overdue_count >= 0),
    CONSTRAINT ck_pfcc_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_finance_customer_credit IS '瀹㈡埛淇＄敤琛? 瀹㈡埛淇＄敤璇勫垎涓庣瓑绾э紙A/B/C/D锛?CustomerCreditScoreEvaluator 璇勫垎锛?-100锛?;

COMMENT ON COLUMN pmis_finance_customer_credit.customer_id IS '瀹㈡埛 ID: 鍏ㄥ眬鍞竴';

COMMENT ON COLUMN pmis_finance_customer_credit.customer_name IS '瀹㈡埛鍚嶇О锛堝啑浣欙級';

COMMENT ON COLUMN pmis_finance_customer_credit.credit_level IS '淇＄敤绛夌骇: A=浼樿川(90-100) B=鑹ソ(75-89) C=涓€鑸?60-74) D=椋庨櫓(0-59),fromScore() 浣跨敤 >= 姣旇緝';

COMMENT ON COLUMN pmis_finance_customer_credit.credit_score IS '淇＄敤鍒? 0-100,鏂板鎴烽粯璁?30 鍒嗭紙A 绾у熀绾匡級';

COMMENT ON COLUMN pmis_finance_customer_credit.total_contract_amount IS '绱鍚堝悓閲戦(鍏?';

COMMENT ON COLUMN pmis_finance_customer_credit.total_invoiced_amount IS '绱寮€绁ㄩ噾棰?鍏?';

COMMENT ON COLUMN pmis_finance_customer_credit.total_received_amount IS '绱鍥炴閲戦(鍏?';

COMMENT ON COLUMN pmis_finance_customer_credit.on_time_rate IS '鍙婃椂鍥炴鐜? 0.85=85%';

COMMENT ON COLUMN pmis_finance_customer_credit.contract_count IS '鍚堝悓鎬绘暟';

COMMENT ON COLUMN pmis_finance_customer_credit.overdue_count IS '閫炬湡娆℃暟';

COMMENT ON COLUMN pmis_finance_customer_credit.last_evaluation_at IS '鏈€杩戜竴娆¤瘎浼版椂闂?;

COMMENT ON COLUMN pmis_finance_customer_credit.evaluator IS '璇勪及浜?璇勪及鍣ㄥ悕绉?;

COMMENT ON COLUMN pmis_finance_customer_credit.remark IS '澶囨敞';

COMMENT ON COLUMN pmis_finance_customer_credit.tenant_id IS '绉熸埛 ID';

COMMENT ON COLUMN pmis_finance_customer_credit.provider_trace_id IS '閾捐矾杩借釜 ID';

COMMENT ON COLUMN pmis_finance_customer_credit.deleted IS '閫昏緫鍒犻櫎: 0=鏈垹闄?1=宸插垹闄?;

-- 澶嶅悎/閮ㄥ垎绱㈠紩(鏇夸唬闆舵暎鐨?idx_pfcc_level / idx_pfcc_tenant)
CREATE INDEX IF NOT EXISTS idx_pfcc_tenant_level_score
    ON pmis_finance_customer_credit(tenant_id, credit_level, credit_score DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfcc_tenant_updated
    ON pmis_finance_customer_credit(tenant_id, updated_at DESC)
    WHERE deleted = 0;

-- =====================================================
-- 4. 鍒濆鏁版嵁锛氫俊鐢ㄧ瓑绾у瓧鍏革紙鐢ㄤ簬鍓嶇灞曠ず锛?
-- =====================================================
-- credit_level 瀛楁鍚箟锛堣涓婃柟 COLUMN COMMENT锛? A=浼樿川(90-100) B=鑹ソ(75-89) C=涓€鑸?60-74) D=椋庨櫓(0-59)

-- --------------------------------------------------------------------

-- ============================ [013] init pmis evm schema ============================

-- =====================================================
-- PMIS 鎵规10 DDL锛欵VM 鎸ｅ€?/ 瀵瑰鎶ヤ环璐圭巼 / 瀵瑰唴鎴愭湰璐圭巼 / 鍒╂鼎娴嬬畻
-- 鐗堟湰: V1.0.0_013
-- 鎻忚堪: 鎸ｅ€兼祴閲?pmis_evm_measure)銆佸澶栨姤浠疯垂鐜?pmis_rate_card)銆?
--       瀵瑰唴鎴愭湰璐圭巼(pmis_rate_internal)銆佸埄娑︽祴绠楃増鏈?pmis_profit_simulation)
-- =====================================================

-- =====================================================
-- 1. EVM 鎸ｅ€兼祴閲忚〃 pmis_evm_measure

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_profit_simulation(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    simulation_code     VARCHAR(64)  NOT NULL,
    simulation_name     VARCHAR(256) NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
    version             INTEGER      NOT NULL DEFAULT 1,
    scenario_type       VARCHAR(32)  NOT NULL DEFAULT 'BASE',   -- BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM
    contract_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
    external_revenue    NUMERIC(15,2) NOT NULL DEFAULT 0,
    internal_cost       NUMERIC(15,2) NOT NULL DEFAULT 0,
    expected_hours      NUMERIC(15,2) NOT NULL DEFAULT 0,
    blended_rate        NUMERIC(15,2) NOT NULL DEFAULT 0,
    gross_profit        NUMERIC(15,2) NOT NULL DEFAULT 0,
    gross_margin        NUMERIC(7,4)  NOT NULL DEFAULT 0,
    target_margin       NUMERIC(7,4)  NOT NULL DEFAULT 0,
    labor_cost          NUMERIC(15,2) NOT NULL DEFAULT 0,
    purchase_cost       NUMERIC(15,2) NOT NULL DEFAULT 0,
    expense_cost        NUMERIC(15,2) NOT NULL DEFAULT 0,
    outsource_cost      NUMERIC(15,2) NOT NULL DEFAULT 0,
    assumptions         TEXT,                                  -- JSON
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMPTZ,
    remark              TEXT,
    applicant_id        VARCHAR(20),
    applicant_name      VARCHAR(64),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pps_code              UNIQUE (simulation_code, deleted),
    CONSTRAINT ck_pps_scenario_type     CHECK (scenario_type IN ('BASE','OPTIMISTIC','PESSIMISTIC','CUSTOM')),
    CONSTRAINT ck_pps_status_enum       CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','ARCHIVED')),
    CONSTRAINT ck_pps_version_pos       CHECK (version > 0),
    CONSTRAINT ck_pps_amounts_nonneg    CHECK (contract_amount >= 0 AND external_revenue >= 0 AND internal_cost >= 0
                                              AND expected_hours >= 0 AND blended_rate >= 0
                                              AND labor_cost >= 0 AND purchase_cost >= 0
                                              AND expense_cost >= 0 AND outsource_cost >= 0),
    CONSTRAINT ck_pps_margin_range      CHECK (gross_margin >= -1 AND gross_margin <= 1 AND target_margin >= -1 AND target_margin <= 1),
    CONSTRAINT ck_pps_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_profit_simulation IS '鍒╂鼎娴嬬畻鐗堟湰琛?What-if: 鍚屼竴绔嬮」鏀寔澶氫釜娴嬬畻鐗堟湰,create() 鑷姩 version=max+1,APPROVED/ARCHIVED 鐘舵€佺姝㈠垹闄?;

COMMENT ON COLUMN pmis_profit_simulation.simulation_code IS '娴嬬畻鍗曞彿: 涓氬姟鍞竴,濡?SIM-2026-001';

COMMENT ON COLUMN pmis_profit_simulation.simulation_name IS '娴嬬畻鍚嶇О';

COMMENT ON COLUMN pmis_profit_simulation.initiation_id IS '鎵€灞炵珛椤?ID';

COMMENT ON COLUMN pmis_profit_simulation.version IS '鐗堟湰鍙? 鍚岀珛椤瑰唴閫掑,create() 鏃惰嚜鍔?max+1';

COMMENT ON COLUMN pmis_profit_simulation.scenario_type IS '鍦烘櫙绫诲瀷: BASE 鍩哄噯 / OPTIMISTIC 涔愯 / PESSIMISTIC 鎮茶 / CUSTOM 鑷畾涔?;

COMMENT ON COLUMN pmis_profit_simulation.contract_amount IS '鍚堝悓閲戦(鍏?';

COMMENT ON COLUMN pmis_profit_simulation.external_revenue IS '澶栭儴鏀跺叆(鍏?: 瀵瑰鎶ヤ环鍚堣';

COMMENT ON COLUMN pmis_profit_simulation.internal_cost IS '鍐呴儴鎴愭湰(鍏?: 浜哄姏 + 閲囪喘 + 璐圭敤 + 澶栧寘';

COMMENT ON COLUMN pmis_profit_simulation.expected_hours IS '棰勮宸ユ椂(灏忔椂)';

COMMENT ON COLUMN pmis_profit_simulation.blended_rate IS '缁煎悎浜哄ぉ璐圭巼(鍏?';

COMMENT ON COLUMN pmis_profit_simulation.gross_profit IS '姣涘埄娑?鍏? = external_revenue - internal_cost';

COMMENT ON COLUMN pmis_profit_simulation.gross_margin IS '姣涘埄鐜? 0.25=25%';

COMMENT ON COLUMN pmis_profit_simulation.target_margin IS '鐩爣姣涘埄鐜? 涓氬姟鏂归璁剧殑杈炬爣绾?;

COMMENT ON COLUMN pmis_profit_simulation.labor_cost IS '浜哄伐鎴愭湰(鍏?';

COMMENT ON COLUMN pmis_profit_simulation.purchase_cost IS '閲囪喘鎴愭湰(鍏?';

COMMENT ON COLUMN pmis_profit_simulation.expense_cost IS '璐圭敤(鍏?';

COMMENT ON COLUMN pmis_profit_simulation.outsource_cost IS '澶栧寘鎴愭湰(鍏?';

COMMENT ON COLUMN pmis_profit_simulation.assumptions IS '鍋囪鏉′欢 JSON: 杈撳叆鍙傛暟蹇収';

COMMENT ON COLUMN pmis_profit_simulation.status IS '娴嬬畻鐘舵€? DRAFT 鑽夌 / SUBMITTED 宸叉彁浜?/ APPROVED 宸叉壒鍑?/ REJECTED 宸查┏鍥?/ ARCHIVED 宸插綊妗?REJECTED 鍙洖閫€鍒?DRAFT';

COMMENT ON COLUMN pmis_profit_simulation.approver_name IS '瀹℃壒浜哄鍚嶏紙鍐椾綑锛?;

COMMENT ON COLUMN pmis_profit_simulation.approved_at IS '瀹℃壒鏃堕棿';

COMMENT ON COLUMN pmis_profit_simulation.remark IS '澶囨敞';

COMMENT ON COLUMN pmis_profit_simulation.applicant_id IS '鐢宠浜?ID';

COMMENT ON COLUMN pmis_profit_simulation.applicant_name IS '鐢宠浜哄鍚嶏紙鍐椾綑锛?;

COMMENT ON COLUMN pmis_profit_simulation.tenant_id IS '绉熸埛 ID';

COMMENT ON COLUMN pmis_profit_simulation.provider_trace_id IS '閾捐矾杩借釜 ID';

COMMENT ON COLUMN pmis_profit_simulation.deleted IS '閫昏緫鍒犻櫎: 0=鏈垹闄?1=宸插垹闄?;

-- 澶嶅悎/閮ㄥ垎绱㈠紩(鏇夸唬闆舵暎鐨?idx_psm_initiation / idx_psm_version / idx_psm_status)
CREATE INDEX IF NOT EXISTS idx_psm_tenant_initiation_version
    ON pmis_profit_simulation(tenant_id, initiation_id, version DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_psm_tenant_status_scenario
    ON pmis_profit_simulation(tenant_id, status, scenario_type)
    WHERE deleted = 0;

-- =====================================================
-- 5. 鍒濆鍖?L1-L18 鑱岀骇榛樿瀵瑰鎶ヤ环璐圭巼锛堝熀绾垮弬鑰冿級
-- =====================================================
INSERT INTO pmis_rate_card
    (rate_code, level_code, project_type, customer_level, billing_unit, rate_amount, currency, effective_date, status, tenant_id, provider_trace_id)
VALUES
    ('RC-L1-DEFAULT',  'L1',  NULL, NULL, 'DAY',  1200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L2-DEFAULT',  'L2',  NULL, NULL, 'DAY',  1500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L3-DEFAULT',  'L3',  NULL, NULL, 'DAY',  1800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L4-DEFAULT',  'L4',  NULL, NULL, 'DAY',  2200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L5-DEFAULT',  'L5',  NULL, NULL, 'DAY',  2600.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L6-DEFAULT',  'L6',  NULL, NULL, 'DAY',  3000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L7-DEFAULT',  'L7',  NULL, NULL, 'DAY',  3500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L8-DEFAULT',  'L8',  NULL, NULL, 'DAY',  4000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L9-DEFAULT',  'L9',  NULL, NULL, 'DAY',  4500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L10-DEFAULT', 'L10', NULL, NULL, 'DAY',  5200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L11-DEFAULT', 'L11', NULL, NULL, 'DAY',  6000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L12-DEFAULT', 'L12', NULL, NULL, 'DAY',  7000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L13-DEFAULT', 'L13', NULL, NULL, 'DAY',  8200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L14-DEFAULT', 'L14', NULL, NULL, 'DAY',  9500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L15-DEFAULT', 'L15', NULL, NULL, 'DAY', 11000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L16-DEFAULT', 'L16', NULL, NULL, 'DAY', 13000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L17-DEFAULT', 'L17', NULL, NULL, 'DAY', 15000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
        ('RC-L18-DEFAULT', 'L18', NULL, NULL, 'DAY', 18000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init') ON CONFLICT DO NOTHING;

-- =====================================================
-- 6. 鍒濆鍖?L1-L18 鑱岀骇榛樿瀵瑰唴鎴愭湰璐圭巼锛堝熀绾垮弬鑰冿級
-- =====================================================
INSERT INTO pmis_rate_internal
    (rate_code, level_code, billing_unit, cost_amount, currency, effective_date, status, tenant_id, provider_trace_id)
VALUES
    ('RI-L1-DEFAULT',  'L1',  'DAY',  800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L2-DEFAULT',  'L2',  'DAY',  1000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L3-DEFAULT',  'L3',  'DAY',  1200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L4-DEFAULT',  'L4',  'DAY',  1500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L5-DEFAULT',  'L5',  'DAY',  1800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L6-DEFAULT',  'L6',  'DAY',  2100.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L7-DEFAULT',  'L7',  'DAY',  2400.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L8-DEFAULT',  'L8',  'DAY',  2800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L9-DEFAULT',  'L9',  'DAY',  3200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L10-DEFAULT', 'L10', 'DAY',  3700.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L11-DEFAULT', 'L11', 'DAY',  4200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L12-DEFAULT', 'L12', 'DAY',  4800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L13-DEFAULT', 'L13', 'DAY',  5500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L14-DEFAULT', 'L14', 'DAY',  6300.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L15-DEFAULT', 'L15', 'DAY',  7300.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L16-DEFAULT', 'L16', 'DAY',  8500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L17-DEFAULT', 'L17', 'DAY', 10000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
        ('RI-L18-DEFAULT', 'L18', 'DAY', 12000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init') ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [015] init pmis cockpit views ============================

-- ============================================================
-- V1.0.0_015  缁忚惀椹鹃┒鑸?+ 楂樼骇鎶ヨ〃  瑙嗗浘鑴氭湰
-- ============================================================
-- 璇存槑锛氫负椹鹃┒鑸变笌楂樼骇鎶ヨ〃鎻愪緵璺ㄦā鍧楄仛鍚堣鍥撅紝閬垮厤鍦?Java 灞傚仛
--      澶氭鍗曡〃鏌ヨ銆傛墍鏈夎鍥?LEFT JOIN + COALESCE 纭繚 0 鏀跺叆/0 鎴愭湰
--      鐨勯」鐩篃鑳藉嚭鐜板湪涓嬮捇缁撴灉涓€?
-- ============================================================

-- ----------------------------
-- 1. 椤圭洰鏀跺叆 + 鎴愭湰瑙嗗浘锛堟寜 initiation 脳 period锛?
-- ----------------------------
-- 浼樺寲: 鏄惧紡甯?tenant_id,閬垮厤 JOIN 鏀惧ぇ瀵艰嚧璺ㄧ鎴锋暟鎹硠闇?
CREATE OR REPLACE VIEW pmis_view_initiation_revenue_cost
    WITH (security_invoker = true) AS
SELECT i.tenant_id,
       i.id              AS initiation_id,
       COALESCE((SELECT SUM(amount) FROM pmis_profit_revenue r
                  WHERE r.initiation_id = i.id AND r.deleted = 0
                    AND r.tenant_id = i.tenant_id), 0)         AS total_revenue,
       COALESCE((SELECT SUM(amount) FROM pmis_finance_invoice p
                  WHERE p.initiation_id = i.id AND p.deleted = 0
                    AND p.tenant_id = i.tenant_id), 0)         AS invoiced_amount,
       COALESCE((SELECT SUM(amount) FROM pmis_profit_revenue r2
                  WHERE r2.initiation_id = i.id AND r2.deleted = 0
                    AND r2.status = 'CONFIRMED'
                    AND r2.tenant_id = i.tenant_id), 0)        AS confirmed_revenue,
       COALESCE((SELECT SUM(amount) FROM pmis_cost_allocation
                  WHERE initiation_id = i.id AND deleted = 0 AND cost_type = 'LABOR'
                    AND tenant_id = i.tenant_id), 0)           AS labor_cost,
       COALESCE((SELECT SUM(amount) FROM pmis_cost_purchase
                  WHERE initiation_id = i.id AND deleted = 0
                    AND tenant_id = i.tenant_id), 0)          AS purchase_cost,
       COALESCE((SELECT SUM(amount) FROM pmis_cost_expense
                  WHERE initiation_id = i.id AND deleted = 0
                    AND tenant_id = i.tenant_id), 0)          AS expense_cost
FROM pmis_project_initiation i
WHERE i.deleted = 0;

COMMENT ON VIEW pmis_view_initiation_revenue_cost IS '椤圭洰鏀跺叆 + 鎴愭湰鑱氬悎瑙嗗浘: CockpitReportServiceImpl 璇诲彇,total_revenue 鍖呭惈鎵€鏈夋敹鍏ヨ褰?confirmed_revenue 浠?CONFIRMED 鐘舵€?labor/purchase/expense 涓夌被鎴愭湰鍒嗗埆鑱氬悎;LEFT JOIN + COALESCE 淇濊瘉 0 鏀跺叆/0 鎴愭湰椤圭洰涔熷嚭鐜?姣忔潯瀛愭煡璇㈠己鍒跺甫 tenant_id = i.tenant_id 闃叉璺ㄧ鎴锋暟鎹薄鏌?;

-- ----------------------------
-- 2. 椤圭洰 EVM 棰勮鍒嗗竷
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_initiation_evm
    WITH (security_invoker = true) AS
SELECT tenant_id,
       initiation_id,
       CASE
           WHEN COUNT(*) FILTER (WHERE alert_level = 'RED') > 0 THEN 'RED'::VARCHAR
           WHEN COUNT(*) FILTER (WHERE alert_level = 'YELLOW') > 0 THEN 'YELLOW'::VARCHAR
           ELSE 'NORMAL'::VARCHAR
       END                                           AS top_alert,
       COUNT(*) FILTER (WHERE alert_level = 'RED')    AS red_count,
       COUNT(*) FILTER (WHERE alert_level = 'YELLOW') AS yellow_count,
       COUNT(*) FILTER (WHERE alert_level = 'NORMAL') AS green_count
FROM pmis_evm_measure
WHERE deleted = 0
GROUP BY tenant_id, initiation_id;

COMMENT ON VIEW pmis_view_initiation_evm IS '椤圭洰 EVM 棰勮鍒嗗竷瑙嗗浘: 鎸?tenant_id + 绔嬮」鑱氬悎 RED/YELLOW/NORMAL 璁℃暟,AdvancedReportService#evmReport 璇诲彇,top_alert 鍙栨渶楂樼瓑绾?;

-- ----------------------------
-- 3. 缁忚惀椹鹃┒鑸?KPI 鎬昏瑙嗗浘
-- ----------------------------
-- 娉ㄦ剰: 澶氱鎴峰満鏅笅,姝よ鍥炬寜 tenant_id 鍒嗙粍鑱氬悎,纭繚绉熸埛闂存暟鎹殧绂?
CREATE OR REPLACE VIEW pmis_view_cockpit_overview
    WITH (security_invoker = true) AS
SELECT
    tenant_id,
    (SELECT COUNT(*) FROM pmis_project_initiation
        WHERE deleted = 0 AND stage IN ('APPROVED','IN_PROGRESS')
          AND tenant_id = t.tenant_id)                                            AS active_projects,
    (SELECT COALESCE(SUM(amount), 0) FROM pmis_finance_invoice
        WHERE deleted = 0 AND status IN ('ISSUED','RED_REVERSED')
          AND tenant_id = t.tenant_id)                                            AS total_invoiced,
    (SELECT COALESCE(SUM(allocated_amount), 0) FROM pmis_finance_payment
        WHERE deleted = 0 AND status = 'ALLOCATED'
          AND tenant_id = t.tenant_id)                                           AS confirmed_revenue
FROM (SELECT DISTINCT tenant_id FROM pmis_project_initiation WHERE deleted = 0) t;

COMMENT ON VIEW pmis_view_cockpit_overview IS '缁忚惀椹鹃┒鑸?KPI 鎬昏瑙嗗浘: 鎸?tenant_id 鍒嗙粍姹囨€?active_projects/total_invoiced/confirmed_revenue,鍗曠鎴峰満鏅繑鍥炲崟琛?澶氱鎴烽渶鍓嶇鎸夌鎴疯繃婊?搴曞眰瀛愭煡璇㈤兘寮哄埗甯?tenant_id 鍏宠仈,鏉滅粷璺ㄧ鎴锋暟鎹薄鏌?CockpitReportController#overview 鐩存帴璇诲彇';

-- --------------------------------------------------------------------

-- ============================ [017] init pmis after sales schema ============================

-- ============================================================
-- V1.0.0_017  椤圭洰鍞悗绠＄悊  鑴氭湰
-- ============================================================
-- 璇存槑锛氭壒娆?14 椤圭洰鍞悗绠＄悊锛圥RD 3.8锛?
-- 1) 璐ㄤ繚鏈燂細pmis_warranty
-- 2) 杩愮淮宸ュ崟锛歱mis_ops_ticket
-- 3) 婊℃剰搴﹁瘎浠凤細pmis_satisfaction

-- ============================================================

-- ----------------------------
-- 1) 宸ユ椂琛ㄦ柊澧?billable 瀛楁
-- ----------------------------
ALTER TABLE pmis_execution_time_entry
    ADD COLUMN IF NOT EXISTS billable SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN pmis_execution_time_entry.billable IS '鍙璐规爣璇? 1=鍙璐癸紙璁″叆 BillableUtilization锛?0=闈炶璐?;

-- ----------------------------
-- 3) 姣忔棩瀵硅处琛?
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_reconcile_daily (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    reconcile_date      DATE         NOT NULL,
    reconcile_type      VARCHAR(32)  NOT NULL,
    initiation_id       VARCHAR(20),
    expected_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    actual_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    diff_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    diff_pct            NUMERIC(8,4) NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'OK',
    detail              TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 鏋氫妇绾︽潫
    CONSTRAINT ck_prd_reconcile_type    CHECK (reconcile_type IN ('COST','REVENUE','PAYMENT','INVOICE','TIMESHEET','PROFIT','BENCH','BUDGET')),
    CONSTRAINT ck_prd_status_enum       CHECK (status IN ('OK','WARN','FAIL')),
    -- 鏁板€间笌姣斾緥鑼冨洿
    CONSTRAINT ck_prd_diff_amount_eq    CHECK (diff_amount = actual_amount - expected_amount),
    CONSTRAINT ck_prd_diff_pct_range    CHECK (diff_pct >= -1 AND diff_pct <= 1),
    CONSTRAINT ck_prd_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_reconcile_daily IS '姣忔棩鑷姩瀵硅处琛? 鎴愭湰/鏀跺叆/鍥炴/寮€绁?璺ㄦā鍧楁牎楠?ReconcileServiceImpl 鎵ц';

COMMENT ON COLUMN pmis_reconcile_daily.reconcile_date IS '瀵硅处鏃ユ湡: 姣忔棩 02:00 瑙﹀彂';

COMMENT ON COLUMN pmis_reconcile_daily.reconcile_type IS '瀵硅处绫诲瀷: COST 鎴愭湰 / REVENUE 鏀跺叆 / PAYMENT 鍥炴 / INVOICE 寮€绁?/ TIMESHEET 宸ユ椂 / PROFIT 鍒╂鼎 / BENCH 闂茬疆 / BUDGET 棰勭畻';

COMMENT ON COLUMN pmis_reconcile_daily.initiation_id IS '鎵€灞炵珛椤?ID: 鍙┖,NULL 琛ㄧず鍏ㄥ眬缁村害';

COMMENT ON COLUMN pmis_reconcile_daily.expected_amount IS '搴旇閲戦(鍏?';

COMMENT ON COLUMN pmis_reconcile_daily.actual_amount IS '瀹炶閲戦(鍏?';

COMMENT ON COLUMN pmis_reconcile_daily.diff_amount IS '宸紓閲戦(鍏? = actual - expected';

COMMENT ON COLUMN pmis_reconcile_daily.diff_pct IS '宸紓姣斾緥: -1 ~ 1,渚嬪 0.05=5%';

COMMENT ON COLUMN pmis_reconcile_daily.status IS '瀵硅处鐘舵€? OK 涓€鑷?/ WARN 璀﹀憡锛坾diff_pct| < 5%锛? FAIL 澶辫触锛坾diff_pct| >= 5%锛?;

COMMENT ON COLUMN pmis_reconcile_daily.detail IS '瀵硅处鏄庣粏 JSON: 鍒楀嚭宸紓椤?;

COMMENT ON COLUMN pmis_reconcile_daily.tenant_id IS '绉熸埛 ID';

COMMENT ON COLUMN pmis_reconcile_daily.provider_trace_id IS '閾捐矾杩借釜 ID';

COMMENT ON COLUMN pmis_reconcile_daily.deleted IS '閫昏緫鍒犻櫎: 0=鏈垹闄?1=宸插垹闄?;

-- 澶嶅悎/閮ㄥ垎绱㈠紩(鏇夸唬闆舵暎鐨勫崟鍒楃储寮?
CREATE INDEX IF NOT EXISTS idx_prd_tenant_date_type
    ON pmis_reconcile_daily(tenant_id, reconcile_date DESC, reconcile_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_prd_tenant_init_date
    ON pmis_reconcile_daily(tenant_id, initiation_id, reconcile_date DESC)
    WHERE deleted = 0 AND initiation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_prd_tenant_status_date
    ON pmis_reconcile_daily(tenant_id, status, reconcile_date DESC)
    WHERE deleted = 0 AND status IN ('WARN','FAIL');

-- 鍞竴绾︽潫锛氭瘡澶╂瘡涓淮搴﹀彧鑳芥湁涓€鏉?
CREATE UNIQUE INDEX IF NOT EXISTS uk_prd_tenant_date_type_init
    ON pmis_reconcile_daily(tenant_id, reconcile_date, reconcile_type, COALESCE(initiation_id, '0'), deleted);

-- --------------------------------------------------------------------

-- ============================ [020] init pmis billable utilization snapshot ============================

-- ====================================================================
-- V1.0.0_020  鍙璐瑰埄鐢ㄧ巼蹇収琛?
--
--  璇存槑锛氬彲璁¤垂鍒╃敤鐜囷紙BillableUtilization锛夌敱 cronjob 姣忔棩璁＄畻鍚?
--        鎸佷箙鍖栧埌鏈〃锛岄┚椹惰埍 / 鎺掕姒?/ 瓒嬪娍鍒嗘瀽鍧囩洿鎺ヨ蹇収锛?
--        閬垮厤姣忔瀹炴椂鑱氬悎 pmis_execution_time_entry 澶ц〃銆?
--
--  鍐欏叆璺緞锛歽dsz-pmis-cronjob 妯″潡鐨?
--           BillableUtilizationJobHandler#execute
--  璇诲彇璺緞锛欳ockpitReportService / AdvancedReportService /
--           BillableUtilizationController
--
--  閿璁★細(period, employee_id) 鍞竴锛?
--         PostgreSQL UPSERT ON CONFLICT 淇濊瘉骞傜瓑銆?

