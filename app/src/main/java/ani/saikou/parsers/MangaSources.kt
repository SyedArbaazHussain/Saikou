package ani.saikou.parsers

import ani.saikou.Lazier
import ani.saikou.lazyList
import ani.saikou.parsers.manga.*

object MangaSources : MangaReadSources() {
    override val list: List<Lazier<BaseParser>> = lazyList(

        "AllManga" to ::AllManga,
        "MangaBuddy" to ::MangaBuddy,
        "WeebCentral" to ::WeebCentral,
        "MangaHub" to ::MangaHub,
        "MangaRead" to ::MangaRead,
        "MangaPill" to ::MangaPill,
        "MangaDex" to ::MangaDex,
//        "Toonily" to ::Toonily,  //stubborn ass provider returning wrong results idk why
//        "MangaKatana" to ::MangaKatana,  // host url isnot resolving
    )
}

object HMangaSources : MangaReadSources() {
    val aList: List<Lazier<BaseParser>> = lazyList(

        "Manhwa18" to ::Manhwa18,

    )
    override val list = listOf(aList,MangaSources.list).flatten()
}
