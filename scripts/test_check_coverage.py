"""Exercise the actual CI gate, including its unrounded 95% boundary."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class CoverageGateTest(unittest.TestCase):
    def run_gate(self, covered, missed):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "coverage.xml"
            summary = root / "summary.md"
            report.write_text(
                '<report><package name="example"><sourcefile name="Interface.kt"/>'
                f'<sourcefile name="App.kt"><counter type="LINE" covered="{covered}" missed="{missed}"/>'
                '</sourcefile></package>'
                f'<counter type="LINE" covered="{covered}" missed="{missed}"/></report>'
            )
            result = subprocess.run([sys.executable, str(Path(__file__).with_name("check_coverage.py")),
                                     str(report), "--minimum", "95"], capture_output=True, text=True,
                                    env={**os.environ, "GITHUB_STEP_SUMMARY": str(summary)})
            return result, summary.read_text() if summary.exists() else ""

    def test_exact_target_passes_and_summary_matches_output(self):
        result, summary = self.run_gate(95, 5)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(result.stdout, summary)
        self.assertIn("95.00% (95/100)", summary)
        self.assertIn("App.kt: 5 uncovered lines", summary)

    def test_rounding_never_turns_a_failure_into_a_pass(self):
        result, summary = self.run_gate(94999, 5001)
        self.assertEqual(1, result.returncode)
        self.assertIn("95.00%", summary)  # Display rounds; the gate must not.

    def test_empty_report_cannot_pass(self):
        result, _ = self.run_gate(0, 0)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("no executable lines", result.stderr)


if __name__ == "__main__":
    unittest.main()
