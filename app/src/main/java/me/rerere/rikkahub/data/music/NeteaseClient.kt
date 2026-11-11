package me.rerere.rikkahub.data.music

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云音乐社区接口客户端(非官方,一期"一起听"用)。
 *
 * 风险边界(已向用户声明):
 * - 社区接口随时可能失效,失效只能换源或停用
 * - VIP/版权歌免登录拿不到直链;扫码登录后用账号权益,大部分 VIP 歌可播
 * - cookie 存本地 DataStore;第三方调用存在理论风控风险(自用低频,实际很低)
 *
 * weapi 加密 = 两次 AES-128-CBC(preset key → 随机 key,同 iv)+ RSA 加密随机 key 的逆序,
 * 算法与密钥为社区通用公开常量(NeteaseCloudMusicApi 同款)。
 */
class NeteaseClient {
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10_000, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(15_000, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 登录 cookie(形如 "MUSIC_U=xxx; __csrf=yyy"),空 = 未登录(仍可搜/播免费歌) */
    @Volatile
    var cookie: String = ""

    // ---------- weapi 加密 ----------

    private val base62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val presetKey = "0CoJUm6Qyw8W8jud".toByteArray(Charsets.UTF_8)
    private val iv = "0102030405160708".toByteArray(Charsets.UTF_8)
    private val rsaPubBase64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZJsZgY5Mk29o5EXjN0/" +
            "h60R1mo3Xc769d4eO2F9t9tC7MCTZBCvZZmNhPZbe1FaD6BdYBReK9zLmIUbUJMx" +
            "iRldFwXZXK4X8b8zsqREhRDA3FdCZeB0+BZVE5OhBwl2z0wVB5BfJxCfeCVGwjVh" +
            "0QIDAQAB"

    private fun aesCbc(data: ByteArray, key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP)
    }

