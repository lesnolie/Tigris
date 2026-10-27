import unittest
from vendor_install_guidance import registry, guidance_for
from installation_strategy import select

class VendorGuidanceTests(unittest.TestCase):
    def sample(self, name, sdk="28"):
        return {"manufacturer": name, "brand": name, "model": "fixture",
                "fingerprint": "synthetic/test", "firmware": "test", "sdk": sdk}

    def test_known_families(self):
        brands = {"Sony": "sony", "TCL": "tcl", "FFalcon": "tcl",
                  "Xiaomi": "xiaomi", "Redmi": "xiaomi", "Skyworth": "coocaa",
                  "Coocaa": "coocaa", "Hisense": "hisense", "VIDAA": "hisense",
                  "OPPO": "oppo", "XGIMI": "xgimi", "Konka": "konka",
                  "LeEco": "letv", "Other": "generic"}
        for brand, family in brands.items():
            with self.subTest(brand=brand):
                data = guidance_for(self.sample(brand))
                self.assertEqual(data["vendor"], family)
                self.assertTrue(data["read_only"])
                self.assertFalse(data["hardware_verified"])
                self.assertEqual(data["mutations"], [])

    def test_conflict_is_ambiguous(self):
        result = guidance_for({"manufacturer": "Sony", "brand": "Xiaomi"})
        self.assertEqual(result["status"], "identity_conflict")

    def test_strategy_is_not_changed(self):
        for brand, ident, executor in [
            ("Sony", "sony-native", "legacy"),
            ("TCL", "tcl-compatible", "legacy"),
            ("Xiaomi", "xiaomi-diagnostic", "apk-only"),
            ("Skyworth", "generic-android", "apk-only"),
        ]:
            plan = select(self.sample(brand))
            self.assertEqual(plan["strategy"]["id"], ident)
            self.assertEqual(plan["strategy"]["executor"], executor)
            self.assertEqual(plan["vendor_guidance"]["mutations"], [])
        self.assertEqual(select(self.sample("TCL", sdk="26"))["status"], "blocked")

    def test_registry_unique(self):
        self.assertEqual(len(registry()), 10)
        self.assertEqual(len({row["id"] for row in registry()}), 10)

if __name__ == "__main__":
    unittest.main()
