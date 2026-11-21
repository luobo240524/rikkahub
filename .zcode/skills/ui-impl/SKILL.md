---
name: ui-impl
description: Compose/Kotlin 代码写完或改完一批后、commit 与构建前的强制自查清单(本项目踩坑全集机械化)。写新文件、改 UI 组件、动 Room/DAO、碰消息管线、加新图标后必须逐项过;CI 的 compileDistKotlin 只是最后防线,不许拿来替代本清单。用户说「自查一下/过一遍清单」也触发。
---

# Compose 实现自检(写完即查,不是构建前才查)

每完成一个改动批次当场执行,在回复里给出逐项结论;有错当场修,修完复查。

## 清单

1. **import 完整性**——协程 `launch`/composable 相关最易漏。新建文件按「符号 → 导入清单」逐个核对:
   把代码里用到的每个外部符号列出来对照 import 区,**重读改动区盯不出来**(r275/r277 两连败全栽在这)。
2. **fillMaxWidth 子项会撑满 wrap-content 父 Column**,兄弟 intrinsic 内容被按 Start 挤偏
   (r281 语音条不靠右根因)。对齐丢失先查中间包裹层有无满宽子项。
3. **HugeIcons 双 import**:object + `me.rerere.hugeicons.stroke.X` 扩展属性都要;
   新图标名先用 find-hugeicons 技能查,代码库没用过的名字不能猜。
4. **M3 `Surface(onClick)` 内置 48dp 最小触摸尺寸**——密集按钮行会被挤爆(图标缩半/按钮变药丸);
   修法 = Surface 仅容器 + 固定 size(40).clip(CircleShape).clickable。
5. **edge-to-edge**:输入区必须 `.imePadding()`(adjustResize 不生效);Scaffold innerPadding 已含
   状态栏 inset,自绘头部再加 statusBarsPadding = 双重空隙。
6. **DrawScope/geometry API**:drawArc 只有 topLeft+size 形参(drawCircle 才有 center+radius);
   Offset 在 ui.geometry、StrokeCap 在 ui.graphics;ClipMode/drawOutline 已被 BOM 移除。
7. **Room**:手写迁移 SQL 不能带 DEFAULT 子句(除非实体 @ColumnInfo(defaultValue));
   新加 DAO/Entity 漏 import → KSP MissingType(报错不指名,查新加的东西)。
8. **Kotlin 语言坑**:`$var` 直接连中文会并成中文标识符,必须 `${var}`;
   KDoc 注释里禁 `*/` 序列;const 禁前向引用、顶层 const 跨包不能「文件名.属性」寻址;
   **局部变量先声明后使用**(r323 踩:横幅引用了下方才声明的 remember 变量)。
9. **Edit replace_all 缩进陷阱**:同一模式两处调用点缩进不同只命中一处——改完 `grep -c` 核对处数。
10. **文件编码**:改完用 `file <路径>` 验 UTF-8(YAML/XML 只用 Edit 工具改,禁 Set-Content)。
11. **sealed class 加成员**→ grep 全部穷尽 when(`is X ->` 与 `X::class`)零遗漏。

## 收尾

自查通过 → 结果复查(对照原始需求重读 diff、推演命中/未命中场景各路径新行为)→ 才轮到 commit(走 deliver 技能)。
