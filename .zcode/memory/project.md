---
summary: RikkaHub 二改维护:目标、结构与任务状态
created: 2026-11-01 22:09
updated: 2026-11-10
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
- 五期新增:气泡共享光学库(ui/components/message/BubbleOptics.kt:neumorphShadow/
  liquidGlassShadow/liquidGlassEdge);气泡材质含液态玻璃(清透/烟灰=Assistant.bubbleLiquidStyle)
- 六期新增:静音守卫(speech/.../asr/SilenceGuard.kt)+send_voice 语音条工具(tools/local/
  SendVoiceTool.kt,VoiceBlock 渲染)+GenerationLoop.extraSystemPrompt 通道(通话口语化注入)

## 进行中

- [2026-11-10] **r230 交付,等实机验收**:六期全套(通话全链修复:三家 ASR 静音守卫回退 MIC/
  DashScope 阈值 omit-if-zero+默认0.7+重连不自杀/OpenAI火山补重连/空转写3次报错/通话页独占
  TTS/合成失败跳过/通话口语化注入)+send_voice 语音条工具(本地工具页「语音条」开关)+输入栏
  三项(表情包按 send_sticker 门控+自绘笑脸图标+showAsrInputButton 开关);
  验收清单见 handoffs/五期六期迭代交付r230待验收.memo.md
- [2026-11-10] **本地 git 仓库停在 bbad2ab2,远端 HEAD=e51f642f**——直连断连期间开发在 tarball
  快照(D:\zcode\tmp-refs\rikkahub-r227\luobo240524-rikkahub-565a6c2)上做+REST 推送;直连恢复
  后先 git fetch+ff 对齐再动代码,期间继续快照+REST 模式
- [2026-11-10] 边界升级候选:通话静音/免提、提醒系统推送、来电通知直达接听、send_voice 转写
- [2026-11-08] 三期候选池:打卡提醒限频/深色适配/热力图徽标/朋友圈图片发帖;新功能一律进发现页
- [2026-11-10] DB version=32(timeline_events,纯增量);versionName=2.5.6-rN/versionCode=
  run_number(工作流-P注入);远端 HEAD=e51f642f(r230=run 42655766584)

## 已验收里程碑

- [2026-11-08] r209 验收通过;[2026-11-09] 四期 r214 验收:7 条反馈全部转为五期/六期迭代完成
