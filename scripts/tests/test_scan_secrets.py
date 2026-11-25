import unittest

from scripts.scan_secrets import scan_text


class SecretScannerTests(unittest.TestCase):
    def test_detects_openai_project_key(self):
        self.assertTrue(scan_text("OPENAI_API_KEY=sk-proj-" + "A" * 40))

    def test_detects_github_token(self):
        self.assertTrue(scan_text("token=ghp_" + "A" * 36))

    def test_detects_private_key_material(self):
        marker = "-" * 5 + "BEGIN " + "PRIVATE KEY" + "-" * 5
        self.assertTrue(scan_text(marker))

    def test_ignores_normal_docs_and_placeholders(self):
        text = "Set API_KEY to YOUR_API_KEY_HERE. Use HTTPS and never paste secrets."
        self.assertEqual([], scan_text(text))


if __name__ == "__main__":
    unittest.main()
