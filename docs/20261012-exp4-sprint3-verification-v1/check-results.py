"""Read-only checks of isolated acceptance artifacts; no business data access."""
import csv
from collections import Counter
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

directory = Path(__file__).resolve().parent
root = directory.parents[1]
full_cases, passed_cases, bad_cases, counts = set(), set(), [], []
for folder in ("backend-full-xml", "supplemental-xml"):
    for file in sorted((directory / folder).glob("TEST-*.xml")):
        suite = ET.parse(file).getroot()
        counts.append({"run": folder, "class": suite.attrib["name"], **{
            key: int(suite.get(key, 0)) for key in ("tests", "skipped", "failures", "errors")}})
        occurrences = Counter()
        for case in suite.findall("testcase"):
            name = (case.get("classname"), case.get("name"))
            occurrences[name] += 1
            # JUnit dynamic tests can share a method name. Preserve each registered
            # occurrence instead of collapsing 13 acceptance tests into one.
            identity = (*name, occurrences[name])
            if folder == "backend-full-xml":
                full_cases.add(identity)
            if case.find("failure") is not None or case.find("error") is not None:
                bad_cases.append(identity)
            elif case.find("skipped") is None:
                passed_cases.add(identity)

ns = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
sheet_checks = []
with ZipFile(directory / "snapshot-export.xlsx") as archive:
    assert archive.testzip() is None
    for name in archive.namelist():
        if name.startswith("xl/worksheets/") and name.endswith(".xml"):
            tree = ET.fromstring(archive.read(name))
            texts = [node.text for node in tree.findall(".//s:t", ns)]
            sheet_checks.append({"name": name, "formulaCells": len(tree.findall(".//s:f", ns)),
                                 "timestamps": [text for text in texts if text and text.startswith("2026-10-12T")]})
assert len(sheet_checks) == 2
assert all(sheet["formulaCells"] == 0 for sheet in sheet_checks)
assert sheet_checks[0]["timestamps"] and sheet_checks[0]["timestamps"] == sheet_checks[1]["timestamps"]
with (directory / "snapshot-export.csv").open(encoding="utf-8-sig", newline="") as file:
    csv_rows = list(csv.reader(file))
assert all(code in str(csv_rows) for code in ("SYN-CORE", "SYN-OPTION", "SYN-EMPTY"))

hash_checks = json.loads((directory / "historical-hash-checks.json").read_text(encoding="utf-8-sig"))
assert all(check["matches"] for check in hash_checks)
browser = json.loads((directory / "browser-results.json").read_text(encoding="utf-8-sig"))
assert browser["passed"] == 8 and not browser["pageErrors"]
upgrade = json.loads((directory / "upgrade-results.json").read_text(encoding="utf-8-sig"))
assert len(upgrade) == 2 and all(item["independentSeries"] == 2 and item["wholeTextAdvice"] == 1 for item in upgrade)
assert not bad_cases
summary = {"registeredCurrentBackendCases": len(full_cases),
           "passedCurrentBackendCasesAcrossRuns": len(full_cases & passed_cases),
           "notRerunCurrentCases": sorted(full_cases - passed_cases), "badCases": bad_cases,
           "suites": counts, "historicalHashMatches": len(hash_checks),
           "browserGroups": browser["passed"], "browserPageErrors": browser["pageErrors"],
           "upgradePasses": len(upgrade), "export": {"sheets": sheet_checks, "csvRows": len(csv_rows)}}
(directory / "verification-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({key: value for key, value in summary.items() if key not in ("suites", "export")}, ensure_ascii=False))
