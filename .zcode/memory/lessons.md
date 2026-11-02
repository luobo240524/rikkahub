---
summary: 踩坑记录:现象、根因与规避方式
created: 2026-11-01 22:09
updated: 2026-11-01 22:09
status: active
---

# 教训

- [2026-11-01] **r180 构建失败(Gradle Build 步骤)**:SafeModeActivity.kt 用了 `db.rawQuery(...)`,而 Room 的 SupportSQLiteDatabase 接口只有 query()/execSQL();规避:返回结果行的 PRAGMA 一律 `query("...")`,参考 data/sync/DatabaseBackup.kt:42
- [2026-11-01] **静默安装 git 失败(退出码 2)**:普通 shell 无管理员权限;规避:PowerShell `Start-Process -Verb RunAs` 提权安装
- [2026-11-01] **未认证 API 读不到 Actions 日志(403)**:日志下载接口必须认证;规避:gh CLI + GH_TOKEN 读 `--log-failed`
- [2026-11-02] **UIMessage.modelId 是 `Uuid?` 不是 String**——r185 编译失败根因:`message.modelId?.takeIf { it.isNotBlank() }` 编不过;兜底用 `?.toString()`
- [2026-11-02] **CI 偶发 SDK 组件下载损坏(ZipException: Archive is not a ZIP archive)**——r187 失败根因,GitHub 运节点网络抖动,非代码问题;`gh run rerun <id>` 重跑即可
