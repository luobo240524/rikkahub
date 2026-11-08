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
- 四期新增:时间轴(ui/pages/timeline/)+语音通话(ui/pages/call/,路由 Screen.VoiceCall/Timeline);
  语音条=utils/VoiceRecorder+ui/components/message/VoiceMessageBubble;主动来电=voice_call_ring 工具

## 进行中

- [2026-11-08] **r214 交付,等用户新窗口验收(先备份,DB 31→32)**:四期全套(实心立体材质凸凹/
  单行框线贴合修复/下拉红点/时间轴四能力/语音条/语音通话)+三点反馈迭代(通话开关门控/
  入口收进发现页/主动来电工具);验收清单见 handoffs/四期交付r214与通话入口迭代.memo.md
- [2026-11-08] 四期 v1 已声明边界:语音条不转文字/通话无静音免提/节点提醒无系统推送/
  来电通知不直达接听(通知→app→发现→语音通话)——验收后按需升级
- [2026-11-08] 三期候选池:打卡提醒限频/深色适配/热力图徽标/朋友圈图片发帖;新功能一律进发现页
- [2026-11-08] DB version=31(social_interactions.reply_to+mood_checkins去唯一索引,纯增量);
  versionName=2.5.6-rN/versionCode=run_number(工作流-P注入);远程 HEAD=2d8f2526
