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
            val limit = 32
            val offset = 0
            val searchUrl = "$hostUrl/search/data"

            val hxCurrentUrl =
                "$hostUrl/search?text=${encode(query)}" + "&sort=Best+Match&order=Descending&official=Any&anime=Any&adult=Any&display_mode=Full+Display"

            val headers = commonHeaders + mapOf(
                "HX-Request" to "true",
                "HX-Current-URL" to hxCurrentUrl
            )
            val html = client.get(
                searchUrl,
                headers = headers,
                params = mapOf(
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                    "text" to query,
                    "sort" to "Best Match",
                    "order" to "Descending",
                    "official" to "Any",
                    "anime" to "Any",
                    "adult" to "Any",
                    "display_mode" to "Full Display"
                )
            ).text

            val results = mutableListOf<ShowResponse>()
            val cardSeparator = "bg-base-300"
            val items = html.split(cardSeparator)

            for (i in 1 until items.size) {
                val block = items[i]

                val href = block.substringAfter("href=\"", "").substringBefore("\"")
                if (href.isBlank() || !href.contains("/series/")) {
                    continue
                }
                val seriesId = href.substringAfter("/series/")

                var title = block.substringAfter("class=\"text-ellipsis truncate\">", "")
                    .substringBefore("</div>").trim()
                if (title.isBlank() || title.contains("<") || title.length > 150) {
                    title = block.substringAfter("/series/$seriesId\">", "").substringBefore("</a>")
                        .trim()
                }
                if (title.contains(">")) {
                    title = title.substringAfterLast(">").trim()
                }

                if (title.isBlank() || title.contains("<")) {
                    title = seriesId.substringAfter("/").replace("-", " ").trim()
                }

                var coverUrl = block.substringAfter("src=\"", "").substringBefore("\"")
                if (coverUrl.isBlank() || coverUrl.contains("data:") || !coverUrl.startsWith("http")) {
                    val alternativeSrc = block.substringAfter("srcset=\"", "").substringBefore(" ")
                    if (alternativeSrc.isNotBlank() && alternativeSrc.startsWith("http")) {
                        coverUrl = alternativeSrc
                    }
                }
                results.add(
                    ShowResponse(
                        name = title,
                        link = seriesId,
                        coverUrl = coverUrl
                    )
                )
            }

            results
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

            val html = client.get(targetUrl, headers = headers).text
            val chaptersList = mutableListOf<MangaChapter>()

            val separator = "href=\"https://weebcentral.com/chapters/"
            val chunks = html.split(separator)
            val chapNumRe = Regex("""(?i)Chapter\s*(\d+(?:\.\d+)?)""")
            val fallbackNumRe = Regex("""\d+(?:\.\d+)?""")

            for (i in 1 until chunks.size) {
                val chunk = chunks[i]
                val chapterId = chunk.substringBefore("\"")
                val rowContent = chunk.substringBefore("</tr>")

                var cleanText = ""
                var inTag = false
                for (char in rowContent) {
                    if (char == '<') {
                        inTag = true; continue
                    }
                    if (char == '>') {
                        inTag = false; cleanText += " "; continue
                    }
                    if (!inTag) {
                        cleanText += char
                    }
                }

                val normalizedText = cleanText.replace(Regex("\\s+"), " ").trim()

                var chapterNumber = chapNumRe.find(normalizedText)?.groupValues?.get(1)
                if (chapterNumber == null) {
                    chapterNumber = fallbackNumRe.find(normalizedText)?.value ?: "$i"
                }

                chaptersList.add(
                    MangaChapter(
                        number = chapterNumber,
                        link = chapterId
                    )
                )
            }
            chaptersList.reversed()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        if (chapterLink.isBlank()) {
            return emptyList()
        }

        return try {
            val targetUrl = "$hostUrl/chapters/$chapterLink/images"
            val headers = commonHeaders + mapOf("Referer" to "$hostUrl/")

            val html = client.get(
                targetUrl,
                headers = headers,
                params = mapOf(
                    "is_prev" to "False",
                    "current_page" to "1",
                    "reading_style" to "long_strip"
                )
            ).text

            html.split("<img")
                .drop(1)
                .map { chunk -> chunk.substringBefore(">") }
                .map { tag ->
                    var imgUrl = tag.substringAfter("src=\"", "").substringBefore("\"")
                    if (imgUrl.isBlank() || imgUrl.contains("<") || imgUrl.contains("data:")) {
                        imgUrl = tag.substringAfter("data-src=\"", "").substringBefore("\"")
                    }
                    imgUrl.replace("&amp;", "&").trim()
                }
                .filter { url ->
                    url.isNotBlank() && (
                            url.contains(".jpg", ignoreCase = true) ||
                                    url.contains(".png", ignoreCase = true) ||
                                    url.contains(".webp", ignoreCase = true)
                            )
                }
                .map { validUrl ->
                    MangaImage(
                        url = FileUrl(
                            url = validUrl, headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
                                "Referer" to "${hostUrl}/"
                            )
                        )
                    )
                }

        } catch (e: Exception) {
            emptyList()
        }
    }
}