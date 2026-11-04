"""Byte-exactness verifier for backslash escapes and non-ASCII bytes.

Root cause addressed: the author's emission of backslash runs and non-ASCII
characters through file tools is untrustworthy (see MoneyUtils RUPEE case),
while the transport itself is byte-exact. This script asserts exact expected
bytes using chr() codes only, so the checks themselves cannot be mangled.

Run from the project root (tenderops-ai):  python docs/test-logs/verify_bytes.py
Exit code 0 = all checks pass.
"""
import os
import sys

BS = chr(92)  # backslash
ESC = chr(27)
fails = []


def check_contains(path, desc, needle):
    try:
        with open(path, encoding="utf-8") as fh:
            data = fh.read()
    except FileNotFoundError:
        print("MISSING " + path)
        fails.append(path + " MISSING")
        return
    if needle in data:
        print("PASS " + path + " :: " + desc)
    else:
        print("FAIL " + path + " :: " + desc)
        fails.append(path + " :: " + desc)


def check_absent(path, desc, needle):
    try:
        with open(path, encoding="utf-8") as fh:
            data = fh.read()
    except FileNotFoundError:
        print("MISSING " + path)
        fails.append(path + " MISSING")
        return
    if needle not in data:
        print("PASS " + path + " :: " + desc)
    else:
        print("FAIL " + path + " :: " + desc)
        fails.append(path + " :: " + desc)


ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(ROOT)

# MASTER_PROMPT.md must be verbatim ASCII with single-backslash paths and escapes.
check_contains("MASTER_PROMPT.md", "rupee escape present", BS + "u20B9")
check_contains("MASTER_PROMPT.md", "P-backslash-MASTER_PROMPT.md", "P" + BS + "MASTER_PROMPT.md")
check_contains("MASTER_PROMPT.md", "dotdot-backslash-legal", ".." + BS + "legal-agent-workshop")

# PowerShell scripts: single-backslash Windows paths.
check_contains("scripts/run-server.ps1", "server pom path", "$Root" + BS + "server" + BS + "pom.xml")
check_contains("scripts/run-client.ps1", "client dir", "$Root" + BS + "client")
check_contains("scripts/run-tests.ps1", "test-logs dir", BS + "docs" + BS + "test-logs")
check_contains("scripts/verify-all.ps1", "verify log name", "verify-server-")
check_contains("scripts/run-live-check.ps1", "login path", "/api/v1/auth/login")
check_contains("scripts/probe-meta-api.ps1", "env path", "$Root" + BS + ".env")

# Bash scripts: backslash-quote JSON and backslash-n curl format.
check_contains("scripts/run-live-check.sh", "plain-quote email JSON", "'{\"email\"")
check_contains("scripts/probe-meta-api.sh", "curl newline format", BS + "nHTTP_STATUS")

# MoneyUtils.java: ASCII escape, doubled regex backslashes, no ESC garbage, no marker left.
check_contains("server/src/main/java/com/tenderops/common/MoneyUtils.java",
               "rupee ASCII escape", BS + "u20B9")
check_contains("server/src/main/java/com/tenderops/common/MoneyUtils.java",
               "doubled regex backslash", BS + BS + "s")
check_absent("server/src/main/java/com/tenderops/common/MoneyUtils.java",
             "no ESC char", ESC)
check_absent("server/src/main/java/com/tenderops/common/MoneyUtils.java",
             "no marker token left", "RUPEESIGN09")

# Probe scripts: real PNG blob, length-guarded (never trust rendered output for it).
import re as _re

for _p, _v in (
    ("scripts/probe-meta-api.ps1", "tinyPng"),
    ("scripts/probe-meta-api.sh", "TINY_PNG"),
):
    with open(_p, encoding="utf-8") as _fh:
        _d = _fh.read()
    _m = _re.search(_v + '[ ]*=[ ]*"([A-Za-z0-9+/=]+)"', _d)
    _ok = bool(_m) and len(_m.group(1)) >= 90
    print(("PASS " if _ok else "FAIL ") + _p + " :: png blob len>=90")
    if not _ok:
        fails.append(_p + " blob")
check_absent("scripts/probe-meta-api.ps1", "no blob marker", "BASE64_")
check_absent("scripts/probe-meta-api.sh", "no blob marker", "BASE64_")

# Global: no ESC anywhere; report any non-ASCII file.
for dirpath, _dirnames, filenames in os.walk("."):
    if ".git" in dirpath:
        continue
    for name in filenames:
        if name.endswith((".class", ".jar", ".png", ".pdf")):
            continue
        p = os.path.join(dirpath, name)
        try:
            with open(p, encoding="utf-8") as fh:
                content = fh.read()
        except (UnicodeDecodeError, OSError):
            continue
        if ESC in content:
            print("FAIL " + p + " :: contains ESC char")
            fails.append(p + " ESC")
        odd = sorted({ord(c) for c in content if ord(c) > 127})
        if odd:
            print("NONASCII " + p + " :: " + ",".join(str(o) for o in odd[:12]))

print("RESULT " + ("PASS" if not fails else "FAIL(" + str(len(fails)) + ")"))
sys.exit(1 if fails else 0)
