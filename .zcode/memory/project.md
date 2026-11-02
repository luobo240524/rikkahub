---
summary: RikkaHub 二改维护:目标、结构与任务状态
created: 2026-11-01 22:09
updated: 2026-11-01 22:09
status: active
---

# 项目状态

## 目标

维护 luobo240524/rikkahub 二改版(上游 rikkahub/rikkahub,v2.5.6 基础):消息分条(按助手开关)、
MD3 Expressive 风格、安全模式急救等自定义功能;GitHub Actions 云端构建 APK 发布供手机覆盖安装。

## 结构

- 工作分支 `custom-2.5.6`(master 仅上游参考);push 该分支自动触发 `.github/workflows/custom-build.yml`
- 多模块:app(UI/逻辑)、ai(厂商抽象)、common、document、search、speech、web、workspace 等
- `app/src/dist/` 源集:应用名"RikkaHub X"、X 角标图标、包名 me.rerere.rikkahub.debug
- 根目录 `HANDOFF_IMGGEN_FREEZE.md`:生图卡死事故完整交接(按节追加,勿重写旧节)
- `.zcode/skills/` meow-handoff/meow-recall;`.zcode/memory/` 本记忆库

## 进行中

- [2026-11-02] 气泡边框美化(构思已定,待用户说"构建"再实施):边框颜色/渐变 + emoji/PNG贴纸,每条气泡都带,动物趴顶边效果
- [2026-11-03] 每日心情打卡(构思已定,待构建,与上条一并实施):用户点选今日心情(emoji/表情包+打卡短语)一键打卡;**助手也参与打卡**(AI 也选心情配短语);入口考虑应用内页面或安卓桌面小组件;数据 Room 表(日期/主体[用户|助手]/心情/短语);可与记忆库联动(如 /areas/mood.md)
- r187 已构建交付(gh-proxy.com 10KB/s);记忆轨 a6fbf7e3 因直连被卡待补推(仓库外有 patch 备份)
