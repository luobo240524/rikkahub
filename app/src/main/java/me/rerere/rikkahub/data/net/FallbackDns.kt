package me.rerere.rikkahub.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 系统 DNS 失败时兜底的 DoH(加密 DNS)解析器。
 *
 * 背景:双卡移动网络下运营商 DNS 间歇性返回 EAI_NODATA("No address associated with hostname"),
 * 聊天/唤醒请求成批失败,而域名本身健康(公网可解析)。DoH 走 IP 直连(证书含 IP SAN),
 * bootstrap 不依赖系统 DNS,不受运营商 DNS 抽风影响。
 *
 * 策略:系统解析优先(命中后正缓存 60s);失败时依次尝试 DoH 端点(结果正缓存 5 分钟);
 * DoH 全挂时进入 60 秒冷却,期间直接抛原异常,避免重试链每 20 秒都空跑 DoH。
 */
class FallbackDns : Dns {
    private val dohClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val cache = ConcurrentHashMap<String, CachedEntry>()
    private val dohFailUntil = java.util.concurrent.atomic.AtomicLong(0)

    private data class CachedEntry(val addresses: List<InetAddress>, val expiresAt: Long)

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        cache[hostname]?.let { if (now < it.expiresAt) return it.addresses }

        return try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) {
                cache[hostname] = CachedEntry(addresses, now + POSITIVE_TTL_SYSTEM)
                addresses
            } else {
                throw UnknownHostException("empty result for $hostname")
            }
        } catch (e: UnknownHostException) {
            // DoH 冷却期内直接抛,不再空跑
            if (now < dohFailUntil.get()) throw e
            val resolved = dohLookup(hostname)
            if (resolved != null) {
                cache[hostname] = CachedEntry(resolved, now + POSITIVE_TTL_DOH)
                resolved
            } else {
                dohFailUntil.set(now + DOH_FAIL_COOLDOWN)
                throw e
            }
        }
    }

    private fun dohLookup(hostname: String): List<InetAddress>? {
        for (endpoint in DOH_ENDPOINTS) {
            val ips = runCatching {
                val url = endpoint.toHttpUrl().newBuilder()
                    .addQueryParameter("name", hostname)
                    .addQueryParameter("type", "A")
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                dohClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    val json = Json.parseToJsonElement(body).jsonObject
                    // Status 0 = NOERROR 且有应答
                    if ((json["Status"]?.jsonPrimitive?.intOrNull ?: -1) != 0) return@runCatching null
                    val answers = json["Answer"]?.jsonArray ?: return@runCatching null
                    answers.mapNotNull { element ->
                        val data = (element as? kotlinx.serialization.json.JsonObject)
                            ?.get("data")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        // 只要 A 记录的 IPv4
                        if (IPV4.matches(data)) data else null
                    }                    .mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
                    .takeIf { it.isNotEmpty() }
                }
            }.getOrNull()
            if (ips != null) return ips
        }
        return null
    }

    companion object {
        // 阿里公共 DNS 的 DoH JSON 接口,IP 直连(证书含 IP SAN,无需 DNS 解析)
        private val DOH_ENDPOINTS = listOf(
            "https://223.5.5.5/resolve",
            "https://223.6.6.6/resolve",
        )
        private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private const val POSITIVE_TTL_SYSTEM = 60_000L
        private const val POSITIVE_TTL_DOH = 5 * 60_000L
        private const val DOH_FAIL_COOLDOWN = 60_000L
    }
}
