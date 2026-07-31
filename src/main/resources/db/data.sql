-- 初始化数据：禁播词 + 1 个测试话术模板 + 1 个客户风险评估

-- 禁播词
INSERT INTO tb_forbidden_phrase (id, phrase, severity, product_types, regulation_ref) VALUES
('fp-001', '保证收益', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-002', '稳赚不赔', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-003', '保本保息', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-004', '绝对安全', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-005', '和存款一样', 'MEDIUM', 'WEALTH,FUND', '商业银行理财业务监督管理办法'),
('fp-006', '肯定超过', 'MEDIUM', 'WEALTH,FUND', '商业银行理财业务监督管理办法'),
('fp-007', '无风险', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-008', '基本不会亏', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-009', '肯定盈利', 'HIGH', 'FUND', '基金募集机构投资者适当性管理实施指引'),
('fp-010', '一定不亏', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条');

-- 1 个测试话术模板（理财稳健型）
INSERT INTO tb_script_template (
    id, product_id, product_type, version, risk_level,
    mandatory_disclosure, forbidden_phrases, required_questions,
    channel_overrides, content_hash, status, approved_by, approved_at
) VALUES (
    'st-001', 'BNK-FIN-2026Q3-001', 'WEALTH', '2026Q3-R1', 'R2',
    '["本产品为非保本浮动收益型理财，不保证本金和收益", "业绩比较基准不代表实际收益，过往业绩不代表未来表现", "产品有 180 天封闭期，封闭期内不可提前赎回", "本金及收益风险由投资者自行承担"]',
    '["保本保息", "无风险", "稳赚不赔", "绝对安全", "和存款一样", "肯定超过 3.8%"]',
    '["您是否已了解产品为非保本浮动收益", "您是否已了解业绩比较基准的含义", "您是否已了解 180 天封闭期限制", "您的投资目标是否与本产品期限匹配"]',
    '{"OFFLINE": {"sync_mode": "same_frame"}, "REMOTE_VIDEO": {"sync_mode": "same_frame"}, "SELF_AI": {"ai_disclosure": "per_minute", "watermark": true}, "INTERNET_TEXT": {"sync_mode": "text_only"}}',
    'a3f5d2e8b2c4e6f8', 'APPROVED', 'compliance-team-001', CURRENT_TIMESTAMP
);

-- 1 个客户风险评估
INSERT INTO tb_risk_assessment (
    id, customer_id_hash, assessment_id, answers_json,
    overall_score, risk_level, valid_until
) VALUES (
    'ra-001', 'cust-hash-001', 'ASSESS20260801-0001',
    '{"q1_age":"35-50","q2_income":"500k-1m","q3_experience":"3-5年","q4_loss_tolerance":"20%","q5_horizon":"1-3年"}',
    65.0, 'C3', DATEADD('MONTH', 12, CURRENT_DATE)
);
