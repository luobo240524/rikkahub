package me.rerere.search

import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "DeepseekSearchService"

/**
 * DeepSeek 搜索:官方 Search & Fetch API(自研搜索引擎,2026-10 开放)。
 * - Search  POST {base}/v1/tools/search  → 结构化网页结果(query 1~128 字符,支持 site:/lang:/after: 等算符)
 * - Fetch   POST {base}/v1/tools/fetch   → 抓取单页返回 Markdown
 * 与 DeepSeek API 同一套 Bearer 鉴权,计费按成功请求(千次 $0.75,有空闲免费额度)。
 */
object DeepseekSearchService : SearchService<SearchServiceOptions.DeepseekOptions> {
    override val name: String = "DeepSeek"

    @Composable
    override fun Description() {
        val uriHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                uriHandler.openUri("https://platform.deepseek.com/")
            }
        ) {
            Text(stringResource(R.string.click_to_get_api_key))
        }
    }

    override fun parameters(options: SearchServiceOptions.DeepseekOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "The search query, 1-128 characters. Supports operators: site:, -site:, \"exact phrase\", lang:zh,en, after:2025-01-01, before:")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.DeepseekOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "The HTTP/HTTPS URL of the web page to fetch as Markdown")
                })
            },
            required = listOf("url")
        )

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.DeepseekOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (serviceOptions.apiKey.isBlank()) {
                error("DeepSeek API key is required")
            }
            // query 上限 128 字符,超了截断
            val query = (params["query"]?.jsonPrimitive?.content ?: error("query is required"))
                .take(128)

            val body = buildJsonObject {
                put("query", query)
                put("count", commonOptions.resultSize.coerceIn(1, 50))
            }

            Log.i(TAG, "search: $query")

            val request = Request.Builder()
                .url(serviceOptions.customUrl.trimEnd('/') + "/v1/tools/search")
                .post(body.toString().toRequestBody())
                .addHeader("Authorization", "Bearer ${serviceOptions.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).await()
            if (response.isSuccessful) {
                val responseBody = response.body.string()
                val root = json.parseToJsonElement(responseBody).jsonObject
                val results = root["results"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
                val items = results.mapNotNull { element ->
                    runCatching {
                        val obj = element.jsonObject
                        SearchResultItem(
                            title = obj["title"]?.jsonPrimitive?.content ?: return@runCatching null,
                            url = obj["url"]?.jsonPrimitive?.content ?: return@runCatching null,
                            // 优先摘要,片段兜底
                            text = (obj["summary"]?.jsonPrimitive?.content ?: "").ifBlank {
                                obj["snippet"]?.jsonPrimitive?.content ?: ""
                            },
                            publishedDate = obj["date"]?.jsonPrimitive?.content,
                        )
                    }.getOrNull()
                }
                return@withContext Result.success(SearchResult(items = items))
            } else {
                error("response failed #${response.code}: ${response.body?.string()}")
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.DeepseekOptions
    ): Result<ScrapedResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (serviceOptions.apiKey.isBlank()) {
                error("DeepSeek API key is required")
            }
            val url = params["url"]?.jsonPrimitive?.content ?: error("url is required")

            val body = buildJsonObject {
                put("url", url.take(8192))
            }

            val request = Request.Builder()
                .url(serviceOptions.customUrl.trimEnd('/') + "/v1/tools/fetch")
                .post(body.toString().toRequestBody())
                .addHeader("Authorization", "Bearer ${serviceOptions.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).await()
            if (response.isSuccessful) {
                val responseBody = response.body.string()
                val root = json.parseToJsonElement(responseBody).jsonObject
                val title = root["title"]?.jsonPrimitive?.content.orEmpty()
                val content = root["content"]?.jsonPrimitive?.content.orEmpty()
                ScrapedResult(
                    urls = listOf(
                        ScrapedResultUrl(
                            url = url,
                            content = content,
                            metadata = ScrapedResultMetadata(title = title.ifBlank { null }),
                        )
                    )
                )
            } else {
                error("response failed #${response.code}: ${response.body?.string()}")
            }
        }
    }
}
