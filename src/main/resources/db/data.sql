-- =========================================================================
-- 初始化数据 v1.2
-- 覆盖: 禁播词 50+ / 话术模板 5 / 风险评估 3 / 测试业务 4 / 录像 5 / 节点 12 / 事件 25
-- =========================================================================

-- 1. 禁播词 (50+ 覆盖保险/理财/基金/数字人/远程 5 大场景)
-- 注意: phrase 长度 >= 3, 避免误报正常话术中的子串 (如 "非保本" 不应触发 "保本" 禁播词)
INSERT INTO tb_forbidden_phrase (id, phrase, severity, product_types, regulation_ref) VALUES
-- 保险产品专属
('fp-001', '保证收益', 'HIGH', 'INSURANCE', '金发〔2026〕8号-第二十一条'),
('fp-002', '稳赚不赔', 'HIGH', 'INSURANCE,WEALTH', '金发〔2026〕8号-第二十一条'),
('fp-003', '保本保息', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-004', '绝对安全', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-005', '和存款一样', 'MEDIUM', 'WEALTH,INSURANCE', '商业银行理财业务监督管理办法'),
('fp-006', '肯定超过', 'MEDIUM', 'WEALTH,INSURANCE', '商业银行理财业务监督管理办法'),
('fp-007', '无风险', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-008', '基本不会亏', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-009', '肯定盈利', 'HIGH', 'FUND,WEALTH', '基金募集机构投资者适当性管理实施指引'),
('fp-010', '一定不亏', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-011', '稳赚保底', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-012', '稳赢获利', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-013', '不会亏本', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-014', '保本承诺', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-015', '保证不会亏', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-016', '没有任何风险', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-017', '最安全的投资', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-018', '零亏损保证', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-019', '保证赚钱', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-020', '只赚不赔', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
-- 银行理财专属
('fp-021', '肯定高于银行', 'HIGH', 'WEALTH,INSURANCE', '商业银行理财业务监督管理办法'),
('fp-022', '比银行利息高', 'HIGH', 'WEALTH', '商业银行理财业务监督管理办法'),
('fp-023', '保本型理财', 'HIGH', 'WEALTH', '商业银行理财业务监督管理办法'),
('fp-024', '刚性兑付', 'HIGH', 'WEALTH', '商业银行理财业务监督管理办法'),
('fp-025', '肯定能拿回本金', 'HIGH', 'WEALTH', '商业银行理财业务监督管理办法'),
('fp-026', '和定期一样', 'MEDIUM', 'WEALTH', '商业银行理财业务监督管理办法'),
('fp-027', '银行保本产品', 'HIGH', 'WEALTH', '商业银行理财业务监督管理办法'),
('fp-028', '无亏损风险', 'HIGH', 'WEALTH', '商业银行理财业务监督管理办法'),
('fp-029', '保本型基金', 'HIGH', 'FUND', '基金募集机构投资者适当性管理实施指引'),
('fp-030', '净值保本承诺', 'HIGH', 'FUND', '基金募集机构投资者适当性管理实施指引'),
-- 数字人 / 远程专属
('fp-031', '我是真人', 'HIGH', 'ALL', '金发〔2026〕8号-第二十六条 (数字人必须标识)'),
('fp-032', '我是理财经理', 'HIGH', 'ALL', '金发〔2026〕8号-第二十六条'),
('fp-033', '本对话 AI 不会参与', 'HIGH', 'ALL', '金发〔2026〕8号-第二十六条'),
('fp-034', '本次对话无 AI', 'HIGH', 'ALL', '金发〔2026〕8号-第二十六条'),
('fp-035', '本产品无风险等级', 'HIGH', 'ALL', '金融机构产品适当性管理办法'),
-- 收益承诺 / 夸大
('fp-036', '稳赚三成', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-037', '半年翻倍', 'MEDIUM', 'FUND,WEALTH', '基金募集机构投资者适当性管理实施指引'),
('fp-038', '高收益低风险', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-039', '一年回本保证', 'MEDIUM', 'FUND,WEALTH', '基金募集机构投资者适当性管理实施指引'),
('fp-040', '保证上涨', 'HIGH', 'ALL', '金发〔2026〕8号-第二十一条'),
('fp-041', '肯定持续上涨', 'HIGH', 'FUND', '基金募集机构投资者适当性管理实施指引'),
('fp-042', '独家内幕消息', 'HIGH', 'ALL', '证券法-第七十六条'),
('fp-043', '内部消息渠道', 'HIGH', 'ALL', '证券法-第七十六条'),
('fp-044', '稳赚不赔的理财', 'HIGH', 'WEALTH', '商业银行理财业务监督管理办法'),
('fp-045', '最后机会', 'MEDIUM', 'ALL', '消费者权益保护法-第八条'),
('fp-046', '错过就没了', 'MEDIUM', 'ALL', '消费者权益保护法-第八条'),
('fp-047', '仅供 VIP 购买', 'MEDIUM', 'ALL', '消费者权益保护法-第八条'),
('fp-048', '有内部渠道', 'MEDIUM', 'ALL', '消费者权益保护法-第八条'),
('fp-049', '买到就是赚到', 'HIGH', 'ALL', '消费者权益保护法-第八条'),
('fp-050', '一夜暴富神话', 'HIGH', 'ALL', '消费者权益保护法-第八条'),
-- 跨渠道统一
('fp-051', '和股票一样稳', 'MEDIUM', 'ALL', '金融机构产品适当性管理办法'),
('fp-052', '和保险一样', 'MEDIUM', 'ALL', '金融机构产品适当性管理办法'),
('fp-053', '百分百保证', 'HIGH', 'ALL', '广告法-第二十八条'),
('fp-054', '绝对最高', 'MEDIUM', 'ALL', '广告法-第二十八条'),
('fp-055', '最佳收益', 'MEDIUM', 'ALL', '广告法-第二十八条'),
('fp-056', '最好选择', 'MEDIUM', 'ALL', '广告法-第二十八条'),
('fp-057', '首选产品', 'MEDIUM', 'ALL', '广告法-第二十八条'),
('fp-058', '行业第一', 'MEDIUM', 'ALL', '广告法-第二十八条'),
('fp-059', '顶级收益', 'MEDIUM', 'ALL', '广告法-第二十八条'),
('fp-060', '国家级安全', 'MEDIUM', 'ALL', '广告法-第二十八条');

-- 2. 话术模板 5 个 (覆盖 3 族 5 产品)
INSERT INTO tb_script_template (id, product_id, product_type, version, risk_level, mandatory_disclosure, forbidden_phrases, required_questions, channel_overrides, content_hash, status, approved_by, approved_at) VALUES
-- 投连险 P5
('st-001', 'LIC-INV-2026Q3-001', 'INSURANCE', '2026Q3-R1', 'P5',
 '["本产品属于投资连结型保险, 投资部分回报具有不确定性", "可能低于定期存款或出现本金损失", "保险公司不保证最低收益, 不承诺保本", "犹豫期为本合同生效之日起 15 天内, 犹豫期内退保仅扣除不超过 10 元的工本费"]',
 '["保证收益", "稳赚", "保本", "肯定高于银行", "绝对安全", "基本不会亏", "肯定盈利"]',
 '["您是否已了解本产品属于投资连结型保险", "您是否了解投资部分回报具有不确定性, 可能损失本金", "您是否了解 15 天犹豫期及退保规则", "您的投资目标是否与本产品风险等级匹配"]',
 '{"OFFLINE":{"sync_mode":"same_frame"},"REMOTE_VIDEO":{"sync_mode":"same_frame"},"SELF_AI":{"ai_disclosure":"per_minute","watermark":true},"INTERNET_TEXT":{"sync_mode":"text_only"}}',
 'b2c4e6f8a3f5d2e8', 'APPROVED', 'compliance-team-001', CURRENT_TIMESTAMP),

-- 银行稳健理财 R2
('st-002', 'BNK-FIN-2026Q3-001', 'WEALTH', '2026Q3-R1', 'R2',
 '["本产品为非保本浮动收益型理财, 不保证本金和收益", "业绩比较基准 3.0%-3.8% 不代表实际收益, 过往业绩不代表未来表现", "产品有 180 天封闭期, 封闭期内不可提前赎回", "本金及收益风险由投资者自行承担"]',
 '["保本保息", "无风险", "稳赚不赔", "和存款一样", "肯定超过 3.8%", "保本型理财", "刚性兑付"]',
 '["您是否已了解产品为非保本浮动收益", "您是否已了解业绩比较基准的含义", "您是否已了解 180 天封闭期限制", "您的投资目标是否与本产品期限匹配"]',
 '{"OFFLINE":{"sync_mode":"same_frame"},"REMOTE_VIDEO":{"sync_mode":"same_frame"},"SELF_AI":{"ai_disclosure":"per_minute","watermark":true},"INTERNET_TEXT":{"sync_mode":"text_only"}}',
 'c3d4e5f6a7b8c9d0', 'APPROVED', 'compliance-team-001', CURRENT_TIMESTAMP),

-- 纯债基金 R2
('st-003', 'FND-BOND-2026Q3-001', 'FUND', '2026Q3-R1', 'R2',
 '["基金有风险, 投资须谨慎", "本基金净值会随市场波动, 过往业绩不代表未来表现", "本基金不保证保本及最低收益", "您有权在 T+1 日申请赎回"]',
 '["保本", "无风险", "承诺收益", "稳赚不赔", "一定盈利", "肯定不亏", "保本型基金", "净值保本"]',
 '["您是否已了解基金净值会随市场波动", "您是否了解本基金主要投资于债券资产", "您的投资期限是否符合本基金定位"]',
 '{"OFFLINE":{"sync_mode":"same_frame"},"REMOTE_VIDEO":{"sync_mode":"same_frame"},"SELF_AI":{"ai_disclosure":"per_minute","watermark":true},"INTERNET_TEXT":{"sync_mode":"text_only"}}',
 'd4e5f6a7b8c9d0e1', 'APPROVED', 'compliance-team-002', CURRENT_TIMESTAMP),

-- 银行混合理财 R3 (新增)
('st-004', 'BNK-MIX-2026Q3-002', 'WEALTH', '2026Q3-R1', 'R3',
 '["本产品为混合型理财, 投资于债券+股票, 净值会有波动", "业绩比较基准 4.0%-6.0% 不代表实际收益", "产品有 365 天封闭期", "本金及收益风险由投资者自行承担"]',
 '["保本保息", "无风险", "稳赚", "和存款一样", "肯定超过 6%", "保本型理财"]',
 '["您是否已了解混合型理财的净值波动风险", "您是否了解股票部分的市场风险", "您的投资目标是否与本产品 R3 风险等级匹配"]',
 '{"OFFLINE":{"sync_mode":"same_frame"},"REMOTE_VIDEO":{"sync_mode":"same_frame"},"SELF_AI":{"ai_disclosure":"per_minute","watermark":true},"INTERNET_TEXT":{"sync_mode":"text_only"}}',
 'e5f6a7b8c9d0e1f2', 'APPROVED', 'compliance-team-001', CURRENT_TIMESTAMP),

-- 股票基金 R4 (新增)
('st-005', 'FND-STK-2026Q3-002', 'FUND', '2026Q3-R1', 'R4',
 '["本基金主要投资于股票资产, 净值波动较大", "基金有风险, 投资须谨慎", "过往业绩不代表未来表现", "本基金不保证保本及最低收益"]',
 '["保本", "稳赚", "无风险", "肯定涨", "翻倍", "净值保本", "保本型基金"]',
 '["您是否已了解股票型基金的高波动风险", "您是否能承受短期 30% 以上的净值波动", "您的投资期限是否符合股票型基金定位"]',
 '{"OFFLINE":{"sync_mode":"same_frame"},"REMOTE_VIDEO":{"sync_mode":"same_frame"},"SELF_AI":{"ai_disclosure":"per_minute","watermark":true},"INTERNET_TEXT":{"sync_mode":"text_only"}}',
 'f6a7b8c9d0e1f2a3', 'APPROVED', 'compliance-team-002', CURRENT_TIMESTAMP);

-- 3. 客户风险评估 3 笔 (C3/C3/C5 代表性)
INSERT INTO tb_risk_assessment (id, customer_id_hash, assessment_id, answers_json, overall_score, risk_level, valid_until) VALUES
('ra-001', 'cust-hash-001', 'ASSESS20260801-0001',
 '{"q1_age":"35-50","q2_income":"500k-1m","q3_experience":"3-5年","q4_loss_tolerance":"20%","q5_horizon":"1-3年"}',
 65.0, 'C3', DATEADD('MONTH', 12, CURRENT_DATE)),

('ra-002', 'cust-hash-002', 'ASSESS20260801-0002',
 '{"liquidity":"30天","maturity":"30-90天","leverage":"无","structural_complexity":"普通理财","min_amount":"1万元","investment_direction":"股债混合","offering_method":"公募","issuer_credit":"AA","historical_performance":"15-30%"}',
 50.0, 'C3', DATEADD('MONTH', 12, CURRENT_DATE)),

('ra-003', 'cust-hash-003', 'ASSESS20260801-0003',
 '{"liquidity":"1年","maturity":">1年","leverage":"1:5","structural_complexity":"衍生品/PE","min_amount":"100万","investment_direction":"私募/海外","offering_method":"私募+跨境","issuer_credit":"BBB","historical_performance":"50%+,高弹性"}',
 90.0, 'C5', DATEADD('MONTH', 12, CURRENT_DATE));

-- 4. 测试业务 4 笔 (覆盖不同状态)
INSERT INTO tb_business (id, business_id, business_type, product_id, customer_id_hash, seller_id_hash, channel, state, current_node, amount, risk_level, product_risk_level, created_at, updated_at, archived_at, deleted) VALUES
-- 已归档 (成功)
('biz-001', 'BNK20260801-900001', 'WEALTH', 'BNK-FIN-2026Q3-001', 'cust-hash-001', 'seller-hash-001', 'OFFLINE', 'ARCHIVED', '08-FOLLOWUP', 50000.00, 'C1', 'R2', TIMESTAMP '2026-07-20 10:00:00', TIMESTAMP '2026-07-20 10:45:00', TIMESTAMP '2026-07-20 10:45:00', 0),

-- 已归档 (高风险客户买高风险产品)
('biz-002', 'LIC20260801-900001', 'INSURANCE', 'LIC-INV-2026Q3-001', 'cust-hash-003', 'seller-hash-002', 'REMOTE_VIDEO', 'ARCHIVED', '08-FOLLOWUP', 100000.00, 'C5', 'P5', TIMESTAMP '2026-07-25 14:00:00', TIMESTAMP '2026-07-25 14:50:00', TIMESTAMP '2026-07-25 14:50:00', 0),

-- 进行中 (8 节点完成中)
('biz-003', 'BNK20260801-900003', 'WEALTH', 'BNK-MIX-2026Q3-002', 'cust-hash-002', 'seller-hash-003', 'OFFLINE', 'RECORDING', '05-TRUTH_TELL', 80000.00, 'C3', 'R3', TIMESTAMP '2026-08-01 09:00:00', TIMESTAMP '2026-08-01 09:25:00', NULL, 0),

-- 失败 (异常)
('biz-004', 'FND20260801-900004', 'FUND', 'FND-STK-2026Q3-002', 'cust-hash-002', 'seller-hash-001', 'SELF_AI', 'FAILED', '02-DISCLOSURE', 60000.00, 'C3', 'R4', TIMESTAMP '2026-08-01 10:00:00', TIMESTAMP '2026-08-01 10:15:00', NULL, 0);

-- 5. 录像 5 段 (每笔业务 1 段, biz-001/biz-002 含数字人 2 段)
INSERT INTO tb_recording (id, rec_id, business_id, channel, seller_type, rec_start_utc, rec_end_utc, duration_ms, file_path, file_sha256, file_size_bytes, encryption, blockchain_tx, watermark_visible, audio_id_per_minute, linked_rec_id, location_branch, retention_until, quality_score, quality_status, resolution, fps, audio_bitrate, black_frame_ratio, customer_face_ratio, third_party_count, location_lat, location_lng, ip_address, device_fingerprint, encryption_iv, signed_hash, preservation_id, retention_notified_at, created_at, deleted) VALUES
-- biz-001 线下录像
('rec-001', 'REC20260801-9001', 'BNK20260801-900001', 'OFFLINE', 'HUMAN',
  TIMESTAMP '2026-07-20 10:00:00', TIMESTAMP '2026-07-20 10:45:00', 2700000,
  '/recordings/20260720/BNK20260801-000001.mp4',
  'a3f5d2e8b2c4e6f8a3f5d2e8b2c4e6f8a3f5d2e8b2c4e6f8a3f5d2e8b2c4e6f8',
  52428800, 'SM4-CBC', '0xabc123def456', 0, 0, NULL, '北京朝阳支行',
  DATE '2036-07-20', 95, 'PASS', '1920x1080', 25, 64000, 0.50, 95.00, 0, 39.904200, 116.407400, '192.168.1.1', 'dev-bj-cyz-001', 'iv-001', 'sig-001', NULL, NULL, TIMESTAMP '2026-07-20 10:00:00', 0),

-- biz-002 远程 (1 段: 远程视频)
('rec-002', 'REC20260801-9002', 'LIC20260801-900001', 'REMOTE_VIDEO', 'HUMAN',
  TIMESTAMP '2026-07-25 14:00:00', TIMESTAMP '2026-07-25 14:50:00', 3000000,
  '/recordings/20260725/LIC20260801-000001.mp4',
  'b2c4e6f8a3f5d2e8b2c4e6f8a3f5d2e8b2c4e6f8a3f5d2e8b2c4e6f8a3f5d2e8',
  62914560, 'SM4-CBC', '0xbcd234ef567', 0, 0, NULL, NULL,
  DATE '2036-07-25', 88, 'PASS_WITH_FINDINGS', '1280x720', 24, 48000, 8.50, 88.20, 1, NULL, NULL, '10.0.0.5', 'dev-sh-001', 'iv-002', 'sig-002', 'PR-20260801-001', NULL, TIMESTAMP '2026-07-25 14:00:00', 0),

-- biz-002 数字人 (1 段: AI 数字人, 关联远程)
('rec-003', 'REC20260801-9003', 'LIC20260801-900001', 'SELF_AI', 'AI_DIGITAL_HUMAN',
  TIMESTAMP '2026-07-25 13:00:00', TIMESTAMP '2026-07-25 13:50:00', 3000000,
  '/recordings/20260725/LIC20260801-0001-AI.mp4',
  'c3d4e5f6a7b8c9d0c3d4e5f6a7b8c9d0c3d4e5f6a7b8c9d0c3d4e5f6a7b8c9d0',
  57671680, 'SM4-CBC', '0xcde345fg678', 1, 1, 'REC20260801-9002', NULL,
  DATE '2036-07-25', 92, 'PASS', '1920x1080', 30, 96000, 0.20, 100.00, 0, NULL, NULL, '10.0.0.6', 'dev-ai-001', 'iv-003', 'sig-003', 'PR-20260801-002', NULL, TIMESTAMP '2026-07-25 13:00:00', 0),

-- biz-003 进行中 (5 节点完成)
('rec-004', 'REC20260801-9004', 'BNK20260801-900003', 'OFFLINE', 'HUMAN',
  TIMESTAMP '2026-08-01 09:00:00', NULL, NULL,
  '/recordings/20260801/BNK20260801-000003.mp4',
  'd4e5f6a7b8c9d0e1d4e5f6a7b8c9d0e1d4e5f6a7b8c9d0e1d4e5f6a7b8c9d0e1',
  31457280, 'SM4-CBC', NULL, 0, 0, NULL, '北京海淀支行',
  DATE '2036-08-01', NULL, NULL, '1920x1080', 25, 64000, 0.10, 96.00, 0, 39.983900, 116.316400, '192.168.1.5', 'dev-bj-hd-001', 'iv-004', NULL, NULL, NULL, TIMESTAMP '2026-08-01 09:00:00', 0),

-- biz-004 失败 (禁播词命中)
('rec-005', 'REC20260801-9005', 'FND20260801-900004', 'SELF_AI', 'AI_DIGITAL_HUMAN',
  TIMESTAMP '2026-08-01 10:00:00', TIMESTAMP '2026-08-01 10:15:00', 900000,
  '/recordings/20260801/FND20260801-000004-AI.mp4',
  'e5f6a7b8c9d0e1f2e5f6a7b8c9d0e1f2e5f6a7b8c9d0e1f2e5f6a7b8c9d0e1f2',
  15728640, 'SM4-CBC', '0xdef456gh789', 1, 1, NULL, NULL,
  DATE '2036-08-01', 45, 'FAIL', '1280x720', 20, 32000, 35.00, 65.00, 2, NULL, NULL, '10.0.0.7', 'dev-ai-002', 'iv-005', 'sig-005', NULL, NULL, TIMESTAMP '2026-08-01 10:00:00', 0);

-- 6. 节点明细 (biz-001 8 节点全部完成)
INSERT INTO tb_rec_node (id, business_id, rec_id, node_id, node_name, start_utc, end_utc, duration_ms, completed, evidence_ts, operator_id, deleted) VALUES
('nd-001', 'BNK20260801-900001', 'REC20260801-9001', '01-IDENTITY', '身份核验', TIMESTAMP '2026-07-20 10:00:00', TIMESTAMP '2026-07-20 10:02:00', 120000, 1, TIMESTAMP '2026-07-20 10:02:00', 'seller-hash-001', 0),
('nd-002', 'BNK20260801-900001', 'REC20260801-9001', '02-DISCLOSURE', '风险揭示', TIMESTAMP '2026-07-20 10:02:00', TIMESTAMP '2026-07-20 10:08:00', 360000, 1, TIMESTAMP '2026-07-20 10:08:00', 'seller-hash-001', 0),
('nd-003', 'BNK20260801-900001', 'REC20260801-9001', '03-PRODUCT', '产品展示', TIMESTAMP '2026-07-20 10:08:00', TIMESTAMP '2026-07-20 10:18:00', 600000, 1, TIMESTAMP '2026-07-20 10:18:00', 'seller-hash-001', 0),
('nd-004', 'BNK20260801-900001', 'REC20260801-9001', '04-RIGHTS', '权利义务', TIMESTAMP '2026-07-20 10:18:00', TIMESTAMP '2026-07-20 10:22:00', 240000, 1, TIMESTAMP '2026-07-20 10:22:00', 'seller-hash-001', 0),
('nd-005', 'BNK20260801-900001', 'REC20260801-9001', '05-TRUTH_TELL', '如实告知', TIMESTAMP '2026-07-20 10:22:00', TIMESTAMP '2026-07-20 10:28:00', 360000, 1, TIMESTAMP '2026-07-20 10:28:00', 'seller-hash-001', 0),
('nd-006', 'BNK20260801-900001', 'REC20260801-9001', '06-CONFIRM', '明确肯定', TIMESTAMP '2026-07-20 10:28:00', TIMESTAMP '2026-07-20 10:33:00', 300000, 1, TIMESTAMP '2026-07-20 10:33:00', 'seller-hash-001', 0),
('nd-007', 'BNK20260801-900001', 'REC20260801-9001', '07-SIGN', '签署', TIMESTAMP '2026-07-20 10:33:00', TIMESTAMP '2026-07-20 10:38:00', 300000, 1, TIMESTAMP '2026-07-20 10:38:00', 'seller-hash-001', 0),
('nd-008', 'BNK20260801-900001', 'REC20260801-9001', '08-FOLLOWUP', '补充询问', TIMESTAMP '2026-07-20 10:38:00', TIMESTAMP '2026-07-20 10:45:00', 420000, 1, TIMESTAMP '2026-07-20 10:45:00', 'seller-hash-001', 0),

-- biz-003 5 节点 (进行中)
('nd-009', 'BNK20260801-900003', 'REC20260801-9004', '01-IDENTITY', '身份核验', TIMESTAMP '2026-08-01 09:00:00', TIMESTAMP '2026-08-01 09:02:00', 120000, 1, TIMESTAMP '2026-08-01 09:02:00', 'seller-hash-003', 0),
('nd-010', 'BNK20260801-900003', 'REC20260801-9004', '02-DISCLOSURE', '风险揭示', TIMESTAMP '2026-08-01 09:02:00', TIMESTAMP '2026-08-01 09:08:00', 360000, 1, TIMESTAMP '2026-08-01 09:08:00', 'seller-hash-003', 0),
('nd-011', 'BNK20260801-900003', 'REC20260801-9004', '03-PRODUCT', '产品展示', TIMESTAMP '2026-08-01 09:08:00', TIMESTAMP '2026-08-01 09:18:00', 600000, 1, TIMESTAMP '2026-08-01 09:18:00', 'seller-hash-003', 0),
('nd-012', 'BNK20260801-900003', 'REC20260801-9004', '04-RIGHTS', '权利义务', TIMESTAMP '2026-08-01 09:18:00', TIMESTAMP '2026-08-01 09:22:00', 240000, 1, TIMESTAMP '2026-08-01 09:22:00', 'seller-hash-003', 0),
('nd-013', 'BNK20260801-900003', 'REC20260801-9004', '05-TRUTH_TELL', '如实告知', TIMESTAMP '2026-08-01 09:22:00', TIMESTAMP '2026-08-01 09:25:00', 180000, 1, TIMESTAMP '2026-08-01 09:25:00', 'seller-hash-003', 0);

-- 7. 质检结果 1 笔 (biz-001)
INSERT INTO tb_qa_result (id, qa_id, rec_id, business_id, checker_type, ai_model_version, ai_qa_score, ai_qa_result, issues_json, human_reviewer_id, human_review_status, rectification_status, check_time, deleted) VALUES
('qa-001', 'QA20260720-0001', 'REC20260801-9001', 'BNK20260801-900001', 'AI_PLUS_HUMAN', 'qa-llm-v3.2.0', 92.50, 'PASS_WITH_FINDINGS',
 '[{"type":"LATE_DISCLOSURE","severity":"LOW","nodeId":"02-DISCLOSURE","regulation":"金发8号-第二十一条"}]',
 'auditor-001', 'CONFIRMED', 'NO_NEED', TIMESTAMP '2026-07-20 10:50:00', 0);

-- 8. 事件流 (biz-001 完整状态机轨迹)
INSERT INTO tb_event (id, business_id, event_type, from_state, to_state, actor_id, actor_type, event_data, created_at) VALUES
('evt-001', 'BNK20260801-900001', 'STATE_TRANSITION', NULL, 'INIT', 'SYSTEM', 'SYSTEM', '{"reason":"BUSINESS_CREATED"}', TIMESTAMP '2026-07-20 10:00:00'),
('evt-002', 'BNK20260801-900001', 'STATE_TRANSITION', 'INIT', 'RISK_ASSESSED', 'SYSTEM', 'SYSTEM', '{"reason":"Risk assessed: C1"}', TIMESTAMP '2026-07-20 10:00:30'),
('evt-003', 'BNK20260801-900001', 'STATE_TRANSITION', 'RISK_ASSESSED', 'SCRIPT_LOADED', 'SYSTEM', 'SYSTEM', '{"reason":"Script loaded"}', TIMESTAMP '2026-07-20 10:00:45'),
('evt-004', 'BNK20260801-900001', 'STATE_TRANSITION', 'SCRIPT_LOADED', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"reason":"Recording started"}', TIMESTAMP '2026-07-20 10:01:00'),
('evt-005', 'BNK20260801-900001', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"01-IDENTITY","duration":120000}', TIMESTAMP '2026-07-20 10:02:00'),
('evt-006', 'BNK20260801-900001', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"02-DISCLOSURE","duration":360000}', TIMESTAMP '2026-07-20 10:08:00'),
('evt-007', 'BNK20260801-900001', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"03-PRODUCT","duration":600000}', TIMESTAMP '2026-07-20 10:18:00'),
('evt-008', 'BNK20260801-900001', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"04-RIGHTS","duration":240000}', TIMESTAMP '2026-07-20 10:22:00'),
('evt-009', 'BNK20260801-900001', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"05-TRUTH_TELL","duration":360000}', TIMESTAMP '2026-07-20 10:28:00'),
('evt-010', 'BNK20260801-900001', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"06-CONFIRM","duration":300000,"affirmative":true}', TIMESTAMP '2026-07-20 10:33:00'),
('evt-011', 'BNK20260801-900001', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"07-SIGN","duration":300000}', TIMESTAMP '2026-07-20 10:38:00'),
('evt-012', 'BNK20260801-900001', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"08-FOLLOWUP","duration":420000}', TIMESTAMP '2026-07-20 10:45:00'),
('evt-013', 'BNK20260801-900001', 'STATE_TRANSITION', 'RECORDING', 'RECORDED', 'SYSTEM', 'SYSTEM', '{"reason":"All nodes completed"}', TIMESTAMP '2026-07-20 10:45:00'),
('evt-014', 'BNK20260801-900001', 'STATE_TRANSITION', 'RECORDED', 'AI_QA', 'SYSTEM', 'SYSTEM', '{"reason":"AI QA started"}', TIMESTAMP '2026-07-20 10:45:30'),
('evt-015', 'BNK20260801-900001', 'STATE_TRANSITION', 'AI_QA', 'AI_QA_PASSED', 'SYSTEM', 'SYSTEM', '{"reason":"AI QA passed: PASS_WITH_FINDINGS","score":92.5}', TIMESTAMP '2026-07-20 10:50:00'),
('evt-016', 'BNK20260801-900001', 'STATE_TRANSITION', 'AI_QA_PASSED', 'SIGNED', 'customer-hash-001', 'CUSTOMER', '{"reason":"Customer signed"}', TIMESTAMP '2026-07-20 10:55:00'),
('evt-017', 'BNK20260801-900001', 'STATE_TRANSITION', 'SIGNED', 'ARCHIVED', 'SYSTEM', 'SYSTEM', '{"reason":"Archived"}', TIMESTAMP '2026-07-20 10:55:30'),

-- biz-004 失败事件
('evt-018', 'FND20260801-900004', 'STATE_TRANSITION', NULL, 'INIT', 'SYSTEM', 'SYSTEM', '{"reason":"BUSINESS_CREATED"}', TIMESTAMP '2026-08-01 10:00:00'),
('evt-019', 'FND20260801-900004', 'STATE_TRANSITION', 'INIT', 'RISK_ASSESSED', 'SYSTEM', 'SYSTEM', '{"reason":"Risk assessed: C3"}', TIMESTAMP '2026-08-01 10:00:30'),
('evt-020', 'FND20260801-900004', 'STATE_TRANSITION', 'RISK_ASSESSED', 'SCRIPT_LOADED', 'SYSTEM', 'SYSTEM', '{"reason":"Script loaded"}', TIMESTAMP '2026-08-01 10:01:00'),
('evt-021', 'FND20260801-900004', 'STATE_TRANSITION', 'SCRIPT_LOADED', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"reason":"Recording started"}', TIMESTAMP '2026-08-01 10:01:30'),
('evt-022', 'FND20260801-900004', 'NODE_COMPLETED', 'RECORDING', 'RECORDING', 'SYSTEM', 'SYSTEM', '{"node":"01-IDENTITY","duration":120000}', TIMESTAMP '2026-08-01 10:03:30'),
('evt-023', 'FND20260801-900004', 'FORBIDDEN_PHRASE_HIT', 'RECORDING', 'FAILED', 'SYSTEM', 'SYSTEM', '{"phrase":"保本","severity":"HIGH","regulation":"金发8号-第二十一条","action":"NODE_FAILED"}', TIMESTAMP '2026-08-01 10:15:00'),
('evt-024', 'FND20260801-900004', 'STATE_TRANSITION', 'RECORDING', 'FAILED', 'system', 'SYSTEM', '{"reason":"MANUAL: FORBIDDEN_PHRASE_HIT (高风险客户买高风险产品, 数字人触发禁播词阻断)"}', TIMESTAMP '2026-08-01 10:15:00'),

-- 犹豫期回访事件 (biz-001 业务)
('evt-025', 'BNK20260801-900001', 'SCHEDULED_FOLLOW_UP', 'ARCHIVED', 'ARCHIVED', 'SYSTEM', 'SYSTEM', '{"phase":"D+1","day":1,"scheduled_at":"2026-07-21","template":"保单摘要推送"}', TIMESTAMP '2026-07-20 10:55:30');

-- 9. 证据保全记录 2 笔 (rec-002 + rec-003 已公证)
INSERT INTO tb_preservation_record (id, preservation_id, rec_id, business_id, requester_id, requester_role, reason, notary_org, notary_cert_no, preserved_at, preservation_hash, file_sha256, expires_at, status, created_at) VALUES
('pr-001', 'PR-20260801-001', 'REC20260801-9002', 'LIC20260801-900001', 'auditor-001', 'AUDITOR', '客户投诉进入司法程序', '北京公证处', 'GZ-2026-001', TIMESTAMP '2026-07-26 09:00:00',
 'sha256hash1-001', 'b2c4e6f8a3f5d2e8b2c4e6f8a3f5d2e8b2c4e6f8a3f5d2e8b2c4e6f8a3f5d2e8',
 TIMESTAMP '2031-07-26', 'NOTARIZED', TIMESTAMP '2026-07-25 18:00:00'),

('pr-002', 'PR-20260801-002', 'REC20260801-9003', 'LIC20260801-900001', 'auditor-001', 'AUDITOR', '客户投诉进入司法程序 (数字人段)', '北京公证处', 'GZ-2026-002', TIMESTAMP '2026-07-26 09:30:00',
 'sha256hash2-001', 'c3d4e5f6a7b8c9d0c3d4e5f6a7b8c9d0c3d4e5f6a7b8c9d0c3d4e5f6a7b8c9d0',
 TIMESTAMP '2031-07-26', 'NOTARIZED', TIMESTAMP '2026-07-25 18:30:00');
