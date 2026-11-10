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
- [2026-11-10] **远端 HEAD=e51f642f(338031e0 同树前驱+e354e42e/parentless 孤儿无害);本地 git 停在 bbad2ab2**——直连恢复后 git fetch+merge --ff-only 对齐;断连期间开发模式=tarball 快照(D:\zcode\tmp-refs\rikkahub-r227\luobo240524-rikkahub-565a6c2)+gh api REST 推送
- [2026-11-10] 用户通话配置:ASR=阿里云 DashScope(qwen3-asr-flash-realtime),TTS 已配(具体供应商未确认);存量 vadThreshold=0.0 由 omit-if-zero 兜底
- [2026-11-10] 五期预览:.zcode/v2/visualizations/0c00f500…/phase5-accept-preview-v1.html(五屏:气泡强度/顶栏下拉/侧边栏通话/助手开关/液态玻璃)
