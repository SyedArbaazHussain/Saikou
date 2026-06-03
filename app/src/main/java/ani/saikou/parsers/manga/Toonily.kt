package ani.saikou.parsers.manga

import android.annotation.SuppressLint
import android.util.Log
import ani.saikou.FileUrl
import ani.saikou.Mapper
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import okhttp3.MultipartBody
import kotlinx.serialization.Serializable
import okhttp3.FormBody

class Toonily : MangaParser() {

    override val name = "Toonily"
    override val saveName = "toonily"
    override val hostUrl = "https://toonily.com"

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",

        "Origin" to "https://toonily.com",
        "Referer" to "https://toonily.com/"
    )
    override suspend fun search(query: String): List<ShowResponse> {
        val requestBody = FormBody.Builder()
            .add("action", "wp-manga-search-manga")
            .add("title", query)
            .build()

        val url = "$hostUrl/wp-admin/admin-ajax.php"


        val response = client.post(url, requestBody = requestBody, headers = headers)

        val status = response.code
        val body = response.body?.string().orEmpty()

        if (status != 200 || body.isBlank()) return emptyList()

        return runCatching {
            val json = Mapper.parse<SearchResponse>(body)

            return json.data.map { item ->
                ShowResponse(
                    name = item.label,
                    link = item.url,
                    coverUrl = item.thumbnail ?: ""
                )
            }
        }.getOrElse {

            emptyList()
        }
    }

    override suspend fun loadChapters(mangaLink: String, extra: Map<String, String>?): List<MangaChapter> {
        val doc = client.get(mangaLink).document
        val chapters = doc.select("#tab-chapter-listing > div > div > ul > li > a").reversed()
        return chapters.map {
            val link = it.attr("href")
            val number = link.substringAfter("/chapter-").substringBefore("/")
            MangaChapter(
                number = number,
                link = link,
                title = it.text(),
            )
        }
    }

    override suspend fun loadImages(chapterLink: String): List<MangaImage> {
        val doc = client.get(chapterLink).document

        return doc.select("div.reading-content > div > img").map { element ->
            MangaImage(
                url = FileUrl(
                    url = element.attr("data-src").filter { !it.isWhitespace() },
                    headers = mapOf("referer" to "$hostUrl/")
                )
            )

        }
    }



    @SuppressLint("UnsafeOptInUsageError")
    @Serializable
    data class SearchResponse(
        val success: Boolean,
        val data: List<SearchItem>
    ) {
        @Serializable
        data class SearchItem(
            val label: String,
            val value: String,
            val url: String,
            val thumbnail: String? = null
        )
    }
}
