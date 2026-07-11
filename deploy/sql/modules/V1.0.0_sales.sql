-- ============================================================
-- PMIS sales module SQL
-- 鍟嗗姟閿€鍞湇鍔?(ydsz-pmis-sales, port 9010)
-- ============================================================
-- 鏈剼鏈?DDL 瀵瑰簲鍚庣 sales 鏈嶅姟鐨?Mapper / DO,
--   鐗╃悊 Mapper 瀹為檯鎵€鍦ㄦā鍧楀嵆琛ㄥ綊灞炪€傝法鏈嶅姟寮曠敤绂佹鐩磋繛,缁熶竴璧?
--   Feign Client (SalesDataClient / FinanceDataClient)銆?
--
-- 琛ㄥ綊灞炰緷鎹? ydsz-pmis-sales/src/main/java/.../infra/mapper/
-- 琛ㄦ暟閲? 6 寮?
-- --------------------------------------------------------------------

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_opportunity(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    opportunity_code  VARCHAR(64)    NOT NULL,
    opportunity_name  VARCHAR(256)   NOT NULL,
    customer_id       VARCHAR(20)         NOT NULL,
    customer_name     VARCHAR(256),
    business_dept_id  VARCHAR(20),
    owner_id          VARCHAR(20)         NOT NULL,
    owner_name        VARCHAR(64),
    level             VARCHAR(8)     NOT NULL DEFAULT 'C',
    source            VARCHAR(64),
    industry          VARCHAR(64),
    estimated_amount  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    win_rate          NUMERIC(5,4)   NOT NULL DEFAULT 0,
    expected_sign_date   DATE,
    expected_start_date  DATE,
    expected_end_date    DATE,
    status            VARCHAR(32)    NOT NULL DEFAULT 'FOLLOWING',
    lost_reason       VARCHAR(512),
    competitor        VARCHAR(256),
    remark            TEXT,
    tags              VARCHAR(512),
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 鏁版嵁瀹屾暣鎬х害鏉?
    CONSTRAINT uk_ppo_code           UNIQUE (opportunity_code, deleted),
    CONSTRAINT ck_ppo_level_enum     CHECK (level IN ('A', 'B', 'C')),
    CONSTRAINT ck_ppo_status_enum    CHECK (status IN ('FOLLOWING', 'QUOTED', 'NEGOTIATING', 'WON', 'LOST', 'INVALID', 'CONVERTED')),
    CONSTRAINT ck_ppo_win_rate_range CHECK (win_rate >= 0 AND win_rate <= 1),
    CONSTRAINT ck_ppo_amount_nonneg  CHECK (estimated_amount >= 0),
    CONSTRAINT ck_ppo_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppo_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_opportunity IS '鍟嗘満涓昏〃: 閿€鍞嚎绱㈠埌鍚堝悓鍓嶇殑婕忔枟绠＄悊,鏀寔璧㈢巼/鍒嗙骇/杞寲绔嬮」';

COMMENT ON COLUMN pmis_project_opportunity.id IS '涓婚敭 ID';

COMMENT ON COLUMN pmis_project_opportunity.opportunity_code IS '鍟嗘満缂栫爜(鍏ㄥ眬鍞竴,濡?OPP20260001)';

COMMENT ON COLUMN pmis_project_opportunity.opportunity_name IS '鍟嗘満鍚嶇О';

COMMENT ON COLUMN pmis_project_opportunity.customer_id IS '瀹㈡埛 ID';

COMMENT ON COLUMN pmis_project_opportunity.customer_name IS '瀹㈡埛鍚嶇О(鍐椾綑,鍑忓皯杩炶〃)';

COMMENT ON COLUMN pmis_project_opportunity.business_dept_id IS '璐熻矗浜嬩笟閮?ID';

COMMENT ON COLUMN pmis_project_opportunity.owner_id IS '鍟嗘満璐熻矗浜?ID(閿€鍞?BD)';

COMMENT ON COLUMN pmis_project_opportunity.owner_name IS '鍟嗘満璐熻矗浜哄鍚?;

COMMENT ON COLUMN pmis_project_opportunity.level IS '鍟嗘満鍒嗙骇: A 澶у鎴?/ B 涓瀷 / C 灏忓瀷';

COMMENT ON COLUMN pmis_project_opportunity.source IS '鍟嗘満鏉ユ簮(鑰佸鎴蜂粙缁?灞曚細/瀹樼綉/鎷涙姇鏍?鍏朵粬)';

COMMENT ON COLUMN pmis_project_opportunity.industry IS '瀹㈡埛鎵€灞炶涓?;

COMMENT ON COLUMN pmis_project_opportunity.estimated_amount IS '棰勮绛剧害閲戦(鍏?';

COMMENT ON COLUMN pmis_project_opportunity.win_rate IS '璧㈢巼(0.0000-1.0000,鐢ㄤ簬鏀跺叆棰勬祴)';

COMMENT ON COLUMN pmis_project_opportunity.expected_sign_date IS '棰勮绛剧害鏃ユ湡';

COMMENT ON COLUMN pmis_project_opportunity.expected_start_date IS '棰勮椤圭洰寮€濮嬫棩鏈?;

COMMENT ON COLUMN pmis_project_opportunity.expected_end_date IS '棰勮椤圭洰缁撴潫鏃ユ湡';

COMMENT ON COLUMN pmis_project_opportunity.status IS '鍟嗘満鐘舵€? FOLLOWING 璺熻繘涓?/ QUOTED 宸叉姤浠?/ NEGOTIATING 璋堝垽涓?/ WON 涓爣 / LOST 杈撳崟 / INVALID 鏃犳晥 / CONVERTED 宸茶浆绔嬮」';

COMMENT ON COLUMN pmis_project_opportunity.lost_reason IS '杈撳崟鍘熷洜';

COMMENT ON COLUMN pmis_project_opportunity.competitor IS '绔炰簤瀵规墜';

COMMENT ON COLUMN pmis_project_opportunity.remark IS '澶囨敞';

COMMENT ON COLUMN pmis_project_opportunity.tags IS '鏍囩(閫楀彿鍒嗛殧,鐢ㄤ簬妫€绱?';

COMMENT ON COLUMN pmis_project_opportunity.created_by IS '鍒涘缓浜?ID';

COMMENT ON COLUMN pmis_project_opportunity.created_at IS '鍒涘缓鏃堕棿';

COMMENT ON COLUMN pmis_project_opportunity.updated_by IS '鏈€鍚庝慨鏀逛汉 ID';

COMMENT ON COLUMN pmis_project_opportunity.updated_at IS '鏈€鍚庝慨鏀规椂闂?;

COMMENT ON COLUMN pmis_project_opportunity.deleted IS '閫昏緫鍒犻櫎鏍囪: 0 鏈垹闄?/ 1 宸插垹闄?;

COMMENT ON COLUMN pmis_project_opportunity.tenant_id IS '绉熸埛 ID(鍗曠鎴烽儴缃查粯璁?1)';

COMMENT ON COLUMN pmis_project_opportunity.version IS '涔愯閿佺増鏈彿(鏇存柊鏃惰嚜澧?闃叉骞跺彂瑕嗙洊)';

CREATE INDEX IF NOT EXISTS idx_ppo_customer
    ON pmis_project_opportunity (customer_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppo_owner
    ON pmis_project_opportunity (owner_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppo_status
    ON pmis_project_opportunity (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppo_level
    ON pmis_project_opportunity (level) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 瀹㈡埛 + 鐘舵€?婕忔枟瑙嗗浘)
CREATE INDEX IF NOT EXISTS idx_ppo_tenant_customer_status
    ON pmis_project_opportunity (tenant_id, customer_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 鍒涘缓鏃堕棿鍊掑簭(鍟嗘満涓績鍒楄〃)
CREATE INDEX IF NOT EXISTS idx_ppo_tenant_created
    ON pmis_project_opportunity (tenant_id, created_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 棰勮绛剧害鏃ユ湡绱㈠紩(鐢ㄤ簬璧㈢巼鍔犳潈鏀跺叆棰勬祴鎵弿)
CREATE INDEX IF NOT EXISTS idx_ppo_expected_sign
    ON pmis_project_opportunity (expected_sign_date) WHERE deleted = 0 AND status IN ('FOLLOWING', 'QUOTED', 'NEGOTIATING');

-- =====================================================
-- 2. 鍟嗘満璺熻繘璁板綍 pmis_project_opportunity_follow

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_opportunity_follow(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    opportunity_id    VARCHAR(20)         NOT NULL,
    follow_type       VARCHAR(32)    NOT NULL,
    follow_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    follower_id       VARCHAR(20)         NOT NULL,
    follower_name     VARCHAR(64),
    content           TEXT,
    next_step         TEXT,
    next_follow_date  DATE,
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 鏁版嵁瀹屾暣鎬х害鏉?
    CONSTRAINT ck_ppof_type_enum     CHECK (follow_type IN ('VISIT', 'CALL', 'QUOTE', 'NEGOTIATE', 'OTHER')),
    CONSTRAINT ck_ppof_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppof_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_opportunity_follow IS '鍟嗘満璺熻繘璁板綍: 鎷滆/鐢佃瘽/鎶ヤ环/璋堝垽鐨勭棔杩圭鐞?鏀寔鏃堕棿绾垮洖婧?;

COMMENT ON COLUMN pmis_project_opportunity_follow.id IS '涓婚敭 ID';

COMMENT ON COLUMN pmis_project_opportunity_follow.opportunity_id IS '鍟嗘満 ID(鍏宠仈 pmis_project_opportunity.id)';

COMMENT ON COLUMN pmis_project_opportunity_follow.follow_type IS '璺熻繘绫诲瀷: VISIT 鎷滆 / CALL 鐢佃瘽 / QUOTE 鎶ヤ环 / NEGOTIATE 璋堝垽 / OTHER 鍏朵粬';

COMMENT ON COLUMN pmis_project_opportunity_follow.follow_at IS '璺熻繘鏃堕棿';

COMMENT ON COLUMN pmis_project_opportunity_follow.follower_id IS '璺熻繘浜?ID';

COMMENT ON COLUMN pmis_project_opportunity_follow.follower_name IS '璺熻繘浜哄鍚?;

COMMENT ON COLUMN pmis_project_opportunity_follow.content IS '璺熻繘鍐呭';

COMMENT ON COLUMN pmis_project_opportunity_follow.next_step IS '涓嬩竴姝ヨ鍒?;

COMMENT ON COLUMN pmis_project_opportunity_follow.next_follow_date IS '涓嬫璺熻繘鏃ユ湡';

COMMENT ON COLUMN pmis_project_opportunity_follow.created_by IS '鍒涘缓浜?ID';

COMMENT ON COLUMN pmis_project_opportunity_follow.created_at IS '鍒涘缓鏃堕棿';

COMMENT ON COLUMN pmis_project_opportunity_follow.updated_by IS '鏈€鍚庝慨鏀逛汉 ID';

COMMENT ON COLUMN pmis_project_opportunity_follow.updated_at IS '鏈€鍚庝慨鏀规椂闂?;

COMMENT ON COLUMN pmis_project_opportunity_follow.deleted IS '閫昏緫鍒犻櫎鏍囪: 0 鏈垹闄?/ 1 宸插垹闄?;

COMMENT ON COLUMN pmis_project_opportunity_follow.tenant_id IS '绉熸埛 ID(鍗曠鎴烽儴缃查粯璁?1)';

COMMENT ON COLUMN pmis_project_opportunity_follow.version IS '涔愯閿佺増鏈彿';

-- [INLINE-OPT] 澶嶅悎绱㈠紩:鍟嗘満 + 璺熻繘鏃堕棿鍊掑簭(鏃堕棿绾垮睍绀?
CREATE INDEX IF NOT EXISTS idx_ppof_opp
    ON pmis_project_opportunity_follow (opportunity_id, follow_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 璺熻繘浜?+ 鏃堕棿绱㈠紩(閿€鍞釜浜哄伐浣滃彴)
CREATE INDEX IF NOT EXISTS idx_ppof_follower_at
    ON pmis_project_opportunity_follow (follower_id, follow_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 涓嬫璺熻繘鏃ユ湡鎻愰啋(鍚庡彴鎵弿)
CREATE INDEX IF NOT EXISTS idx_ppof_next_date
    ON pmis_project_opportunity_follow (next_follow_date) WHERE deleted = 0 AND next_follow_date IS NOT NULL;

-- =====================================================
-- 3. 绔嬮」涓昏〃 pmis_project_initiation

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_contract(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    contract_code     VARCHAR(64)    NOT NULL,
    contract_name     VARCHAR(256)   NOT NULL,
    initiation_id     VARCHAR(20),
    customer_id       VARCHAR(20)         NOT NULL,
    customer_name     VARCHAR(256),
    contract_type     VARCHAR(32)    NOT NULL,
    sign_date         DATE,
    effective_date    DATE,
    expire_date       DATE,
    total_amount      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    currency          VARCHAR(8)     NOT NULL DEFAULT 'CNY',
    payment_terms     TEXT,
    billing_cycle     VARCHAR(32),
    tax_rate          NUMERIC(5,4)   NOT NULL DEFAULT 0,
    status            VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    risk_level        VARCHAR(8)     NOT NULL DEFAULT 'LOW',
    risk_notes        TEXT,
    owner_id          VARCHAR(20)         NOT NULL,
    owner_name        VARCHAR(64),
    contract_file_id  VARCHAR(20),
    workflow_id       VARCHAR(20),
    remark            TEXT,
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 鏁版嵁瀹屾暣鎬х害鏉?
    CONSTRAINT uk_ppc_code           UNIQUE (contract_code, deleted),
    CONSTRAINT ck_ppc_type_enum      CHECK (contract_type IN ('FIXED_PRICE', 'T_M', 'OUTSOURCING', 'PRODUCT', 'MAINTENANCE', 'CONSULTING', 'TRAINING', 'OTHER')),
    CONSTRAINT ck_ppc_status_enum    CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVING', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'TERMINATED')),
    CONSTRAINT ck_ppc_billing_enum   CHECK (billing_cycle IS NULL OR billing_cycle IN ('MONTHLY', 'QUARTERLY', 'MILESTONE', 'ONEOFF')),
    CONSTRAINT ck_ppc_risk_enum      CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_ppc_tax_rate_range CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_ppc_amount_nonneg  CHECK (total_amount >= 0),
    CONSTRAINT ck_ppc_dates_valid    CHECK (expire_date IS NULL OR effective_date IS NULL OR expire_date >= effective_date),
    CONSTRAINT ck_ppc_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppc_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_contract IS '鍚堝悓涓昏〃: 椤圭洰绛剧害鍚堝悓,鍏宠仈绔嬮」/瀹㈡埛/浠樻鏉℃,鏀拺寮€绁ㄥ洖娆?;

COMMENT ON COLUMN pmis_project_contract.id IS '涓婚敭 ID';

COMMENT ON COLUMN pmis_project_contract.contract_code IS '鍚堝悓缂栫爜(鍏ㄥ眬鍞竴,濡?CT20260001)';

COMMENT ON COLUMN pmis_project_contract.contract_name IS '鍚堝悓鍚嶇О';

COMMENT ON COLUMN pmis_project_contract.initiation_id IS '鍏宠仈绔嬮」 ID(鍏宠仈 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_project_contract.customer_id IS '瀹㈡埛 ID';

COMMENT ON COLUMN pmis_project_contract.customer_name IS '瀹㈡埛鍚嶇О(鍐椾綑)';

COMMENT ON COLUMN pmis_project_contract.contract_type IS '鍚堝悓绫诲瀷: FIXED_PRICE / T_M / OUTSOURCING / PRODUCT / MAINTENANCE / CONSULTING / TRAINING / OTHER';

COMMENT ON COLUMN pmis_project_contract.sign_date IS '绛剧害鏃ユ湡';

COMMENT ON COLUMN pmis_project_contract.effective_date IS '鍚堝悓鐢熸晥鏃ユ湡';

COMMENT ON COLUMN pmis_project_contract.expire_date IS '鍚堝悓鍒版湡鏃ユ湡';

COMMENT ON COLUMN pmis_project_contract.total_amount IS '鍚堝悓鎬婚(鍏?鍚◣)';

COMMENT ON COLUMN pmis_project_contract.currency IS '甯佺(榛樿 CNY)';

COMMENT ON COLUMN pmis_project_contract.payment_terms IS '浠樻鏉℃(濡?3-3-3-1 棰勪粯/鍚姩/UAT/璐ㄤ繚)';

COMMENT ON COLUMN pmis_project_contract.billing_cycle IS '缁撶畻鍛ㄦ湡(MONTHLY 鏈堢粨 / QUARTERLY 瀛ｇ粨 / MILESTONE 閲岀▼纰?/ ONEOFF 涓€娆℃€?';

COMMENT ON COLUMN pmis_project_contract.tax_rate IS '閫傜敤绋庣巼(0.0000-1.0000)';

COMMENT ON COLUMN pmis_project_contract.status IS '鍚堝悓鐘舵€? DRAFT 鑽夌 / SUBMITTED 宸叉彁浜?/ APPROVING 瀹℃壒涓?/ ACTIVE 鎵ц涓?/ SUSPENDED 鏆傚仠 / EXPIRED 宸插埌鏈?/ TERMINATED 宸茬粓姝?;

COMMENT ON COLUMN pmis_project_contract.risk_level IS '椋庨櫓绛夌骇: LOW 浣?/ MEDIUM 涓?/ HIGH 楂?;

COMMENT ON COLUMN pmis_project_contract.risk_notes IS '椋庨櫓璇存槑';

COMMENT ON COLUMN pmis_project_contract.owner_id IS '鍚堝悓璐熻矗浜?ID(閿€鍞?瀹㈡埛缁忕悊)';

COMMENT ON COLUMN pmis_project_contract.owner_name IS '鍚堝悓璐熻矗浜哄鍚?;

COMMENT ON COLUMN pmis_project_contract.contract_file_id IS '鍚堝悓鏂囦欢 ID(鍏宠仈 pmis_file.id)';

COMMENT ON COLUMN pmis_project_contract.workflow_id IS '瀹℃壒娴佺▼瀹炰緥 ID';

COMMENT ON COLUMN pmis_project_contract.remark IS '澶囨敞';

COMMENT ON COLUMN pmis_project_contract.created_by IS '鍒涘缓浜?ID';

COMMENT ON COLUMN pmis_project_contract.created_at IS '鍒涘缓鏃堕棿';

COMMENT ON COLUMN pmis_project_contract.updated_by IS '鏈€鍚庝慨鏀逛汉 ID';

COMMENT ON COLUMN pmis_project_contract.updated_at IS '鏈€鍚庝慨鏀规椂闂?;

COMMENT ON COLUMN pmis_project_contract.deleted IS '閫昏緫鍒犻櫎鏍囪: 0 鏈垹闄?/ 1 宸插垹闄?;

COMMENT ON COLUMN pmis_project_contract.tenant_id IS '绉熸埛 ID(鍗曠鎴烽儴缃查粯璁?1)';

COMMENT ON COLUMN pmis_project_contract.version IS '涔愯閿佺増鏈彿';

CREATE INDEX IF NOT EXISTS idx_ppc_customer
    ON pmis_project_contract (customer_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppc_init
    ON pmis_project_contract (initiation_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppc_status
    ON pmis_project_contract (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppc_sign
    ON pmis_project_contract (sign_date) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppc_risk
    ON pmis_project_contract (risk_level) WHERE deleted = 0;

-- [INLINE-OPT] 鍚堝悓璐熻矗浜?+ 鐘舵€?閿€鍞悎鍚屽彴璐?
CREATE INDEX IF NOT EXISTS idx_ppc_owner_status
    ON pmis_project_contract (owner_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 鍒版湡鏃ユ湡(鍒版湡棰勮鎵弿)
CREATE INDEX IF NOT EXISTS idx_ppc_tenant_expire
    ON pmis_project_contract (tenant_id, expire_date) WHERE deleted = 0 AND expire_date IS NOT NULL;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 鍒涘缓鏃堕棿鍊掑簭(鍚堝悓涓績鍒楄〃)
CREATE INDEX IF NOT EXISTS idx_ppc_tenant_created
    ON pmis_project_contract (tenant_id, created_at DESC) WHERE deleted = 0;

-- =====================================================
-- 7. 鍚堝悓琛ュ厖鍗忚 pmis_project_contract_supplement

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_contract_supplement(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    contract_id       VARCHAR(20)         NOT NULL,
    supplement_code   VARCHAR(64)    NOT NULL,
    supplement_name   VARCHAR(256)   NOT NULL,
    supplement_type   VARCHAR(32)    NOT NULL,
    change_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    new_total_amount  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    effective_date    DATE,
    expire_date       DATE,
    content           TEXT,
    file_id           VARCHAR(20),
    status            VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 鏁版嵁瀹屾暣鎬х害鏉?
    CONSTRAINT uk_ppcs_code          UNIQUE (supplement_code, deleted),
    CONSTRAINT ck_ppcs_type_enum     CHECK (supplement_type IN ('AMOUNT', 'SCOPE', 'TERM', 'OTHER')),
    CONSTRAINT ck_ppcs_status_enum   CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_ppcs_amount_nonneg CHECK (new_total_amount >= 0),
    CONSTRAINT ck_ppcs_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppcs_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_contract_supplement IS '鍚堝悓琛ュ厖鍗忚: 涓诲悎鍚岀璁㈠悗鐨勯噾棰?鑼冨洿/宸ユ湡/鍏朵粬琛ュ厖鏉℃,娉曞姟澶囨';

COMMENT ON COLUMN pmis_project_contract_supplement.id IS '涓婚敭 ID';

COMMENT ON COLUMN pmis_project_contract_supplement.contract_id IS '涓诲悎鍚?ID(鍏宠仈 pmis_project_contract.id)';

COMMENT ON COLUMN pmis_project_contract_supplement.supplement_code IS '琛ュ厖鍗忚缂栫爜(鍏ㄥ眬鍞竴)';

COMMENT ON COLUMN pmis_project_contract_supplement.supplement_name IS '琛ュ厖鍗忚鍚嶇О';

COMMENT ON COLUMN pmis_project_contract_supplement.supplement_type IS '琛ュ厖绫诲瀷: AMOUNT 閲戦 / SCOPE 鑼冨洿 / TERM 宸ユ湡 / OTHER 鍏朵粬';

COMMENT ON COLUMN pmis_project_contract_supplement.change_amount IS '鍙樻洿閲戦(鍙鍙礋)';

COMMENT ON COLUMN pmis_project_contract_supplement.new_total_amount IS '鍙樻洿鍚庡悎鍚屾€婚';

COMMENT ON COLUMN pmis_project_contract_supplement.effective_date IS '鐢熸晥鏃ユ湡';

COMMENT ON COLUMN pmis_project_contract_supplement.expire_date IS '鍒版湡鏃ユ湡';

COMMENT ON COLUMN pmis_project_contract_supplement.content IS '鍗忚姝ｆ枃';

COMMENT ON COLUMN pmis_project_contract_supplement.file_id IS '鍗忚鏂囦欢 ID(鍏宠仈 pmis_file.id)';

COMMENT ON COLUMN pmis_project_contract_supplement.status IS '鐘舵€? DRAFT 鑽夌 / APPROVED 宸茬 / REJECTED 宸查┏鍥?;

COMMENT ON COLUMN pmis_project_contract_supplement.created_by IS '鍒涘缓浜?ID';

COMMENT ON COLUMN pmis_project_contract_supplement.created_at IS '鍒涘缓鏃堕棿';

COMMENT ON COLUMN pmis_project_contract_supplement.updated_by IS '鏈€鍚庝慨鏀逛汉 ID';

COMMENT ON COLUMN pmis_project_contract_supplement.updated_at IS '鏈€鍚庝慨鏀规椂闂?;

COMMENT ON COLUMN pmis_project_contract_supplement.deleted IS '閫昏緫鍒犻櫎鏍囪: 0 鏈垹闄?/ 1 宸插垹闄?;

COMMENT ON COLUMN pmis_project_contract_supplement.tenant_id IS '绉熸埛 ID(鍗曠鎴烽儴缃查粯璁?1)';

COMMENT ON COLUMN pmis_project_contract_supplement.version IS '涔愯閿佺増鏈彿';

-- [INLINE-OPT] 澶嶅悎绱㈠紩:鍚堝悓 + 绫诲瀷(鎸夌被鍨嬫煡鐪嬭ˉ鍏呭崗璁?
CREATE INDEX IF NOT EXISTS idx_ppcs_contract_type
    ON pmis_project_contract_supplement (contract_id, supplement_type) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 鐘舵€?琛ュ厖鍗忚鍙拌处)
CREATE INDEX IF NOT EXISTS idx_ppcs_tenant_status
    ON pmis_project_contract_supplement (tenant_id, status) WHERE deleted = 0;

-- =====================================================
-- 8. 鍚堝悓鍙樻洿璁板綍 pmis_project_contract_change

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_contract_change(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    contract_id       VARCHAR(20)         NOT NULL,
    change_code       VARCHAR(64)    NOT NULL,
    change_type       VARCHAR(32)    NOT NULL,
    change_reason     TEXT,
    before_value      TEXT,
    after_value       TEXT,
    amount_delta      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    impact_analysis   TEXT,
    status            VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    applicant_id      VARCHAR(20),
    applicant_name    VARCHAR(64),
    approver_id       VARCHAR(20),
    approver_name     VARCHAR(64),
    approved_at       TIMESTAMPTZ,
    workflow_id       VARCHAR(20),
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 鏁版嵁瀹屾暣鎬х害鏉?
    CONSTRAINT uk_ppcc_code          UNIQUE (change_code, deleted),
    CONSTRAINT ck_ppcc_type_enum     CHECK (change_type IN ('SCOPE', 'AMOUNT', 'TERM', 'PERSONNEL', 'PROGRESS')),
    CONSTRAINT ck_ppcc_status_enum   CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_ppcc_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppcc_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_contract_change IS '鍚堝悓鍙樻洿璁板綍: 鑼冨洿/閲戦/宸ユ湡/浜哄憳/杩涘害鐨勫彉鏇?闇€璧板鎵规祦';

COMMENT ON COLUMN pmis_project_contract_change.id IS '涓婚敭 ID';

COMMENT ON COLUMN pmis_project_contract_change.contract_id IS '涓诲悎鍚?ID(鍏宠仈 pmis_project_contract.id)';

COMMENT ON COLUMN pmis_project_contract_change.change_code IS '鍙樻洿鍗曠紪鐮?鍏ㄥ眬鍞竴)';

COMMENT ON COLUMN pmis_project_contract_change.change_type IS '鍙樻洿绫诲瀷: SCOPE 鑼冨洿 / AMOUNT 閲戦 / TERM 宸ユ湡 / PERSONNEL 浜哄憳 / PROGRESS 杩涘害';

COMMENT ON COLUMN pmis_project_contract_change.change_reason IS '鍙樻洿鍘熷洜';

COMMENT ON COLUMN pmis_project_contract_change.before_value IS '鍙樻洿鍓嶅€?;

COMMENT ON COLUMN pmis_project_contract_change.after_value IS '鍙樻洿鍚庡€?;

COMMENT ON COLUMN pmis_project_contract_change.amount_delta IS '閲戦鍙樺寲(鍙鍙礋)';

COMMENT ON COLUMN pmis_project_contract_change.impact_analysis IS '褰卞搷鍒嗘瀽(鑼冨洿/宸ユ湡/鎴愭湰/椋庨櫓)';

COMMENT ON COLUMN pmis_project_contract_change.status IS '鐘舵€? DRAFT 鑽夌 / SUBMITTED 宸叉彁浜?/ APPROVING 瀹℃壒涓?/ APPROVED 宸叉壒鍑?/ REJECTED 宸查┏鍥?;

COMMENT ON COLUMN pmis_project_contract_change.applicant_id IS '鐢宠浜?ID';

COMMENT ON COLUMN pmis_project_contract_change.applicant_name IS '鐢宠浜哄鍚?;

COMMENT ON COLUMN pmis_project_contract_change.approver_id IS '瀹℃壒浜?ID';

COMMENT ON COLUMN pmis_project_contract_change.approver_name IS '瀹℃壒浜哄鍚?;

COMMENT ON COLUMN pmis_project_contract_change.approved_at IS '瀹℃壒鏃堕棿';

COMMENT ON COLUMN pmis_project_contract_change.workflow_id IS '瀹℃壒娴佺▼瀹炰緥 ID';

COMMENT ON COLUMN pmis_project_contract_change.created_by IS '鍒涘缓浜?ID';

COMMENT ON COLUMN pmis_project_contract_change.created_at IS '鍒涘缓鏃堕棿';

COMMENT ON COLUMN pmis_project_contract_change.updated_by IS '鏈€鍚庝慨鏀逛汉 ID';

COMMENT ON COLUMN pmis_project_contract_change.updated_at IS '鏈€鍚庝慨鏀规椂闂?;

COMMENT ON COLUMN pmis_project_contract_change.deleted IS '閫昏緫鍒犻櫎鏍囪: 0 鏈垹闄?/ 1 宸插垹闄?;

COMMENT ON COLUMN pmis_project_contract_change.tenant_id IS '绉熸埛 ID(鍗曠鎴烽儴缃查粯璁?1)';

COMMENT ON COLUMN pmis_project_contract_change.version IS '涔愯閿佺増鏈彿';

-- [INLINE-OPT] 澶嶅悎绱㈠紩:鍚堝悓 + 绫诲瀷(鍚堝悓鍙樻洿鍘嗗彶)
CREATE INDEX IF NOT EXISTS idx_ppcc_contract_type
    ON pmis_project_contract_change (contract_id, change_type) WHERE deleted = 0;

-- [INLINE-OPT] 鐘舵€佺储寮?寰呭鎵瑰伐浣滃彴)
CREATE INDEX IF NOT EXISTS idx_ppcc_status
    ON pmis_project_contract_change (status) WHERE deleted = 0;

-- [INLINE-OPT] 瀹℃壒浜?+ 鐘舵€?瀹℃壒浜哄伐浣滃彴)
CREATE INDEX IF NOT EXISTS idx_ppcc_approver_status
    ON pmis_project_contract_change (approver_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 澶嶅悎绱㈠紩:绉熸埛 + 瀹℃壒鏃堕棿鍊掑簭(鍙樻洿瀹¤)
CREATE INDEX IF NOT EXISTS idx_ppcc_tenant_approved
    ON pmis_project_contract_change (tenant_id, approved_at DESC) WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ============================ [010] init pmis execution schema ============================
-- [INLINE-OPT] 宸茬粺涓€涓哄崟鏂囦欢 V1.0.0.sql 鐨勬渶缁堝舰鎬?
--   1) 鏃堕棿瀛楁 TIMESTAMP 鈫?TIMESTAMPTZ
--   2) 鍏ㄩ儴瀹¤瀛楁缁熶竴涓?created_by/created_at/updated_by/updated_at
--   3) tenant_id NOT NULL DEFAULT 1
--   4) 鍐呰仈 status/category/type/deleted/window_check CHECK 绾︽潫
--   5) 鍐呰仈 (tenant_id, created_at DESC) WHERE deleted = 0 澶嶅悎閮ㄥ垎绱㈠紩
--   6) status/owner/category 绫荤储寮曞叏閮ㄥ姞 WHERE deleted = 0 閮ㄥ垎鏉′欢
--   7) 鍐呰仈 (initiation_id, period) 绛変笟鍔′笓鐢ㄥ鍚堢储寮?
-- =====================================================
-- PMIS 椤圭洰鎵ц/鎴愭湰/鍒╂鼎妯″潡 DDL
-- 鐗堟湰: V1.0.0_010 (merged into V1.0.0.sql)
-- 鎻忚堪: WBS 浠诲姟銆佸伐鏃躲€佹垚鏈綊闆嗐€佸埄娑︽牳绠?
-- =====================================================

-- =====================================================
-- 1. WBS 浠诲姟琛?pmis_execution_wbs_task

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_contract_template(
    id                     VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    template_code          VARCHAR(64)  NOT NULL,
    template_name          VARCHAR(256) NOT NULL,
    contract_type          VARCHAR(32)  NOT NULL,            -- FIXED_PRICE/T_M/OUTSOURCING/PRODUCT/MAINTENANCE/CONSULTING/TRAINING/OTHER
    version                VARCHAR(32)  NOT NULL DEFAULT '1.0.0',
    payment_terms          TEXT,
    default_payment_days   INTEGER      NOT NULL DEFAULT 30,
    default_penalty_rate   NUMERIC(5,4) NOT NULL DEFAULT 0,
    sla_description        TEXT,
    deliverables           TEXT,
    content                TEXT,                              -- 妯℃澘姝ｆ枃
    customer_level         VARCHAR(16),                      -- A/B/C/D
    project_level          VARCHAR(16),                      -- L1-L18
    status                 VARCHAR(32)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/PUBLISHED/DEPRECATED
    author_id              VARCHAR(20),
    author_name            VARCHAR(64),
    remark                 TEXT,
    tenant_id              VARCHAR(20)       NOT NULL DEFAULT '1',
    created_by             VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                SMALLINT     NOT NULL DEFAULT 0,
    -- 涓氬姟鍞竴鎬? 鍚岀鎴蜂笅 template_code + 杞垹闄や綅 鍞竴
    CONSTRAINT uk_ppct_code            UNIQUE (template_code, deleted),
    -- 鏋氫妇绾︽潫
    CONSTRAINT ck_ppct_contract_type   CHECK (contract_type IN ('FIXED_PRICE','T_M','OUTSOURCING','PRODUCT','MAINTENANCE','CONSULTING','TRAINING','OTHER')),
    CONSTRAINT ck_ppct_customer_level  CHECK (customer_level IS NULL OR customer_level IN ('A','B','C','D')),
    CONSTRAINT ck_ppct_status_enum     CHECK (status IN ('DRAFT','PUBLISHED','DEPRECATED')),
    -- 鏁板€奸潪璐?/ 姣斾緥鑼冨洿
    CONSTRAINT ck_ppct_payment_days    CHECK (default_payment_days >= 0),
    CONSTRAINT ck_ppct_penalty_range   CHECK (default_penalty_rate >= 0 AND default_penalty_rate <= 1),
    CONSTRAINT ck_ppct_deleted_enum    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_project_contract_template IS '鍚堝悓妯℃澘琛? 8 绫婚」鐩被鍨嬶紙FIXED_PRICE/T_M/OUTSOURCING/PRODUCT/MAINTENANCE/CONSULTING/TRAINING/OTHER锛夌殑鏍囧噯鍖栧悎鍚屾ā鏉?鍚堝悓璧疯崏鏃舵寜绫诲瀷寮曠敤';

COMMENT ON COLUMN pmis_project_contract_template.template_code IS '妯℃澘缂栫爜: 涓氬姟鍞竴,濡?TPL-FIX-001';

COMMENT ON COLUMN pmis_project_contract_template.template_name IS '妯℃澘鍚嶇О';

COMMENT ON COLUMN pmis_project_contract_template.contract_type IS '鍚堝悓绫诲瀷: FIXED_PRICE 鍥哄畾鎬讳环 / T_M 浜烘湀璁¤垂 / OUTSOURCING 浜哄姏澶栧寘 / PRODUCT 浜у搧閿€鍞?/ MAINTENANCE 杩愮淮鏈嶅姟 / CONSULTING 鍜ㄨ鏈嶅姟 / TRAINING 鍩硅鏈嶅姟 / OTHER 鍏朵粬';

COMMENT ON COLUMN pmis_project_contract_template.version IS '妯℃澘鐗堟湰鍙? 璇箟鍖栫増鏈?榛樿 1.0.0';

COMMENT ON COLUMN pmis_project_contract_template.payment_terms IS '浠樻鏉℃: 鏂囨湰鎻忚堪,渚嬪"3-3-3-1"鍒嗛樁娈垫瘮渚?;

COMMENT ON COLUMN pmis_project_contract_template.default_payment_days IS '榛樿璐︽湡(澶?: 0=棰勪粯,30=鏈堢粨30澶?;

COMMENT ON COLUMN pmis_project_contract_template.default_penalty_rate IS '榛樿杩濈害閲戞瘮渚? 0.0010=鍗冨垎涔嬩竴,浣滀负鍚堝悓鍩哄噯';

COMMENT ON COLUMN pmis_project_contract_template.sla_description IS 'SLA 鎻忚堪: 鏈嶅姟绛夌骇鍗忚,渚嬪 P1 4 灏忔椂鍝嶅簲';

COMMENT ON COLUMN pmis_project_contract_template.deliverables IS '浜や粯鐗╂竻鍗? 鍚堝悓绾﹀畾鐨勪氦浠樼墿鍒楄〃';

COMMENT ON COLUMN pmis_project_contract_template.content IS '妯℃澘姝ｆ枃: 鍚崰浣嶇 ${} 鐨勫悎鍚屾鏂?;

COMMENT ON COLUMN pmis_project_contract_template.customer_level IS '瀹㈡埛绾у埆: A/B/C/D 淇＄敤绛夌骇,NULL=鍏ㄧ骇鍒€傜敤';

COMMENT ON COLUMN pmis_project_contract_template.project_level IS '椤圭洰绾у埆: L1-L18 澶嶆潅搴︾瓑绾?NULL=鍏ㄧ骇鍒€傜敤';

COMMENT ON COLUMN pmis_project_contract_template.status IS '妯℃澘鐘舵€? DRAFT 鑽夌 / PUBLISHED 宸插彂甯?/ DEPRECATED 宸插簾寮?鐘舵€佹満绾挎€?;

COMMENT ON COLUMN pmis_project_contract_template.author_id IS '妯℃澘浣滆€?ID';

COMMENT ON COLUMN pmis_project_contract_template.author_name IS '妯℃澘浣滆€呭鍚嶏紙鍐椾綑锛?;

COMMENT ON COLUMN pmis_project_contract_template.remark IS '澶囨敞';

COMMENT ON COLUMN pmis_project_contract_template.tenant_id IS '绉熸埛 ID: 澶氱鎴烽殧绂?;

COMMENT ON COLUMN pmis_project_contract_template.deleted IS '閫昏緫鍒犻櫎: 0=鏈垹闄?1=宸插垹闄?;

-- 澶嶅悎/閮ㄥ垎绱㈠紩(鏇夸唬闆舵暎鐨?idx_ppct_type_status / idx_ppct_tenant)
CREATE INDEX IF NOT EXISTS idx_ppct_tenant_type_status
    ON pmis_project_contract_template(tenant_id, contract_type, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppct_tenant_created
    ON pmis_project_contract_template(tenant_id, created_at DESC)
    WHERE deleted = 0;

-- =====================================================
-- 2. 椤圭洰鍙樻洿涓昏〃 pmis_project_change

