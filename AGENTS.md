# Repository Guidelines

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
```

本地单测注意：项目目录或 Gradle 缓存路径含中文时，JDK 在 zh-CN Windows（`sun.jnu.encoding=GBK`）下会用 GBK 解码 Gradle 写入的 UTF-8 测试 worker classpath 文件，报「找不到 GradleWorkerMain」。改用 ASCII 盘符运行：

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradlew-ascii.ps1 :app:testDebugUnitTest
```

脚本用 `subst` 临时给项目目录与 `GRADLE_USER_HOME` 各挂一个 ASCII 盘符，退出自动清理；路径本来就是 ASCII 时等同于直接调用 `gradlew`。普通编译任务不受该问题影响。

远端构建与 APK 分发：本地无需 JDK。代码推送后运行 `pwsh -File tools/publish-remote-build.ps1`；脚本会重试推送、等待对应提交的 GitHub Actions 构建、从 Release 读取 ARM64 APK，并测速直链和镜像后报告最快可用下载链接及 SHA-256。每次远端构建完成后都应提供测速结果和最快 APK 链接。

### 双线出包与记忆同步（2026-11-27 起，方案 A）

- **侧分支出包拿号**：版本提交照旧推侧分支，用 `workflow_dispatch` 触发构建，该号即版本号
  （`custom-build.yml` 的 push 触发只监听 `custom-2.5.6`，侧分支 push 不构建）。
- **整合推送带 `[skip ci]`**：cherry-pick/merge 回 `custom-2.5.6` 时，**最顶上那笔 commit message 必须带
  `[skip ci]`**（GitHub 按被推送的 HEAD commit 判断）。否则同一份代码会再构建一次、多占一个号（r372 教训）。
  习惯写法：`chore(memory): rNNN 并入 [skip ci]`。工具：
  `powershell -NoProfile -ExecutionPolicy Bypass -File tools/merge-back.ps1 -Version rNNN`。
- **每出完一个版本就同步一次记忆**：构建出包后立刻把 `.zcode/memory/` 的更新（INDEX/facts/handoffs/log）
  提交并**单独推送**一次，另一台设备、另一个窗口 `pull` 就能拿到同样的结论与上下文。
  用 `powershell -NoProfile -ExecutionPolicy Bypass -File tools/push-memory.ps1`：脚本只允许 `**.md`、
  `.zcode/`、`docs/`、`tools/`、`.gitignore` 进提交，发现代码/资源文件混入就中止。
- **为什么必须拆开推**：`paths-ignore` 覆盖 `**.md`、`docs/**`、`tools/**`、`.zcode/**`、
  `.github/workflows/**`、`.gitignore`，只含这些文件的 push 不构建、不占号；一旦和代码混在同一次 push，
  整个 push 就会触发构建。
- `run_number` 在触发瞬间即 +1，**取消或失败都不退号**，所以触发前先确认远端 sha 与本地一致。


## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function and hosts static frontend build files (
  built from web-ui/ React project)
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Workflow Discipline(通用硬规则,适用于一切任务——修 bug、加功能、重构均约束;用户 2026-11-14 明令)

- **认真完整,禁止偷懒**:动手前查清全部影响面(grep 调用点;全局/共享链路——模型解析、消息管线、
  工具工厂等——上还有谁、各在什么条件下走);改动严格限定在需求范围内——不用波及无关路径的省事
  做法,不"顺手"夹带需求之外的东西,不只修表层不查根因;改完核对"不该变的路径确实没变"。
- **写完代码立即自查,不是构建前才查**:每完成一个改动批次当场核对——①import 完整性(协程/
  composable 最易漏)②跨文件重命名/签名变更 grep 全部调用点③YAML/XML 用 Edit 工具改并验 UTF-8
  ④Room 实体列与迁移注册一致⑤新 API 对照踩坑记录⑥括号/代码块平衡重读关键段;CI 只是最后防线。
- **结果复查,严格确认按要求完成**:任务收尾前对照原始需求复核——重读需求 → 重读 diff → 推演受影响
  路径(命中/未命中需求的场景各走一遍,列出行为变化)→ 回复里明确报告"要求是什么/改了什么/各路径
  新行为";编译过 ≠ 完成,与需求对不上就是没做完。
- **不清楚随时问,不擅自动手**:需求含糊、影响面存疑、多种做法拿不准时,停下来说明疑点询问用户,
  拍板后再动手;禁止按自己的猜测决定行为语义。

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.
