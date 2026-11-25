package me.rerere.rikkahub.data.world

/**
 * 「你的世界」玩法常量(拍板口径 2026-11-22,数值集中可调)。
 * 与预览页 JS(pet-v4/garden-v2)的常量保持同值:页面为显示层,数值真源在此。
 */
object WorldConstants {
    // ---------- 宠物(cove 玩法) ----------
    const val MAX_PETS = 6 // 多宠上限(含蛋),住满显示「住满啦」
    // 破壳信号:内部单位×10(满 100 = 页面显示 10.0),不动 DB 字段类型、无迁移
    const val HATCH_NEED = 100 // 普通蛋满格信号(页面显示 10.0)
    const val HATCH_NEED_RARE = 120 // 稀有金蛋满格(页面显示 12.0)
    const val SIGNAL_FEED = 5 // 喂食/暖蛋 +0.5
    const val SIGNAL_CARE = 5 // 摇蛋/盖毯 +0.5
    const val SIGNAL_TA = 5 // TA 照顾 +0.5
    const val SIGNAL_CHAT = 1 // 聊天/关系信号 +0.1(小额贡献,不卡冷却)
    const val COOLDOWN_FEED_MS = 4L * 3600_000L // 喂食/暖蛋冷却 4h
    const val COOLDOWN_CARE_MS = 4L * 3600_000L // 摇蛋/盖毯冷却 4h
    const val COOLDOWN_TA_MS = 6L * 3600_000L // TA 照顾冷却 6h
    const val CROSS_DAYS = 2 // 破壳至少跨 2 个本地自然日(领养当天算第 1 天)
    // 孵蛋信号事件 action(冷却查询按此过滤;旧数据 hatch_signal 不影响)
    const val HATCH_ACT_FEED = "hatch_feed"
    const val HATCH_ACT_CARE = "hatch_care"
    const val HATCH_ACT_TA = "hatch_ta"
    const val HATCH_ACT_CHAT = "hatch_chat"
    const val RARE_CHANCE = 0.12 // 领养蛋时出稀有金蛋的概率
    val DIRECT_PET_POOL = listOf("cove-cat", "cove-dog") // 可直接领养的普通宠物
    // 神秘蛋孵化池:参考 cove-pet-nest 原作的 4 个幻想身份,不再只孵猫狗
    val FANTASY_HATCH_POOL = listOf("moon", "forest", "stardust", "tide")

    // 物种 key → 默认中文名/种类(用户领养后档案卡 ✎ 可改名)
    val PET_NAMES = linkedMapOf(
        "cove-cat" to "奶油",
        "cove-dog" to "泡芙",
        "egg" to "小蛋蛋",
        "moon" to "月团",
        "forest" to "茸耳",
        "stardust" to "星角",
        "tide" to "潮鳞",
    )
    val PET_KINDS = linkedMapOf(
        "cove-cat" to "云绒猫",
        "cove-dog" to "云绒犬",
        "egg" to "神秘灵宠",
        "moon" to "月光绒兽",
        "forest" to "森林长耳兽",
        "stardust" to "星尘独角兽",
        "tide" to "潮汐蛟龙",
    )

    // ---------- 花园 ----------
    val FLOWERS = linkedMapOf(
        "sun" to "小葵",
        "bell" to "风铃",
        "mint" to "薄荷",
        "tul" to "郁金香",
        "dai" to "雏菊",
        "lav" to "薰衣草",
        "ros" to "玫瑰",
        "suc" to "多肉",
    )
    val STAGES = listOf("种子", "新芽", "打苞", "盛放")
    val STAGE_THRESHOLDS = listOf(0, 6, 14, 26) // growth → stage 档位(>=阈值进位)
    const val GROWTH_PER_CARE = 1 // 每次园务(浇水/除虫/除草)涨的生长点
    const val THIRST_MAX = 100
    const val THIRST_PER_HOUR = 4 // decay-on-read:每小时口渴度增量
    const val THIRSTY_THRESHOLD = 55 // 页面「渴了」判定线
    const val PEST_AFTER_HOURS = 20L // 距上次照料超过此时长才可能生虫长草
    const val PEST_CHANCE = 0.30 // 生虫概率(每次读取判定,落在整点种子上防抖)

    // ---------- 碎碎念 ----------
    // 11 心情词表(七问拍板):key = DB 存储,词/emoji 给页面渲染
    val MOODS = linkedMapOf(
        "happy" to ("开心" to "☀"),
        "miss" to ("想念" to "🌙"),
        "calm" to ("平静" to "🍃"),
        "sad" to ("难过" to "🌧"),
        "expect" to ("期待" to "✨"),
        "shy" to ("害羞" to "🌷"),
        "aggrieved" to ("委屈" to "💧"),
        "lazy" to ("慵懒" to "🐈"),
        "content" to ("满足" to "🌿"),
        "excited" to ("兴奋" to "⚡"),
        "emo" to ("emo" to "🌌"),
    )

    // ---------- 留灯 ----------
    const val LIGHT_KEEP_DAYS = 30 // 熄灯保留天数,过期惰性清理(亮灯永留)

    // ---------- 粮仓/通用 ----------
    const val GRANARY_PER_ROUND = 1 // 每聊一轮粮仓 +1
    const val HUNGRY_AFTER_HOURS = 8L // 距上次喂食超过此时长=饿了(页面状态派生)
    const val DEDUPE_WINDOW_MS = 30_000L // 日记去重窗口:同 actor+action 连续点只记一条
    const val DIARY_LIMIT = 20 // 宠物日记/小园日记回显条数
}
