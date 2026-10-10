# ADAPTATION.md — 二改版官方更新适配手册

> 本文档记录二改版相对官方 2.5.6 的全部改动与适配方法。
> 官方发布新版本时，按本手册操作即可完成适配。
> 最后更新：2026-10-10（对应 r24）

---

## 一、二改版构成

- 基础：官方 RikkaHub 2.5.6
- 分支：`custom-2.5.6`（luobo240524/rikkahub fork）
- 产物：RikkaHub X（applicationId `me.rerere.rikkahub.debug`，与官方共存）

---

## 二、零冲突自定义文件（官方更新时原样保留/复制）

| 文件 | 功能 |
|------|------|
| `app/.../service/BubbleSplitter.kt` | 消息分条（`<split/>` 标记法 + 输出净化器 stripToStringWrapper） |
| `app/.../service/WakeProtocol.kt` | 自主唤醒协议（提示词/标记/确认条渲染） |
| `app/.../service/AutonomousWakeWorker.kt` | 唤醒定时任务（WorkManager） |
| `app/.../data/stickers/StickerStore.kt` | 贴纸库（相册导入/直链下载/索引持久化） |
| `app/.../data/ai/tools/local/SendStickerTool.kt` | 助手发贴纸工具 |
| `app/.../ui/components/ai/StickerPanel.kt` | 贴纸面板（含 parseStickerPasteLines 批量解析） |
| `app/src/dist/**` | 名称 RikkaHub X（清单级覆盖）/ 图标 X 角标 |
| `.github/workflows/custom-build.yml` | CI 构建（固定签名 keystore 缓存 dist-keystore-v1） |

---

## 三、接线点清单（官方文件内的修改，更新时需重新对齐）

### 1. ChatService.kt 🔴 高危（核心消息管线）

- **imports**：WorkManager 系列（ExistingWorkPolicy/OneTimeWorkRequestBuilder/WorkManager/workDataOf）、`java.util.concurrent.TimeUnit`、`android.content.Context`
- **handleMessageComplete**：
  - 新参数 `hiddenInput: UIMessage? = null`
  - `messages =` 拼接 hiddenInput（唤醒触发）
  - **collect 块开头**：净化器（最后一条助手消息剥 `Text(text=...)` 包装）→ 分条 splitAssistantBubbles → 过滤 `isSynthetic`（顺序不可颠倒）
  - **onSuccess**：追加 `sessionManager.launchWithSession { handleWakeSchedule(conversationId) }`
- **新方法**：`handleWakeSchedule`（解析 `<wake after="30m" note="..."/>` → WorkManager 定时 → 每日上限计数）、`triggerAutonomousWake`（会话存在检查 → 每日上限 → 合成 USER 消息 → handleMessageComplete）

### 2. GenerationLoop.kt · generateInternal 🟡 中危

- system prompt buildString 末尾追加：`splitMessages → SPLIT_MESSAGES_PROMPT`；`autonomousWake → wakePrompt(自定义优先) ?: AUTONOMOUS_WAKE_PROMPT`

### 3. ai 模块 ChatCompletionsAPI.kt 🟡 中危

- **addNonAssistantMessage** 的 Image 分支：`part.stickerTextOrNull()` 非空 → 转语义文本 `[用户发送了表情包 id（意思是：desc）]`（贴纸不依赖识图）
- 新增私有扩展 `stickerTextOrNull()`（解析 metadata 的 sticker/stickerId/stickerDesc）

### 4. ChatMessage.kt 🟡 中危

- **imports**：haze 系列、widthIn、prettifyWakeTag、BubbleShape、BackgroundEffectType、contentOrNull
- **新参数**：`showDateTime` / `showActionsRow` / `showModelInfo` / `hazeState: HazeState?`
- **头部信息行门控**：`(USER || showModelInfo) && (assistant?.showHeaderInfo ?: true)`
- **操作按钮门控**：`lastMessage → !loading`；`!showActionsRow → false`
- **气泡 Surface**：bubbleShape（16dp/4dp微信/24dp胶囊）+ glassModifier（GLASS 模式 hazeGlass）+ 半透明色（玻璃 = opacity*0.3 下限 0.12）
- **气泡限宽**：`widthIn(max = 300.dp)`
- **Image 分支**：metadata sticker → 140dp 大图
- **Text 分支**：assistant 文本过 `prettifyWakeTag`（唤醒确认条）
- **StickerBlock** 渲染分支（140dp）

### 5. ChatList.kt 🟢 低危

- itemsIndexed 内计算 `isLastOfRun` / `isFirstOfRun` → 传 `showDateTime` / `showActionsRow` / `showModelInfo`

### 6. Assistant.kt 🟢 低危

