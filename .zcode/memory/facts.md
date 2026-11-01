---
summary: 环境约束、关键路径与可复用命令
created: 2026-11-01 22:09
updated: 2026-11-01 22:09
status: active
---

# 事实

## 环境

- [2026-11-01] 工作机只有 C:/D 两盘(无 F 盘,旧交接文档里的 F:\zcode\rikkahub x 是旧机器路径)
- [2026-11-01] 本机无 Android SDK/JDK,不能本地编译;编译验证全靠 GitHub Actions CI
- [2026-11-01] github.com 直连间歇阻断(api.github.com 与 raw.githubusercontent 通常可达);推送失败先重试再等等
- [2026-11-01] gh CLI 用 GH_TOKEN 方式调用(GCM 的 gho_ token 缺 read:org,`gh auth login` 会拒)

## 路径与位置

- [2026-11-01] 本地克隆 D:\zcode项目存储文件\rikkahub x;git 在 C:\Program Files\Git
- [2026-11-01] gh CLI 在 C:\Users\文\tools\gh-cli\bin(已入用户 PATH);GH_TOKEN 已设为用户环境变量
- [2026-11-01] Context7 MCP 配在 ~/.zcode/cli/config.json 的 mcp.servers,查 Compose/Material3/Room API 文档用

## 命令与操作

- [2026-11-01] 发布新版本:改动后 `git push origin custom-2.5.6`,CI 自动构建,版本号 rN=run_number
- [2026-11-01] 读构建日志:`gh run view --job <job_id> --log-failed -R luobo240524/rikkahub`
- [2026-11-01] 交付下载链接:先实测速度(直连/gh-proxy.com 等),按速度排序给纯文本完整 URL,不用 markdown 链接
- [2026-11-01] 用户手机装 arm64 变体 app-arm64-v8a-dist-rN.apk,来自 custom-build prerelease
