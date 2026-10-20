# RikkaHub 定制版 → 3.0.0 升级冲突分析

生成时间：2026-10-19　|　基线：官方 2.5.6　|　当前定制：custom-2.5.6 分支（领先 100 提交）

## 总览

| 类别 | 数量 | 说明 |
|---|---|---|
| 定制改动文件 | 64 | 你这版相对 2.5.6 改/加的所有文件 |
| 官方 3.0.0 改动文件 | 300 | 官方 2.5.6→3.0.0 的全部变化 |
| 🔴🟡🟢 冲突文件（双方都改） | 29 | 需要手工合并 |
| ✅ 直接平移（官方没动） | 35 | 复制即可，几乎零风险 |
| 🎁 免费获得（仅官方改） | 271 | 3.0.0 新功能自动到手 |

## 结论先行

1. **好消息**：你最关心的 UI 层（ChatMessage 气泡 240 行、ChatList、ChatPage、助手设置页 +397 行）
   官方 3.0.0 **一个都没动**——气泡样式/胶囊/分条/贴纸面板这些可以整文件平移。
2. **坏消息**：3.0.0 重构了生成架构——`GenerationLoop.kt` 被整个删除（-570 行），
   `ChatService.kt` 大改（-877/+696），`PreferencesStore.kt` 砍掉 624 行。
   唤醒系统的三大挂载点（触发/重试/回滚）和贴纸折叠转换器都要在**新架构上重新落地**。
3. **工作量估计**：机械平移半天 + ChatService/生成管线重挂载 1-2 天 + 全量回归测试半天。合计约 **2-3 个整天**。
4. **建议**：3.0.0 昨天刚发，大改版必有一波早期 bug。**先观望 2-4 周**，等官方出 3.0.1/3.0.2 补丁后再移植，
   一次搞定。现阶段 r99 稳定可用。

## 冲突文件清单（29 个，按风险分级）

| 风险 | 文件 | 定制改动 | 官方改动 | 冲突内容 |
|---|---|---|---|---|
| 🟡 中危 | `ClaudeProvider.kt` | +23/-12 | +1/-11 | AI 模块小改，官方也动了 |
| 🟡 中危 | `ResponseAPI.kt` | +38/-13 | +4/-1 | AI 模块小改，官方也动了 |
| 🟡 中危 | `Message.kt` | +17/-0 | +45/-2 | AI 消息模型加过字段，官方也扩了模型 |
| 🟢 低危 | `build.gradle.kts` | +23/-7 | +18/-11 |  |
| 🟢 低危 | `AndroidManifest.xml` | +4/-0 | +10/-2 |  |
| 🟡 中危 | `RikkaHubApp.kt` | +34/-11 | +53/-3 | 通知渠道/崩溃处理初始化，官方也改了这里 |
| 🟡 中危 | `RouteActivity.kt` | +1/-1 | +78/-13 | 官方大改路由，定制只有 1 行 |
| 🔴 高危 | `GenerationLoop.kt` | +61/-0 | +0/-570 | 官方已删除该文件（-570行），生成管线换了实现；strip/贴纸转换器挂载点需找新位置 |
| 🟡 中危 | `ChatToolFactory.kt` | +3/-1 | +83/-49 | 自定义工具（表情包/唤醒/搜索）注册点，官方重排了工具体系 |
| 🔴 高危 | `PreferencesStore.kt` | +17/-0 | +129/-624 | 设置存储大重构（-624行），气泡透明度等自定义设置字段需重新安家 |
| 🟢 低危 | `FilesManager.kt` | +6/-0 | +9/-4 |  |
| 🟢 低危 | `Assistant.kt` | +15/-0 | +10/-7 |  |
| 🟡 中危 | `AppModule.kt` | +11/-12 | +29/-7 | 依赖注入变化（StickerStore/WakeRegistry 等注册） |
| 🟡 中危 | `ViewModelModule.kt` | +0/-1 | +23/-1 | DI 变化（小） |
| 🔴 高危 | `ChatService.kt` | +547/-7 | +696/-877 | 唤醒触发/重试/回滚/拒绝备注全部在此，官方重构了该文件 |
| 🟡 中危 | `ChatInput.kt` | +73/-0 | +54/-53 | 贴纸面板/编辑状态挂载点，官方重排了输入框 |
| 🟢 低危 | `ChatMessage.kt` | +240/-22 | +0/-0 | AI 消息模型加过字段，官方也扩了模型 |
| 🟢 低危 | `ChatMessageActions.kt` | +2/-1 | +0/-0 |  |
| 🟢 低危 | `ChatInputState.kt` | +34/-0 | +0/-0 |  |
| 🟢 低危 | `AssistantBasicPage.kt` | +397/-0 | +0/-0 |  |
| 🟢 低危 | `AssistantLocalToolPage.kt` | +59/-0 | +0/-0 |  |
| 🟢 低危 | `ChatDrawer.kt` | +64/-7 | +0/-0 |  |
| 🟢 低危 | `ChatList.kt` | +19/-1 | +0/-0 |  |
| 🟢 低危 | `ChatPage.kt` | +45/-3 | +0/-0 |  |
| 🟢 低危 | `ChatVM.kt` | +0/-8 | +0/-0 |  |
| 🟢 低危 | `ConversationExport.kt` | +14/-0 | +0/-0 |  |
| 🟢 低危 | `ConversationList.kt` | +28/-8 | +0/-0 |  |
| 🟢 低危 | `SettingPreferencesGeneralPage.kt` | +33/-0 | +0/-0 |  |
| 🟢 低危 | `SettingPreferencesUIPage.kt` | +84/-0 | +0/-0 |  |

