---
summary: RikkaHub 二改维护:目标、结构与任务状态
created: 2026-11-01 22:09
updated: 2026-11-08
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
- 发现页主题 tokens 在 `ui/pages/discover/DiscoverTheme.kt`;预览稿在 `.zcode/v2/visualizations/`

## 进行中

- [2026-11-08] **三期主题系统+十项验收已交付 r204,等用户装机验收**:七套主题(基线亮暗/手账/
  绿色含绿叶层/蓝色水彩/杂志/星空)🎨折叠下拉切换、心里话便签墙(双列瀑布纸色随主题)、
  助手写心里话(secrets_write)、评论「xx回复xx」格式、打卡自定义emoji+按日查看、
  图标居中、设置图标 Settings02、提示词去「用户」化(NAMING_GUIDE)
- [2026-11-08] DB version=30(secrets.author 列,纯增量);r204 直连 1.27MB/s 交付
- [2026-11-08] 三期候选未开工:打卡提醒限频/深色适配/预设主题包/热力图与连续徽标/朋友圈图片发帖;
  新功能一律进发现页
- 远程 HEAD=87dccc35 与本地同步;r199~r204 全周期交付
