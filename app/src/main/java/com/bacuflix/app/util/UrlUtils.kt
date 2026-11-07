package com.bacuflix.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object UrlUtils {
    private const val WEB_BASE_URL = "https://ngcuhs3.github.io/bacuflix/"

    data class RouteInfo(
        val movieSlug: String? = null,
        val ep: String? = null
    )

    fun buildMovieUrl(slug: String): String {
        val clean = slug.trim()
        if (clean.isEmpty()) return WEB_BASE_URL
        val encoded = URLEncoder.encode(clean, "UTF-8")
        return "$WEB_BASE_URL?movie=$encoded"
    }

    fun buildEpisodeUrl(slug: String, ep: Int): String {
        val clean = slug.trim()
        if (clean.isEmpty()) return WEB_BASE_URL
        val encodedSlug = URLEncoder.encode(clean, "UTF-8")
        return "$WEB_BASE_URL?movie=$encodedSlug&ep=$ep"
    }

    fun parseDeepLink(uri: Uri?): RouteInfo {
        if (uri == null) return RouteInfo()
        val movieParam = uri.getQueryParameter("movie") ?: uri.getQueryParameter("slug")
        val epParam = uri.getQueryParameter("ep")
        if (!movieParam.isNullOrBlank()) {
            return RouteInfo(movieSlug = movieParam.trim(), ep = epParam?.trim())
        }
        val path = uri.path?.trim('/') ?: ""
        if (path.isNotBlank()) {
            return RouteInfo(movieSlug = path, ep = epParam?.trim())
        }
        return RouteInfo()
    }

    fun copyToClipboard(context: Context, label: String, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareMovieLink(context: Context, title: String, url: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title - Xem trên BACUFlix: $url")
            }
            val chooser = Intent.createChooser(sendIntent, "Chia sẻ phim").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            copyToClipboard(context, title, url)
        }
    }

    fun stripHtmlTags(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;", RegexOption.IGNORE_CASE), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
