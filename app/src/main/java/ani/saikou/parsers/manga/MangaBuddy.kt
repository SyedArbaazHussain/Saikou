package ani.saikou.parsers.manga

import android.util.Log
import ani.saikou.FileUrl
import ani.saikou.Mapper.json
import ani.saikou.client
import ani.saikou.parsers.MangaChapter
import ani.saikou.parsers.MangaImage
import ani.saikou.parsers.MangaParser
import ani.saikou.parsers.ShowResponse
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


@OptIn(InternalSerializationApi::class)
class MangaBuddy : MangaParser() {

    override val name = "MangaBuddy"
    override val saveName = "manga_buddy"
    override val hostUrl = "https://mangak.io"

    private val apiUrl = "https://api.mangak.io"

    private val headers = mapOf(
        "Referer" to "$hostUrl/"
    )


    override suspend fun search(query: String): List<ShowResponse> {

        val encodedQuery = encode(query)
        val url = "$apiUrl/titles/search?q=$encodedQuery&limit=24"

        return try {
            val response = client.get(url)
            val body = response.body.string()
            val res = json.decodeFromString<SearchResponse>(body ?: "")
            res.data.items.map {
                ShowResponse(
                    name = it.name,
                    link = it.id,
                    coverUrl = FileUrl(
                        url = it.cover,
                        headers = headers

                    )
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

        val url = "$apiUrl/titles/$mangaLink/chapters?cv=${System.currentTimeMillis()}"
        return try {
            val response = client.get(url)
            val body = response.body.string()
            val res = json.decodeFromString<ChapterResponse>(body ?: "")
            res.data.chapters.forEachIndexed { index, ch ->
                Log.d(
                    "MangaBuddy",
                    "[$index] name=${ch.name}, number=${ch.chapterNumber}, url=${ch.url}"
                )
            }

            val sorted = res.data.chapters
                .sortedBy { it.chapterNumber ?: Float.MAX_VALUE }
            val result = sorted.map {
                MangaChapter(
                    number = it.chapterNumber?.toString() ?: it.name,
                    link = it.url,
                    title = it.name
                )
            }

            result

        } catch (e: Exception) {
            emptyList()
        }
    }


    override suspend fun loadImages(chapterLink: String): List<MangaImage> {

        return try {

            val url = if (chapterLink.startsWith("http")) {
                chapterLink
            } else {
                "$hostUrl$chapterLink"
            }

            val response = client.get(url)

            val body = response.body.string()

            if (body.isBlank()) return emptyList()

            val jsonStart = body.indexOf("<script id=\"__NEXT_DATA__\"")
            if (jsonStart == -1) {
                Log.e("MangaBuddy", "__NEXT_DATA__ not found")
                return emptyList()
            }
            val jsonStartTag = body.indexOf(">", jsonStart) + 1
            val jsonEndTag = body.indexOf("</script>", jsonStartTag)

            val jsonString = body.substring(jsonStartTag, jsonEndTag)

            val root = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(jsonString)

            val pageProps = root["props"]
                ?.jsonObject
                ?.get("pageProps")
                ?.jsonObject

            val initialChapter = pageProps
                ?.get("initialChapter")
                ?.jsonObject

            val images = initialChapter
                ?.get("images")
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }

            if (images.isNullOrEmpty()) {
                return emptyList()
            }

            return images.map { MangaImage(FileUrl(it, headers)) }

        } catch (e: Exception) {
            emptyList()
        }
    }

    @Serializable
    data class SearchResponse(
        val data: SearchData
    )

    @Serializable
    data class SearchData(
        val items: List<MangaItemDto> = emptyList(),

        )


    @Serializable
    data class MangaItemDto(
        val id: String,
        val name: String,
        val cover: String,
        val url: String
    )
    @Serializable
    data class ChapterResponse(
        val data: ChapterList
    )
    @Serializable
    data class ChapterList(
        val chapters: List<ChapterItem> = emptyList()
    )

    @Serializable
    data class ChapterItem(
        val url: String,
        val name: String,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("chapter_number") val chapterNumber: Float? = null
    )

}