import unittest
from unittest.mock import patch

from scripts.play import subscription_catalog as catalog


def draft():
    item = catalog.catalog_payload()
    for plan in item["basePlans"]:
        plan["state"] = "DRAFT"
    return item


class SubscriptionCatalogTests(unittest.TestCase):
    def test_default_check_cannot_create(self):
        with patch.object(catalog, "api", return_value=None) as api:
            result = catalog.run("test-token", "check", False)
        self.assertFalse(result["exists"])
        api.assert_called_once_with("test-token", "GET", "/subscriptions/tripcanvas_ad_free")

    def test_create_requires_explicit_gate(self):
        with patch.object(catalog, "api", return_value=None):
            with self.assertRaises(catalog.CatalogError):
                catalog.run("test-token", "create-draft", False)

    def test_request_contains_two_korean_autorenewal_prices(self):
        payload = catalog.catalog_payload()
        self.assertEqual(catalog.PRODUCT, payload["productId"])
        by_id = {x["basePlanId"]: x for x in payload["basePlans"]}
        self.assertEqual("P1M", by_id["monthly"]["autoRenewingBasePlanType"]["billingPeriodDuration"])
        self.assertEqual("P1Y", by_id["yearly"]["autoRenewingBasePlanType"]["billingPeriodDuration"])
        self.assertEqual("1000", by_id["monthly"]["regionalConfigs"][0]["price"]["units"])
        self.assertEqual("9900", by_id["yearly"]["regionalConfigs"][0]["price"]["units"])
        self.assertNotIn("state", by_id["monthly"])
        self.assertNotIn("state", by_id["yearly"])

    def test_create_draft_requires_reread_and_no_activation(self):
        current = draft()
        with patch.object(catalog, "api", side_effect=[
            None, {"regionVersion": {"version": "2026/11"}},
            current, current
        ]) as api:
            result = catalog.run("test-token", "create-draft", True)
        self.assertTrue(result["exists"])
        self.assertFalse(result["saleActivated"])
        self.assertEqual(4, api.call_count)
        self.assertIn("regionsVersion.version=", api.call_args_list[2].args[3][:30] if False else
                      api.call_args_list[2].args[2])

    def test_price_mismatch_refuses_catalog(self):
        item = draft()
        item["basePlans"][0]["regionalConfigs"][0]["price"]["units"] = "2000"
        with self.assertRaises(catalog.CatalogError):
            catalog.verify(item)

    def test_active_product_is_reported_not_mutated(self):
        item = draft()
        item["basePlans"][0]["state"] = "ACTIVE"
        with patch.object(catalog, "api", return_value=item) as api:
            result = catalog.run("test-token", "create-draft", True)
        self.assertTrue(result["saleActivated"])
        api.assert_called_once()


if __name__ == "__main__":
    unittest.main()
