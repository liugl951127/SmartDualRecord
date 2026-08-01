
-- ===========================================================
-- Saga 示例 (v1.7.0)
-- ===========================================================
-- 跑过 3 次 demo, 1 次成功 2 次失败+补偿, 真实数据
-- 实际 Saga 由运行时 API 触发, 这里只塞几条历史记录
INSERT INTO tb_saga_instance (id, saga_id, saga_name, status, current_step, total_steps, payload_json, started_at, completed_at, updated_at) VALUES
('saga-seed-001', 'saga-seed-completed-001', 'CREATE_BUSINESS_DEMO', 'COMPLETED', 4, 4, '[{"amount":"50000","customerIdHash":"cust-hash-001"}]', '2026-08-01 10:00:00', '2026-08-01 10:00:00', '2026-08-01 10:00:00'),
('saga-seed-002', 'saga-seed-compensated-001', 'CREATE_BUSINESS_DEMO', 'COMPENSATED', 1, 4, '[{"amount":"100000"}]', '2026-08-01 10:01:00', '2026-08-01 10:01:00', '2026-08-01 10:01:00'),
('saga-seed-003', 'saga-seed-compensated-002', 'CREATE_BUSINESS_DEMO', 'COMPENSATED', 3, 4, '[{"amount":"200000"}]', '2026-08-01 10:02:00', '2026-08-01 10:02:00', '2026-08-01 10:02:00');

INSERT INTO tb_saga_step (id, saga_id, step_order, step_name, target_method, compensate_method, status, started_at, completed_at, duration_ms) VALUES
('saga-step-001', 'saga-seed-completed-001', 0, '创建业务', 'SagaDemoService.createBusinessStep', 'SagaDemoService.compensateCreateBusiness', 'COMPLETED', '2026-08-01 10:00:00', '2026-08-01 10:00:00', 120),
('saga-step-002', 'saga-seed-completed-001', 1, '创建录像', 'SagaDemoService.createRecordingStep', 'SagaDemoService.compensateCreateRecording', 'COMPLETED', '2026-08-01 10:00:00', '2026-08-01 10:00:00', 95),
('saga-step-003', 'saga-seed-completed-001', 2, '写初始事件', 'SagaDemoService.initEventStep', 'SagaDemoService.compensateInitEvent', 'COMPLETED', '2026-08-01 10:00:00', '2026-08-01 10:00:00', 78),
('saga-step-004', 'saga-seed-completed-001', 3, '风评快照', 'SagaDemoService.riskSnapshotStep', 'SagaDemoService.compensateRiskSnapshot', 'COMPLETED', '2026-08-01 10:00:00', '2026-08-01 10:00:00', 110),
('saga-step-005', 'saga-seed-compensated-001', 0, '创建业务', 'SagaDemoService.createBusinessStep', 'SagaDemoService.compensateCreateBusiness', 'COMPENSATED', '2026-08-01 10:01:00', '2026-08-01 10:01:00', 105),
('saga-step-006', 'saga-seed-compensated-001', 1, '创建录像', 'SagaDemoService.createRecordingStep', 'SagaDemoService.compensateCreateRecording', 'FAILED', '2026-08-01 10:01:00', '2026-08-01 10:01:00', 50),
('saga-step-007', 'saga-seed-compensated-002', 0, '创建业务', 'SagaDemoService.createBusinessStep', 'SagaDemoService.compensateCreateBusiness', 'COMPENSATED', '2026-08-01 10:02:00', '2026-08-01 10:02:00', 130),
('saga-step-008', 'saga-seed-compensated-002', 1, '创建录像', 'SagaDemoService.createRecordingStep', 'SagaDemoService.compensateCreateRecording', 'COMPENSATED', '2026-08-01 10:02:00', '2026-08-01 10:02:00', 88),
('saga-step-009', 'saga-seed-compensated-002', 2, '写初始事件', 'SagaDemoService.initEventStep', 'SagaDemoService.compensateInitEvent', 'COMPENSATED', '2026-08-01 10:02:00', '2026-08-01 10:02:00', 65),
('saga-step-010', 'saga-seed-compensated-002', 3, '风评快照', 'SagaDemoService.riskSnapshotStep', 'SagaDemoService.compensateRiskSnapshot', 'FAILED', '2026-08-01 10:02:00', '2026-08-01 10:02:00', 42);