## 直接平移清单（35 个，官方 3.0.0 未触碰）

| 文件 | 改动 | 内容 |
|---|---|---|
| `custom-build.yml` | +97/-0 | CI 构建流程 |
| `ADAPTATION.md` | +146/-0 | 适配说明文档 |
| `GoogleProvider.kt` | +15/-7 |  |
| `InteractionsAPI.kt` | +7/-1 |  |
| `ChatCompletionsAPI.kt` | +41/-20 | HTML 错误页人话报错 |
| `AndroidManifest.xml` | +9/-0 |  |
| `ic_launcher_foreground.png` | +0/-0 |  |
| `ic_launcher_foreground.png` | +0/-0 |  |
| `ic_launcher_foreground.png` | +0/-0 |  |
| `ic_launcher_foreground.png` | +0/-0 |  |
| `ic_launcher_foreground.png` | +0/-0 |  |
| `strings.xml` | +4/-0 |  |
| `LocalToolOption.kt` | +8/-0 |  |
| `LocalTools.kt` | +34/-1 |  |
| `SearchMessageDetailTool.kt` | +97/-0 | 消息详情工具 |
| `SearchMessagesTool.kt` | +74/-0 | 消息搜索工具 |
| `SendStickerTool.kt` | +74/-0 | 表情包工具 |
| `WakeRecordsTool.kt` | +67/-0 | 唤醒记录查询工具 |
| `OcrTransformer.kt` | +8/-1 |  |
| `StickerToolFoldTransformer.kt` | +71/-0 | 贴纸折叠转换器 |
| `StickerStore.kt` | +216/-0 | 贴纸库 |
| `PendingRestore.kt` | +17/-1 |  |
| `AutonomousWakeWorker.kt` | +67/-0 | 唤醒执行 Worker |
| `BubbleSplitter.kt` | +173/-0 | 消息分条 |
| `WakeProtocol.kt` | +234/-0 | 唤醒协议（标记解析/触发提示/重试策略） |
| `WakeRegistry.kt` | +112/-0 | 唤醒登记表（五状态） |
| `CrashReportActivity.kt` | +56/-0 |  |
| `StickerPanel.kt` | +463/-0 | 贴纸面板 |
| `ChatMessageCot.kt` | +24/-0 |  |
| `WakeCapsule.kt` | +192/-0 | ⏰ 胶囊面板 |
| `CrashHandler.kt` | +20/-1 |  |
| `strings.xml` | +82/-1 |  |
| `strings.xml` | +86/-1 |  |
| `strings.xml` | +86/-1 |  |
| `gradle-daemon-jvm.properties` | +0/-13 |  |

## 移植策略（到时候照此执行）

1. **第一步（机械）**：35 个平移文件直接从定制分支复制到 3.0.0 基线，编译一轮清错误
2. **第二步（核心）**：在 3.0.0 新 ChatService/生成管线上重新挂载唤醒钩子——
   触发注入、ok=false 判定、失败回滚、重试排程、拒绝备注、空回复判定，逐个找新落点
3. **第三步（设置）**：气泡相关设置字段迁移到 3.0.0 新存储结构
4. **第四步（回归）**：全量测试——唤醒全状态机、分条、贴纸、编辑、重回、气泡样式
5. **注意**：官方 3.0.0 的「编辑消息可修改思考内容」「记忆库改版」与定制功能有交集，
   移植时需要决策保留哪套交互
