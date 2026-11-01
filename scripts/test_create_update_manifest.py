import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("update_manifest", Path(__file__).with_name("create-update-manifest.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class UpdateManifestTest(unittest.TestCase):
    def test_uses_actual_artifact_metadata_size_and_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "app.apk"
            apk.write_bytes(b"test apk")
            metadata = {"applicationId": "app.pengwa.debug", "minSdkVersionForDexing": 29,
                        "elements": [{"versionCode": 100101, "versionName": "0.1.0-nightly.1", "filters": []}]}
            manifest = module.create_manifest(apk, metadata, "https://example.test/app.apk", "notes")
            self.assertEqual(manifest["versionCode"], 100101)
            self.assertEqual(manifest["packageName"], "app.pengwa.debug")
            self.assertEqual(manifest["minSdk"], 29)
            self.assertEqual(manifest["size"], 8)
            self.assertEqual(manifest["sha256"], hashlib.sha256(b"test apk").hexdigest())

    def test_rejects_split_apk_outputs(self):
        for elements in ([], [{"filters": [{"filterType": "ABI"}]}], [{}, {}]):
            with self.assertRaises(ValueError):
                module.create_manifest(Path("unused.apk"), {"elements": elements}, "", "")


if __name__ == "__main__":
    unittest.main()