- 追加字段：`splitMessages` / `splitPrompt` / `autonomousWake` / `wakePrompt` / `wakeDailyLimit(默认20)` / `showHeaderInfo(默认true)`

### 7. AssistantBasicPage.kt 🟢 低危

- 分条开关+提示词编辑框（留空用默认+恢复默认按钮）
- 自主唤醒开关+提示词编辑框+每日唤醒上限（数字框，0=不限）
- 「显示模型名称与用户头像」开关

### 8. 其他低危

- **AssistantLocalToolPage.kt**：SendSticker 开关
- **LocalToolOption.kt**：`SendSticker` data object
- **LocalTools.kt**：注入 StickerStore + sendStickerTool
- **AppModule.kt**：StickerStore singleton；LocalTools 构造加第 5 参
- **ChatInputState.kt**：`addSticker(part)`
- **ChatInput.kt**：贴纸参数（stickers/stickerUrl/onImportStickerUrls/onImportStickerUri/onRemoveSticker）+ 😜 按钮 + StickerPanel + onPick 进输入框（metadata 带 stickerId/stickerDesc）
- **ChatPage.kt**：stickerStore/stickers 状态 + 批量导入回调（toast 统计）
- **ConversationExport.kt**：StickerBlock 分支 + imports
- **ChatMessageCot.kt**：StickerBlock + isSuccessfulStickerSend + STICKER_SEND_TOOL_NAME + stickerUrlFromOutput
- **PreferencesStore.kt**：DisplaySetting.`bubbleShape` + `BubbleShape` 枚举
- **SettingPreferencesGeneralPage.kt**：气泡形状 Select
- **build.gradle.kts**：dist buildType（keystore.properties 签名）

### 9. strings ×3（低危）

新增键（锚点用 `assistant_page_wake_daily_limit_desc`，**别用 thinking_budget——它不在 strings.xml**）：
`assistant_page_split_prompt` / `assistant_page_autonomous_wake_prompt(+_hint)` / `assistant_page_custom_prompt_hint` / `assistant_page_prompt_reset` / `assistant_page_wake_daily_limit_(title|desc)` / `assistant_page_local_tools_send_sticker_(title|desc)` / `assistant_page_show_header_info(+_desc)` / `sticker_panel_*`（paste_hint/desc_hint/add/removed/import_gallery/desc_required/no_url_found/desc_dialog_title/confirm/cancel）

⚠️ 插入后必须 `ET.parse` 校验；含尖括号的值必须转义 `&lt; &gt;`

---

## 四、路线 A：merge 官方上游（小版本首选）

```bash
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream --tags
git merge upstream/v2.5.7   # 官方新 tag
```

冲突处理原则：
- 二节独立文件冲突 → 保留我方
- 三节接线点冲突 → 双方逻辑都保留，按本手册核对插入点仍然存在
- ChatService collect 管线结构变了 → 按"净化 → 分条 → 过滤 synthetic"顺序重新插入
- 官方若实现了同款功能（如分条）→ 删我方实现，切官方

## 五、路线 B：补丁重放（官方大重构时）

1. 下载官方新源码，替换本地源码目录
2. **保留**：`.github/`、`app/src/dist/`、二节全部独立文件
3. 按三节清单逐个重新接线
4. 推送触发构建

## 六、验证清单（每次适配后过一遍）

- [ ] 名称 RikkaHub X、图标带 X 角标（重启手机看）
- [ ] 分条：开关开 → 模型输出 `<split/>` → 多气泡
- [ ] 尾注/时间戳/操作按钮：仅组内末条
- [ ] 玻璃：背景效果玻璃 → 气泡磨砂 + 透明度滑块联动
- [ ] 贴纸：相册导入（描述必填弹窗）/批量直链/点击进输入框/发送/助手选图/非识图模型收到语义文本
- [ ] 唤醒：确认条显示/短醒测试/每日上限
- [ ] 备份：官方备份导入后头像背景正常（沙盒路径改写）

## 七、已知限制

- 表情包语义统一仅 OpenAI 兼容协议（Google/Claude 原生协议未改）
- WorkManager 唤醒 ±1~2 分钟飘移（系统省电机制）
- 工作区不随备份迁移（安卓沙盒限制，无解）
- 模型偶发输出序列化包装 → 净化器兜底，若再现开日志页 Request logging 取证

## 八、快速参考

- 推送：`gh_fix*.py`（GitHub API：blob → tree → commit → ref），改 FILES 数组复用
- CI：push 到 `custom-2.5.6` 自动构建，产物发布到 `custom-build` release
- 失败排查：Actions 日志搜 `e: file://`；发布页取最新按 **版本号数值** 比较（r8 字典序 > r18）
- 签名：keystore 缓存 `dist-keystore-v1`，丢失则需全网重装
