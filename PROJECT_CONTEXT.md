# PROJECT_CONTEXT — 冷启动上下文（给任何机器上的新会话）

> 本文档 = 二改版的"记忆外置"。在新电脑上打开 WorkBuddy/任何 AI 助手，
> 让它先读本文档 + `PROJECT_REPORT.md`，即可不依赖本地记忆继续开发。
> 最后更新：2026-10-20（r118）

---

## 这是什么仓库

**RikkaHub 定制版**：基于官方 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 2.5.6 的个人二改版，
分支 `custom-2.5.6`。所有修改通过 GitHub API 直接推送（本机不安装 Android SDK、不本地编译）。

- 分支：`custom-2.5.6`（主开发线）
- 构建：`.github/workflows/custom-build.yml`——**push 即自动构建**，产物发到 Releases（custom-build tag）
- 产物命名：`app-arm64-v8a-dist-rN.apk`（N 递增，与提交无固定对应，看 Releases 最新）
- 应用包名：dist 独立包名，与官方版**并存不覆盖**
- 版本历史/根因档案/挂账：**`PROJECT_REPORT.md`**（每次构建后必须更新，这是惯例）
- 3.0.0 移植评估：`RikkaHub_3.0.0_升级冲突分析.md`（决策：观望等 3.0.1）

---

## 核心定制系统（改哪里）

| 系统 | 文件 | 说明 |
|---|---|---|
| 唤醒协议/提示词/重试 | `service/WakeProtocol.kt` | 标记解析、触发提示、60次重试策略 |
| 唤醒登记表 | `service/WakeRegistry.kt` | 五状态记录，含僵尸清理(2h)与取消匹配RETRYING |
| 唤醒执行 | `service/AutonomousWakeWorker.kt` | WorkManager Worker |
| 唤醒调度/重试/回滚/绑定 | `service/ChatService.kt` | triggerAutonomousWake / handleWakeSchedule / updateConversationOverride |
| 唤醒胶囊面板 | `ui/pages/chat/WakeCapsule.kt` | ⏰ 面板 |
| 消息分条 | `service/BubbleSplitter.kt` | splitMessages |
| 贴纸库/面板/工具 | `data/stickers/StickerStore.kt`、`ui/components/ai/StickerPanel.kt`、`data/ai/tools/local/SendStickerTool.kt` | 表情包系统 |
| 气泡样式 | `ui/components/message/ChatMessage.kt` + `Assistant.kt` 的 bubble* 字段 + `AssistantBasicPage.kt` | 每助手气泡 |
| 对话设置固定 | `Conversation.kt`(override字段) + `ChatVM.kt`(setChatModel) + `ChatService.kt`(updateConversationOverride) | 模型/搜索绑定对话，**已落库(DB v26)** |
| 每助手字体/字号 | `Assistant.kt` + `ui/theme/ChatFont.kt` + `ChatMessage.kt` | 字体下拉带预览+自定义导入 |
| HTML错误人话化 | `ai/.../ChatCompletionsAPI.kt` | 错误页翻译 |

**数据库**：`data/db/AppDatabase.kt`（版本 26）、`data/db/migrations/`（迁移）、
`data/db/entity/ConversationEntity.kt`（逐列映射——**给 Conversation 加新字段必须同步：实体列+迁移+双向映射**，否则保存即丢）。
Assistant 的字段走 DataStore JSON 序列化，加字段只需默认值。

---

## 修改与构建流程（无本地环境）

1. **改代码**：让 AI 直接修改本地源码（或任意方式生成目标文件的完整新内容）
2. **推送**：运行 `tools/push_files.py`（把改动文件列表填进 FILES）——
   通过 GitHub API 创建 blob → tree → commit → 更新分支引用
3. **构建**：push 自动触发 Actions，无需手动
4. **查状态**：运行 `tools/check_build.py`（看最新 run 结果与 Releases 最新 APK）
5. **取包**：Releases → `https://gh-proxy.com/https://github.com/luobo240524/rikkahub/releases/download/custom-build/app-arm64-v8a-dist-rN.apk`（备用镜像 ghfast.top）
6. **更新报告**：构建成功后更新 `PROJECT_REPORT.md` 并推送（惯例，勿忘）

### Token（重要）

`tools/push_files.py` 从环境变量 `GITHUB_TOKEN` 读取令牌（**令牌不入库**——本仓库公开，
入库即被盗用）。新机器首次使用：

1. GitHub → Settings → Developer settings → Personal access tokens → 生成
   （勾选 `repo` 权限即可）
2. 设置环境变量：`setx GITHUB_TOKEN "ghp_xxx"`（Windows）后重开终端

---

## 修改纪律（血泪教训）

- **永不自作主张修改消息持久化行为**——曾因"编辑消息自动截断重生"误删几百条消息且不可恢复
- 给 Conversation 加字段：实体列+迁移+双向映射三件套缺一不可
- 给 Assistant 加字段：加默认值即可（JSON 序列化自动兼容）
- material3 是 1.5.0-alpha29：ExposedDropdownMenuBox/MenuAnchorType 等 API 不存在，下拉用基础 DropdownMenu
- 触发唤醒回复的失败会被重试链放大：任何"保存抛异常"的地雷都会变成无限重试风暴
- 诊断用 `android.util.Log` 的探针不进应用日志页；要进日志页用 `me.rerere.rikkahub.utils.Logging`

---

## 挂账事项（截至 r118）

1. 手动打断唤醒生成会留半截消息（罕见，取消路径不回滚）
2. 循环任务显示 08:59（设 09:00）偏差未实测
3. 模型绑定探针（ChatVM）日志通道待改为 Logging
4. 表情包单请求改造（贴纸固定在文字后）——**用户已搁置**
5. 3.0.0 移植——观望等 3.0.1
