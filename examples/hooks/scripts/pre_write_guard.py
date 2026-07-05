import json
import sys


MAX_CONTENT_CHARS = 200_000
BLOCKED_SUFFIXES = (".env", ".pem", ".key", ".p12")


def main():
    payload = json.load(sys.stdin)
    args = payload.get("arguments") or {}
    path = str(args.get("path") or "")
    content = str(args.get("content") or "")

    normalized = path.replace("\\", "/").lower()
    if normalized.endswith(BLOCKED_SUFFIXES):
        print(json.dumps({
            "decision": "deny",
            "reason": "write target looks like a secret-bearing file"
        }))
        return

    if len(content) > MAX_CONTENT_CHARS:
        print(json.dumps({
            "decision": "deny",
            "reason": "write content is larger than the recipe limit"
        }))
        return

    print(json.dumps({"decision": "allow"}))


if __name__ == "__main__":
    main()
