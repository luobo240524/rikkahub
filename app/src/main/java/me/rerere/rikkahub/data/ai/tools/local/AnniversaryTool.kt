package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.dao.AnniversaryDAO
import me.rerere.rikkahub.data.db.entity.AnniversaryEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 纪念日工具:读取/录入/更新/删除重要日期(生日/纪念日/相识满X天这类单次日期)。
 * 触达:当天会话注入提示词 + 时间轴当日展示(展示由时间轴聚合层处理)。
 */

/** 下次发生日期与倒计时天数(单次日期过期后返回 null) */
internal fun nextOccurrence(a: AnniversaryEntity, today: LocalDate = LocalDate.now()): Pair<LocalDate, Long>? {
    fun dateIn(year: Int): LocalDate? = runCatching {
        val last = YearMonth.of(year, a.month).lengthOfMonth()
        LocalDate.of(year, a.month, a.day.coerceAtMost(last))
    }.getOrNull()
    return if (a.repeatYearly) {
        val d = dateIn(today.year)?.takeIf { !it.isBefore(today) } ?: dateIn(today.year + 1)
        d?.let { it to ChronoUnit.DAYS.between(today, it) }
    } else {
        // 单次:目标年精确命中,过期即不再返回
        val y = a.targetYear ?: return null
        val d = dateIn(y)
        d?.takeIf { !it.isBefore(today) }?.let { it to ChronoUnit.DAYS.between(today, it) }
    }
}

/**
 * anniversary_read:列出全部纪念日(含下次日期与倒计时天数)。
 */
internal fun buildAnniversaryReadTool(anniversaryDAO: AnniversaryDAO): Tool = Tool(
    name = "anniversary_read",
    description = "List all remembered anniversaries (birthdays, anniversary dates, one-off milestones) " +
        "with the next occurrence date and days remaining. Use it before mentioning dates, planning " +
        "surprises, or checking what is coming up.",
    parameters = {
        InputSchema.Obj(properties = buildJsonObject {}, required = listOf())
    },
    systemPrompt = { _, _ ->
        "anniversary_read 列出你记着的重要日期(生日/纪念日/单次日期),每条带下次日期和倒计时天数。" +
            "回答「还有多久」「快到什么日子了吗」或打算准备惊喜前先读一下,别凭空猜。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { _ ->
        val list = withContext(Dispatchers.IO) { anniversaryDAO.flowAll().first() }
        val payload = buildJsonObject {
            put("total", list.size)
            put("anniversaries", buildJsonArray {
                for (a in list) {
                    add(buildJsonObject {
                        put("id", a.id)
                        put("name", a.name)
                        put("month", a.month)
                        put("day", a.day)
                        if (a.targetYear != null) put("targetYear", a.targetYear)
                        put("repeatYearly", a.repeatYearly)
                        if (a.note.isNotBlank()) put("note", a.note)
                        val next = nextOccurrence(a)
                        if (next != null) {
                            put("nextDate", next.first.toString())
                            put("daysUntil", next.second)
                        } else {
                            put("passed", true)
                        }
                    })
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

/**
 * anniversary_write:录入/更新/删除纪念日。
 * delete_id=删除;update_id=更新(需带新值);否则新增。单次日期(如相识第100天)传 year + repeat_yearly=false。
 */
internal fun buildAnniversaryWriteTool(anniversaryDAO: AnniversaryDAO): Tool = Tool(
    name = "anniversary_write",
    description = "Create, update or delete an anniversary (birthday, anniversary date, one-off milestone " +
        "like day-100). Pass delete_id to remove; update_id plus new values to modify; otherwise create. " +
        "month 1-12, day 1-31. For one-off dates pass year and repeat_yearly=false.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Anniversary name, e.g. 生日 / 相识纪念日 / 相识第100天")
                })
                put("month", buildJsonObject {
                    put("type", "integer")
                    put("description", "Month 1-12 (Gregorian)")
                })
                put("day", buildJsonObject {
                    put("type", "integer")
                    put("description", "Day 1-31")
                })
                put("year", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional target year for one-off dates (e.g. 2027)")
                })
                put("repeat_yearly", buildJsonObject {
                    put("type", "boolean")
                    put("description", "true = repeats every year (default); false = one-off (requires year)")
                })
                put("note", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional note, e.g. preparation plans")
                })
                put("update_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional: id of the anniversary to update (provide new values)")
                })
                put("delete_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional: id of the anniversary to delete")
                })
            },
            required = listOf()
        )
    },
    systemPrompt = { _, _ ->
        "你可以用 anniversary_write 记录重要日期:对话里出现生日、纪念日、相识满X天这类日期时主动录入或更新;" +
            "「相识第100天」这类单次日期传 year 和 repeat_yearly=false;TA 让你删掉某条时传 delete_id。" +
            "录入前先用 anniversary_read 看已有条目避免重复。这个工具就在你当前的工具清单里," +
            "不要因为历史对话里说过没有权限就不敢用。" +
            me.rerere.rikkahub.service.NAMING_GUIDE
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        fun str(k: String): String? = argsObj?.get(k)?.let { it as? JsonPrimitive }?.contentOrNull
        fun int(k: String): Int? = str(k)?.toIntOrNull()
        fun bool(k: String): Boolean? = argsObj?.get(k)?.let { it as? JsonPrimitive }?.booleanOrNull
        val payload = withContext(Dispatchers.IO) {
            runCatching {
                val deleteId = int("delete_id")?.toLong()
                val updateId = int("update_id")?.toLong()
                when {
                    deleteId != null -> {
                        val target = anniversaryDAO.getById(deleteId)
                        if (target == null) {
                            buildJsonObject { put("ok", false); put("error", "NOT_FOUND") }
                        } else {
                            anniversaryDAO.delete(target)
                            buildJsonObject { put("ok", true); put("deleted", deleteId) }
                        }
                    }

                    else -> {
                        val name = str("name").orEmpty().trim().take(50)
                        val month = int("month") ?: -1
                        val day = int("day") ?: -1
                        val year = int("year")
                        if (name.isBlank() || month !in 1..12 || day !in 1..31) {
                            buildJsonObject { put("ok", false); put("error", "INVALID_ARGS") }
                        } else {
                            val repeat = bool("repeat_yearly") ?: (year == null)
                            val note = str("note").orEmpty().take(200)
                            if (updateId != null) {
                                val target = anniversaryDAO.getById(updateId)
                                if (target == null) {
                                    buildJsonObject { put("ok", false); put("error", "NOT_FOUND") }
                                } else {
                                    anniversaryDAO.update(
                                        target.copy(
                                            name = name, month = month, day = day,
                                            targetYear = if (repeat) null else year,
                                            repeatYearly = repeat, note = note,
                                        )
                                    )
                                    buildJsonObject { put("ok", true); put("updated", updateId) }
                                }
                            } else {
                                val id = anniversaryDAO.insert(
                                    AnniversaryEntity(
                                        name = name,
                                        month = month,
                                        day = day,
                                        targetYear = if (repeat) null else year,
                                        repeatYearly = repeat,
                                        note = note,
                                        createdAt = System.currentTimeMillis(),
                                    )
                                )
                                buildJsonObject { put("ok", true); put("id", id) }
                            }
                        }
                    }
                }
            }.getOrElse {
                buildJsonObject { put("ok", false); put("error", "DB_ERROR") }
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
