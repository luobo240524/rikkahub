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

- [2026-11-08] **r209 交付,等用户新窗口验收**:含 r205批十三项+试用反馈四项+二轮四项(设置图标
  48dp挤压真根因/下拉补发现/回复提醒/心里话授权)+r209批(顶栏地球常驻/版本号跟随构建/
  便签掩码修正/窄列按钮两行重构);验收清单见 handoffs/r209交付与自检机制落地.memo.md
- [2026-11-08] 心里话权限结案(重启 app 工具刷新,助手成功写入);CI Self-Check compileDistKotlin
  自检步已验证;写完代码立即自查规则定稿(decisions.md)
- [2026-11-08] **四期路线图已定,等开工(先出类实机预览)**:语音条消息(微信式)+语音通话(打电话式,
  OpenAIRealtime/Volcengine实时ASR底子)+时间轴四能力(自动提炼/手动记录/串联动态/节点提醒);
  三期候选:打卡提醒限频/深色适配/热力图徽标/朋友圈图片发帖;新功能一律进发现页
- [2026-11-08] DB version=31(social_interactions.reply_to+mood_checkins去唯一索引,纯增量);
  versionName=2.5.6-rN/versionCode=run_number(工作流-P注入);远程 HEAD=2d8f2526
