import json
from pathlib import Path
import re
import sys


SECRET_VALUE = re.compile(
    r'(?i)("(?:authorization|api[_-]?key|token|password|secret|key)"\s*:\s*")[^"]*(")'
)


def redact(value):
    return SECRET_VALUE.sub(r"\1***\2", value)


def main():
    raw = sys.stdin.read()
    payload = json.loads(raw)
    project_path = Path(payload.get("project_path") or ".")
    log_dir = project_path / ".YuCLI"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_file = log_dir / "hook-events.jsonl"
    log_file.write_text("", encoding="utf-8") if not log_file.exists() else None

    line = redact(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
    with log_file.open("a", encoding="utf-8") as handle:
        handle.write(line + "\n")

    print(json.dumps({"decision": "allow"}))


if __name__ == "__main__":
    main()
