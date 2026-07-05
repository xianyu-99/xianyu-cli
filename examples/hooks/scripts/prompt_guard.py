import json
import re
import sys


SECRET_PATTERNS = [
    re.compile(r"\b(api[_-]?key|token|password|secret)\b", re.IGNORECASE),
    re.compile(r"\b(print|show|dump|reveal)\b.*\b(env|credentials|secrets?)\b", re.IGNORECASE),
]


def main():
    payload = json.load(sys.stdin)
    prompt = str((payload.get("arguments") or {}).get("prompt") or "")

    if any(pattern.search(prompt) for pattern in SECRET_PATTERNS):
        print(json.dumps({
            "decision": "deny",
            "reason": "prompt appears to request secret disclosure"
        }))
        return

    print(json.dumps({"decision": "allow"}))


if __name__ == "__main__":
    main()
