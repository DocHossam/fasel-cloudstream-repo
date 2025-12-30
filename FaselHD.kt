import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FaselHD : MainAPI() {
    override var mainUrl = "https://www.fasel-hd.com"
    override var name = "FaselHD"
    override var lang = "ar"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document

        return doc.select("article").mapNotNull { article ->
            val a = article.selectFirst("a") ?: return@mapNotNull null
            val title = article.selectFirst("h2")?.text() ?: return@mapNotNull null
            val link = a.attr("href")
            val poster = article.selectFirst("img")?.attr("src")

            MovieSearchResponse(
                name = title,
                url = link,
                apiName = this.name,
                type = TvType.Movie,
                posterUrl = poster
            )
        }
    }

    override fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: "Unknown"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[name=description]")?.attr("content")

        // استخراج iframe
        val iframeUrl = doc.selectFirst("iframe")?.attr("src")
            ?: throw ErrorLoadingException("No iframe found")

        // فتح صفحة embed
        val embedDoc = app.get(
            iframeUrl,
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to USER_AGENT
            )
        ).document

        // محاولة استخراج m3u8 أو mp4
        val videoUrls = mutableListOf<ExtractorLink>()

        // 1️⃣ video tag
        embedDoc.select("video source").forEach {
            val src = it.attr("src")
            if (src.isNotBlank()) {
                videoUrls.add(
                    ExtractorLink(
                        source = name,
                        name = "FaselHD",
                        url = src,
                        referer = iframeUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = src.contains(".m3u8")
                    )
                )
            }
        }

        // 2️⃣ JavaScript m3u8 (fallback)
        val script = embedDoc.select("script").joinToString("\n") { it.data() }
        Regex("(https?://[^\"']+\\.m3u8)").find(script)?.groupValues?.get(1)?.let { m3u8 ->
            videoUrls.add(
                ExtractorLink(
                    source = name,
                    name = "FaselHD",
                    url = m3u8,
                    referer = iframeUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
        }

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie
        ) {
            posterUrl = poster
            plot = description
            this.dataUrl = url
            addLinks(videoUrls)
        }
    }
}
