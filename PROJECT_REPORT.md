# RikkaHub 定制版 · 项目报告（持续更新）

> 本文件由 AI 助手在每次构建后更新，记录版本演进、根因分析与已知问题。
> 基线：官方 2.5.6　|　分支：custom-2.5.6　|　构建：GitHub Actions（云端，本机不参与编译）
> 冷启动上下文（修改流程/文件地图/纪律）见 `PROJECT_CONTEXT.md`；3.0.0 移植评估见 `RikkaHub_3.0.0_升级冲突分析.md`；构建工具在 `tools/`

---

## 当前状态（2026-10-20）

**最新构建：r118**（commit ec1ac950）
- 修复：唤醒取消匹配扩展到 RETRYING 状态（此前对重试中记录免疫，16:27 日志实证）
- 修复：僵尸重试记录自动清理（RETRYING 超 2 小时无活动 → 自动标「已放弃」）
- 修复：insertConversation 也走 base64 清理（新建对话首次保存不再可能被阻断）

---

## 核心定制系统

### 1. 自主唤醒（Wake）
- 文件：`service/WakeProtocol.kt`（协议/提示词）、`service/WakeRegistry.kt`（登记表）、`service/AutonomousWakeWorker.kt`（执行）、`ui/pages/chat/WakeCapsule.kt`（胶囊面板）
- 状态机：待触发 → (重试中↔) → 已触发 / 已取消 / 已放弃
- 重试策略：20 秒×15 次一波 → 波间 15 分钟 → 共 4 波 60 次 → 耗尽标「已放弃」；循环任务耗尽后**自动续期到下一周期**（任务不死）
- 失败回滚（方案 A）：每轮尝试前记录节点边界，失败精确截回，聊天零残留
- 拒绝备注闭环：标记被拒绝时写隐藏备注（界面不显示），被接受后自动清理
- 僵尸清理：RETRYING 超 2 小时无活动自动标「已放弃」（防重试链中断后的尸体）
- 加速执行：波内即时启动 + fireAt 内部等待；波间常规延迟

### 2. 对话设置固定（模型/搜索绑定对话）
- 字段：`Conversation.chatModelIdOverride` / `webSearchOverride`
- **已落库**：ConversationEntity 两列 + DB v26 迁移（Migration_25_26）+ 双向映射
- 三处显示/执行源全部对话级：选择器（ChatInput modelListState）、生成（handleMessageComplete）、顶栏（TopBar）
- 持久化走 `ChatService.updateConversationOverride` → saveConversation（勿用 updateConversationState——只改内存不落库）

### 3. 每助手字体与字号
- 字段：`Assistant.chatFontFamily` / `chatCustomFontPath` / `fontSizeRatio`（Assistant 走 DataStore JSON 序列化，加字段无需迁移）
- 设置页 UI：字体下拉（带各字体预览+自定义导入 .ttf/.otf/.ttc）+ 字号滑杆 0.5x~2x 带重置
- 注意：material3 是 1.5.0-alpha29，ExposedDropdownMenuBox 系列 API 已移除，用基础 DropdownMenu 实现

### 4. 表情包（r120 终结式改造）
- send_sticker 工具改为**终结式**：执行后直接结束本轮回复（单次请求），文字须在工具调用前写完，贴纸固定在回复末尾
- 混合其他工具（搜索/日历）时保留续写，不影响那些流程
- 贴纸是回复消息的一部分（Tool part 渲染为贴纸大图）——重回/删除天然作用于整条

### 5. 消息分条（r117 架构修正）
- **数据只存一条消息**（<split/> 标记保留在文本中），删除/重回天然作用于整条
- 分条移到渲染层：ChatMessage 按 splitDisplaySegments() 拆成多条气泡（含强制分条算法）
- 重回保留旧版本：旧回复并入新回复节点的版本历史（可切换查看），失败自动恢复旧回复
- 流式路径不再拆节点（splitAssistantBubbles 退役，保留在 BubbleSplitter 供历史兼容）

---

## 重要根因档案（踩坑记录，勿重蹈）

| 根因 | 症状 | 修复 |
|---|---|---|
| ConversationEntity 逐列映射，新字段无列即丢 | 对话绑定重启丢失 | DB v26 + 迁移（r112） |
| markCancelled 只匹配 PENDING | RETRYING 记录无法取消（16:27 日志实证） | 匹配扩展（r118） |
| conversationToConversationEntity 的 require(无base64) | 对话混入内嵌图片→所有保存永久失败→唤醒无限重试风暴 | 落库前统一清理为占位文本（r116） |
| updateConversationState 只改内存不落库 | 覆盖写了但重启丢 | updateConversationOverride → saveConversation（r111） |
| TopBar 读全局/助手级模型 | 对话内换模型顶栏不刷新 | 顶栏改对话级覆盖（r115） |
| 拒绝备注永久留史 | 模型反复自我否定+乱排唤醒 | 被接受后自动清理（r97） |
| 编辑消息自动截断重生 | 误删后续几百条消息（不可恢复！） | 回滚官方行为（r99），**勿再自作主张改持久化行为** |
| material3 1.5.0-alpha29 移除 Exposed API | 下拉菜单编译失败 | 基础 DropdownMenu 实现（r105） |

---

## 已知未修（挂账）

1. 手动打断唤醒生成会留下半截消息（罕见，取消路径不回滚）
2. 循环任务显示 08:59（设 09:00），显示偏差未实测定位
3. 模型绑定探针日志（ChatVM 部分）走 android.util.Log，不进应用日志页
4. xingya.site 中转 timeout/stream reset 间歇出现（外部源，应用已兜底重试）

---

## 版本时间线（近期）

| 版本 | 内容 |
|---|---|
| r99 | 编辑回滚官方行为 + 唤醒空回复=失败 + 重回跳过合成节点 |
| r105 | 字体下拉 + 预览（基础 DropdownMenu 方案） |
| r110 | 对话设置固定（模型/搜索）+ 每助手字体 |
| r112 | 对话覆盖字段落库（DB v26） |
| r113 | 字体大小覆盖 |
| r115 | 顶栏模型名对话级 |
| r116 | base64 保存阻断修复 + 诊断探针 |
| **r118** | **取消匹配 RETRYING + 僵尸清理 + 插入路径清理** |
| r117 | 分条架构修正：渲染层分条 + 重回版本分支 + 失败恢复 |
| r120 | 表情包终结式：单次请求，贴纸固定在回复末尾，重回作用于整条 |

---

## 3.0.0 移植备忘

- 官方 3.0.0 已发布（10-18），UI 全面重做，GenerationLoop 删除、ChatService 重构
- 定制 64 文件：冲突 29（高危 3）/ 直接平移 35 / 免费获得 271，工作量 2-3 天
- **决策：观望，等 3.0.1 稳定后再移植**；届时按冲突报告四步策略执行
- 两应用并存：官方 3.0.0 与二改版（dist 包名）互不覆盖
