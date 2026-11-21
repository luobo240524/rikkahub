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
- 七期新增:通话收纳便签卡(ChatList.kt buildCallDisplayItems/CallRecordCard)+通话生命周期告知
  (ChatService.notifyCallEnded/notifyCallDeclined,isSynthetic 事实备注)+hang_up_call 工具
  (tools/local/HangUpCallTool.kt,AppEvent.VoiceCallHangupRequest)+静默提醒(service/
  IdleNotifyWorker.kt+ChatService.runIdleNotifyCheck;注意唤醒八期又给静默提醒加了每助手开关)
  +Assistant.voiceCallSkipThinking(通话轮 reasoning OFF)+VoiceSessionController.speak 签名
  (String,Boolean)续句 flush=false
- 八期后新增(其他窗口 r282-r322):vitals 生命体征系统(data/vitals/:VitalsEngine computePulse/decay+
  VitalsTagParser+VAD 词典 assets/vitals_vad.json 302 词+VitalsReportTransformer 流式自评+
  VitalsFallbackAnalyzer 小模型兜底+VitalsPage/VM+聊天顶栏迷你角标;DB v34→35→36 vitals_state/vital_pulse);
  FallbackDns(系统 DNS 失败→DoH 兜底);称呼统一(助手下昵称→全局→TA,自称「我」,只改模型副本);
  voiceServiceEnabled 每助手开关;DeepSeek 官方搜索;ASR 错误静默化;角标 LED 流动

## 进行中

- [2026-11-21] **内心世界:方案B已拍板(WebView本地HTML+原生外壳,预览即成品),预览稿交付,三问待拍板**
  (心声扩协议/互动取舍/入口替换VitalsPage还是新路由);实现=HTML资产+JS桥4接口+协议扩展
- [2026-11-21] r280 九项验收仍未完整过(清单在 r274-r280 快照 Next);r316-r322 真机验收也欠着;
  [2026-11-15] r281 语音条靠右修复已交付(b0b74b22,gh-proxy 链接)
- [2026-11-15] DeepSeek TTS 已接=备用通道(仅两音色);ASR 维持 DashScope 不变(DeepSeek ASR 非流式不接)
- [2026-11-21] 本地 git=远端 HEAD 5e9a166f(r322);直连间歇(gh-proxy 备胎);禁 reset --hard 回旧基
- [2026-11-11] 边界升级候选:通话静音/免提、提醒系统推送、来电通知直达接听(通知按钮直达通话页)
- [2026-11-08] 三期候选池:打卡提醒限频/深色适配/热力图徽标/朋友圈图片发帖;新功能一律进发现页
- [2026-11-21] DB version=36;versionName=2.5.6-rN/versionCode=run_number(工作流-P注入);
  远端 HEAD=5e9a166f(r322);r275/r277 为 CI 自败占号

## 已验收里程碑

- [2026-11-08] r209 验收通过;[2026-11-09] 四期 r214 验收:7 条反馈全部转为五期/六期迭代完成
