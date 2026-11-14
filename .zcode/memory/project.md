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

## 进行中

- [2026-11-15] **r273 三件交付,等真机验收**:①WebDAV 自动备份(每天一次设置+数据库,保留份数可调)
  ②通话摘要进时间轴+通话收纳卡玻璃胶囊改版(方案C)③纪念日第一波(TA 工具+当天注入+助手开关);
  验收清单见 handoffs/r268-r273修复与三功能交付.memo.md
- [2026-11-15] **纪念日第二波待做**:管理页(时间轴顶栏🎂入口+列表增删改)+时间轴当日展示卡(页面层聚合)
- [2026-11-15] DeepSeek TTS 已接=备用通道(仅两音色);ASR 维持 DashScope 不变(DeepSeek ASR 非流式不接)
- [2026-11-15] 本地 git=远端 HEAD d9ff2ccd(r273);直连间歇(gh-proxy 备胎);禁 reset --hard 回旧基
- [2026-11-11] 边界升级候选:通话静音/免提、提醒系统推送、来电通知直达接听(通知按钮直达通话页)
- [2026-11-08] 三期候选池:打卡提醒限频/深色适配/热力图徽标/朋友圈图片发帖;新功能一律进发现页
- [2026-11-15] DB version=34;versionName=2.5.6-rN/versionCode=run_number(工作流-P注入);
  远端 HEAD=d9ff2ccd(r273=run 43614444979)

## 已验收里程碑

- [2026-11-08] r209 验收通过;[2026-11-09] 四期 r214 验收:7 条反馈全部转为五期/六期迭代完成
