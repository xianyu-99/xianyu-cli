# GitHub 访问经验

## 适用场景
- 查看项目 README、文档
- 获取 release 信息
- 浏览代码（注意：raw 文件 URL 可直接 fetch，页面需要浏览器）

## 推荐策略
1. 先 `web_fetch` 目标 URL（如 https://github.com/user/repo）
2. 若需要看代码文件内容，用 `browser_navigate` 打开后 `browser_get_dom`
3. 需要下载 release asset 时，获取下载链接后用 `web_fetch`

## 已知限制
- GitHub 对未登录用户有速率限制
- 部分私有仓库无法访问
