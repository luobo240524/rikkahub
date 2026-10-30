# 交接：生图卡死事故排查与修复（2026-10-30）

> 写给 zcode：这是今天 WorkBuddy 侧对「聊天内生图 → 全路由永久 ANR」事故的完整排查记录。
> 已推修复在 r176/r177，但**用户装 r177 后仍未恢复**，卡点见「当前卡点」。

## 一、事故现象

- 用户在聊天内用 gpt-image-2 模型生图，生成过程中应用突然卡死（ANR）
- 之后每次打开应用都卡死在界面（含生图/翻译快捷方式路由），点不了任何东西
- **重装应用（覆盖安装）无效** → 问题在持久化数据或启动路径
- **官方版做同样操作同样卡死** → 实锤是上游 RikkaHub 的 bug，与二改改动无关

## 二、根因链（代码证据，已核实）

1. **流式生图 O(n²) 累积**：`ai/src/main/java/me/rerere/ai/ui/StreamChunkHandler.kt`
   `ImageDelta` 分支把每块 base64 直接拼到 `Image.url`（`image.url + chunk.data`），
   每块都整串复制一份。gpt-image-2 一张图几 MB / 几百个分块 → 主线程被内存复制海啸淹没 → 生成中 ANR。

2. **裸 base64 绕过全部落库防护**：只发 `ImageDelta` 不发 `ImageStart` 的流（gpt-image-2 即是），
   产出的 Image part url 是**没有 `data:` 前缀的裸 base64**。而以下三处都只认 `data:` 前缀：
   - `ai/.../ui/Message.kt` → `hasBase64Part()`
   - `app/.../data/repository/ConversationRepository.kt` → `sanitizeConversationForPersist()`
   - `app/.../data/files/FilesManager.kt` → `convertBase64ImagePartToLocalFile()`
   → 数 MB 巨型 base64 被原样写进 `message_node.messages`（JSON 列）→ 重装不清数据 →
   每次打开该对话都卡死。

3. **新怀疑（r176 无效后提出）**：每次 DB 打开都会同步执行 `SELECT jieba_dict(?)`
   （`AppDatabaseFactory.onOpen` → `SimpleDictManager.extractDict`）加载分词词典。
   若生图时的内存风暴把进程杀死在词典解压/写库中途 → 词典文件或 WAL 损坏 →
   **每次打开数据库即挂死 → 任何路由都 ANR**。此假设可解释「官方版也中招」+「r176 清库无效」。

## 三、已推送的修复（r176 commit 2535deb2、r177 commit b0834885）

| 修复 | 文件 | 内容 |
|---|---|---|
| 流式改缓冲 | `StreamChunkHandler.kt` | ImageDelta 改 StringBuilder 攒块，ImageEnd 一次性写回；delta-only 流补 `data:image/png;base64,` 前缀 |
| 防护补漏 | `Message.kt` / `ConversationRepository.kt` / `FilesManager.kt` | url 长度 >100_000 一律视为内嵌 base64（含裸 base64） |
| 启动自修复 | `DatabaseRepair.kt`（新）+ `RikkaHubApp.kt` | 启动后台扫描 message_node 行 >200KB，超长 Image url（含 Tool.output 嵌套）替换为占位文本；解析失败重置节点；无残留置位 flag。**r177 起改为后台执行不阻塞主线程** |
| 安全模式入口 | `RouteActivity.kt` + `shortcuts.xml` + strings | 长按图标 → 「Safe Mode/安全模式」快捷方式 → RouteActivity 重定向 SafeModeActivity（置于 setContent 前，不碰 DB） |
| 应急工具 | `SafeModeActivity.kt` | ①一键修复（SimpleDictManager.resetExtractedDict + DatabaseRepair(force) + wal_checkpoint TRUNCATE）②取消全部后台任务（WorkManager.cancelAllWork）③导出诊断报告（退出记录/文件大小/DB统计/内存日志/crash-safe.log，MediaStore 下载目录 + app 外部目录双写，分两阶段落盘防 DB 挂死连报告都没有） |

## 四、当前卡点（用户实测）

- r176 覆盖安装后仍全路由卡死
- r177 覆盖安装后，长按图标点「安全模式」提示 **「未安装该应用」**
- 用户手机现状：官方版 3.1.1（已清数据备用）+ 二改版（卡死中），r177 为覆盖安装

### 疑点：本地构建与分支不一致

- 分支构建的应用名 = `RikkaHub`（`values*/strings.xml` 的 app_name，manifest 无 alias）
- 用户手机上的二改版显示名 = **`RikkaHub X`**
- → **zcode 本地（F:\zcode\rikkahub x）存在未推送的改动**（至少应用名，可能还有包名/其他）
- 风险：若本地还改了 applicationId，则 GitHub Actions 构建的包与用户手机装的**不是同一个应用**，
  WorkBuddy 侧推的所有修复都打不到用户的卡死应用上
- 「未安装该应用」也可能是两个包名错乱导致的 launcher 快捷方式解析失败

## 五、下一步（按优先级）

1. **zcode：把本地改动全部推上分支**（至少 app_name；如有 applicationId/签名差异必须说明），
   确认 CI 构建出的包就是用户手机上那个（覆盖安装不被拒绝）
2. **让用户再点一次「安全模式」**（覆盖安装后 launcher 快捷方式刷新有几秒延迟，等 10 秒再试；
   或重启手机后试）
3. 安全模式进去后：**先点 ③导出诊断报告**（报告里有退出记录/DB 统计/文件大小/崩溃前日志），
   按报告定位卡死点；再点 ①一键修复 → 进入应用
4. 若「一键修复」后恢复正常 → 验证 gpt-image-2 生图（r176 的流式缓冲应根治生成中卡死）
5. 若仍卡死 → 诊断报告是唯一可靠线索，别再猜

## 六、给未来排查者的备忘

- 官方版 3.1.1 与二改版（2.5.6 基础）备份格式不互通（二改备份数据无法导入 3.0.0+），迁移需另行处理
- `message_node.messages` 是 JSON 字符串列，超长行 = base64 污染的特征
- 诊断报告里重点看：`databases/rikka_hub.db-wal` 大小、`simple_dict/` 完整性、
  退出记录 REASON_ANR(6)/CRASH_NATIVE(5)、message_node maxRowLen
