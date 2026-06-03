package ani.saikou.parsers.manga

import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import android.util.Log
import ani.saikou.FileUrl
import kotlinx.serialization.InternalSerializationApi
import kotlin.collections.mapOf

@OptIn(InternalSerializationApi::class)
class WeebCentral : MangaParser() {

    override val name = "WeebCentral"
    override val saveName = "weeb_central"
    override val hostUrl = "https://weebcentral.com"

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.5",
        "Connection" to "keep-alive"
    )

    override suspend fun search(query: String): List<ShowResponse> {
        if (query.isBlank()) return emptyList()

        return try {
            val response = client.post(
                url = "$hostUrl/search/simple?location=main",
                data = mapOf("text" to query),
                headers = mapOf(
                    "HX-Request" to "true",
                    "HX-Trigger" to "quick-search-input",
                    "HX-Trigger-Name" to "text",
                    "HX-Target" to "quick-search-result",
                    "HX-Current-URL" to "$hostUrl/"
                )
            )

            val doc = response.document

            doc.select("a[href*=/series/]").map { element ->
                val href = element.attr("href")

                val title = element
                    .selectFirst("div.flex-1")
                    ?.text()
                    ?.trim()
                    .orEmpty()

                val cover = element
                    .selectFirst("img")
                    ?.attr("src")
                    .orEmpty()

                ShowResponse(
                    name = title,
                    link = href.substringAfter("/series/"),
                    coverUrl = cover
                )
            }
        } catch (e: Exception) {

            emptyList()
        }
    }

    override suspend fun loadChapters(
        mangaLink: String,
        extra: Map<String, String>?
    ): List<MangaChapter> {
        if (mangaLink.isBlank()) return emptyList()

        return try {
            val mangaId = mangaLink.substringBefore("/")
            val targetUrl = "$hostUrl/series/$mangaId/full-chapter-list"

            val headers = commonHeaders + mapOf(
                "Referer" to "$hostUrl/series/$mangaLink",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )

            val response = client.get(targetUrl, headers = headers)

            val doc = response.document

            val links = doc.select("a[href*=/chapters/]")

            val chapNumRe = Regex("""(?i)Chapter\s*(\d+(?:\.\d+)?)""")
            val fallbackNumRe = Regex("""\d+(?:\.\d+)?""")

            val chapters = links.mapIndexedNotNull { index, el ->

                val href = el.attr("href")
                val chapterId = href.substringAfter("/chapters/")
                val text = el.text().replace(Regex("\\s+"), " ").trim()


                val chapterNumber =
                    chapNumRe.find(text)?.groupValues?.get(1)
                        ?: fallbackNumRe.find(text)?.value
                        ?: chapterId

                MangaChapter(
                    number = chapterNumber,
                    link = chapterId
                )
            }.reversed()


            chapters

        } catch (e: Exception) {

            emptyList()
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        if (chapterLink.isBlank()) return emptyList()

        return try {
            val targetUrl = "$hostUrl/chapters/$chapterLink/images"

            val headers = commonHeaders + mapOf(
                "Referer" to "$hostUrl/"
            )
            val response = client.get(
                targetUrl,
                headers = headers,
                params = mapOf(
                    "is_prev" to "False",
                    "current_page" to "1",
                    "reading_style" to "long_strip"
                )
            )

            val doc = response.document
            val imgElements = doc.select("img, source")
            val images = imgElements.mapIndexedNotNull { index, el ->

                val raw =
                    el.attr("src").ifBlank { el.attr("data-src") }
                        .ifBlank { el.attr("data-original") }
                        .ifBlank { el.attr("srcset").substringBefore(" ") }

                val url = raw.replace("&amp;", "&").trim()

                if (url.isBlank()) {
                    return@mapIndexedNotNull null
                }

                if (!url.startsWith("http")) {
                    return@mapIndexedNotNull null
                }

                if (!url.contains(Regex("""\.(jpg|png|webp|jpeg)""", RegexOption.IGNORE_CASE))) {
                    return@mapIndexedNotNull null
                }
                MangaImage(
                    url = FileUrl(
                        url = url,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
                            "Referer" to "$hostUrl/"
                        )
                    )
                )
            }

            images

        } catch (e: Exception) {

            emptyList()
        }
    }
}