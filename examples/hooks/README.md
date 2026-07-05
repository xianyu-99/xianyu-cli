# Hook Recipes

`hooks.json` 展示三类常用 hook：

- `PreToolUse`：写文件前检查敏感路径和大文件写入。
- `UserPromptSubmit`：拦截明显要求泄露密钥的 prompt。
- `PostToolUse`：异步记录工具事件到项目级 `.YuCLI/hook-events.jsonl`。

这些脚本只使用 Python 标准库。启用前请先阅读脚本逻辑，并按项目需要调整规则。
