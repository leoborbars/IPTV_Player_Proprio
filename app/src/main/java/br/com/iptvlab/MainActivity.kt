package br.com.iptvlab

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
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
    private var client: XtreamClient? = null
    private var movieCategories: List<CatalogEntry> = emptyList()
    private var seriesCategories: List<CatalogEntry> = emptyList()
    private var allMovies: List<CatalogEntry> = emptyList()
    private var fourKCategory: CatalogEntry? = null
    private var fourKMovies: List<CatalogEntry> = emptyList()
    private var recentMovies: List<CatalogEntry> = emptyList()
    private var featuredMovie: CatalogEntry? = null
    private var featuredDetails: MovieDetails? = null
    private var featuredCategoryName: String? = null
    private var featuredSeries: List<SeriesEntry> = emptyList()
    private var homeLoading = false
    private var homeError: String? = null
    private var movies: List<CatalogEntry> = emptyList()
    private var categoryName = ""
    private var selectedMovieCategoryId: Int? = null
    private var currentMovieNav = "Filmes"
    private var backAction: (() -> Unit)? = null
    private var returnFromPlayer: (() -> Unit)? = null
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var diagnosticView: TextView? = null
    private var diagnosticsVisible = true
    private var videoDecoder = "aguardando"
    private var audioDecoder = "aguardando"
    private var droppedFrames = 0
    private var bufferingEvents = 0
    private var latestError = "nenhum"

    private val lunaUi by lazy {
        LunaHomeScreen(this, LunaHomeActions(
            onNavigate = ::navigate,
            onRetry = { showLogin() },
            onPlay = { movie -> playMovie(movie) { showHome() } },
            onMovieDetails = { movie -> openMovieDetails(movie) { showHome() } },
            onMovieCategory = { category -> selectMovieCategory(category, "Filmes 4K") },
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
        root.setBackgroundColor(Color.rgb(12, 17, 25))
        setContentView(root)
        showLogin()
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
    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(35), dp(25), dp(35), dp(25))
    }
    private fun replace(view: View) { root.removeAllViews(); root.addView(view) }
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
        stopPlayer()
        backAction = null
        val saved = credentialStore.load()
        val screen = column()
        screen.addView(label("LunaStream", 30f))
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
        screen.addView(button("Entrar no LunaStream") {
            val url = server.text.toString().trim()
            if (url.startsWith("http://")) msg("Aviso: HTTP sem criptografia transmite credenciais pela rede")
            val credentials = SavedCredentials(url, user.text.toString().trim(), pass.text.toString())
            val shouldRemember = remember.isChecked
            val c = try { XtreamClient(credentials.server, credentials.username, credentials.password) }
                catch (e: IllegalArgumentException) { msg(e.message ?: "Dados inválidos"); return@button }
            status.text = "Conectando..."
            movieCategories = emptyList()
            seriesCategories = emptyList()
            allMovies = emptyList()
            fourKCategory = null
            fourKMovies = emptyList()
            recentMovies = emptyList()
            featuredMovie = null
            featuredDetails = null
            featuredCategoryName = null
            featuredSeries = emptyList()
            client = c
            homeLoading = true
            homeError = null
            replace(lunaUi.home(homeData()))
            runNetwork({
                c.authenticate()
                runCatching {
                    if (shouldRemember) credentialStore.save(credentials) else credentialStore.clear()
                }.onFailure {
                    runOnUiThread { msg("Não foi possível guardar os dados neste aparelho") }
                }
                val loadedCategories = c.movieCategories()
                val loadedMovies = c.movies()
                val loadedFourKCategory = loadedCategories.firstOrNull { isFourKCategory(it.title) }
                val loadedFourKMovies = loadedFourKCategory?.let { category ->
                    loadedMovies.filter { it.categoryId == category.id }.ifEmpty {
                        runCatching { c.movies(category.id) }.getOrDefault(emptyList())
                    }
                }.orEmpty()
                val loadedSeriesCategories = runCatching { c.seriesCategories() }.getOrDefault(emptyList())
                val loadedSeries = runCatching { c.series() }.getOrDefault(emptyList())
                val loadedRecent = sortByProviderDate(loadedMovies)
                val loadedFeatured = loadedRecent.firstOrNull() ?: loadedMovies.firstOrNull()
                val loadedDetails = loadedFeatured?.let { featured -> runCatching { c.movieDetails(featured.id) }.getOrNull() }
                val loadedFeaturedCategory = loadedFeatured?.categoryId?.let { id -> loadedCategories.firstOrNull { it.id == id }?.title }
                runOnUiThread {
                    movieCategories = loadedCategories
                    seriesCategories = loadedSeriesCategories
                    allMovies = loadedMovies
                    fourKCategory = loadedFourKCategory
                    fourKMovies = loadedFourKMovies
                    recentMovies = loadedRecent
                    featuredMovie = loadedFeatured
                    featuredDetails = loadedDetails
                    featuredCategoryName = loadedFeaturedCategory
                    featuredSeries = loadedSeries
                    homeLoading = false
                    homeError = null
                    showHome()
                }
            }, {
                homeLoading = false
                homeError = "Não foi possível carregar o catálogo. Confira o servidor e tente novamente."
                showHome()
            })
        })
        val scroll = ScrollView(this).apply { addView(screen) }
        replace(scroll)
    }

    private fun homeData() = LunaHomeData(
        movieCategories = movieCategories,
        featuredMovie = featuredMovie,
        featuredDetails = featuredDetails,
        featuredCategory = featuredCategoryName,
        fourKCategory = fourKCategory,
        fourKMovies = fourKMovies,
        recentMovies = recentMovies,
        featuredSeries = featuredSeries,
        loading = homeLoading,
        errorMessage = homeError
    )

    private fun showHome() {
        backAction = null
        replace(lunaUi.home(homeData()))
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
                    replace(lunaUi.messageScreen("Filmes 4K", "Filmes 4K", "O catálogo não informou uma categoria chamada Filmes 4K ou Filmes UHD.", "Voltar ao início") { showHome() })
                } else {
                    selectMovieCategory(category, "Filmes 4K")
                }
            }
            "Continuar" -> {
                backAction = { showHome() }
                replace(lunaUi.messageScreen("Continuar", "Continuar assistindo", "Ainda não há progresso salvo neste dispositivo.", "Voltar ao início") { showHome() })
            }
            "Favoritos" -> {
                backAction = { showHome() }
                replace(lunaUi.messageScreen("Favoritos", "Favoritos", "Ainda não há favoritos salvos neste dispositivo.", "Voltar ao início") { showHome() })
            }
            "Configurações" -> {
                backAction = { showHome() }
                replace(lunaUi.messageScreen(
                    "Configurações",
                    "Configurações",
                    "Edite o endereço ou os dados de conexão Xtream.",
                    "Editar conexão"
                ) { showLogin() })
            }
        }
    }

    private fun showMovieCategories() {
        backAction = { showHome() }
        replace(lunaUi.categoryPicker("Filmes · Categorias", "Filmes", movieCategories) { category ->
            selectMovieCategory(category, "Filmes")
        })
    }

    private fun showSeriesCategories() {
        backAction = { showHome() }
        replace(lunaUi.categoryPicker("Séries · Categorias", "Séries", seriesCategories) { category ->
            selectSeriesCategory(category)
        })
    }

    private fun selectMovieCategory(category: CatalogEntry, nav: String) {
        currentMovieNav = nav
        selectedMovieCategoryId = category.id
        categoryName = category.title
        val cached = allMovies.filter { it.categoryId == category.id }
        if (cached.isNotEmpty()) {
            movies = cached.sortedBy { it.title.lowercase() }
            showMovies()
            return
        }
        backAction = { if (nav == "Filmes 4K") showHome() else showMovieCategories() }
        replace(lunaUi.messageScreen(nav, category.title, "Carregando filmes do catálogo…", "Voltar") {
            if (nav == "Filmes 4K") showHome() else showMovieCategories()
        })
        val c = client ?: return
        runNetwork({
            val result = c.movies(category.id)
            runOnUiThread {
                movies = result.sortedBy { it.title.lowercase() }
                showMovies()
            }
        }, {
            runOnUiThread {
                backAction = { if (nav == "Filmes 4K") showHome() else showMovieCategories() }
                replace(lunaUi.messageScreen(nav, category.title, "Não foi possível carregar esta categoria. Tente novamente.", "Voltar") {
                    if (nav == "Filmes 4K") showHome() else showMovieCategories()
                })
            }
        })
    }

    private fun showMovies() {
        backAction = { if (currentMovieNav == "Filmes 4K") showHome() else showMovieCategories() }
        replace(lunaUi.movieCatalog(
            title = "$categoryName  ·  ${movies.size} títulos",
            selectedNav = currentMovieNav,
            categories = movieCategories,
            selectedCategoryId = selectedMovieCategoryId,
            movies = movies,
            onCategory = { selectMovieCategory(it, currentMovieNav) },
            onMovie = { movie -> openMovieDetails(movie) { showMovies() } }
        ))
    }

    private fun selectSeriesCategory(category: CatalogEntry) {
        val cached = featuredSeries.filter { it.categoryId == category.id }
        if (cached.isNotEmpty()) {
            showSeries(category, cached)
            return
        }
        backAction = { showSeriesCategories() }
        replace(lunaUi.messageScreen("Séries", category.title, "Carregando séries do catálogo…", "Voltar às categorias") { showSeriesCategories() })
        val c = client ?: return
        runNetwork({
            val result = c.series(category.id)
            runOnUiThread { showSeries(category, result) }
        }, {
            runOnUiThread {
                backAction = { showSeriesCategories() }
                replace(lunaUi.messageScreen("Séries", category.title, "Não foi possível carregar esta categoria.", "Voltar às categorias") { showSeriesCategories() })
            }
        })
    }

    private fun showSeries(category: CatalogEntry? = null, items: List<SeriesEntry> = featuredSeries) {
        val title = category?.let { "${it.title}  ·  ${items.size} séries" } ?: "Séries"
        backAction = { showSeriesCategories() }
        replace(lunaUi.seriesCatalog(
            title = title,
            selectedNav = "Séries",
            categories = seriesCategories,
            selectedCategoryId = category?.id,
            series = items,
            onCategory = ::selectSeriesCategory,
            onSeries = ::openSeriesDetails
        ))
    }

    private fun openMovieDetails(movie: CatalogEntry, returnTo: () -> Unit) {
        val details = if (featuredMovie?.id == movie.id) featuredDetails else null
        val category = movie.categoryId?.let { id -> movieCategories.firstOrNull { it.id == id }?.title }
        val description = buildList {
            category?.let { add("Categoria: $it") }
            movie.extension.takeIf { it.isNotBlank() }?.let { add("Contêiner informado: ${it.uppercase()}") }
            details?.year?.let { add("Ano: $it") }
            details?.duration?.let { add("Duração: $it") }
            details?.genre?.let { add("Gênero: $it") }
            details?.plot?.let { add(it) }
        }.joinToString("\n\n").ifBlank { "O catálogo não informou outros detalhes para este título." }
        AlertDialog.Builder(this)
            .setTitle(details?.title?.takeIf { it.isNotBlank() } ?: movie.title)
            .setMessage(description)
            .setNegativeButton("Fechar", null)
            .setPositiveButton("Assistir") { _, _ -> playMovie(movie, returnTo) }
            .show()
    }

    private fun openSeriesDetails(series: SeriesEntry) {
        AlertDialog.Builder(this)
            .setTitle(series.title)
            .setMessage("Esta série veio do catálogo Xtream. A navegação por temporadas e episódios será adicionada em um próximo marco.")
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun sortByProviderDate(entries: List<CatalogEntry>): List<CatalogEntry> = entries.withIndex()
        .sortedWith(compareBy<IndexedValue<CatalogEntry>> { it.value.addedAt == null }
            .thenByDescending { it.value.addedAt ?: 0L }
            .thenBy { it.index })
        .map { it.value }

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
        stopPlayer()
        returnFromPlayer = returnTo
        videoDecoder = "aguardando"; audioDecoder = "aguardando"
        droppedFrames = 0; bufferingEvents = 0; latestError = "nenhum"
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        // PlayerView usa SurfaceView por padrão, preferida para preservar HDR no Android TV.
        val pv = PlayerView(this).apply {
            player = exo
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        playerView = pv
        root.removeAllViews()
        root.addView(pv, FrameLayout.LayoutParams(-1, -1))
        val info = label("Carregando dados técnicos...", 16f).apply {
            setBackgroundColor(Color.argb(215, 5, 12, 20))
            setPadding(dp(15), dp(12), dp(15), dp(12))
        }
        diagnosticView = info
        root.addView(info, FrameLayout.LayoutParams(dp(580), -2, Gravity.TOP or Gravity.START))
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(225, 5, 12, 20))
        }
        dock.addView(button("Diagnóstico") { diagnosticsVisible = !diagnosticsVisible; info.visibility = if (diagnosticsVisible) View.VISIBLE else View.GONE })
        dock.addView(button("Áudio") { TrackSelectionDialogBuilder(this, "Faixas de áudio", exo, C.TRACK_TYPE_AUDIO).build().show() })
        dock.addView(button("Legendas") { TrackSelectionDialogBuilder(this, "Legendas", exo, C.TRACK_TYPE_TEXT).setShowDisableOption(true).build().show() })
        dock.addView(button("Voltar") { leavePlayer() })
        root.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        exo.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { updateDiagnostics() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) bufferingEvents++
                updateDiagnostics()
            }
            override fun onPlayerError(error: PlaybackException) {
                latestError = error.errorCodeName
                updateDiagnostics()
                msg("Erro de reprodução: ${error.errorCodeName}")
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
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
        uiHandler.removeCallbacks(refresh)
        uiHandler.post(refresh)
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

    private fun stopPlayer() {
        uiHandler.removeCallbacks(refresh)
        playerView?.player = null
        playerView = null
        player?.release()
        player = null
        diagnosticView = null
    }

    private fun leavePlayer() {
        stopPlayer()
        val destination = returnFromPlayer
        returnFromPlayer = null
        (destination ?: { showHome() }).invoke()
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

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { stopPlayer(); worker.shutdownNow(); super.onDestroy() }
}
