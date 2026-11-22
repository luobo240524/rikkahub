---
summary: 环境约束、关键路径与可复用命令
created: 2026-11-01 22:09
updated: 2026-11-10
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
- [2026-11-01] 纯文档/记忆提交不触发构建:custom-build.yml 配了 paths-ignore(**.md、docs/**、.zcode/**、.gitignore),commit 68549ed0;代码改动照常构建
- [2026-11-01] 读构建日志:`gh run view --job <job_id> --log-failed -R luobo240524/rikkahub`
- [2026-11-01] 交付下载链接:先实测速度(直连/gh-proxy.com 等),按速度排序给纯文本完整 URL,不用 markdown 链接
- [2026-11-01] 用户手机装 arm64 变体 app-arm64-v8a-dist-rN.apk,来自 custom-build prerelease
- [2026-11-04] HugeIcons 是 jitpack 依赖(com.github.rikkahub:hugeicons-compose),本机无 gradle 缓存,新图标名只能从代码库已用集合验证(已用名含 Earth/InLove/Clock02/Menu03)
- [2026-11-04] 导航是 androidx.navigation3(NavDisplay+Screen:NavKey 类型化路由,非 NavHost);Room 新表用手写 Migration 免 schema json;整库备份自动覆盖新表——细节在 phase2-blueprint.md
- [2026-11-07] **数据库 version=28**:mood_checkins((date,subject) 唯一索引,重复打卡 REPLACE 覆盖)+moments_posts 两表;DAO 已进 DataSourceModule,LocalTools 10 参注入(AppModule)
- [2026-11-07] **发现体系入口**:收纳菜单 WakeCapsule.kt(发现/打卡/朋友圈三项可导航)+抽屉 ChatDrawer Earth 行;路由 Screen.Discover/Moments/MoodCheckin;页面在 ui/pages/{discover,moments,mood}/,VM 走 koinViewModel+ViewModelModule
- [2026-11-07] **安全模式自动通道**:连续 2 次启动失败后冷启直接进 SafeMode(RouteActivity shouldEnterSafeMode),也可长按应用图标;SafeMode 有一键修复(重置词典+清理库+WAL压缩+VACUUM)
- [2026-11-07] 设置页间距滑杆用 CardGroupScope.formItem;气泡颜色预设 BubbleColorPresets 硬编码中文在 AssistantBasicPage.kt L1594(不走 strings)
- [2026-11-08] **DB version=30**:secrets 加 author 列(user/assistant,纯增量 ALTER);发现体系支持助手写心里话(secrets_write 工具)
- [2026-11-08] **发现页主题系统**:tokens 在 ui/pages/discover/DiscoverTheme.kt(七套:BASELINE_LIGHT/DARK,JOURNAL,GREEN,BLUE,EDITORIAL,STARRY);选择存 DisplaySetting.discoverTheme;装饰全 drawBehind(点阵/星点/水彩晕染/叶形/虚线框);心里话便签纸色=DiscoverTheme.noteColors
- [2026-11-08] **后台提醒链路**:SocialNotifyController(选最近会话+15s去抖+无社交工具跳过)→ChatService.triggerSocialNotify→handleMessageComplete(requireVisibleReply=false);通知摘要文案在三个 VM,"用户"已全部改 TA/对方
- [2026-11-08] NAMING_GUIDE 常量在 service/WakeProtocol.kt——助手提示词称呼指引(自定昵称,禁叫「用户」),声明必须在引用它的 const 之前
- [2026-11-08] r204 交付:直连 1.27MB/s(app-arm64-v8a-dist-r204.apk);Actions run_number 跳号,产物无 r203 直接 r204
- [2026-11-08] **DB version=31**:social_interactions 加 reply_to 列(评论回复目标,user/assistant)+mood_checkins 去 (date,subject) 唯一索引(同日多次打卡,Migration_30_31,DAO 改 insert)
- [2026-11-08] **评论回复语义**:reply_to=null=顶层评论渲染「评论者：内容」;非空=「评论者 回复 被回复者：内容」;UI 点评论行进入回复模式(输入框「回复 xxx」);moments/mood/secrets_interact 工具均有 reply_to 参数,回复 actor=assistant 时后台提醒助手
- [2026-11-08] **心里话三件套无条件授权**(secrets_read/write/interact 不依赖本地工具开关,AssistantLocalToolPage 心里话开关已删);便签互动:助手写的且不可见的才掩码,用户自己的不可见便签始终显示内容+互动
- [2026-11-08] **WakeCapsule=顶栏地球常驻**(点按下拉:发现/朋友圈/心里话/打卡;长按唤醒任务;showFeatureHub 旧路径已删);DrawerAction 固定 40dp 盒(Surface(onClick) 48dp 最小尺寸曾挤爆 6 按钮行,见 lessons)
- [2026-11-08] **版本号跟随构建**:工作流 -PversionCode=${{ run_number }} -PversionName="2.5.6-rN",build.gradle.kts findProperty 接收;关于页显示 2.5.6-rN 可确认覆盖安装生效;CI 有 Self-Check compileDistKotlin 快速失败步(任务名已验证)
- [2026-11-08] 心里话排列样式:SecretsLayout(WATERFALL/LIST/GRID)存 DisplaySetting.secretsLayout,顶栏折叠下拉;便签底行两行布局(窄列防 weight 挤压);便签互动点赞评论+回复+红点(secret 类型)
- [2026-11-08] 杂志主题扁平排版:DiscoverTheme 新增 cardFlat/divider/gbLeftBar(帖子无底卡+底部细横线+互动区左红竖线);CardGroupListItem 自绘行(图标垂直居中,M3 ListItem 带 supporting 时 leading 顶对齐)
- [2026-11-08] r206/r207/r209 交付(r206=十三项+反馈四项,r207=二轮四项,r209=顶栏常驻+版本号+便签修复);超长对话里模型会按历史幻觉"没有某工具",重启 app/开新对话测试
- [2026-11-08] **DB version=32**:timeline_events 表(id/event_date/event_time/title/content/type=auto|manual|reminder/author/remind_at/remind_fired=已看过/conversation_id/created_at/updated_at,索引event_date),Migration_31_32 纯增量;TimelineEventDAO 在 DataSourceModule;串联动态=页面层聚合不落表
- [2026-11-08] **四期新增结构**:ui/pages/timeline/{TimelinePage,TimelineVM}.kt+ui/pages/call/{VoiceCallPage,VoiceCallVM}.kt(路由 Screen.Timeline/VoiceCall);语音条=utils/VoiceRecorder(MediaRecorder m4a→filesDir/voice_notes)+ui/components/message/VoiceMessageBubble(Audio part metadata voice=true/duration);工具 timeline_read/write(无条件授权)+voice_call_ring(voiceCallEnabled 才注入);LocalTools 13参
- [2026-11-08] **语音通话链路**:发现页入口→VoiceCallPage 自动挑会话(lastConversationId→当前助手最近)→VoiceSessionController(实时ASR→LLM→流式TTS);ASR 限 OpenAIRealtime/DashScope/Volcengine 三家(服务器VAD);字幕=voice.transcript+会话流最新assistant回复
- [2026-11-08] **r214 交付**:run 42488845567 success,产物 app-arm64-v8a-dist-r214.apk=2.5.6-r214;本轮 gh-proxy/ghfast/llkk/ghproxy 四镜像全挂,直连 33KB/s 起步会爬坡;CI 自检步四轮全部快速失败兜住 6 类编译错(见 lessons)
- [2026-11-08] 四期预览:.zcode/v2/visualizations/1a148d8…/phase4-preview-v2.html(三屏轮播:聊天/通话/时间轴);多屏预览主题变量须遍历所有 .neu-screen 逐一设置(曾只设屏1致竖线隐形)
- [2026-11-09] **五期新增结构**:BubbleOptics.kt(共享气泡光学:neumorphShadow/liquidGlassShadow/liquidGlassEdge,阴影挂 Surface 前、边缘挂 haze 后);Assistant 新字段 bubbleLiquidStyle(clear/smoked)+discoverEnabled+timelineEnabled;VoiceCall=@Serializable data class(assistantId:String?=null),裸引用必须 Screen.VoiceCall() 带括号;LocalTools 仍 13 参(ttsManager/settingsStore 已在)
- [2026-11-10] **六期新增结构**:SilenceGuard.kt(静音守卫,归一化RMS<0.012持续3.5s触发)+SendVoiceTool.kt(send_voice:TtsSynthesizer 合成→filesDir/voice_replies 落盘→{success,url,duration})+LocalToolOption.SendVoice+MessagePartBlock.VoiceBlock(复用 VoiceMessageBubble)+GenerationLoop.generateText/generateInternal 加 extraSystemPrompt+ChatService.VOICE_CALL_SYSTEM_PROMPT(voice_call 元数据触发)+pcmToWav 移为 AudioPlayer.kt 顶层 public+DisplaySetting.showAsrInputButton+ChatInput.SimpleFaceIcon(自绘笑脸)
- [2026-11-10] **r230 交付**:run 42655766584 success,app-arm64-v8a-dist-r230.apk=2.5.6-r230,直连 1.78MB/s 最快(gh-proxy 24KB/s,ghfast 挂);首轮 r229 被自检步兜出 4 类编译错(见 lessons)后 e51f642f 修复
- [2026-11-10] **本地 git 已 ff 对齐 3e56685a(=远端 HEAD;另一窗口并行推进至 r237/DB33)**——断连期间开发模式=tarball 快照+gh api REST 推送仍可用,但 REST 推完**禁止 reset --hard 回旧基**(会删工作区文件,/recall 读不到;正确做法=fetch 恢复后 ff/restore 指定路径)
- [2026-11-10] 用户通话配置:ASR=阿里云 DashScope(qwen3-asr-flash-realtime),TTS 已配(具体供应商未确认);存量 vadThreshold=0.0 由 omit-if-zero 兜底
- [2026-11-10] 五期预览:.zcode/v2/visualizations/0c00f500…/phase5-accept-preview-v1.html(五屏:气泡强度/顶栏下拉/侧边栏通话/助手开关/液态玻璃)
- [2026-11-11] **r240 交付**:run 42853574288 success,app-arm64-v8a-dist-r240.apk=2.5.6-r240,直连最快(ghfast 当次挂);本地 git 已 ff 对齐 52743384(r245,=远端 HEAD;含唤醒八期:静默提醒每助手开关/地球退化时钟/接力去重+背景遮罩开关)
- [2026-11-11] **聊天输入栏结构**:ChatInput.kt 底部行=模型选择+搜索(SearchPickerButton L499-533)+推理按钮|表情包(send_sticker门控)+「+」(onMoreClick→ChatFilesPickerSheet)+语音条(send_voice门控)+ASR按钮+发送;「+」面板=FilesPicker(附件/注入/压缩上下文/语音模式);语音模式=聊天页内嵌连续语音对话(onStartVoiceMode→rememberVoiceModeStarter,需 server-VAD ASR 三家,非流式整段念,区别于全屏通话页)
- [2026-11-11] **document 模块解析能力**:PdfParser.parserPdf/EpubParser.parse/DocxParser/PptxParser 全部返回整篇 String——无分页/章节结构;共同阅读需扩展 EpubParser 按 spine 章节返回+PDF 按页(PdfRenderer)
- [2026-11-11] **九期评估结论**:搜索收纳=半小时级(ChatInput 删按钮+FilesPicker 加入口);共同阅读可做(TXT/EPUB 一期,新表 books/reading_progress/book_annotations+Screen.BookReader);一起听=机控制可做,网易云无官方 API,社区 API 有 VIP/版权/接口失效三风险,须先向用户声明再动工
- [2026-11-14] **r261-r266 交付链**(通话十期):827678ae 挂后台+按住说话+断线自愈→d25b2cd1 指针API修→b3a1ecbb MiMo回退+「用户」→TA全清理→b815ffa0 四项修(思考常驻/心里话门控/唤醒日志/通话剥标记)→9975f957 提速+通话专用模型生效→0ff915f5 记忆工具恢复→e24a0a72 VAD 1200ms→c86e048d 撤销置顶记忆加戏;产物 r265/r266 同内容
- [2026-11-14] **通话新结构**:VoiceCallSessionManager(Koin单例,替代 VoiceCallVM,koinInject 绑定)持有 TTS/ASR 构建+会话流订阅+eventBus 挂断监听;VoiceCallForegroundService(foregroundServiceType=microphone,Manifest 加 FOREGROUND_SERVICE_MICROPHONE);VoiceCallMiniBar 挂 RouteActivity NavHost 后(Navigator 加 currentKey 判断当前路由);ACTION_RESUME_VOICE_CALL 通知回页
- [2026-11-14] **通话轮瘦身参数**:Assistant.voiceCallMode(运行时字段不落库)→ChatToolFactory 跳过 memory 外全部来源;contextMessageLimit 8;VOICE_CALL_SYSTEM_PROMPT 含禁动作描写+禁分条标记;TTS 软切句 SOFT_SENTENCE_ENDERS(≥24字逗号先开口)
- [2026-11-14] 用户 MiMo TTS:voicedesign 模型;音色描述留空自动降级内置音色(白桦),描述非空走设计模式(音色有漂移是模型特性);VAD/断句设置页可调 300-5000
- [2026-11-14] 共同阅读/一起听/省token 已由其他窗口交付(r250-r257,cb7701e7),无交接快照,细节看提交历史与提交信息
- [2026-11-15] **DB version=34**(Migration_33_34:timeline_events 加 ref_id + 新建 anniversaries 表含 target_year);远端 HEAD=d9ff2ccd(r273=run 43614444979);本地=远端已同步
- [2026-11-15] **自动备份链路**:AutoBackupController(RikkaHubApp AppScope 启动 5s 后检查,距上次≥24h 执行)→webDavSync.backup(items=[DATABASE])→listBackupFiles 裁剪保留份数;开关在备份页提醒 tab(BackupReminderConfig.autoBackupEnabled/KeepCount/lastAutoBackupTime);成功同时更新 lastBackupTime 不再弹手动提醒
- [2026-11-15] **DeepSeek 语音能力边界**:TTS=POST /v1/tools/tts 整段 wav/pcm 24kHz,voice_id 仅 stella/tide,不支持语速,1.5元/万字符(限时),自带 filter_markdown;ASR=REST 整段上传最长 120s/8MiB 非流式(0.39元/h),与实时 ASR 架构不匹配不接
- [2026-11-15] **纪念日结构**:anniversaries(name/month/day/target_year/repeat_yearly/note);工具 anniversary_read(倒计时)/anniversary_write(delete_id/update_id 分支);当天注入走 ChatService.handleMessageComplete 的 extraSystemPrompt listOfNotNull(与一起听/共同阅读同通道,本地工具开关门控);管理页已交付(见下条)
- [2026-11-15] **r274-r280 交付链**:3d65d8de(r274 摘要称呼+通话打字+语音条 ExoPlayer)→561e4f02(r276 唤醒无消息治理)→534bfebc+8a3d2c19(r278 纪念日第二波)→47593d0f(r279 imePadding+删除确认+顶栏滚走)→0f3e77e0(r280 顶栏空隙);r275/r277 CI 自败占号;远端 HEAD=0f3e77e0
- [2026-11-15] **纪念日第二波结构**:Screen.Anniversary+AnniversaryPage/VM(ui/pages/anniversary/,ViewModelModule 注册);TimelineVM 叠 anniversaryDAO 流出 TimelineItem.Anniversary(每年=今年那天,单次=目标年,09:00 排序);时间轴顶栏 CalendarAdd01 入口
- [2026-11-15] **社交页顶栏=SocialScrollHeader**(DiscoverTheme.kt,theme 可空回退 onSurface)作列表首项随内容滚走,已替换 9 页固定 LargeFlexibleTopAppBar(发现/朋友圈/心里话/打卡/时间轴/一起听/共同阅读/纪念日);状态栏高度由 Scaffold innerPadding 提供,头部勿再 statusBarsPadding;删除确认走 RikkaConfirmDialog
- [2026-11-15] **通话打字发送链路**:VoiceSessionController.textInputs 通道+Event.TextInput(select 双源)→打断收音 cancelAndJoin→submitUtterance 与语音同管线;VoiceCallSessionManager.sendText(非通话态拒绝);VoiceCallPage 输入框 imePadding
- [2026-11-15] **通话摘要链路**:notifyCallEnded→summarizeCallForTimeline(answered&&rounds>0)→call_id 元数据圈定轮次→fastModel(generateTitle 同款 providerHandler.generateText 模式)→timeline_events(type=call,ref_id=callId 去重);CallRecordCard=方案C 渐变胶囊(展开切明面板)
- [2026-11-15] **AGENTS.md 新增「Workflow Discipline」节**:四条通用硬规则(修bug禁偷懒/写完即查/结果复查/不清楚就问),每个窗口自动加载;用户明令约束一切任务
- [2026-11-15] r281 交付:run 281 success,app-arm64-v8a-dist-r281.apk(语音条靠右修复,b0b74b22);当次直连/ghfast/ghproxy/llkk/moeyy 全挂,gh-proxy ~73KB/s 唯一可用
- [2026-11-21] **本机无 python/py,node 不在 PATH;Electron-as-node 可用**(ELECTRON_RUN_AS_NODE=1 配 "D:/zcode/ZCode.exe",node v24.21.0)——node --check 语法检查、起临时 http 静态服务器均可;browser-use IAB 不支持 file:// goto(须 localhost 服务器再开);IAB fullPage 截图对超高页平铺错乱(用 setViewportSize+clip)
- [2026-11-21] **内心世界三底座**:①WebView 组件(ui/components/webview/WebView.kt)javaScriptEnabled+domStorage+state.interfaces JS 桥(addJavascriptInterface)+Console 日志调试面板;②web 模块=Ktor CIO 服务器(WebServerManager 手动启停,支持 localhostOnly 绑 127.0.0.1)+REST(routes/:conversations/settings/files/events SSE)+web-ui React 工程由 gradle 打进 static 资源;③vitals 数据面=VitalsVM StateFlow(bpm/breath/valence/arousal/dominance/safety/confidence/moodBase/emotion/modelWord/pulses/curve),replayCurve 重放算法可直接产出情感坐标轨迹
- [2026-11-21] **内心世界预览稿**:D:\zcode项目存储文件\.zcode\v2\visualizations\dd03117802b3ef8308310b75811eebef879026c257461c1e476a9e1d5d55cf29\inner-world-preview-v1.html(会话可视化目录,repo 外不入库,实现时先拷进 .zcode/v2/visualizations/);八主题/戳心/点词弹层/睡醒切换浏览器实测通过
- [2026-11-21] 远端 HEAD=5e9a166f(r322),本地已同步;DB v36;r315/316 vitals 自评协议退到纯后台+思考链静默化(草拟标签也不漏)——与内心世界心声协议直接相关;r318-321 角标 LED/限宽 80dp/顶栏 3:2 分宽;r322 ASR 错误静默化
- [2026-11-21] **r323-326 交付链**:f4ced384(r323 编辑思考+协议转后台+静默化扩面)→ce3480d3(r324 通用尾行词)→48d48eae(r325 裸词升级)→55201148(四技能)→8e5dabf4(r326 角标定宽)→982839e4(docs)=远端 HEAD;DB v36;VitalsReminderInputTransformer.kt 已删除;上游 v3.0.0 的编辑思考实现=ChatInputState.editingReasoning(TextFieldState)+横幅按钮+FullScreenEditor(TextFieldState)重载
- [2026-11-21] **四项目技能**:.zcode/skills/{ui-preview,ui-impl,deliver,rootcause}/SKILL.md——热加载即生效(写入后当次会话即可 Skill 调用);ui-preview 含「参数级小改动免预览」豁免条款
- [2026-11-21] **顶栏角标现状**:ChatPage 标题行分配反转(角标无权重先量,VitalsMiniBadge 文本槽 widthIn(max=80.dp) 未动);「你的世界」探讨结论与分期见 decisions [2026-11-21];内心世界心声源转向=VitalsFallbackAnalyzer prompt 扩 inner_voice
- [2026-11-22] **本地编译链全通(本机不再只能靠 CI)**:AS 2026.2.2.9 在 C:\Program Files\Android\Android Studio(jbr=JDK25)+SDK D:\AndroidSdk(local.properties 已写 sdk.dir,gitignore 覆盖)+用户级 C:\Users\文\.gradle\gradle.properties 加 android.overridePathCheck=true(项目路径含中文 AGP 拦);命令=JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ANDROID_HOME="D:/AndroidSdk" ./gradlew :app:compileDistKotlin -x :web:buildWebUi(热缓存 ~10min);web-ui 依赖已装,node/pnpm 便携版 D:\tools\nodejs\node-v22.14.0-win-x64(用户级 .npmrc 已指 npmmirror;bash 里 pnpm 要 cmd //c 调)
- [2026-11-22] **你的世界素材与预览**:.zcode/v2/visualizations/your-world/ 下 cover-v1~v6.html+assets/(frame-green.jpg 928x1232 水彩花框/frame-variants 四色系/sketch-cover;fonts 47MB 已 gitignore,重取=npmmirror fontsource tarballs 解到 assets/fonts/<名>);本地静态服=ELECTRON_RUN_AS_NODE=1 "D:/zcode/ZCode.exe" .zcode/v2/visualizations/serve.mjs(127.0.0.1:8811)
- [2026-11-22] 本地 git HEAD=8a786b0b(be0b253e 封面预览+素材+serve.mjs、8a786b0b schema v36 入库);app/schemas 36.json 由本地构建生成入库(Room exportSchema 活跃,v37 迁移时同理);push 断连后台重试中,成功与否以远端为准
- [2026-11-22] **你的世界预览定稿线四件**:.zcode/v2/visualizations/your-world/ 下 cover-v7/v8/v9+home-v1+thoughts-v3+lights-v1.html;壁纸取色兜底实现参照各页 applyWall(REGIONS 逐区亮度 tone/panelAlpha+全图平均色派生 paper/paper2/paper3/panelbg);细节见 handoffs/你的世界四页预览与壁纸兜底.memo.md
- [2026-11-22] **your-world assets 新增**:bg-butterfly-water/bg-bedroom-dream/bg-window-butterfly.jpg(壁纸,带小红书水印,正式版建议无水印原图)+bulb-blue.png/bulb-yellow.jpg(原图,前者白底不透明 RGBA)+bulb-blue-cut.png(洪泛抠图成品 629x977 透明底,留灯图标用)
- [2026-11-22] 并行窗口 r327=b1c8ab4b(思维链还原原生,删 VitalsReasoningScrubTransformer);本窗口 git 39c73e1c=远端(7 连推全成功,低速超时+后台重试法);INDEX/分层已更新到四页定稿线
- [2026-11-22] **你的世界宠物素材库定案**:assets/pets/ 下 12 webp≈19MB=cove 四件套(cat/dog/egg/room-sprites-light.webp,384px 格 3×2 姿态系,水彩绒毛)+PetDex 10 张(8 列 9 行帧动画系,**已撤销不上页面**,正式版删);正式版只打包 cove 4 张≈460KB;授权=cove-pet-nest(moonlin1213,MIT,NOTICE 四图共享)+PetDex(CodeWithBasu,MIT);原版 Cove app 闭源不可用,VPet 美术独立版权禁商用,OpenPets(MIT)后备
- [2026-11-22] **cove 玩法数据模型(实现注)**:world_pet_state 加 hatch 列+粮仓全局计数(settings 或全局列);garden 需 garden_log 表(actor/action/plant/time)——v37 五表外新增或并入事件表**待新窗口拍板**;「对话攒粮」接 GenerationLoop 每轮+1;TA 共同照顾接唤醒轮工具;孵化池/稀有概率/信号需求做成常量
- [2026-11-22] 本窗口 git 推进:79edc013(花园 v1-v2)+bfa340f0(宠物 v1-v4+assets 13 webp)=HEAD,叠加并行 r330(db6c31a7,思考清理手动一次性);远端同步一次成功
