import unittest

from aegis_release_convergence import (
    CANARY_LEVELS,
    GA_GATES,
    CanonicalSessionMergeRegistry,
    CustomerOpenApprovalGate,
    GAGateRegistry,
    SessionAsset,
    CanaryPromotionPolicy,
)


class ReleaseConvergenceTests(unittest.TestCase):
    def test_ga_gate_fail_closed(self):
        statuses = {gate: "PASS" for gate in GA_GATES}
        statuses["G3_SECURITY"] = "NOT_EXECUTED"
        decision = GAGateRegistry().evaluate(statuses)
        self.assertFalse(decision.passed)
        self.assertEqual(decision.decision, "NOT_PROMOTED")

    def test_ga_gate_promotes_only_all_pass(self):
        decision = GAGateRegistry().evaluate({gate: "PASS" for gate in GA_GATES})
        self.assertTrue(decision.passed)
        self.assertEqual(decision.decision, "GA_PROMOTE")

    def test_session_registry_blocks_owner_conflict(self):
        registry = CanonicalSessionMergeRegistry()
        registry.register(SessionAsset("a", "audit", "core", "1", "AuditPort", "e1"))
        with self.assertRaises(ValueError):
            registry.register(SessionAsset("b", "audit", "other", "1", "AuditPort", "e2"))

    def test_session_registry_requires_evidence(self):
        registry = CanonicalSessionMergeRegistry()
        with self.assertRaises(ValueError):
            registry.register(SessionAsset("a", "audit", "core", "1", "AuditPort", ""))

    def test_canary_security_event_rolls_back(self):
        decision = CanaryPromotionPolicy().next_level(
            "INTERNAL",
            {"security_events": 1, "rollback_ready": True},
        )
        self.assertEqual(decision.action, "ROLLBACK")

    def test_canary_clean_metrics_promote(self):
        decision = CanaryPromotionPolicy().next_level(
            "INTERNAL",
            {
                "security_events": 0,
                "error_rate_delta": 0,
                "p95_latency_delta_ms": 0,
                "rollback_ready": True,
            },
        )
        self.assertEqual(decision.level, "LIMITED_CUSTOMER")
        self.assertIn(decision.level, CANARY_LEVELS)

    def test_public_open_blocks_missing_operational_evidence(self):
        evidence = {
            "ga_gate_passed": True,
            "customer_terms_ready": True,
            "privacy_notice_ready": True,
            "support_runbook_ready": True,
            "incident_response_ready": False,
            "status_page_ready": True,
            "monitoring_alerting_ready": True,
            "backup_restore_evidence": True,
            "rollback_evidence": True,
            "security_evidence": True,
        }
        decision = CustomerOpenApprovalGate().evaluate(evidence)
        self.assertFalse(decision.passed)
        self.assertIn("incident_response_ready", decision.missing)


if __name__ == "__main__":
    unittest.main()
