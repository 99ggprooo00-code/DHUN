import tempfile
import unittest
from pathlib import Path

from validate_fixtures import FIXTURE_ROOTS, fixture_files, validate_file


class FixtureValidationTest(unittest.TestCase):
    def validate_text(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.json"
            path.write_text(text, encoding="utf-8")
            return validate_file(path)

    def test_accepts_valid_json(self):
        self.assertEqual(self.validate_text('{"items": [], "continuation": null}'), {"items": [], "continuation": None})

    def test_rejects_malformed_json(self):
        with self.assertRaises(ValueError):
            self.validate_text('{"items": []}}')

    def test_rejects_duplicate_keys_in_nested_objects(self):
        with self.assertRaisesRegex(ValueError, "duplicate object key"):
            self.validate_text('{"action": {"token": "feed", "token": "shelf"}}')

    def test_rejects_non_json_numbers(self):
        for value in ("NaN", "Infinity", "-Infinity"):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "non-JSON numeric"):
                self.validate_text('{"value": ' + value + '}')

    def test_checked_in_inputs_are_valid(self):
        files = fixture_files(FIXTURE_ROOTS)
        self.assertTrue(files, "fixture validation must not silently check nothing")
        for path in files:
            with self.subTest(path=str(path)):
                validate_file(path)


if __name__ == "__main__":
    unittest.main()
