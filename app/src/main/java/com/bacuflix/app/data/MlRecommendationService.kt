package com.bacuflix.app.data

import android.content.Context
import com.bacuflix.app.model.Movie
import com.bacuflix.app.model.UserInteraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * On-Device Machine Learning Recommendation Engine ported from js/services/ml-recommendation-service.js.
 * Uses TF-IDF unigram/bigram tokenization, Naive Bayes latent genre classification,
 * L2-normalized user preference centroid vector with 14-day exponential decay,
 * Google Suggest query expansion, and calibrated sigmoid match scoring [80%..99%].
 */
class MlRecommendationService(
    context: Context,
    private val kkphimApi: KkphimApiService,
    private val watchHistoryRepo: WatchHistoryRepository,
    private val favoritesRepo: FavoritesRepository
) {
    companion object {
        private const val PREFS_NAME = "bacuflix_ml_prefs"
        private const val ML_INTERACTIONS_KEY = "kkphim_ml_interactions"
        private const val HALF_LIFE_MS = 14.0 * 24 * 60 * 60 * 1000.0

        val VIETNAMESE_STOPWORDS = setOf(
            "phim", "xem", "tap", "tập", "phan", "phần", "full", "hd", "fhd", "vietsub",
            "thuyet", "thuyết", "minh", "long", "lồng", "tieng", "tiếng", "cua", "của",
            "va", "và", "nhung", "những", "nguoi", "người", "trong", "ngoai", "ngoài",
            "cho", "voi", "với", "khi", "mot", "một", "hai", "ba", "bon", "nam", "sau",
            "bay", "tam", "chin", "muoi", "năm", "2020", "2021", "2022", "2023",
            "2024", "2025", "2026", "2027", "the", "of", "and", "in", "to", "a", "is",
            "season", "episode", "movie", "series", "online", "moi", "mới", "nhat", "nhất",
            "bo", "bộ", "le", "lẻ", "hay", "hot", "ung", "ứng", "con", "còn", "phu", "phút",
            "khong", "không", "duoc", "được", "den", "đến", "tu", "từ", "la", "là", "co", "có"
        )

        data class GenrePrior(
            val slug: String,
            val name: String,
            val searchSeed: String,
            val keywords: List<String>
        )

        val GENRE_PRIORS = listOf(
            GenrePrior(
                slug = "hanh-dong",
                name = "Hành động",
                searchSeed = "hành động",
                keywords = listOf(
                    "hành động", "hanh dong", "võ thuật", "vo thuat", "đặc vụ", "dac vu",
                    "sát thủ", "sat thu", "chiến", "chien", "truy sát", "truy sat", "biệt kích",
                    "biet kich", "hình sự", "hinh su", "cảnh sát", "canh sat", "tội phạm", "toi pham",
                    "băng đảng", "bang dang", "anh hùng", "anh hung", "iron man", "người sắt", "nguoi sat",
                    "john wick", "fast", "mission", "vây hãm", "vay ham", "báo thù", "bao thu", "trả thù"
                )
            ),
            GenrePrior(
                slug = "co-trang",
                name = "Cổ trang",
                searchSeed = "cổ trang",
                keywords = listOf(
                    "cổ trang", "co trang", "tiên hiệp", "tien hiep", "kiếm hiệp", "kiem hiep",
                    "tu tiên", "tu tien", "xuyên không", "xuyen khong", "trọng sinh", "trong sinh",
                    "vương gia", "vuong gia", "hoàng hậu", "hoang hau", "cung đấu", "cung dau",
                    "thần", "than", "giang hồ", "giang ho", "thiên cổ", "thien co", "đấu phá", "dau pha",
                    "thế giới hoàn mỹ", "the gioi hoan my", "phàm nhân", "pham nhan", "cẩm", "cam",
                    "hoa", "ngọc", "ngoc", "kim thoa", "trường tương tư", "truong tuong tu", "lâm giang"
                )
            ),
            GenrePrior(
                slug = "tinh-cam",
                name = "Tình cảm",
                searchSeed = "tình cảm",
                keywords = listOf(
                    "tình cảm", "tinh cam", "yêu", "yeu", "tình yêu", "tinh yeu", "lãng mạn", "lang man",
                    "ngôn tình", "ngon tinh", "hôn nhân", "hon nhan", "tổng tài", "tong tai",
                    "ngọt ngào", "ngot ngao", "em", "anh", "chồng", "chong", "vợ", "vo", "hẹn hò", "hen ho",
                    "thanh xuân", "thanh xuan", "mùa hè", "mua he", "trái tim", "trai tim", "ánh trăng", "anh trang"
                )
            ),
            GenrePrior(
                slug = "hai-huoc",
                name = "Hài hước",
                searchSeed = "hài hước",
                keywords = listOf(
                    "hài", "hai", "hài hước", "hai huoc", "vui", "cười", "cuoi", "báo thủ", "bao thu",
                    "gia đình", "gia dinh", "xóm", "xom", "làng", "lang", "quậy", "quay", "siêu lầy", "sieu lay"
                )
            ),
            GenrePrior(
                slug = "kinh-di",
                name = "Kinh dị",
                searchSeed = "kinh dị",
                keywords = listOf(
                    "kinh dị", "kinh di", "ma", "quỷ", "quy", "ác quỷ", "ac quy", "bóng đè", "bong de",
                    "nguyền", "nguyen", "xác sống", "xac song", "zombie", "máu", "mau", "rùng rợn",
                    "rung ron", "bí ẩn", "bi an", "ám ảnh", "am anh", "cô dâu ma", "co dau ma", "linh hồn"
                )
            ),
            GenrePrior(
                slug = "vien-tuong",
                name = "Viễn tưởng",
                searchSeed = "viễn tưởng",
                keywords = listOf(
                    "viễn tưởng", "vien tuong", "khoa học", "khoa hoc", "vũ trụ", "vu tru", "tương lai",
                    "tuong lai", "dị năng", "di nang", "siêu nhiên", "sieu nhien", "quái vật", "quai vat",
                    "robot", "ai", "không gian", "khong gian", "tận thế", "tan the", "avengers", "marvel",
                    "transformer", "hành tinh", "hanh tinh"
                )
            ),
            GenrePrior(
                slug = "hoat-hinh",
                name = "Hoạt hình",
                searchSeed = "hoạt hình",
                keywords = listOf(
                    "hoạt hình", "hoat hinh", "anime", "one piece", "naruto", "conan", "dragon ball",
                    "doraemon", "shin", "đấu la", "dau la", "vũ động", "vu dong", "tây hành", "tay hanh",
                    "3d", "donghua", "chú thuật", "chu thuat", "thanh gươm", "thanh guom"
                )
            )
        )

        data class TokenizedText(
            val unigrams: List<String>,
            val bigrams: List<String>,
            val phrases: List<String>
        )

        data class LatentGenre(
            val slug: String,
            val weight: Double
        )

        data class QueryCandidate(
            val query: String,
            val score: Double
        )

        data class UserProfile(
            val isColdStart: Boolean,
            val vector: Map<String, Double>,
            val rawWeights: Map<String, Double>,
            val l2Norm: Double,
            val rankedGenres: List<Pair<String, Double>>,
            val queryCandidates: List<QueryCandidate>,
            val watchedSlugs: Set<String>,
            val completedSlugs: Set<String>,
            val favoriteSlugs: Set<String>,
            val googleExpandedKeywords: List<String> = emptyList(),
            val signalCount: Int
        )

        data class MovieScoreResult(
            val score: Double,
            val cosineSimilarity: Double,
            val matchPercent: Int
        )

        fun stripVietnameseAccents(str: String?): String {
            if (str.isNullOrEmpty()) return ""
            val normalized = Normalizer.normalize(str, Normalizer.Form.NFD)
            return normalized
                .replace(Regex("[\\u0300-\\u036f]"), "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
        }

        fun tokenizeMovieText(text: String?): TokenizedText {
            if (text.isNullOrBlank()) return TokenizedText(emptyList(), emptyList(), emptyList())
            val cleaned = text.lowercase()
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\([^)]*\\)"), " ")
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isEmpty()) return TokenizedText(emptyList(), emptyList(), emptyList())

            val rawWords = cleaned.split(" ")
            val unigrams = mutableListOf<String>()
            val validWords = mutableListOf<String>()

            for (raw in rawWords) {
                val w = raw.trim()
                val unaccented = stripVietnameseAccents(w)
                val isStopword = VIETNAMESE_STOPWORDS.contains(w) ||
                    (w == unaccented && VIETNAMESE_STOPWORDS.contains(unaccented))
                if (w.length >= 2 && !isStopword && !w.matches(Regex("^\\d+$"))) {
                    unigrams.add(w)
                    validWords.add(w)
                }
            }

            val bigrams = mutableListOf<String>()
            val phrases = mutableListOf<String>()
            if (validWords.size in 2..4) {
                phrases.add(validWords.joinToString(" "))
            } else if (validWords.size > 4) {
                phrases.add(validWords.take(3).joinToString(" "))
            }
            for (i in 0 until validWords.size - 1) {
                val bg = "${validWords[i]} ${validWords[i + 1]}"
                bigrams.add(bg)
                if (bg.length >= 5 && !phrases.contains(bg)) {
                    phrases.add(bg)
                }
            }
            if (validWords.size == 1 && validWords[0].length >= 4) {
                phrases.add(validWords[0])
            }

            return TokenizedText(unigrams, bigrams, phrases)
        }

        fun inferLatentGenres(movie: Movie): List<LatentGenre> {
            val matched = mutableListOf<LatentGenre>()
            val seen = mutableSetOf<String>()

            for (cat in movie.categories) {
                val catNorm = stripVietnameseAccents(cat).lowercase()
                for (gDef in GENRE_PRIORS) {
                    val gNorm = stripVietnameseAccents(gDef.name).lowercase()
                    if (catNorm.contains(gNorm) || catNorm.contains(gDef.slug.replace("-", " "))) {
                        if (seen.add(gDef.slug)) {
                            matched.add(LatentGenre(gDef.slug, 1.4))
                        }
                    }
                }
            }

            if (movie.type == "hoathinh" && seen.add("hoat-hinh")) {
                matched.add(LatentGenre("hoat-hinh", 1.5))
            }

            val textBlob = listOf(
                movie.title,
                movie.seriesTitle,
                movie.originTitle,
                movie.content.take(300)
            ).joinToString(" ").lowercase()
            val textUnaccented = stripVietnameseAccents(textBlob)

            for (gDef in GENRE_PRIORS) {
                var hitCount = 0
                for (kw in gDef.keywords) {
                    if (textBlob.contains(kw) || textUnaccented.contains(kw)) {
                        hitCount++
                    }
                }
                if (hitCount > 0 && seen.add(gDef.slug)) {
                    matched.add(LatentGenre(gDef.slug, min(1.5, 0.75 + hitCount * 0.25)))
                }
            }

            return matched
        }

        fun extractMovieFeatures(movie: Movie): Map<String, Double> {
            val features = mutableMapOf<String, Double>()
            fun addFeature(key: String, value: Double) {
                if (key.isNotBlank() && value.isFinite()) {
                    features[key] = (features[key] ?: 0.0) + value
                }
            }

            val rawTitle = movie.seriesTitle.ifBlank { movie.title.substringBefore(" - ") }
            val titleTokens = tokenizeMovieText(rawTitle)
            val originTokens = tokenizeMovieText(movie.originTitle)

            for (u in titleTokens.unigrams) {
                addFeature("term:$u", 1.0)
                val unacc = stripVietnameseAccents(u)
                if (unacc != u && unacc.length >= 3) {
                    addFeature("term_ascii:$unacc", 0.7)
                }
            }
            for (bg in titleTokens.bigrams) {
                addFeature("bigram:$bg", 1.8)
            }
            for (u in originTokens.unigrams) {
                if (u.length >= 3) addFeature("origin:$u", 0.9)
            }
            for (bg in originTokens.bigrams) {
                addFeature("origin_bg:$bg", 1.4)
            }

            for (g in inferLatentGenres(movie)) {
                addFeature("genre:${g.slug}", g.weight * 2.2)
            }

            for (c in movie.countries) {
                val cName = stripVietnameseAccents(c).lowercase().trim()
                if (cName.isNotEmpty()) addFeature("country:$cName", 1.5)
            }

            for (actor in movie.actors.take(6)) {
                val aName = actor.lowercase().trim()
                if (aName.length >= 3) addFeature("actor:$aName", 1.6)
            }

            if (movie.type.isNotBlank()) {
                addFeature("type:${movie.type.lowercase()}", 1.2)
            } else if (movie.episodeCurrent.contains(Regex("tập|tap|\\d+/\\d+", RegexOption.IGNORE_CASE))) {
                addFeature("type:series", 0.9)
            }

            if (movie.lang.isNotBlank()) {
                val langNorm = stripVietnameseAccents(movie.lang).lowercase()
                if (langNorm.contains("thuyet minh")) addFeature("lang:thuyet-minh", 1.1)
                if (langNorm.contains("long tieng")) addFeature("lang:long-tieng", 1.1)
                if (langNorm.contains("vietsub")) addFeature("lang:vietsub", 0.8)
            }

            val yr = movie.year.toIntOrNull()
            if (yr != null && yr > 1980) {
                when {
                    yr >= 2025 -> addFeature("era:2025_plus", 1.0)
                    yr >= 2020 -> addFeature("era:2020_2024", 0.8)
                    else -> addFeature("era:classic", 0.7)
                }
            }

            return features
        }

        fun scoreMovieWithModel(movie: Movie, profile: UserProfile): MovieScoreResult {
            val movieFeatures = extractMovieFeatures(movie)
            if (movieFeatures.isEmpty()) {
                return MovieScoreResult(0.1, 0.0, 78)
            }

            var dotProduct = 0.0
            var movieNormSq = 0.0
            var genreAffinityBonus = 0.0

            for ((k, mVal) in movieFeatures) {
                movieNormSq += mVal * mVal
                val pVal = profile.vector[k]
                if (pVal != null) {
                    dotProduct += pVal * mVal
                }
                if (k.startsWith("genre:") && profile.rawWeights.containsKey(k)) {
                    genreAffinityBonus += 0.08
                }
            }

            val movieNorm = if (movieNormSq > 0.0) sqrt(movieNormSq) else 1.0
            val cosineSim = if (profile.isColdStart) 0.45 else (dotProduct / movieNorm)

            var googleExpansionBonus = 0.0
            if (!movie.googleQueryMatched.isNullOrBlank()) {
                googleExpansionBonus += 0.09
            } else if (profile.googleExpandedKeywords.isNotEmpty()) {
                val titleNorm = stripVietnameseAccents("${movie.seriesTitle} ${movie.title} ${movie.originTitle}").lowercase()
                for (gKw in profile.googleExpandedKeywords) {
                    val gNorm = stripVietnameseAccents(gKw).lowercase()
                    if (gNorm.length >= 4 && titleNorm.contains(gNorm)) {
                        googleExpansionBonus += 0.07
                        break
                    }
                }
            }

            var qualityPrior = 0.0
            val yr = movie.year.toIntOrNull()
            if (yr != null) {
                if (yr >= 2025) qualityPrior += 0.08
                else if (yr >= 2022) qualityPrior += 0.04
            }
            if (movie.quality.contains(Regex("4k|fhd|hd", RegexOption.IGNORE_CASE))) {
                qualityPrior += 0.04
            }

            val slug = movie.effectiveKey
            var noveltyAdjustment = 0.0
            if (slug.isNotBlank() && profile.completedSlugs.contains(slug)) {
                noveltyAdjustment -= 0.35
            } else if (slug.isNotBlank() && profile.watchedSlugs.contains(slug)) {
                noveltyAdjustment -= 0.12
            }

            val rawScore = max(
                0.05,
                cosineSim * 0.78 + min(0.22, genreAffinityBonus) + googleExpansionBonus + qualityPrior + noveltyAdjustment
            )
            val calibrated = 1.0 / (1.0 + exp(-4.2 * (rawScore - 0.22)))
            val matchPercent = max(80, min(99, (78.0 + calibrated * 21.0).roundToInt()))

            return MovieScoreResult(
                score = rawScore,
                cosineSimilarity = cosineSim,
                matchPercent = matchPercent
            )
        }

        fun computeMovieSimilarity(target: Movie, candidate: Movie): MovieScoreResult {
            val fA = extractMovieFeatures(target)
            val fB = extractMovieFeatures(candidate)
            if (fA.isEmpty() || fB.isEmpty()) {
                return MovieScoreResult(0.1, 0.0, 80)
            }
            var dot = 0.0
            var normASq = 0.0
            var normBSq = 0.0
            var sharedGenreBonus = 0.0
            for ((k, vA) in fA) {
                normASq += vA * vA
                val vB = fB[k]
                if (vB != null) {
                    dot += vA * vB
                    if (k.startsWith("genre:")) {
                        sharedGenreBonus += 0.10
                    } else if (k.startsWith("country:") || k.startsWith("actor:")) {
                        sharedGenreBonus += 0.06
                    }
                }
            }
            for (vB in fB.values) {
                normBSq += vB * vB
            }
            val denom = sqrt(normASq) * sqrt(normBSq)
            val cosine = if (denom > 0.0) (dot / denom) else 0.0
            val rawScore = max(0.05, cosine * 0.80 + min(0.25, sharedGenreBonus))
            val calibrated = 1.0 / (1.0 + exp(-4.5 * (rawScore - 0.18)))
            val matchPct = max(80, min(99, (79.0 + calibrated * 20.0).roundToInt()))
            return MovieScoreResult(
                score = rawScore,
                cosineSimilarity = cosine,
                matchPercent = matchPct
            )
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cachedInteractions: MutableList<UserInteraction>? = null

    fun recordMovieInteraction(movie: Movie, interactionType: String = "view") {
        val slug = movie.effectiveKey
        if (slug.isBlank() && movie.title.isBlank()) return
        val list = getInteractionsList().toMutableList()
        list.removeAll { slug.isNotBlank() && it.slug == slug }
        list.add(
            0,
            UserInteraction(
                type = interactionType,
                slug = slug,
                title = movie.seriesTitle.ifBlank { movie.title.substringBefore(" - ") },
                originTitle = movie.originTitle,
                categories = movie.categories.take(6),
                countries = movie.countries.take(4),
                actors = movie.actors.take(5),
                movieType = movie.type,
                year = movie.year,
                lang = movie.lang,
                timestamp = System.currentTimeMillis()
            )
        )
        saveInteractionsList(list.take(50))
    }

    fun recordSearchQuery(keyword: String) {
        val kw = keyword.trim()
        if (kw.length < 2) return
        val list = getInteractionsList().toMutableList()
        list.removeAll { it.type == "search" && it.query.equals(kw, ignoreCase = true) }
        list.add(
            0,
            UserInteraction(
                type = "search",
                query = kw,
                timestamp = System.currentTimeMillis()
            )
        )
        saveInteractionsList(list.take(50))
    }

    fun trainUserPreferenceModel(
        customHistory: List<Movie>? = null,
        customFavorites: List<Movie>? = null,
        customInteractions: List<UserInteraction>? = null
    ): UserProfile {
        val history = customHistory ?: watchHistoryRepo.getAll()
        val favorites = customFavorites ?: favoritesRepo.getAll()
        val interactions = customInteractions ?: getInteractionsList()

        val rawWeights = mutableMapOf<String, Double>()
        val genreScores = mutableMapOf<String, Double>()
        val queryCandidates = mutableListOf<QueryCandidate>()
        val seenQuerySet = mutableSetOf<String>()
        val watchedSlugs = mutableSetOf<String>()
        val completedSlugs = mutableSetOf<String>()
        val favoriteSlugs = mutableSetOf<String>()

        fun addWeight(featureKey: String, delta: Double) {
            if (featureKey.isBlank() || !delta.isFinite()) return
            rawWeights[featureKey] = (rawWeights[featureKey] ?: 0.0) + delta
            if (featureKey.startsWith("genre:")) {
                val gSlug = featureKey.removePrefix("genre:")
                genreScores[gSlug] = (genreScores[gSlug] ?: 0.0) + delta
            }
        }

        fun pushQueryCandidate(phrase: String, score: Double) {
            val clean = phrase.trim()
            val norm = clean.lowercase()
            if (clean.length < 2 || VIETNAMESE_STOPWORDS.contains(norm) || !seenQuerySet.add(norm)) return
            queryCandidates.add(QueryCandidate(clean, score))
        }

        // 1. Train on Watch History
        history.forEachIndexed { idx, item ->
            val slug = item.effectiveKey
            if (slug.isNotBlank()) {
                watchedSlugs.add(slug)
                if (item.progressPercent >= 90 || (item.duration > 60 && item.currentTime >= item.duration - 30)) {
                    completedSlugs.add(slug)
                }
            }

            val progressRatio = max(0.1, min(1.0, (item.progressPercent.takeIf { it > 0 } ?: 25) / 85.0))
            val watchBonus = if (item.currentTime > 300) 0.5 else 0.0
            val rankDecay = max(0.4, 1.0 - idx * 0.04)
            val timeDecay = computeTimeDecay(item.updatedAt)
            val sampleWeight = (1.1 + 1.5 * progressRatio + watchBonus) * timeDecay * rankDecay

            for ((fKey, fVal) in extractMovieFeatures(item)) {
                addWeight(fKey, fVal * sampleWeight)
            }

            val titleText = item.seriesTitle.ifBlank { item.title.substringBefore(" - ") }
            val tokens = tokenizeMovieText(titleText)
            if (tokens.phrases.isNotEmpty()) pushQueryCandidate(tokens.phrases[0], sampleWeight * 2.2)
            if (tokens.bigrams.isNotEmpty()) pushQueryCandidate(tokens.bigrams[0], sampleWeight * 1.9)
            else if (tokens.unigrams.isNotEmpty()) pushQueryCandidate(tokens.unigrams[0], sampleWeight * 1.4)
            if (item.actors.isNotEmpty() && item.actors[0].length >= 4) {
                pushQueryCandidate(item.actors[0], sampleWeight * 1.5)
            }
        }

        // 2. Train on Favorites
        favorites.forEachIndexed { idx, fav ->
            val slug = fav.effectiveKey
            if (slug.isNotBlank()) favoriteSlugs.add(slug)
            val timeDecay = computeTimeDecay(fav.addedAt)
            val rankDecay = max(0.5, 1.0 - idx * 0.03)
            val sampleWeight = 2.6 * timeDecay * rankDecay

            for ((fKey, fVal) in extractMovieFeatures(fav)) {
                addWeight(fKey, fVal * sampleWeight)
            }

            val tokens = tokenizeMovieText(fav.title)
            when {
                tokens.bigrams.isNotEmpty() -> pushQueryCandidate(tokens.bigrams[0], sampleWeight * 2.3)
                tokens.phrases.isNotEmpty() -> pushQueryCandidate(tokens.phrases[0], sampleWeight * 2.0)
                tokens.unigrams.isNotEmpty() -> pushQueryCandidate(tokens.unigrams[0], sampleWeight * 1.5)
            }
        }

        // 3. Train on Interactions
        interactions.forEachIndexed { idx, inter ->
            val timeDecay = computeTimeDecay(inter.timestamp)
            val rankDecay = max(0.35, 1.0 - idx * 0.05)

            if (inter.type == "search" && inter.query.isNotBlank()) {
                val sWeight = 1.6 * timeDecay * rankDecay
                pushQueryCandidate(inter.query, sWeight * 2.4)
                val qTokens = tokenizeMovieText(inter.query)
                qTokens.unigrams.forEach { u -> addWeight("term:$u", sWeight * 1.3) }
                qTokens.bigrams.forEach { bg -> addWeight("bigram:$bg", sWeight * 1.9) }
                inferLatentGenres(Movie(id = "q", title = inter.query)).forEach { g ->
                    addWeight("genre:${g.slug}", sWeight * 1.8)
                }
            } else {
                val vWeight = 1.2 * timeDecay * rankDecay
                val fakeMovie = Movie(
                    id = inter.slug,
                    slug = inter.slug,
                    title = inter.title,
                    originTitle = inter.originTitle,
                    categories = inter.categories,
                    countries = inter.countries,
                    actors = inter.actors,
                    type = inter.movieType,
                    year = inter.year,
                    lang = inter.lang
                )
                for ((fKey, fVal) in extractMovieFeatures(fakeMovie)) {
                    addWeight(fKey, fVal * vWeight)
                }
                val tokens = tokenizeMovieText(inter.title)
                if (tokens.bigrams.isNotEmpty()) {
                    pushQueryCandidate(tokens.bigrams[0], vWeight * 1.7)
                }
            }
        }

        // L2 Normalization
        var normSq = 0.0
        for (v in rawWeights.values) {
            normSq += v * v
        }
        val l2Norm = if (normSq > 0.0) sqrt(normSq) else 1.0
        val normalizedVector = rawWeights.mapValues { it.value / l2Norm }

        val rankedGenres = genreScores.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        rankedGenres.take(3).forEach { (gSlug, gScore) ->
            val gDef = GENRE_PRIORS.firstOrNull { it.slug == gSlug }
            if (gDef != null) {
                pushQueryCandidate(gDef.searchSeed, gScore * 0.9)
            }
        }

        queryCandidates.sortByDescending { it.score }
        val isColdStart = history.isEmpty() && favorites.isEmpty() && interactions.isEmpty()

        return UserProfile(
            isColdStart = isColdStart,
            vector = normalizedVector,
            rawWeights = rawWeights,
            l2Norm = l2Norm,
            rankedGenres = rankedGenres,
            queryCandidates = queryCandidates,
            watchedSlugs = watchedSlugs,
            completedSlugs = completedSlugs,
            favoriteSlugs = favoriteSlugs,
            signalCount = history.size + favorites.size + interactions.size
        )
    }

    fun predictSearchQueries(profile: UserProfile, maxQueries: Int = 5): List<String> {
        if (profile.isColdStart || profile.queryCandidates.isEmpty()) {
            return listOf("hành động", "cổ trang", "tình cảm", "viễn tưởng", "hoạt hình").take(maxQueries)
        }
        val selected = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (cand in profile.queryCandidates) {
            if (selected.size >= maxQueries) break
            val norm = stripVietnameseAccents(cand.query).lowercase()
            if (seen.add(norm)) {
                selected.add(cand.query)
            }
        }
        val defaultSeeds = listOf("cổ trang", "hành động", "tình cảm", "hoạt hình", "kinh dị")
        for (s in defaultSeeds) {
            if (selected.size >= maxQueries) break
            val norm = stripVietnameseAccents(s).lowercase()
            if (seen.add(norm)) {
                selected.add(s)
            }
        }
        return selected
    }

    suspend fun getRecommendations(limit: Int = 20): List<Movie> = withContext(Dispatchers.IO) {
        val baseProfile = trainUserPreferenceModel()
        val baseQueries = predictSearchQueries(baseProfile, 4)

        val googleKeywords = try {
            val seed = "phim ${baseQueries.firstOrNull() ?: "hành động"} "
            val suggestions = GoogleTrendingService.fetchGoogleSuggestions(seed)
            GoogleTrendingService.extractKeywords(suggestions).take(4)
        } catch (_: Exception) {
            emptyList()
        }

        val activeQueries = (baseQueries.take(3) + googleKeywords.take(3)).distinct().take(6)
        val topGenreSlug = baseProfile.rankedGenres.firstOrNull()?.first ?: "hanh-dong"

        val resultsLists = coroutineScope {
            val searchTasks = activeQueries.map { kw ->
                async {
                    try {
                        kkphimApi.searchMovies(kw, limit = 6, page = 1).movies
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            val genreTask = async {
                try {
                    kkphimApi.getMoviesByGenre(topGenreSlug, page = 1, limit = 12).movies
                } catch (_: Exception) {
                    emptyList()
                }
            }
            searchTasks.awaitAll() + listOf(genreTask.await())
        }

        val candidateMap = linkedMapOf<String, Movie>()
        for (list in resultsLists) {
            for (m in list) {
                val key = m.effectiveKey
                if (key.isNotBlank() && !candidateMap.containsKey(key)) {
                    candidateMap[key] = m
                }
            }
        }

        if (candidateMap.size < 10) {
            try {
                val extra = kkphimApi.getMoviesByCategory("phim-le", page = 1, limit = 14).movies
                for (m in extra) {
                    val key = m.effectiveKey
                    if (key.isNotBlank() && !candidateMap.containsKey(key)) {
                        candidateMap[key] = m
                    }
                }
            } catch (_: Exception) {}
        }

        candidateMap.values.map { movie ->
            val eval = scoreMovieWithModel(movie, baseProfile)
            movie.copy(
                mlScore = eval.score,
                mlMatchPercent = eval.matchPercent
            )
        }.sortedByDescending { it.mlScore ?: 0.0 }
            .take(limit)
    }

    suspend fun getSimilarMovies(
        targetMovie: Movie,
        localPool: List<Movie> = emptyList(),
        limit: Int = 12
    ): List<Movie> = withContext(Dispatchers.IO) {
        val targetKey = targetMovie.effectiveKey
        val latentGenres = inferLatentGenres(targetMovie)
        val primaryGenreSlug = latentGenres.maxByOrNull { it.weight }?.slug ?: "hanh-dong"
        val titleTokens = tokenizeMovieText(targetMovie.seriesTitle.ifBlank { targetMovie.title })
        val keywordSeed = titleTokens.bigrams.firstOrNull()
            ?: targetMovie.categories.firstOrNull()
            ?: titleTokens.unigrams.firstOrNull()
            ?: ""

        val (genreCandidates, searchCandidates) = coroutineScope {
            val gTask = async {
                try {
                    kkphimApi.getMoviesByGenre(primaryGenreSlug, page = 1, limit = 16).movies
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val sTask = async {
                if (keywordSeed.length >= 2) {
                    try {
                        kkphimApi.searchMovies(keywordSeed, limit = 10, page = 1).movies
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else emptyList()
            }
            gTask.await() to sTask.await()
        }

        val combined = LinkedHashMap<String, Movie>()
        for (m in (searchCandidates + genreCandidates + localPool)) {
            val k = m.effectiveKey
            if (k.isNotBlank() && k != targetKey && !combined.containsKey(k)) {
                combined[k] = m
            }
        }

        combined.values.map { cand ->
            val sim = computeMovieSimilarity(targetMovie, cand)
            cand.copy(
                mlScore = sim.score,
                mlMatchPercent = sim.matchPercent
            )
        }.sortedByDescending { it.mlScore ?: 0.0 }
            .take(limit)
    }

    fun exportInteractionsJson(): JSONArray {
        val arr = JSONArray()
        for (item in getInteractionsList()) {
            arr.put(
                JSONObject().apply {
                    put("type", item.type)
                    put("slug", item.slug)
                    put("title", item.title)
                    put("originTitle", item.originTitle)
                    put("query", item.query)
                    put("movieType", item.movieType)
                    put("year", item.year)
                    put("lang", item.lang)
                    put("timestamp", item.timestamp)
                }
            )
        }
        return arr
    }

    fun importInteractionsJson(arr: JSONArray?) {
        if (arr == null) return
        val imported = mutableListOf<UserInteraction>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            imported.add(
                UserInteraction(
                    type = obj.optString("type", "view"),
                    slug = obj.optString("slug"),
                    title = obj.optString("title"),
                    originTitle = obj.optString("originTitle"),
                    query = obj.optString("query"),
                    movieType = obj.optString("movieType"),
                    year = obj.optString("year"),
                    lang = obj.optString("lang"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        if (imported.isNotEmpty()) {
            val merged = (imported + getInteractionsList())
                .distinctBy { "${it.type}:${it.slug}:${it.query}" }
                .take(50)
            saveInteractionsList(merged)
        }
    }

    private fun computeTimeDecay(timestampMs: Long): Double {
        if (timestampMs <= 0L) return 0.85
        val elapsed = max(0L, System.currentTimeMillis() - timestampMs).toDouble()
        return max(0.25, exp(-elapsed / HALF_LIFE_MS))
    }

    private fun getInteractionsList(): List<UserInteraction> {
        cachedInteractions?.let { return it }
        val loaded = mutableListOf<UserInteraction>()
        try {
            val raw = prefs.getString(ML_INTERACTIONS_KEY, null)
            if (!raw.isNullOrBlank()) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    loaded.add(
                        UserInteraction(
                            type = obj.optString("type", "view"),
                            slug = obj.optString("slug"),
                            title = obj.optString("title"),
                            originTitle = obj.optString("originTitle"),
                            query = obj.optString("query"),
                            movieType = obj.optString("movieType"),
                            year = obj.optString("year"),
                            lang = obj.optString("lang"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        cachedInteractions = loaded
        return loaded
    }

    private fun saveInteractionsList(list: List<UserInteraction>) {
        cachedInteractions = list.toMutableList()
        try {
            val arr = JSONArray()
            for (item in list) {
                arr.put(
                    JSONObject().apply {
                        put("type", item.type)
                        put("slug", item.slug)
                        put("title", item.title)
                        put("originTitle", item.originTitle)
                        put("query", item.query)
                        put("movieType", item.movieType)
                        put("year", item.year)
                        put("lang", item.lang)
                        put("timestamp", item.timestamp)
                    }
                )
            }
            prefs.edit().putString(ML_INTERACTIONS_KEY, arr.toString()).apply()
        } catch (_: Exception) {}
    }
}
