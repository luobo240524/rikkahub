---
summary: 踩坑记录:现象、根因与规避方式
created: 2026-11-01 22:09
updated: 2026-11-10
status: active
---

# 教训

- [2026-11-01] **r180 构建失败(Gradle Build 步骤)**:SafeModeActivity.kt 用了 `db.rawQuery(...)`,而 Room 的 SupportSQLiteDatabase 接口只有 query()/execSQL();规避:返回结果行的 PRAGMA 一律 `query("...")`,参考 data/sync/DatabaseBackup.kt:42
- [2026-11-01] **静默安装 git 失败(退出码 2)**:普通 shell 无管理员权限;规避:PowerShell `Start-Process -Verb RunAs` 提权安装
- [2026-11-01] **未认证 API 读不到 Actions 日志(403)**:日志下载接口必须认证;规避:gh CLI + GH_TOKEN 读 `--log-failed`
- [2026-11-02] **UIMessage.modelId 是 `Uuid?` 不是 String**——r185 编译失败根因:`message.modelId?.takeIf { it.isNotBlank() }` 编不过;兜底用 `?.toString()`
- [2026-11-02] **CI 偶发 SDK 组件下载损坏(ZipException: Archive is not a ZIP archive)**——r187 失败根因,GitHub 运节点网络抖动,非代码问题;`gh run rerun <id>` 重跑即可
- [2026-11-03] **唤醒"已触发却无消息"第二形态**:wakeReplyHasContent 把 reasoning/工具调用也算实质内容,流在只吐思考阶段断掉→标记FIRED但用户看不到消息;修复=只认"用户可见内容"(非空白Text/Image/Video/Audio/Document),仅思考/工具调用→回滚重试(dcd9109c)
- [2026-11-04] **Compose BOM 2026.09 移除了 drawscope.ClipMode/drawOutline,clipRect 无 clipMode 参数**——r189/r190 连败根因;写 DrawScope 扩展时只用稳定 API(drawPath+Path.arcTo/drawRoundRect/drawLine);MessagePartsBlock 是 ChatMessage 内独立私有 composable,顶层新参数要手动穿透
- [2026-11-04] **androidx.core.graphics.ColorUtils 才有 HSL 工具**,android.graphics.ColorUtils 在本项目解析失败(参考 SettingThemePage 导入)
- [2026-11-07] **本地工具 execute 的 args 是 JsonElement**,取字段必须先 `args as? JsonObject` 再 get——直接 `args?.get()` 编不过(r197 根因)
- [2026-11-07] **PaddingValues.plus 是项目自定义扩展**(utils/ComposeExt.kt),用 `contentPadding + PaddingValues(...)` 需 import `me.rerere.rikkahub.utils.plus`(r197 根因)
- [2026-11-07] **设置页列表是 CardGroup 自定义 DSL**:item(onClick/modifier/overline/supporting/leading/trailing/colors/headlineContent) 无尾 lambda,滑杆类用 formItem(content)——标准 LazyListScope.item 写法编不过(r193 根因)
- [2026-11-06] **RoundRect 无"四裸Float+四角命名参数"重载**:用 RoundRect(rect=Rect(l,t,r,b), topLeft/topRight/bottomRight/bottomLeft)(r195 根因)
- [2026-11-06] **形状圆角在矮气泡里被系统按比例钳制**,手算钳制必然走样——描边圆角一律从 `shape.createOutline(size, layoutDirection, this)` 的 RoundRect 取(单行气泡框线不贴合的根因)
- [2026-11-06] **Screen 定义在 RouteActivity.kt 文件级**(L627 起),import 路径 `me.rerere.rikkahub.Screen` 而非 RouteActivity.Screen
- [2026-11-06] **push 成功但 workflow 未触发偶发**(cbe32160):push 后 45s 查 `gh run list`,没触发就 `gh workflow run custom-build.yml --ref custom-2.5.6` 手动补
- [2026-11-08] **HugeIcons 图标必须双 import(第三次踩定稿)**——import HugeIcons 对象+import me.rerere.hugeicons.stroke.X;object 访问仅限内置子集,Mic01/Play/Pause/Voice/Calendar03/ArrowLeft01/Add01 等是 stroke 包扩展属性;写法 HugeIcons.X;新图标名只能用代码库已用集合验证
- [2026-11-07] **joinToString 的 transform 非内联**——lambda 里不能调 @Composable;先 `map {}`(内联)再 joinToString
- [2026-11-08] **const val 初始化器禁止前向引用**——引用后声明的 const 报"must be initialized";被引用常量必须声明在前
- [2026-11-08] **顶层 const 跨包不能「文件名.属性」寻址**——`xxx.service.WakeProtocol.NAMING_GUIDE` 报 Unresolved(facade 类不存在);用 `import xxx.service.NAMING_GUIDE` 直引
- [2026-11-08] **matchParentSize 不是顶层 import**(第二次踩)——它是 BoxScope 成员,Box 直接子项免导入;顶层导入直接 Unresolved
- [2026-11-08] **Actions run_number 会跳号**——成功 run 42442649272 产物叫 r204(没有 r203);交付链接以 release 实际资产名为准
- [2026-11-07] **图标显小不一定是容器小**——Settings03 glyph 视口自带留白,加容器尺寸无效;换图标(Settings02)才治本
- [2026-11-08] **github 直连断连处置**——后台循环重试(60s 步进)通常能过;REST API 兜底(blob→tree→commit→ref)已验证;后台循环报失败先核 remote ref 再动(曾白建 orphan commit)
- [2026-11-08] **M3 Surface(onClick) 内置 48dp 最小触摸尺寸**——300dp 抽屉 6 按钮 6×48=288>268 可用,Row 顺序测量把最后按钮挤成药丸+图标缩半(设置图标三轮验收真根因,r199 加第 6 按钮后出现);修法=Surface 仅容器+固定 size(40).clip(CircleShape).clickable
- [2026-11-08] **M3 ListItem 带 supporting 时 leading 图标原生顶对齐**——垂直居中须自绘行(CardGroupListItem 已改);ListItemColors 无 contentColor 字段(用 headlineColor)
- [2026-11-08] **窄列(双列/三列)里 Row 的 weight(1f) 组件被非 weight 固定内容挤到 0 宽**(便签点赞评论按钮消失);修法=拆两行,固定内容最小化(名字 maxLines+weight(1f,fill=false))
- [2026-11-08] **PowerShell Set-Content 把 UTF-8 YAML 写成 GBK+NEL→workflow 解析 0 秒失败**;改 yml 只能用 Edit 工具,改完 file 验编码;**CI 失败的 run 也占 run_number**(0s 失败吃掉 r208 号,产物跳 r209)
- [2026-11-08] **kotlinx.coroutines.launch 忘 import**→SecretsPage 构建失败;写完自查 import(协程/composable 最易漏)
- [2026-11-08] **versionCode/versionName 恒定(191/2.5.6)致覆盖安装无法确认是否生效**(用户装了新包却见旧行为,排障绕远)——已改 -PversionCode=run_number 注入,关于页 2.5.6-rN 可查
- [2026-11-08] **超长对话(100K+ tokens)里模型按历史幻觉"没有某工具"**(不查当前工具列表,旧对话"没写权限"记忆污染)——重启 app/开新对话测试;工具 systemPrompt 写明"就在当前工具清单里"防幻觉
- [2026-11-08] **r195 的"唤醒带事项"修复是假修**——注入的是 wake_note_+MD5 标签不是 note 原文;r209 起传原文(KEY_NOTE+taskKey 兜底查 Registry)
- [2026-11-14] **PointerInputChange 无 isCancelled**(当前 androidx 已移除)——取消检测用成员 isConsumed;awaitFirstDown 在 foundation.gestures(不是 ui.input.pointer);changedToUp 是 ui.input.pointer 扩展函数(r262 编译失败根因,CI 兜住)
- [2026-11-14] **通话链路不走消息标记管线**——TTS 朗读源/字幕/通话收纳卡全文会裸露 <split/>/<wake> 且标记被逐句朗读;用 stripChatControlMarkers(BubbleSplitter,同包可用 WAKE_TAG)剥离
- [2026-11-14] **记忆 prompt 只注入索引**(profile/preferences 例外全文常驻),内容靠 memory_read/search 主动召回——裁工具时记忆工具必须保留,否则通话失忆
- [2026-11-14] **gh run watch 两种坑**——经管道会吞 --exit-status 退出码(误报成功);本身也可能被网络抖动打断(误报失败);轮询 `gh run view --json status,conclusion` 最稳
- [2026-11-14] **MiMo voicedesign 每次请求现编音色**(固定描述也漂移男女切换)——要稳定音色:描述留空走内置音色
- [2026-11-14] **CI 失败的 run 也占 run_number**(重申:连续失败+撤销都会跳号,r266 可能与 r265 内容相同)——交付链接以 release 实际资产为准
- [2026-11-14] **共享解析点加无条件"最优先"覆盖=劫持所有调用方**——r264 修通话专用模型时把 voiceCallModelId 放进 handleMessageComplete 全局模型链首,普通聊天/唤醒/后台通知全被劫持(r266 用户报障);修法=按触发条件分支(assistantOverride!=null 才解析);全局链路改动先问"不触发的场景走哪条分支"
- [2026-11-08] **Offset 在 androidx.compose.ui.geometry,不在 ui.graphics**(TimelinePage 竖线轴根因);StrokeCap 才在 ui.graphics
- [2026-11-08] **runCatching 是 stdlib,import kotlinx.coroutines.runCatching 必编不过**(TimelineVM/VoiceCallVM 两处踩);runCatching{} lambda 无 it,receiver 写法或直接调
- [2026-11-08] **AppDatabase 加 abstract DAO 漏 import→KSP MissingType PROCESSING_ERROR**(报错不指名类型,查新加 DAO/Entity 的 import)
- [2026-11-08] **ChatPage 顶栏 actions 是独立作用域**,拿不到外层 vm/setting/assistant——新顶栏入口要么传参要么放组件外
- [2026-11-08] **Edit replace_all 缩进陷阱**:同一模式两处调用点缩进不同只命中一处(ChatMessage 双链 44/48 空格);自查必须 grep -c 核对处数
- [2026-11-08] **预览 HTML 必须首行 meta charset**(否则浏览器按 1252 解码中文乱码);::visualize 引用 JSON 路径用正斜杠(反斜杠未转义=宿主解析失败不内联);多屏预览的主题变量要遍历所有 .screen 元素逐一设置
- [2026-11-10] **internal 跨 Gradle 模块不可见**——speech 模块 internal fun 在 app 模块编译报"internal in file";跨模块复用一律 public
- [2026-11-10] **StrokeCap 在 androidx.compose.ui.graphics 不在 drawscope(第三次踩)**——import 时认准 graphics 包
- [2026-11-10] **Compose DrawScope.drawArc 没有 centerX/centerY/radius 形参**——只有 topLeft(Offset)+size(Size) 形式;drawCircle 才有 center+radius(CustomPath 自绘图标时踩)
- [2026-11-10] **buildJsonObject 里 put(String,Boolean/Int) 需 import kotlinx.serialization.json.put**(重载扩展,不导则报 Boolean≠JsonElement)
- [2026-11-10] **gh api REST 推送四坑**:大 base64 超 Windows 32KB 参数上限→--input 文件;`-p parents=` 传字符串→commit 孤儿(parents:[])→--input 传 {"parents":["sha"]};更新 ref 必须 -X PATCH(-f 默认 POST);同树兄弟 commit 会 422→force=true(树相同安全)
- [2026-11-10] **bash 管道吞退出码**——`git fetch | tail && echo OK` 永远 OK;成败判断用管道外退出码
- [2026-11-10] **heredoc+sed 拼多行 commit message 会炸**(unterminated s);单行 message 直接 tr -d '\r\n'
- [2026-11-10] **直连断连是间歇的**:push 第 8 次成功、fetch 12 次全败——推送失败先挂后台重试循环(45-60s 步进),别急转 REST
- [2026-11-10] **VoiceCallPage 通话卡「我在听」三根因**:VOICE_COMMUNICATION 静音无检测+空转写静默无限循环+DashScope 阈值 0.0/重连 errorMessage 自杀——静音守卫+空转写计数+omit-if-zero 三招治本;共享 TtsController 被抢占会卡「正在说」(AudioPlayer 对 STATE_IDLE 恢复等待者)
- [2026-11-11] **密封类加成员→所有穷尽 when 编译失败**(AppEvent 加 VoiceCallHangupRequest 兜出 RouteActivity)——加分支 `is AppEvent.X -> Unit`
- [2026-11-11] **CustomTtsState 包装层 speak 参数名是 flushCalled** 不是 flush(转发 TtsController.speak)——两套口径命名不一致,传参认准调用对象
- [2026-11-11] **UIMessage.createdAt 是 kotlinx.datetime.LocalDateTime**——进 java.time API(Duration.between/atZone)必须先 toJavaLocalDateTime(先例 ChatMessageActions.kt)
- [2026-11-11] **HTML 预览 hidden 属性被自定义 display:flex 盖掉**——"点收起无反应";须补 `[hidden]{display:none}`
