package br.com.iptvlab

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var credentialStore: CredentialsStore
    private lateinit var progressStore: WatchProgressStore
    private lateinit var favoriteStore: FavoriteStore
    private lateinit var progressSettingsStore: ProgressSyncSettingsStore
    private var client: XtreamClient? = null
    private var activeProfileId: String? = null
    private var currentPlayingMovie: CatalogEntry? = null
    private var currentPlayingProgressId: String? = null
    private var lastProgressSyncAt = 0L
    private var movieCategories: List<CatalogEntry> = emptyList()
    private var seriesCategories: List<CatalogEntry> = emptyList()
    private var allMovies: List<CatalogEntry> = emptyList()
    private var fourKCategory: CatalogEntry? = null
    private var recentMovies: List<CatalogEntry> = emptyList()
    private var featuredMovie: CatalogEntry? = null
    private var featuredDetails: MovieDetails? = null
    private var featuredCategoryName: String? = null
    private var featuredSeries: List<SeriesEntry> = emptyList()
    private val seriesDetailsCache = mutableMapOf<Int, SeriesDetails>()
    private var seriesDetailsRequestGeneration = 0
    private var pendingSeriesDetailsRequest: Runnable? = null
    private var openingSeriesId: Int? = null
    private var visibleSeriesCategory: CatalogEntry? = null
    private var visibleSeries: List<SeriesEntry> = emptyList()
    private var homeLoading = false
    private var homeError: String? = null
    private var homeErrorActionLabel = "Tentar novamente"
    private var connectionGeneration = 0
    private var homeIsVisible = false
    private var movies: List<CatalogEntry> = emptyList()
    private var categoryName = ""
    private var selectedMovieCategoryId: Int? = null
    private var selectedSeriesCategoryId: Int? = null
    private val allMoviesCategory = CatalogEntry(-1, "Todos os filmes")
    private val favoriteMoviesCategory = CatalogEntry(-4, "Favoritos")
    private val allSeriesCategory = CatalogEntry(-1, "Todas as séries")
    private val favoriteSeriesCategory = CatalogEntry(-4, "Favoritos")
    private val movieDetailsCache = mutableMapOf<Int, MovieDetails>()
    private var movieDetailsRequestGeneration = 0
    private var pendingMovieDetailsRequest: Runnable? = null
    private var currentMovieNav = "Filmes"
    private var activeCatalogSection: String? = null
    private var focusedFavoriteItem: CatalogEntry? = null
    private var favoriteHoldRunnable: Runnable? = null
    private var favoriteHoldTriggered = false
    private var movieSortOrder = MovieSortOrder.NEWEST
    private var seriesSortOrder = MovieSortOrder.NEWEST
    private var backAction: (() -> Unit)? = null
    private var returnFromPlayer: (() -> Unit)? = null
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var diagnosticView: TextView? = null
    private var diagnosticPanel: LinearLayout? = null
    private var playerActionDock: LinearLayout? = null
    private var diagnosticsVisible = false
    private var videoDecoder = "aguardando"
    private var audioDecoder = "aguardando"
    private var droppedFrames = 0
    private var bufferingEvents = 0
    private var latestError = "nenhum"

    private val saveProgressTick = object : Runnable {
        override fun run() {
            if (player != null) {
                saveCurrentProgress(syncRemote = System.currentTimeMillis() - lastProgressSyncAt >= 15_000L)
                uiHandler.postDelayed(this, 5_000L)
            }
        }
    }

    private val leSilUi by lazy {
        LeSilHomeScreen(this, LeSilHomeActions(
            onNavigate = ::navigate,
            onRetry = ::retryCatalog,
            onPlay = { movie -> playMovie(movie) { showHome() } },
            onContinueOpen = ::openContinueItem,
            onContinueRemove = ::removeContinueItem,
            onMovieDetails = { movie -> openMovieDetails(movie) { showHome() } },
            onMovieCategory = { category -> selectMovieCategory(category, "Filmes 4K") },
            onMovieFocus = ::requestMovieDetails,
            onSeriesCategory = { category -> selectSeriesCategory(category) },
            onSeriesDetails = ::openSeriesDetails
        ))
    }

    private val refresh = object : Runnable {
        override fun run() {
            if (player != null) {
                updateDiagnostics()
                uiHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this)
        credentialStore = CredentialsStore(applicationContext)
        progressStore = WatchProgressStore(applicationContext)
        favoriteStore = FavoriteStore(applicationContext)
        progressSettingsStore = ProgressSyncSettingsStore(applicationContext)
        root.setBackgroundColor(Color.rgb(12, 17, 25))
        setContentView(root)
        setAppImmersive()
        val saved = credentialStore.load()
        if (saved != null) {
            startConnection(saved, rememberCredentials = null)
        } else {
            homeLoading = false
            homeError = "Configure o servidor em Configurações para atualizar o catálogo."
            homeErrorActionLabel = "Configurar servidor"
            showHome()
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    private fun label(text: String, size: Float = 22f) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 17f
        isAllCaps = false
        minHeight = dp(54)
        setOnClickListener { action() }
    }
    private fun playerControlButton(icon: String, title: String, action: () -> Unit) = TextView(this).apply {
        text = "$icon  $title"
        contentDescription = title
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Color.rgb(236, 245, 252))
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(5), dp(9), dp(5))
        minHeight = dp(38)
        minWidth = dp(0)
        isFocusable = true
        isClickable = true
        fun setPlayerButtonBackground(focused: Boolean) {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(15).toFloat()
                setColor(if (focused) Color.rgb(18, 91, 124) else Color.argb(150, 18, 31, 47))
                setStroke(dp(if (focused) 2 else 1), if (focused) Color.rgb(72, 206, 246) else Color.argb(115, 129, 174, 202))
            }
        }
        setPlayerButtonBackground(false)
        setOnFocusChangeListener { _, focused -> setPlayerButtonBackground(focused) }
        setOnClickListener { action() }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setAppImmersive()
    }

    private fun setAppImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(35), dp(25), dp(35), dp(25))
    }
    private fun replace(view: View, isHome: Boolean = false) {
        root.removeAllViews()
        root.addView(view)
        homeIsVisible = isHome
    }
    private fun msg(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun runNetwork(task: () -> Unit, onError: (String) -> Unit) {
        worker.execute {
            try { task() } catch (e: Exception) {
                // Não mostrar a mensagem inteira: alguns erros de URL podem conter credenciais.
                runOnUiThread { onError(e.javaClass.simpleName + " — verifique a conexão/servidor") }
            }
        }
    }

    private fun showLogin() {
        connectionGeneration++
        stopPlayer()
        backAction = { showHome() }
        val saved = credentialStore.load()
        val screen = column()
        screen.addView(label("Le&Sil Stream", 30f))
        screen.addView(label("Filmes e séries VOD", 17f))
        val server = EditText(this).apply { hint = "https://servidor:porta"; setSingleLine(); textSize = 22f; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); backgroundTintList = ColorStateList.valueOf(Color.LTGRAY); setText(saved?.server.orEmpty()) }
        val user = EditText(this).apply { hint = "Usuário"; setSingleLine(); textSize = 22f; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); backgroundTintList = ColorStateList.valueOf(Color.LTGRAY); setText(saved?.username.orEmpty()) }
        val pass = EditText(this).apply {
            hint = "Senha"; setSingleLine(); textSize = 22f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(saved?.password.orEmpty())
        }
        screen.addView(server); screen.addView(user); screen.addView(pass)
        val remember = CheckBox(this).apply {
            text = "Lembrar neste dispositivo"
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = saved != null
        }
        screen.addView(remember)
        if (saved != null) screen.addView(button("Esquecer dados salvos") {
            credentialStore.clear()
            showLogin()
        })
        val status = label("Os dados só são guardados se você marcar a opção acima.", 15f)
        screen.addView(status)
        screen.addView(button("Entrar no Le&Sil Stream") {
            val url = server.text.toString().trim()
            if (url.startsWith("http://")) msg("Aviso: HTTP sem criptografia transmite credenciais pela rede")
            val credentials = SavedCredentials(url, user.text.toString().trim(), pass.text.toString())
            val shouldRemember = remember.isChecked
            startConnection(credentials, rememberCredentials = shouldRemember)
        })
        val scroll = ScrollView(this).apply { addView(screen) }
        replace(scroll)
    }

    /** Opens the catalog first, then authenticates and refreshes Xtream data in the background. */
    private fun startConnection(credentials: SavedCredentials, rememberCredentials: Boolean?) {
        val c = try {
            XtreamClient(credentials.server, credentials.username, credentials.password)
        } catch (e: IllegalArgumentException) {
            homeLoading = false
            homeError = e.message ?: "Dados de conexão inválidos."
            homeErrorActionLabel = "Configurações"
            showHome()
            return
        }
        val generation = ++connectionGeneration
        val profileId = WatchProgressStore.profileId(credentials.username)
        val legacyProfileId = WatchProgressStore.legacyProfileId(credentials.server, credentials.username)
        activeProfileId = profileId
        clearCatalogData()
        client = c
        homeLoading = true
        homeError = null
        homeErrorActionLabel = "Tentar novamente"
        replace(leSilUi.catalogLoadingScreen())

        runNetwork({
            c.authenticate()
            syncProgressProfile(profileId, legacyProfileId)
            syncFavoritesProfile(profileId)
            rememberCredentials?.let { remember ->
                runCatching {
                    if (remember) credentialStore.save(credentials) else credentialStore.clear()
                }.onFailure {
                    runOnUiThread {
                        if (generation == connectionGeneration) msg("Não foi possível guardar os dados neste aparelho")
                    }
                }
            }
            val loadedCategories = c.movieCategories()
            val loadedMovies = c.movies()
            val loadedFourKCategory = loadedCategories.firstOrNull { isFourKCategory(it.title) }
            val loadedSeriesCategories = runCatching { c.seriesCategories() }.getOrDefault(emptyList())
            val loadedSeries = runCatching { c.series() }.getOrDefault(emptyList())
            val loadedRecent = sortByProviderDate(loadedMovies)
            val loadedFeatured = loadedRecent.firstOrNull() ?: loadedMovies.firstOrNull()
            val loadedDetails = loadedFeatured?.let { featured -> runCatching { c.movieDetails(featured.id) }.getOrNull() }
            val loadedFeaturedCategory = loadedFeatured?.categoryId?.let { id -> loadedCategories.firstOrNull { it.id == id }?.title }
            runOnUiThread {
                if (generation != connectionGeneration) return@runOnUiThread
                movieCategories = loadedCategories
                seriesCategories = loadedSeriesCategories
                allMovies = loadedMovies
                fourKCategory = loadedFourKCategory
                recentMovies = loadedRecent
                featuredMovie = loadedFeatured
                featuredDetails = loadedDetails
                if (loadedFeatured != null && loadedDetails != null) movieDetailsCache[loadedFeatured.id] = loadedDetails
                featuredCategoryName = loadedFeaturedCategory
                featuredSeries = sortSeries(loadedSeries, MovieSortOrder.NEWEST)
                homeLoading = false
                homeError = null
                showHome()
            }
        }, {
            if (generation != connectionGeneration) return@runNetwork
            homeLoading = false
            homeError = "Não foi possível atualizar o catálogo. Confira o servidor e tente novamente."
            homeErrorActionLabel = "Tentar novamente"
            showHome()
        })
    }

    private fun clearCatalogData() {
        movieCategories = emptyList()
        seriesCategories = emptyList()
        allMovies = emptyList()
        fourKCategory = null
        recentMovies = emptyList()
        featuredMovie = null
        featuredDetails = null
        movieDetailsCache.clear()
        seriesDetailsCache.clear()
        seriesDetailsRequestGeneration++
        pendingSeriesDetailsRequest?.let(uiHandler::removeCallbacks)
        openingSeriesId = null
        visibleSeriesCategory = null
        visibleSeries = emptyList()
        featuredCategoryName = null
        featuredSeries = emptyList()
        movies = emptyList()
    }

    private fun retryCatalog() {
        val saved = credentialStore.load()
        if (saved != null) {
            startConnection(saved, rememberCredentials = null)
        } else {
            navigate("Configurações")
        }
    }

    private fun homeData() = LeSilHomeData(
        movieCategories = movieCategories,
        featuredMovie = featuredMovie,
        featuredDetails = featuredDetails,
        featuredCategory = featuredCategoryName,
        recentMovies = recentMovies,
        continueWatching = continueItems(),
        featuredSeries = featuredSeries,
        loading = homeLoading,
        errorMessage = homeError,
        errorActionLabel = homeErrorActionLabel
    )

    private fun showHome() {
        activeCatalogSection = null
        focusedFavoriteItem = null
        backAction = null
        replace(leSilUi.home(homeData()), isHome = true)
    }

    private fun navigate(destination: String) {
        when (destination) {
            "Início" -> showHome()
            "Filmes" -> showMovieCategories()
            "Séries" -> showSeriesCategories()
            "Filmes 4K" -> {
                val category = fourKCategory
                if (category == null) {
                    backAction = { showHome() }
                    replace(leSilUi.messageScreen("Filmes 4K", "Filmes 4K", "O catálogo não informou uma categoria chamada Filmes 4K ou Filmes UHD.", "Voltar ao início") { showHome() })
                } else {
                    selectMovieCategory(category, "Filmes 4K")
                }
            }
            "Atualizar" -> retryCatalog()
            "Configurações" -> {
                showSettings()
            }
        }
    }

    private fun continueEntries(): List<CatalogEntry> {
        val profile = activeProfileId ?: return emptyList()
        val catalogById = allMovies.associateBy { it.id }
        return progressStore.list(profile).mapNotNull { progress ->
            val id = progress.mediaId.toIntOrNull() ?: return@mapNotNull null
            val catalogItem = catalogById[id]
            (catalogItem ?: CatalogEntry(id, progress.title, posterUrl = progress.posterUrl))
                .copy(posterUrl = progress.posterUrl ?: catalogItem?.posterUrl)
        }
    }

    private fun continueItems(): List<ContinueWatchingItem> {
        val profile = activeProfileId ?: return emptyList()
        val catalogById = allMovies.associateBy { it.id }
        return progressStore.list(profile).mapNotNull { progress ->
            if (progress.mediaId.startsWith("episode:")) {
                ContinueWatchingItem(progress.mediaId, progress.title, progress.posterUrl)
            } else {
                val id = progress.mediaId.toIntOrNull() ?: return@mapNotNull null
                val movie = catalogById[id]
                ContinueWatchingItem(progress.mediaId, movie?.title ?: progress.title,
                    progress.posterUrl ?: movie?.posterUrl, movie?.rating)
            }
        }
    }

    private fun removeContinueItem(mediaId: String) {
        val profile = activeProfileId ?: return
        val current = progressStore.get(profile, mediaId) ?: return
        val removed = current.copy(
            hiddenFromContinue = true,
            updatedAt = maxOf(System.currentTimeMillis(), current.updatedAt + 1)
        )
        progressStore.save(profile, removed)
        showHome()
        msg("Removido de Continuar assistindo")
        worker.execute { syncProgressItem(profile, removed) }
    }

    private fun openContinueItem(mediaId: String) {
        val profile = activeProfileId ?: return
        val progress = progressStore.get(profile, mediaId) ?: return
        if (!mediaId.startsWith("episode:")) {
            val movieId = mediaId.toIntOrNull() ?: return
            val movie = allMovies.firstOrNull { it.id == movieId }
                ?: CatalogEntry(movieId, progress.title, posterUrl = progress.posterUrl)
            playMovie(movie) { showHome() }
            return
        }
        val episodeId = mediaId.removePrefix("episode:").toIntOrNull() ?: return
        val seriesTitle = progress.title.substringBefore(" · S")
        val series = featuredSeries.firstOrNull { it.title.equals(seriesTitle, ignoreCase = true) }
        if (series == null) {
            msg("Série não encontrada no catálogo atual")
            return
        }
        fun resume(details: SeriesDetails) {
            val episode = details.seasons.asSequence().flatMap { it.episodes.asSequence() }
                .firstOrNull { it.id == episodeId }
            if (episode == null) openSeriesDetails(series) else playEpisode(series, details, episode)
        }
        seriesDetailsCache[series.id]?.let { resume(it); return }
        val activeClient = client ?: return
        val generation = connectionGeneration
        val loading = leSilUi.messageScreen("Séries", series.title,
            "Localizando episódio para continuar…", "Voltar") { showHome() }
        backAction = { showHome() }
        replace(loading)
        worker.execute {
            val details = runCatching { activeClient.seriesDetails(series.id) }.getOrNull()
            runOnUiThread {
                if (generation != connectionGeneration || root.getChildAt(0) !== loading) return@runOnUiThread
                if (details == null) {
                    msg("Não foi possível carregar o episódio")
                    showHome()
                } else {
                    seriesDetailsCache[series.id] = details
                    resume(details)
                }
            }
        }
    }

    private fun showContinueCatalog() {
        activeCatalogSection = null
        val entries = continueEntries()
        if (entries.isEmpty()) {
            backAction = { showHome() }
            replace(leSilUi.messageScreen(
                "Continuar", "Continuar assistindo",
                "Os filmes que você começar aparecerão aqui. O progresso fica salvo neste aparelho e pode sincronizar pelo NAS.",
                "Voltar ao início"
            ) { showHome() })
            return
        }
        backAction = { showHome() }
        val category = CatalogEntry(CONTINUE_CATEGORY_ID, "Continuar assistindo")
        replace(leSilUi.movieCatalog(
            title = "Continuar assistindo · ${entries.size} títulos",
            selectedNav = "Continuar",
            categories = listOf(category),
            selectedCategoryId = category.id,
            movies = entries,
            initialDetails = entries.firstOrNull()?.let { movieDetailsCache[it.id] },
            initialDetailsMovieId = entries.firstOrNull()?.id,
            onCategory = {},
            onMovie = { movie -> playMovie(movie) { showHome() } },
            onMovieFocus = ::requestMovieDetails
        ))
    }

    private fun showSettings() {
        activeCatalogSection = null
        backAction = { showHome() }
        val screen = column()
        screen.addView(label("Configurações", 30f))
        screen.addView(label("Catálogo", 18f))
        screen.addView(button("Editar conexão Xtream") { showLogin() })
        screen.addView(label("Sincronizar progresso pelo NAS", 18f))
        screen.addView(label(
            progressSettingsStore.load()?.let { "NAS configurado: ${it.endpoint}" }
                ?: "Opcional · compartilhar o progresso entre TV e celular na rede de casa.",
            15f
        ))
        val nasStatus = label("NAS: verificando conexão…", 15f)
        screen.addView(nasStatus)
        checkNasConnection(nasStatus)
        screen.addView(button("Configurar sincronização NAS") { showNasSyncSettings() })
        screen.addView(button("Voltar") { showHome() })
        replace(ScrollView(this).apply { addView(screen) })
    }

    private fun checkNasConnection(statusView: TextView) {
        val settings = progressSettingsStore.load()
        if (settings == null) {
            statusView.text = "NAS: não configurado"
            return
        }
        worker.execute {
            val result = runCatching {
                val connection = (java.net.URL("${settings.endpoint.trimEnd('/')}/health")
                    .openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 2500
                    readTimeout = 3500
                    requestMethod = "GET"
                }
                try {
                    val code = connection.responseCode
                    val body = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
                    if (code == 200 && body.contains("\"status\":\"ok\"")) {
                        "NAS: conectado"
                    } else {
                        "NAS: API respondeu HTTP $code"
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { "NAS: indisponível" }
            runOnUiThread {
                if (statusView.parent != null) statusView.text = result
            }
        }
    }

    private fun showNasSyncSettings() {
        backAction = { showSettings() }
        val saved = progressSettingsStore.load()
        val screen = column()
        screen.addView(label("Sincronização NAS", 28f))
        screen.addView(label("Informe o endereço local do serviço e o mesmo token usado no OpenMediaVault.", 15f))
        val endpoint = EditText(this).apply {
            hint = "http://192.168.3.10:8787"
            setSingleLine()
            textSize = 19f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
            setText(saved?.endpoint.orEmpty())
        }
        val token = EditText(this).apply {
            hint = "Token do NAS"
            setSingleLine()
            textSize = 19f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            backgroundTintList = ColorStateList.valueOf(Color.LTGRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(saved?.token.orEmpty())
        }
        screen.addView(endpoint)
        screen.addView(token)
        screen.addView(button("Salvar sincronização") {
            val url = endpoint.text.toString().trim().trimEnd('/')
            val secret = token.text.toString()
            val uri = runCatching { android.net.Uri.parse(url) }.getOrNull()
            if (uri == null || !(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) || uri.host.isNullOrBlank()) {
                msg("Informe um endereço HTTP ou HTTPS válido para o NAS")
            } else if (secret.length < 32) {
                msg("O token precisa ter pelo menos 32 caracteres")
            } else {
                runCatching { progressSettingsStore.save(ProgressSyncSettings(url, secret)) }
                    .onSuccess {
                        msg("Sincronização salva. TV e celular devem usar a mesma conta Xtream e token.")
                        showSettings()
                    }
                    .onFailure { msg("Não foi possível salvar as configurações do NAS") }
            }
        })
        if (saved != null) screen.addView(button("Desativar sincronização NAS") {
            progressSettingsStore.clear()
            msg("Sincronização NAS desativada; o progresso local continua salvo.")
            showSettings()
        })
        screen.addView(button("Voltar") { showSettings() })
        replace(ScrollView(this).apply { addView(screen) })
    }

    private fun syncProgressProfile(profileId: String, legacyProfileId: String) {
        val profileTag = profileId.take(8)
        val settings = progressSettingsStore.load() ?: run {
            android.util.Log.w("LeSilProgressSync", "No NAS settings for profile=$profileTag")
            return
        }
        runCatching {
            val syncClient = ProgressSyncClient(settings)
            if (legacyProfileId != profileId) {
                progressStore.merge(profileId, progressStore.all(legacyProfileId))
            }
            val remote = syncClient.fetch(profileId)
            val legacyRemote = if (legacyProfileId == profileId) emptyList() else {
                runCatching { syncClient.fetch(legacyProfileId) }.getOrDefault(emptyList())
            }
            progressStore.merge(profileId, remote + legacyRemote)
            val remoteById = remote.associateBy { it.mediaId }
            progressStore.all(profileId)
                .filter { item ->
                    val remoteItem = remoteById[item.mediaId]
                    item.updatedAt > (remoteItem?.updatedAt ?: 0L) ||
                        (item.updatedAt == remoteItem?.updatedAt && item.hiddenFromContinue &&
                            remoteItem.hiddenFromContinue != true)
                }
                .forEach { syncClient.upsert(profileId, it) }
            lastProgressSyncAt = System.currentTimeMillis()
            remote.size to progressStore.all(profileId).size
        }.onSuccess { (remoteCount, localCount) ->
            android.util.Log.i("LeSilProgressSync", "profile=$profileTag fetched=$remoteCount local=$localCount")
        }.onFailure { error ->
            android.util.Log.e("LeSilProgressSync", "profile=$profileTag fetch failed: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun syncProgressItem(profileId: String, item: WatchProgress) {
        val profileTag = profileId.take(8)
        val settings = progressSettingsStore.load() ?: run {
            android.util.Log.w("LeSilProgressSync", "No NAS settings for upload profile=$profileTag")
            return
        }
        runCatching { ProgressSyncClient(settings).upsert(profileId, item) }
            .onSuccess {
                lastProgressSyncAt = System.currentTimeMillis()
                android.util.Log.i("LeSilProgressSync", "profile=$profileTag upload succeeded")
            }
            .onFailure { error ->
                android.util.Log.e("LeSilProgressSync", "profile=$profileTag upload failed: ${error.javaClass.simpleName}: ${error.message}")
            }
    }

    private fun syncFavoritesProfile(profileId: String) {
        val settings = progressSettingsStore.load() ?: return
        runCatching {
            val syncClient = ProgressSyncClient(settings)
            val remote = syncClient.fetchFavorites(profileId)
            favoriteStore.merge(profileId, remote)
            val remoteByKey = remote.associateBy { it.mediaType to it.mediaId }
            favoriteStore.all(profileId)
                .filter { it.updatedAt > (remoteByKey[it.mediaType to it.mediaId]?.updatedAt ?: 0L) }
                .forEach { syncClient.upsertFavorite(profileId, it) }
        }.onFailure { error ->
            android.util.Log.w("LeSilFavorites", "Sync failed: ${error.javaClass.simpleName}")
        }
    }

    private fun syncFavoriteItem(profileId: String, item: FavoriteEntry) {
        val settings = progressSettingsStore.load() ?: return
        runCatching { ProgressSyncClient(settings).upsertFavorite(profileId, item) }
            .onFailure { error ->
                android.util.Log.w("LeSilFavorites", "Upload failed: ${error.javaClass.simpleName}")
            }
    }

    private fun refreshRemoteState() {
        val profile = activeProfileId ?: return
        val activeClient = client ?: return
        val generation = connectionGeneration
        worker.execute {
            syncProgressProfile(profile, WatchProgressStore.legacyProfileId(activeClient.server, activeClient.username))
            val previousFavorites = favoriteStore.all(profile)
            syncFavoritesProfile(profile)
            val favoritesChanged = favoriteStore.all(profile) != previousFavorites
            runOnUiThread {
                if (generation != connectionGeneration || player != null) return@runOnUiThread
                if (!favoritesChanged) return@runOnUiThread
                if (activeCatalogSection == "movie" && selectedMovieCategoryId == favoriteMoviesCategory.id) {
                    movies = favoriteMovies()
                    showMovies()
                } else if (activeCatalogSection == "series" && selectedSeriesCategoryId == favoriteSeriesCategory.id &&
                    visibleSeriesCategory?.id == favoriteSeriesCategory.id) {
                    showSeries(favoriteSeriesCategory, favoriteSeries())
                }
            }
        }
    }

    private fun favoriteMovies(): List<CatalogEntry> {
        val profile = activeProfileId ?: return emptyList()
        val catalog = allMovies.associateBy { it.id }
        return favoriteStore.active(profile, "movie").map { saved ->
            val item = catalog[saved.mediaId]
            item?.copy(posterUrl = item.posterUrl ?: saved.posterUrl)
                ?: CatalogEntry(saved.mediaId, saved.title, posterUrl = saved.posterUrl)
        }
    }

    private fun favoriteSeries(): List<SeriesEntry> {
        val profile = activeProfileId ?: return emptyList()
        val catalog = featuredSeries.associateBy { it.id }
        return favoriteStore.active(profile, "series").map { saved ->
            val item = catalog[saved.mediaId]
            item?.copy(coverUrl = item.coverUrl ?: saved.posterUrl)
                ?: SeriesEntry(saved.mediaId, saved.title, saved.posterUrl)
        }
    }

    private fun toggleFavorite(type: String, id: Int, title: String, posterUrl: String?): Boolean {
        val profile = activeProfileId ?: return false
        val active = favoriteStore.get(profile, type, id)?.active != true
        val item = FavoriteEntry(type, id, title, posterUrl, System.currentTimeMillis(), active)
        favoriteStore.save(profile, item)
        worker.execute { syncFavoriteItem(profile, item) }
        msg(if (active) "Adicionado aos favoritos" else "Removido dos favoritos")
        if (type == "movie" && activeCatalogSection == "movie" &&
            selectedMovieCategoryId == favoriteMoviesCategory.id && currentMovieNav == "Filmes") {
            movies = favoriteMovies()
            showMovies()
        } else if (type == "series" && activeCatalogSection == "series" && selectedSeriesCategoryId == favoriteSeriesCategory.id &&
            visibleSeriesCategory?.id == favoriteSeriesCategory.id) {
            showSeries(favoriteSeriesCategory, favoriteSeries())
        }
        return active
    }

    private fun showMovieCategories() {
        refreshRemoteState()
        currentMovieNav = "Filmes"
        movieSortOrder = MovieSortOrder.NEWEST
        selectedMovieCategoryId = allMoviesCategory.id
        categoryName = allMoviesCategory.title
        movies = allMovies
        showMovies()
    }

    private fun showSeriesCategories() {
        refreshRemoteState()
        seriesSortOrder = MovieSortOrder.NEWEST
        selectedSeriesCategoryId = allSeriesCategory.id
        showSeries(null, featuredSeries)
    }

    private fun selectMovieCategory(category: CatalogEntry, nav: String) {
        currentMovieNav = nav
        movieSortOrder = if (category.id == favoriteMoviesCategory.id) MovieSortOrder.TITLE_ASC
            else MovieSortOrder.NEWEST
        selectedMovieCategoryId = category.id
        categoryName = category.title
        if (category.id == favoriteMoviesCategory.id) {
            movies = favoriteMovies()
            showMovies()
            refreshRemoteState()
            return
        }
        if (category.id == allMoviesCategory.id) {
            movies = allMovies
            showMovies()
            return
        }
        val cached = allMovies.filter { it.categoryId == category.id }
        if (cached.isNotEmpty()) {
            movies = cached
            showMovies()
            return
        }
        backAction = { if (nav == "Filmes 4K") showHome() else showMovieCategories() }
        replace(leSilUi.messageScreen(nav, category.title, "Carregando filmes do catálogo…", "Voltar") {
            if (nav == "Filmes 4K") showHome() else showMovieCategories()
        })
        val c = client ?: return
        runNetwork({
            val result = c.movies(category.id)
            runOnUiThread {
                movies = result
                showMovies()
            }
        }, {
            runOnUiThread {
                backAction = { if (nav == "Filmes 4K") showHome() else showMovieCategories() }
                replace(leSilUi.messageScreen(nav, category.title, "Não foi possível carregar esta categoria. Tente novamente.", "Voltar") {
                    if (nav == "Filmes 4K") showHome() else showMovieCategories()
                })
            }
        })
    }

    private fun showMovies() {
        activeCatalogSection = "movie"
        backAction = { if (currentMovieNav == "Filmes 4K") showHome() else showMovieCategories() }
        val isFavorites = selectedMovieCategoryId == favoriteMoviesCategory.id
        val effectiveSort = if (isFavorites) MovieSortOrder.TITLE_ASC else movieSortOrder
        val displayedMovies = sortMovies(movies, effectiveSort)
        replace(leSilUi.movieCatalog(
            title = categoryName,
            selectedNav = currentMovieNav,
            categories = if (currentMovieNav == "Filmes 4K") listOfNotNull(fourKCategory)
                else listOf(allMoviesCategory, favoriteMoviesCategory) + movieCategories,
            selectedCategoryId = selectedMovieCategoryId,
            movies = displayedMovies,
            initialDetails = displayedMovies.firstOrNull()?.let { movieDetailsCache[it.id] },
            initialDetailsMovieId = displayedMovies.firstOrNull()?.id,
            onCategory = { selectMovieCategory(it, currentMovieNav) },
            onMovie = { movie -> openMovieDetails(movie) { showMovies() } },
            onMovieFocus = { movie, deliver ->
                focusedFavoriteItem = movie
                requestMovieDetails(movie, deliver)
            },
            favoriteIds = activeProfileId?.let { favoriteStore.active(it, "movie").map(FavoriteEntry::mediaId).toSet() }.orEmpty(),
            onToggleFavorite = { movie -> toggleFavorite("movie", movie.id, movie.title, movie.posterUrl) },
            sortOrder = effectiveSort,
            onSort = if (isFavorites) null else { order -> movieSortOrder = order; showMovies() }
        ))
    }

    private fun selectSeriesCategory(category: CatalogEntry) {
        seriesSortOrder = if (category.id == favoriteSeriesCategory.id) MovieSortOrder.TITLE_ASC
            else MovieSortOrder.NEWEST
        if (category.id == allSeriesCategory.id) {
            selectedSeriesCategoryId = allSeriesCategory.id
            showSeries(null, featuredSeries)
            return
        }
        if (category.id == favoriteSeriesCategory.id) {
            selectedSeriesCategoryId = category.id
            showSeries(category, favoriteSeries())
            refreshRemoteState()
            return
        }
        selectedSeriesCategoryId = category.id
        val cached = featuredSeries.filter { it.categoryId == category.id }
        if (cached.isNotEmpty()) {
            showSeries(category, cached)
            return
        }
        backAction = { showSeriesCategories() }
        replace(leSilUi.messageScreen("Séries", category.title, "Carregando séries do catálogo…", "Voltar às categorias") { showSeriesCategories() })
        val c = client ?: return
        runNetwork({
            val result = c.series(category.id)
            runOnUiThread { showSeries(category, result) }
        }, {
            runOnUiThread {
                backAction = { showSeriesCategories() }
                replace(leSilUi.messageScreen("Séries", category.title, "Não foi possível carregar esta categoria.", "Voltar às categorias") { showSeriesCategories() })
            }
        })
    }

    private fun showSeries(category: CatalogEntry? = null, items: List<SeriesEntry> = featuredSeries) {
        activeCatalogSection = "series"
        openingSeriesId = null
        visibleSeriesCategory = category
        visibleSeries = items
        val isFavorites = selectedSeriesCategoryId == favoriteSeriesCategory.id
        val effectiveSort = if (isFavorites) MovieSortOrder.TITLE_ASC else seriesSortOrder
        val displayedSeries = sortSeries(items, effectiveSort)
        backAction = { showSeriesCategories() }
        replace(leSilUi.seriesCatalog(
            title = category?.title ?: allSeriesCategory.title,
            selectedNav = "Séries",
            categories = listOf(allSeriesCategory, favoriteSeriesCategory) + seriesCategories,
            selectedCategoryId = selectedSeriesCategoryId,
            series = displayedSeries,
            onCategory = ::selectSeriesCategory,
            onSeries = ::openSeriesDetails,
            onSeriesFocus = { series, deliver ->
                focusedFavoriteItem = CatalogEntry(series.id, series.title, posterUrl = series.coverUrl)
                requestSeriesInfo(series) { info -> deliver(info?.asCatalogDetails(series)) }
            },
            favoriteIds = activeProfileId?.let { favoriteStore.active(it, "series").map(FavoriteEntry::mediaId).toSet() }.orEmpty(),
            onToggleFavorite = { series -> toggleFavorite("series", series.id, series.title, series.coverUrl) },
            initialDetails = displayedSeries.firstOrNull()?.let { seriesDetailsCache[it.id]?.asCatalogDetails(it) },
            sortOrder = effectiveSort,
            onSort = if (isFavorites) null else { order -> seriesSortOrder = order; showSeries(category, items) }
        ))
    }

    private fun openMovieDetails(movie: CatalogEntry, returnTo: () -> Unit) {
        val details = movieDetailsCache[movie.id] ?: if (featuredMovie?.id == movie.id) featuredDetails else null
        if (details == null) {
            requestMovieDetails(movie) { loaded -> showMovieDetailsDialog(movie, loaded, returnTo) }
            return
        }
        showMovieDetailsDialog(movie, details, returnTo)
    }

    private fun openMovieCast(movie: CatalogEntry) {
        val cached = movieDetailsCache[movie.id] ?: if (featuredMovie?.id == movie.id) featuredDetails else null
        if (cached != null) {
            showMovieCastDialog(movie, cached)
            return
        }
        val activeClient = client ?: run {
            showMovieCastDialog(movie, null)
            return
        }
        val loading = AlertDialog.Builder(this)
            .setTitle("Elenco")
            .setMessage("Carregando informações do elenco…")
            .setCancelable(false)
            .create()
        loading.show()
        worker.execute {
            val details = runCatching { activeClient.movieDetails(movie.id) }.getOrNull()
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (details != null) movieDetailsCache[movie.id] = details
                showMovieCastDialog(movie, details)
            }
        }
    }

    private fun showMovieCastDialog(movie: CatalogEntry, details: MovieDetails?) {
        val cast = details?.cast?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (details == null) "Não foi possível carregar o elenco agora."
            else "O catálogo não informou o elenco deste título."
        AlertDialog.Builder(this)
            .setTitle("Elenco")
            .setMessage("${movie.title}\n\n$cast")
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun showMovieDetailsDialog(movie: CatalogEntry, details: MovieDetails?, returnTo: () -> Unit) {
        val category = movie.categoryId?.let { id -> movieCategories.firstOrNull { it.id == id }?.title }
        val description = buildList {
            category?.let { add("Categoria: $it") }
            movie.extension.takeIf { it.isNotBlank() }?.let { add("Contêiner informado: ${it.uppercase()}") }
            details?.year?.let { add("Ano: $it") }
            details?.duration?.let { add("Duração: $it") }
            details?.genre?.let { add("Gênero: $it") }
            details?.cast?.let { add("Elenco: $it") }
            details?.director?.let { add("Direção: $it") }
            details?.plot?.let { add(it) }
        }.joinToString("\n\n").ifBlank { "O catálogo não informou outros detalhes para este título." }
        AlertDialog.Builder(this)
            .setTitle(details?.title?.takeIf { it.isNotBlank() } ?: movie.title)
            .setMessage(description)
            .setNegativeButton("Fechar", null)
            .setPositiveButton("Assistir") { _, _ -> playMovie(movie, returnTo) }
            .show()
    }

    private fun requestMovieDetails(movie: CatalogEntry, deliver: (MovieDetails?) -> Unit) {
        val cached = movieDetailsCache[movie.id] ?: if (featuredMovie?.id == movie.id) featuredDetails else null
        if (cached != null) {
            deliver(cached)
            return
        }
        val generation = ++movieDetailsRequestGeneration
        pendingMovieDetailsRequest?.let(uiHandler::removeCallbacks)
        val request = Runnable {
            if (generation != movieDetailsRequestGeneration) return@Runnable
            val activeClient = client
            if (activeClient == null) {
                deliver(null)
                return@Runnable
            }
            worker.execute {
                val details = runCatching { activeClient.movieDetails(movie.id) }.getOrNull()
                runOnUiThread {
                    if (details != null) movieDetailsCache[movie.id] = details
                    if (generation == movieDetailsRequestGeneration) deliver(details)
                }
            }
        }
        pendingMovieDetailsRequest = request
        uiHandler.postDelayed(request, 300)
    }

    private fun openSeriesDetails(series: SeriesEntry) {
        openingSeriesId = series.id
        val cached = seriesDetailsCache[series.id]
        if (cached != null) {
            showSeriesSeasons(series, cached)
            refreshEpisodeProgressOnOpen(series, cached)
            return
        }
        backAction = { showSeries(visibleSeriesCategory, visibleSeries) }
        replace(leSilUi.messageScreen("Séries", series.title, "Carregando temporadas…", "Voltar") {
            showSeries(visibleSeriesCategory, visibleSeries)
        })
        val activeClient = client ?: return
        worker.execute {
            val result = runCatching { activeClient.seriesDetails(series.id) }
            runOnUiThread {
                result.getOrNull()?.let { seriesDetailsCache[series.id] = it }
                if (openingSeriesId != series.id) return@runOnUiThread
                val details = result.getOrNull()
                if (details != null) {
                    showSeriesSeasons(series, details)
                    refreshEpisodeProgressOnOpen(series, details)
                }
                else replace(leSilUi.messageScreen("Séries", series.title,
                    "Não foi possível carregar as temporadas desta série.", "Voltar") {
                    showSeries(visibleSeriesCategory, visibleSeries)
                })
            }
        }
    }

    private fun showSeriesSeasons(series: SeriesEntry, details: SeriesDetails) {
        activeCatalogSection = null
        openingSeriesId = series.id
        backAction = { showSeries(visibleSeriesCategory, visibleSeries) }
        val progress = activeProfileId?.let { profile ->
            details.seasons.flatMap { it.episodes }.mapNotNull { episode ->
                progressStore.get(profile, "episode:${episode.id}")?.let { episode.id to it }
            }.toMap()
        }.orEmpty()
        val shownDetails = if (details.plot.isNullOrBlank()) details.copy(plot = series.plot) else details
        replace(leSilUi.seriesSeasons(series, shownDetails,
            episodeProgress = progress,
            onEpisode = { episode -> playEpisode(series, details, episode) },
            onBack = { showSeries(visibleSeriesCategory, visibleSeries) }
        ))
    }

    private fun refreshEpisodeProgressOnOpen(series: SeriesEntry, details: SeriesDetails) {
        val profile = activeProfileId ?: return
        val activeClient = client ?: return
        val episodeIds = details.seasons.flatMap { season -> season.episodes }
            .map { episode -> "episode:${episode.id}" }
        worker.execute {
            val before = episodeIds.associateWith { progressStore.get(profile, it) }
            syncProgressProfile(profile, WatchProgressStore.legacyProfileId(activeClient.server, activeClient.username))
            val changed = episodeIds.any { progressStore.get(profile, it) != before[it] }
            if (changed) runOnUiThread {
                if (openingSeriesId == series.id && player == null) showSeriesSeasons(series, details)
            }
        }
    }

    private fun requestSeriesInfo(series: SeriesEntry, deliver: (SeriesDetails?) -> Unit) {
        seriesDetailsCache[series.id]?.let { deliver(it); return }
        val generation = ++seriesDetailsRequestGeneration
        pendingSeriesDetailsRequest?.let(uiHandler::removeCallbacks)
        val request = Runnable {
            if (generation != seriesDetailsRequestGeneration) return@Runnable
            val activeClient = client ?: return@Runnable
            worker.execute {
                val details = runCatching { activeClient.seriesDetails(series.id) }.getOrNull()
                runOnUiThread {
                    if (details != null) seriesDetailsCache[series.id] = details
                    if (generation == seriesDetailsRequestGeneration) deliver(details)
                }
            }
        }
        pendingSeriesDetailsRequest = request
        uiHandler.postDelayed(request, 300)
    }

    private fun SeriesDetails.asCatalogDetails(series: SeriesEntry) = MovieDetails(
        title = title ?: series.title,
        posterUrl = coverUrl ?: series.coverUrl,
        backdropUrl = backdropUrl,
        plot = plot?.takeIf { it.isNotBlank() } ?: series.plot,
        cast = cast,
        director = director,
        genre = genre,
        year = year,
        duration = seasons.count { it.episodes.isNotEmpty() }.takeIf { it > 0 }
            ?.let { "$it ${if (it == 1) "temporada" else "temporadas"}" },
        rating = rating ?: series.rating
    )

    private fun sortSeries(entries: List<SeriesEntry>, order: MovieSortOrder): List<SeriesEntry> = when (order) {
        MovieSortOrder.TITLE_DESC -> entries.sortedByDescending { it.title.lowercase() }
        MovieSortOrder.NEWEST -> entries.withIndex()
            .sortedWith(compareBy<IndexedValue<SeriesEntry>> { it.value.addedAt == null }
                .thenByDescending { it.value.addedAt ?: 0L }.thenBy { it.index })
            .map { it.value }
        MovieSortOrder.OLDEST -> entries.withIndex()
            .sortedWith(compareBy<IndexedValue<SeriesEntry>> { it.value.addedAt == null }
                .thenBy { it.value.addedAt ?: 0L }.thenBy { it.index })
            .map { it.value }
        else -> entries.sortedBy { it.title.lowercase() }
    }

    private fun sortByProviderDate(entries: List<CatalogEntry>): List<CatalogEntry> = entries.withIndex()
        .sortedWith(compareBy<IndexedValue<CatalogEntry>> { it.value.addedAt == null }
            .thenByDescending { it.value.addedAt ?: 0L }
            .thenBy { it.index })
        .map { it.value }

    private fun sortMovies(entries: List<CatalogEntry>, order: MovieSortOrder): List<CatalogEntry> = when (order) {
        MovieSortOrder.TITLE_ASC -> entries.sortedBy { it.title.lowercase() }
        MovieSortOrder.TITLE_DESC -> entries.sortedByDescending { it.title.lowercase() }
        MovieSortOrder.NEWEST -> sortByProviderDate(entries)
        MovieSortOrder.OLDEST -> entries.withIndex()
            .sortedWith(compareBy<IndexedValue<CatalogEntry>> { it.value.addedAt == null }
                .thenBy { it.value.addedAt ?: 0L }
                .thenBy { it.index })
            .map { it.value }
        else -> {
            val pattern = requireNotNull(order.formatPattern)
            val (matching, remaining) = entries.partition { movie ->
                val details = movieDetailsCache[movie.id]
                pattern.containsMatchIn(
                    "${movie.title} ${details?.title.orEmpty()} " +
                        "${details?.technicalVideo.orEmpty()} ${details?.technicalAudio.orEmpty()}"
                )
            }
            matching + remaining
        }
    }

    private fun isFourKCategory(name: String): Boolean {
        val normalized = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
        return normalized == "filmes 4k" || normalized == "filmes uhd"
    }

    private fun playMovie(movie: CatalogEntry, returnTo: () -> Unit = { showHome() }) {
        val c = client ?: return
        val url = try { c.movieUrl(movie) } catch (e: Exception) { msg("Formato de arquivo não reconhecido"); return }
        playMedia(movie, url, movie.id.toString(), { openMovieCast(movie) }, returnTo)
    }

    private fun playEpisode(series: SeriesEntry, details: SeriesDetails, episode: EpisodeEntry) {
        val c = client ?: return
        val url = try { c.episodeUrl(episode) } catch (e: Exception) {
            msg("Formato do episódio não reconhecido")
            return
        }
        val entry = CatalogEntry(episode.id,
            "${series.title} · S${episode.seasonNumber} E${episode.number} · ${episode.title}",
            extension = episode.extension,
            posterUrl = episode.imageUrl ?: details.coverUrl ?: series.coverUrl,
            categoryId = series.categoryId)
        playMedia(entry, url, "episode:${episode.id}", {
            AlertDialog.Builder(this).setTitle(series.title)
                .setMessage(details.cast?.takeIf { it.isNotBlank() } ?: "Elenco não informado pelo servidor.")
                .setPositiveButton("Fechar", null).show()
        }) { showSeriesSeasons(series, details) }
    }

    private fun playMedia(movie: CatalogEntry, url: String, progressId: String,
                          showCast: () -> Unit, returnTo: () -> Unit) {
        stopPlayer()
        currentPlayingMovie = movie
        currentPlayingProgressId = progressId
        returnFromPlayer = returnTo
        videoDecoder = "aguardando"; audioDecoder = "aguardando"
        droppedFrames = 0; bufferingEvents = 0; latestError = "nenhum"
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(this, httpDataSourceFactory)
        )
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player = exo
        // PlayerView usa SurfaceView por padrão, preferida para preservar HDR no Android TV.
        val pv = PlayerView(this).apply {
            player = exo
            setBackgroundColor(Color.BLACK)
            setShutterBackgroundColor(Color.BLACK)
    useController = true
    setControllerAutoShow(false)
    setControllerHideOnTouch(true)
    setControllerShowTimeoutMs(4500)
    setControllerAnimationEnabled(true)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
    playerView = pv
    setAppImmersive()
        root.setBackgroundColor(Color.BLACK)
        root.removeAllViews()
        root.addView(pv, FrameLayout.LayoutParams(-1, -1))
        pv.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
        val info = label("Carregando dados técnicos...", 16f).apply {
            setBackgroundColor(Color.argb(215, 5, 12, 20))
            setPadding(dp(15), dp(12), dp(15), dp(12))
        }
        diagnosticView = info
    val diagnosticCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(18), dp(14), dp(18), dp(14))
      background = GradientDrawable().apply {
        setColor(Color.argb(225, 7, 14, 27))
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.argb(150, 78, 190, 255))
      }
      visibility = View.GONE
    }
    val diagnosticHeading = label("DIAGNÓSTICO DO STREAM", 14f).apply {
      setTextColor(Color.rgb(105, 205, 255))
      setTypeface(typeface, Typeface.BOLD)
      setPadding(0, 0, 0, dp(8))
    }
    diagnosticCard.addView(diagnosticHeading)
    diagnosticCard.addView(info)
    diagnosticPanel = diagnosticCard
    root.addView(diagnosticCard, FrameLayout.LayoutParams(dp(620), -2, Gravity.TOP or Gravity.START).apply {
      setMargins(dp(28), dp(28), dp(28), 0)
    })
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(225, 5, 12, 20))
        }
    dock.addView(playerControlButton("ⓘ", "Informação") {
      diagnosticsVisible = !diagnosticsVisible
      diagnosticPanel?.visibility = if (diagnosticsVisible) View.VISIBLE else View.GONE
      pv.showController()
    })
        dock.addView(button("Áudio") { TrackSelectionDialogBuilder(this, "Faixas de áudio", exo, C.TRACK_TYPE_AUDIO).build().show() })
        dock.addView(button("Legendas") { TrackSelectionDialogBuilder(this, "Legendas", exo, C.TRACK_TYPE_TEXT).setShowDisableOption(true).build().show() })
        dock.addView(button("Voltar") { leavePlayer() })
    root.addView(dock, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
      setMargins(dp(16), 0, dp(16), dp(4))
    })
    playerActionDock = dock
    dock.visibility = View.GONE
    dock.setPadding(dp(6), dp(4), dp(6), dp(4))
    dock.gravity = Gravity.CENTER
    dock.background = GradientDrawable().apply {
      setColor(Color.argb(225, 7, 14, 27))
      cornerRadius = dp(18).toFloat()
      setStroke(dp(1), Color.argb(125, 78, 190, 255))
    }
    dock.removeAllViews()
    dock.addView(playerControlButton("ⓘ", "Informação") {
      diagnosticsVisible = !diagnosticsVisible
      diagnosticPanel?.visibility = if (diagnosticsVisible) View.VISIBLE else View.GONE
      pv.showController()
    })
    dock.addView(playerControlButton("♫", "Áudio") {
      TrackSelectionDialogBuilder(this, "Áudio", exo, C.TRACK_TYPE_AUDIO).build().show()
      pv.showController()
    })
    dock.addView(playerControlButton("CC", "Legendas") {
      TrackSelectionDialogBuilder(this, "Legendas", exo, C.TRACK_TYPE_TEXT).build().show()
      pv.showController()
    })
    dock.addView(playerControlButton("★", "Elenco") {
      showCast()
      pv.showController()
    })
    dock.addView(playerControlButton("↩", "Sair") { leavePlayer() })
    pv.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
      val controlsVisible = visibility == View.VISIBLE
      dock.visibility = if (controlsVisible) View.VISIBLE else View.GONE
      if (!controlsVisible) {
        diagnosticsVisible = false
        diagnosticPanel?.visibility = View.GONE
      }
    })
    pv.hideController()

        exo.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { updateDiagnostics() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) bufferingEvents++
                if (playbackState == Player.STATE_ENDED) saveCurrentProgress(syncRemote = true, completed = true)
                updateDiagnostics()
            }
            override fun onPlayerError(error: PlaybackException) {
                var cause: Throwable? = error
                var httpError: HttpDataSource.InvalidResponseCodeException? = null
                while (cause != null && httpError == null) {
                    httpError = cause as? HttpDataSource.InvalidResponseCodeException
                    cause = cause.cause
                }
                val redirectLocation = httpError?.headerFields?.entries
                    ?.firstOrNull { it.key?.equals("Location", ignoreCase = true) == true }
                    ?.value?.firstOrNull()
                val redirectScheme = redirectLocation?.let {
                    android.net.Uri.parse(it).scheme ?: "relative"
                }
                latestError = httpError?.let {
                    "HTTP ${it.responseCode}" + (redirectScheme?.let { scheme -> " → $scheme" } ?: "")
                } ?: error.errorCodeName
                updateDiagnostics()
                msg("Erro de reprodução: $latestError")
            }
        })
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                videoDecoder = decoderName; updateDiagnostics()
            }
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                audioDecoder = decoderName; updateDiagnostics()
            }
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                this@MainActivity.droppedFrames += droppedFrames; updateDiagnostics()
            }
        })
        val item = MediaItem.Builder().setUri(url).apply {
            if (movie.extension.equals("m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        val savedPosition = activeProfileId?.let { profile -> progressStore.get(profile, progressId) }
            ?.takeIf { !it.completed && it.positionMs > 10_000L }
            ?.positionMs ?: 0L
        exo.setMediaItem(item, savedPosition)
        exo.prepare()
        exo.playWhenReady = true
        uiHandler.removeCallbacks(refresh)
        uiHandler.post(refresh)
        uiHandler.removeCallbacks(saveProgressTick)
        uiHandler.postDelayed(saveProgressTick, 5_000L)
    }

    private fun updateDiagnostics() {
        val exo = player ?: return
        val info = diagnosticView ?: return
        val v = exo.videoFormat
        val a = exo.audioFormat
        val hdr = when {
            v?.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision (sinalização do stream)"
            v?.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 -> "PQ/ST2084 (HDR10 ou HDR10+; não distingue metadados dinâmicos)"
            v?.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
            v?.colorInfo == null -> "não informado"
            else -> "SDR / outra transferência"
        }
        val colorDepth = v?.colorInfo?.lumaBitdepth?.takeIf { it > 0 }?.toString() ?: "n/d"
        val state = when (exo.playbackState) {
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "REPRODUZINDO/PRONTO"
            Player.STATE_ENDED -> "FINALIZADO"
            else -> "INICIALIZANDO"
        }
        info.text = """
            STREAM / DECODIFICAÇÃO — NÃO É O SINAL HDMI DO DENON
            Estado: $state  |  Bufferings: $bufferingEvents
            Vídeo: ${v?.sampleMimeType ?: "n/d"} (${v?.codecs ?: "codec n/d"})
            Resolução: ${v?.width ?: 0} × ${v?.height ?: 0}  |  FPS: ${v?.frameRate ?: 0f}
            HDR: $hdr  |  Profundidade: $colorDepth bits
            Decoder vídeo: $videoDecoder
            Áudio: ${a?.sampleMimeType ?: "n/d"}, ${a?.channelCount ?: 0} canais, ${a?.sampleRate ?: 0} Hz
            Decoder áudio: $audioDecoder
            Quadros descartados: $droppedFrames  |  Erro: $latestError
            IMPORTANTE: confirme o formato recebido em Denon → INFO.
        """.trimIndent()
    }

    private fun saveCurrentProgress(syncRemote: Boolean, completed: Boolean = false) {
        val exo = player ?: return
        val movie = currentPlayingMovie ?: return
        val profile = activeProfileId ?: return
        val position = exo.currentPosition.coerceAtLeast(0)
        val rawDuration = exo.duration
        val duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0) 0L else rawDuration
        if (!completed && position < 10_000L) return
        val progressId = currentPlayingProgressId ?: movie.id.toString()
        val isCompleted = completed || progressStore.get(profile, progressId)?.completed == true ||
            (duration > 0L && position >= (duration - 45_000L).coerceAtLeast(0L))
        val progress = WatchProgress(
            mediaId = progressId,
            title = movie.title,
            posterUrl = movie.posterUrl,
            positionMs = if (isCompleted && duration > 0L) duration else position,
            durationMs = duration,
            updatedAt = System.currentTimeMillis(),
            completed = isCompleted,
            hiddenFromContinue = false
        )
        progressStore.save(profile, progress)
        if (syncRemote) worker.execute { syncProgressItem(profile, progress) }
    }

    private fun stopPlayer() {
        saveCurrentProgress(syncRemote = true)
        root.setBackgroundColor(Color.rgb(12, 17, 25))
        uiHandler.removeCallbacks(refresh)
        uiHandler.removeCallbacks(saveProgressTick)
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
        currentPlayingMovie = null
        currentPlayingProgressId = null
        diagnosticView = null
    diagnosticPanel = null
    playerActionDock = null
    diagnosticsVisible = false
    }

    private fun leavePlayer() {
        stopPlayer()
        val destination = returnFromPlayer
        returnFromPlayer = null
        (destination ?: { showHome() }).invoke()
    }

    private fun cancelFavoriteHold() {
        favoriteHoldRunnable?.let(uiHandler::removeCallbacks)
        favoriteHoldRunnable = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::root.isInitialized) return super.dispatchKeyEvent(event)
        val selectKey = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!selectKey) {
            if (event.action == KeyEvent.ACTION_DOWN) cancelFavoriteHold()
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP && favoriteHoldTriggered) {
            cancelFavoriteHold()
            favoriteHoldTriggered = false
            return true
        }
        val focus = root.findFocus()
        val tile = when {
            focus is GridView -> focus.getChildAt(focus.selectedItemPosition - focus.firstVisiblePosition)
            focus?.parent is GridView -> focus
            else -> null
        }
        val item = focusedFavoriteItem
        val section = activeCatalogSection
        if (tile != null && item != null && (section == "movie" || section == "series")) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                cancelFavoriteHold()
                favoriteHoldTriggered = false
                val heldFocus = focus
                val request = Runnable {
                    if (root.findFocus() === heldFocus && activeCatalogSection == section) {
                        favoriteHoldTriggered = true
                        val active = toggleFavorite(section, item.id, item.title, item.posterUrl)
                        leSilUi.setCatalogFavoriteMark(tile, item.id, item.title, active)
                        tile.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    }
                }
                favoriteHoldRunnable = request
                uiHandler.postDelayed(request, 3000)
            } else if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                cancelFavoriteHold()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Use onBackPressedDispatcher em uma versão futura")
    override fun onBackPressed() {
        if (player != null) {
            leavePlayer()
        } else {
            val action = backAction
            if (action != null) {
                backAction = null
                action()
            } else super.onBackPressed()
        }
    }

    override fun onStop() {
        cancelFavoriteHold()
        super.onStop()
        player?.pause()
        saveCurrentProgress(syncRemote = true)
    }
    override fun onDestroy() { stopPlayer(); worker.shutdown(); super.onDestroy() }

    private companion object {
        const val CONTINUE_CATEGORY_ID = -2
    }
}
