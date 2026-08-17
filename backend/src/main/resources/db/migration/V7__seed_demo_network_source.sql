-- The only network_source active by default outside of an operator-configured real connector.
-- Every evidence record produced against it is rendered in the UI with a visible
-- "SYNTHETIC DEMO DATA" badge and is never presented as evidence from a real payer -- see
-- CLAUDE.md 42 and docs/insurance-network-architecture.md ("Demo data"). This migration seeds
-- only the payer/source/network/plan shell; the evidence rows themselves are generated at
-- application startup by DemoNetworkEvidenceSeeder against whichever providers currently exist
-- (providers are imported separately, outside Flyway's control).

INSERT INTO payer (code, name, active)
VALUES ('DOCFIT_DEMO', 'DocFit Demo Network (synthetic test data)', TRUE);

INSERT INTO network_source (payer_id, source_type, name, format, active)
SELECT id, 'MANUAL_DEMO_REFERENCE', 'DocFit synthetic demo evidence generator', 'INTERNAL', TRUE
FROM payer
WHERE code = 'DOCFIT_DEMO';

INSERT INTO insurance_network (payer_id, network_name, external_network_identifier, active)
SELECT id, 'DocFit Demo Network Directory (synthetic)', 'DEMO-NETWORK-1', TRUE
FROM payer
WHERE code = 'DOCFIT_DEMO';

INSERT INTO insurance_plan (payer_id, plan_name, plan_type, external_plan_identifier, source_id, active)
SELECT p.id, 'DocFit Demo PPO (synthetic)', 'PPO', 'DEMO-PLAN-1', ns.id, TRUE
FROM payer p
JOIN network_source ns ON ns.payer_id = p.id
WHERE p.code = 'DOCFIT_DEMO';

INSERT INTO plan_network (plan_id, network_id)
SELECT ip.id, inw.id
FROM insurance_plan ip
JOIN payer p ON p.id = ip.payer_id AND p.code = 'DOCFIT_DEMO'
JOIN insurance_network inw ON inw.payer_id = p.id;