    private fun rsaHex(data: ByteArray): String {
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(Base64.decode(rsaPubBase64, Base64.NO_WRAP)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    private fun weapiPayload(text: String): Pair<String, String> {
        val random = SecureRandom()
        val secretKey = buildString {
            repeat(16) { append(base62[random.nextInt(base62.length)]) }
        }
        val enc1 = aesCbc(text.toByteArray(Charsets.UTF_8), presetKey)
        val params = aesCbc(enc1.toByteArray(Charsets.UTF_8), secretKey.toByteArray(Charsets.UTF_8))
        val encSecKey = rsaHex(secretKey.reversed().toByteArray(Charsets.UTF_8))
        return params to encSecKey
    }

    /** POST weapi 接口,返回响应体 JSON(失败抛异常) */
    private suspend fun postWeapi(path: String, data: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            val (params, encSecKey) = weapiPayload(data.toString())
            val bodyBuilder = okhttp3.FormBody.Builder()
                .add("params", params)
                .add("encSecKey", encSecKey)
            val requestBuilder = okhttp3.Request.Builder()
                .url("https://music.163.com$path")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://music.163.com")
                .post(bodyBuilder.build())
            if (cookie.isNotBlank()) requestBuilder.header("Cookie", cookie)
            httpClient.newCall(requestBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("NETEASE_HTTP_${resp.code}")
                json.parseToJsonElement(body).jsonObject
            }
        }

    // ---------- QR 登录 ----------

    data class QrLogin(val key: String, val qrUrl: String)

    /** 创建扫码登录 key;二维码内容为该 URL(zxing 生成图) */
    suspend fun createQrLogin(): QrLogin = withContext(Dispatchers.IO) {
        val resp = postWeapi("/weapi/login/qrcode/unikey", buildJsonObject { })
        val unikey = resp["unikey"]?.jsonPrimitive?.contentOrNull
            ?: error("NETEASE_QR_KEY_EMPTY")
        QrLogin(
            key = unikey,
            qrUrl = "https://music.163.com/login?codekey=$unikey",
        )
    }

    sealed class QrState {
        data object Waiting : QrState()           // 800 过期
        data object Scanned : QrState()           // 802 已扫码待确认
        data class Authorized(val cookies: String) : QrState()  // 803 成功
        data object Pending : QrState()           // 其他 = 等待扫码
    }

    /** 轮询二维码状态;成功时返回 Set-Cookie 串 */
    suspend fun checkQrLogin(key: String): QrState = withContext(Dispatchers.IO) {
        lateinit var resp: JsonObject
        var setCookies: List<String> = emptyList()
        val (params, encSecKey) = weapiPayload(buildJsonObject { }.toString())
        val request = okhttp3.Request.Builder()
            .url("https://music.163.com/weapi/login/qrcode/client/login")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://music.163.com")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
            .post(
                okhttp3.FormBody.Builder()
                    .add("params", params)
                    .add("encSecKey", encSecKey)
                    .add("key", key)
                    .add("type", "1")
                    .build()
            )
            .build()
        httpClient.newCall(request).execute().use { respRaw ->
            if (!respRaw.isSuccessful) error("NETEASE_HTTP_${respRaw.code}")
            setCookies = respRaw.headers("Set-Cookie")
            resp = json.parseToJsonElement(respRaw.body?.string().orEmpty()).jsonObject
        }
        when (resp["code"]?.jsonPrimitive?.intOrNull) {
            800 -> QrState.Waiting
            802 -> QrState.Scanned
            803 -> {
                val merged = setCookies.mapNotNull { c ->
                    val pair = c.split(";").firstOrNull()?.trim().orEmpty()
                    pair.takeIf { it.isNotBlank() && !it.endsWith("=") }
                }
                QrState.Authorized(merged.joinToString("; "))
            }
            else -> QrState.Pending
        }
    }

    /** 已登录昵称(取不到返回 null) */
    suspend fun getNickname(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = postWeapi("/weapi/nuser/account/get", buildJsonObject { })
            resp["profile"]?.jsonObject?.get("nickname")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    // ---------- 搜索 / 直链 / 歌词 ----------

    data class Song(
        val id: Long,
        val name: String,
        val artist: String,
        val coverUrl: String?,
        val durationMs: Long,
        val fee: Int, // 0 免费 1 VIP 4 购买专辑 8 低音免费
    )

    /** 搜歌,type=1 单曲,默认 30 条 */
    suspend fun search(keyword: String, limit: Int = 30): List<Song> {
        val resp = postWeapi("/weapi/cloudsearch/get/web", buildJsonObject {
            put("s", JsonPrimitive(keyword))
            put("type", JsonPrimitive("1"))
            put("limit", JsonPrimitive(limit.toString()))
            put("offset", JsonPrimitive("0"))
        })
        val songs = resp["result"]?.jsonObject?.get("songs")?.jsonArray ?: return emptyList()
        return songs.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                Song(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null,
                    name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    artist = o["ar"]?.jsonArray
                        ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                        ?.joinToString("/") { it }
                        .orEmpty(),
                    coverUrl = o["al"]?.jsonObject?.get("picUrl")?.jsonPrimitive?.contentOrNull,
                    durationMs = o["dt"]?.jsonPrimitive?.longOrNull ?: 0L,
                    fee = o["fee"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }.getOrNull()
        }
    }

    /** 拿播放直链;无权限(VIP/版权)返回 null */
    suspend fun getSongUrl(id: Long): String? {
        val resp = postWeapi("/weapi/song/enhance/player/url", buildJsonObject {
            put("ids", JsonPrimitive("[$id]"))
            put("br", JsonPrimitive("320000"))
        })
        return resp["data"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
    }

    /** 歌词(LRC 文本);拿不到返回 null */
    suspend fun getLyric(id: Long): String? {
        val resp = postWeapi("/weapi/song/lyric", buildJsonObject {
            put("id", JsonPrimitive(id.toString()))
            put("lv", JsonPrimitive("-1"))
            put("kv", JsonPrimitive("-1"))
            put("tv", JsonPrimitive("-1"))
        })
        return resp["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.contentOrNull
    }

    companion object {
        /** LRC 解析为 (时间ms, 文本) 列表,按时间排序 */
        fun parseLrc(lrc: String): List<Pair<Long, String>> {
            val result = mutableListOf<Pair<Long, String>>()
            lrc.lines().forEach { line ->
                val text = line.substringAfter(']', "").trim()
                if (text.isBlank()) return@forEach
                Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]").findAll(line).forEach { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
                    result.add((min * 60 + sec) * 1000 + frac * 10 to text)
                }
            }
            return result.sortedBy { it.first }
        }

        fun encodeUrlParam(v: String): String = URLEncoder.encode(v, "UTF-8")
    }
}
