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
- 本窗口新增(r323-r326):编辑思考内容(ui/hooks/ChatInputState.kt editingReasoning+ChatInput 横幅+
  FullScreenEditor TextFieldState 重载,守卫放宽=忽略工具部件)/自评协议转纯后台
  (VitalsReminderInputTransformer 已删,VitalsFallbackAnalyzer 每轮转正,⚠️脉冲保留)/思考链静默化
  分组门控裸词(VitalsReasoningScrubTransformer)/顶栏角标定宽反转(ChatPage 标题行,VitalsMiniBadge 未动)/
  四项目技能(.zcode/skills/{ui-preview,ui-impl,deliver,rootcause}/SKILL.md)

- 本窗口新增(11-22):本地编译链全通(AS 2026.2.2.9+SDK D:\AndroidSdk+node/pnpm 便携版,
  compileDistKotlin 本地绿,命令见 facts);你的世界一期预览 v1-v6+素材/字体在
  .zcode/v2/visualizations/your-world/(fonts 47MB 已 gitignore)

- 本窗口新增(11-22 下午):你的世界 UI 预览定稿线四件——cover-v9(蓝星空+动效+花线)/
  home-v1(四悬牌照卡+灯泡抠图图标)/thoughts-v3(词云泡泡+抽取信箱)/lights-v1(亮熄两态);
  壁纸取色兜底(paper/panelbg 派生)+物件/壁纸文字二分;素材三壁纸+灯泡抠图入库 assets;
  细节见 handoffs/你的世界四页预览与壁纸兜底.memo.md

- 本窗口新增(11-22 晚):七问拍板(用户授权 AI 定)+花园页 v2 定稿(收起态小花园+园务
  三件套+小园日记)+宠物页 v4 定稿(**cove-pet-nest 玩法全套**:房间场景/粮仓/TA 共同
  照顾/神秘蛋孵化+12% 稀有金蛋/档案改名/多宠上限 6);素材调研定案=cove 四件套
  (room/cat/dog/egg,MIT),PetDex 撤;细节见 handoffs/你的世界拍板与花园宠物页定稿.memo.md

## 进行中

- [2026-11-26] **r361 验收修复与信件一期未完成**:成年宠物同类冷却/头像首屏/打卡键盘已改但未编译；留灯入口+邮局+Room v40+WorkManager 通知链部分接线，未完成 WorldTool write_letter、schema 40、启动重排与实机验证；详见 handoffs/r361验收修复与信件一期未完成.memo.md
- [2026-11-25] **「你的世界」一期:已实机落地,r336 为交付基线**(DB v37 七表/六页 WebView+JS 桥
  本地闭环/worldEnabled 开关/TA world_read+world_write 工具/全屏化+仿宋字体/壁纸浮层);
  详见 handoffs/你的世界r336交付与TA工具接入.memo.md
- [2026-11-21] **内心世界:方案B已拍板,三问待拍板**;心声源转向=VitalsFallbackAnalyzer 扩 inner_voice
  (自评协议 r323 已转纯后台);实现=HTML资产+JS桥4接口+兜底分析器扩展;预览稿拷进 repo 前置
- [2026-11-21] **ASR 录音态卡死观察线**:发送按钮被录音态卡死无声消失(r322 静默化副作用,已自行恢复),
  用户观察复现中;拟案=超时无音频自愈 stop 或编辑模式按钮常显
- [2026-11-21] 验收欠账:r280 九项+r316-r322+r323-326(编辑思考/静默化裸词/角标定宽);r281 已交付验收项并入
- [2026-11-15] DeepSeek TTS 已接=备用通道(仅两音色);ASR 维持 DashScope 不变(DeepSeek ASR 非流式不接)
- [2026-11-21] 本地 git=远端 HEAD 982839e4(r326+docs);直连间歇(gh-proxy 备胎,重试循环 6-8 次常见);禁 reset --hard 回旧基
- [2026-11-22] 本地 git=bfa340f0=远端(花园 v1-v2+宠物 v1-v4+cove 素材全推,叠加并行 r330 db6c31a7);push 断连=低速超时快速失败+后台 45s 步进重试
- [2026-11-11] 边界升级候选:通话静音/免提、提醒系统推送、来电通知直达接听(通知按钮直达通话页)
- [2026-11-08] 三期候选池:打卡提醒限频/深色适配/热力图徽标/朋友圈图片发帖;新功能一律进发现页
- [2026-11-21] DB version=36;versionName=2.5.6-rN/versionCode=run_number(工作流-P注入);r275/r277 为 CI 自败占号

## 已验收里程碑

- [2026-11-08] r209 验收通过;[2026-11-09] 四期 r214 验收:7 条反馈全部转为五期/六期迭代完成

## 本窗口新增(11-22→11-23 你的世界实机)

- DB v37 七表(world_thoughts/world_lights/world_plants/world_pet_state/world_pet_events/
  garden_log 独立表/world_state 粮仓)+data/world 仓库/映射层+7 DAO;schema 37.json 入库
- 六定稿页资产化 assets/your-world/(4.7MB:壁纸×4+灯泡+cove 四件套)+每页胶水层
  (appmode CSS+AndroidWorld getState/操作桥+壁纸跟随+`.back` 接桥)
- Screen.YourWorld+发现页/顶栏下拉入口(Sparkles「我的世界」)+worldEnabled 每助手开关
- TA 真实参与:world_read/world_write 工具(写碎碎念/留灯/园务/宠物,actor=assistant)
- 字体:方正仿宋 Z02S+GreatVibes 花体(@font-face 顶替 "FangSong"/"Segoe Script" 家族名)
- r334→r336 两包云端交付;HONOR 无线调试直装链+CI keystore 本地落位(签名链打通)
- 细节见 handoffs/你的世界r336交付与TA工具接入.memo.md
