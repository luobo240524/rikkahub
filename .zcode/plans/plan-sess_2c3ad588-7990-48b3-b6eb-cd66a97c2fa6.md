# 二期验收修复 + 三期第一批(6项全做)

## Context

r198 验收反馈 6 项:①ChatDrawer 底部"设置"图标显示残缺;②打卡/朋友圈作者未关联助手名称头像;③用户发布后助手无感知;④两页面 UI 过简;⑤缺助手点赞评论+红点;⑥新增心里话/小秘密。已探明:工具栏在 `ChatDrawer.kt` L822-850(DrawerAction 统一 40dp 圆形、内容区仅 20dp,Settings03 glyph 溢出裁切);作者名硬编码 emoji+通用文案;后台生成可复用唤醒链路(`ChatService.triggerAutonomousWake` 模式);DB 迁移照抄 `Migration_27_28` 模板。UI 借鉴:JetInstagram/微信朋友圈(头像+灰底评论区)、GitHub 式热力图/连续打卡。

已定取向(问题未答,按推荐):助手后台**只点赞/评论不进聊天**(需旁路"无可见文字视为失败"检查);用户侧**双向互动**(也能给助手点赞评论)。

## 一、设置图标修复(ChatDrawer.kt)

`DrawerAction`(L822-850):`Box(padding(10.dp).size(20.dp))` → `padding(8.dp).size(24.dp)`(总footprint不变40dp,glyph 不再裁切)。图标保持 Settings03(其他页 24dp 下正常)。

## 二、DB 28→29(纯增量,两张新表)

- `entity/SocialInteractionEntity.kt`:id/target_type('moment'|'checkin')/target_id/actor('user'|'assistant')/type('like'|'comment')/content?/created_at/is_read(默认1);索引(target_type,target_id)
- `entity/SecretEntity.kt`:id/content/visible(默认1)/conversation_id?/created_at
- `dao/SocialInteractionDAO.kt`(insert、deleteLike、commentsFor、unreadCounts Flow 按 target_type 分组 where actor='assistant' and is_read=0、markReadByType、recentForTool)、`dao/SecretDAO.kt`(insert/flowAll/recent/deleteById)
- `Migration_28_29.kt`(照抄 27_28:事务+CREATE TABLE IF NOT EXISTS+Tracker)→ `AppDatabase.kt` version=29+注册实体+抽象DAO → `AppDatabaseFactory.kt` L22 加迁移 → `DataSourceModule.kt` 加两个 single。备份恢复零改动(整库文件级)

## 三、助手互动工具 + 红点

- `MomentsTools.kt`:`moments_read` payload 补 `put("id", p.id)`;新增 `moments_interact`(post_id/action like|comment/content),like 先查重返回 ALREADY_LIKED,写入 actor='assistant'、is_read=0
- `MoodTools.kt`:同上,`mood_read` 补 id + 新增 `mood_interact`
- 新 `SecretsTool.kt`:`secrets_read`(visible 的返回内容,invisible 返回"对方设置为不可见")
- `LocalTools.kt`:构造器 10→12 参(+两 DAO),注册三工具;`LocalToolOption` 加 `Secrets` 组;`AppModule.kt` 12 个 get();`AssistantLocalToolPage.kt` 加心里话开关
- 红点:新 `SocialBadgeViewModel`(koinViewModel,暴露总数+按类型 unread Flow);显示于 DiscoverPage 两张卡片、ChatDrawer"发现"入口、WakeCapsule 收纳菜单"发现"项(仿 WakeCapsule L86-94 的 8dp 圆点);MomentsVM/MoodCheckinVM init 时 markReadByType 清零

## 四、后台提醒(发布均提醒)

- `ChatService.kt`:`handleMessageComplete` 加参数 `requireVisibleReply: Boolean = true`,L825 检查加此条件;新增公开 `triggerSocialNotify(conversationId, summary)`(仿 triggerAutonomousWake 精简版:launchGenerationJob+previousJob.join+initializeConversation+handleMessageComplete(hiddenInput, requireVisibleReply=false),无 WakeRegistry/无重试链)
- 新 `service/SocialNotifyController.kt`:注入 settingsStore+conversationRepo+ChatService;选目标会话=当前助手最近会话(`getRecentConversations(assistantId,1)`);15s 内存去抖;拼 synthetic 消息(内容摘要+指引"用 moments_interact/mood_interact 点赞评论,不需要时保持安静")
- 触发点:MomentsVM.post、MoodCheckinVM.checkin、SecretsVM.add、用户评论助手帖子时(用户点赞不提醒;助手自己的工具动作不提醒,防循环)

## 五、身份关联(UIAvatar)

- MomentsPage 卡片+MoodCheckinPage 历史:author=='assistant' → `UIAvatar(assistant.name, assistant.avatar)`+助手名;=='user' → `UIAvatar`+`assistant.userNickname ?: settings.userNickname`(头像同理,对齐 ChatMessage.kt L222-223);删 ✏️/🤖 emoji 前缀。数据来源 `LocalSettings.current.getCurrentAssistant()`

## 六、UI 美化

- **MomentsPage**:LazyColumn 首项加渐变封面头图(primaryContainer→secondaryContainer,助手头像+标题);卡片重做=40dp 头像+名字+内容+时间,下方微信式灰底圆角框(点赞人列表+评论逐行);卡片操作行(心形/气泡图标+计数,用户可点赞评论,评论弹小对话框);发布框改胶囊圆角+发送 IconButton
- **MoodCheckinPage**:月历格子日期下嵌心情 emoji(今日圆环,双打卡角落小标);顶部统计行(连续天数/本月打卡/最常心情);历史卡重做(头像+名字+大 emoji+短语+互动行);加"最近30天心情胶囊条"横向 emoji 行
- 新 **SecretsPage**(`ui/pages/secrets/`):Scaffold+列表卡(内容+时间+可见性徽标:"对方可见"/锁"对方不可见")+底部发布框+可见性 FilterChip;`RouteActivity.kt` 加 Screen.Secrets+entry(照抄 L566-576/L783-790);DiscoverPage CardGroup 加心里话入口卡;WakeCapsule 菜单不动(经发现页进入)
- 新页面文案硬编码中文(AGENTS.md 优先不做本地化,同 WakeCapsule 先例)

## 验证

本机无 Android SDK,全靠 CI:改完 commit+push custom-2.5.6 → `gh run list` 45s 内查触发(没触发 workflow_dispatch 手动补)→ 失败 `gh run view --log-failed` 修复重推 → 成功后实测镜像速度给纯文本下载链接(app-arm64-v8a-dist-rN.apk)。交付时提醒:升级前设置→备份→本地导出(28→29 纯增量迁移,安全);验收点=设置图标完整、发布显示助手名头像、发帖后红点亮起+助手点赞评论出现、心里话不可见时工具读不到内容、朋友圈封面头图/打卡 emoji 日历生效。完成后立即 push(每完成即推)。