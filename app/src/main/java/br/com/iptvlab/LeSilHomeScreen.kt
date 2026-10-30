package br.com.iptvlab

import android.content.Context
import android.app.AlertDialog
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.InputType
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

enum class MovieSortOrder(val label: String, val formatPattern: Regex? = null) {
    TITLE_ASC("Título: A–Z"),
    TITLE_DESC("Título: Z–A"),
    NEWEST("Mais recentes"),
    OLDEST("Mais antigos"),
    HDR_FIRST("HDR primeiro", Regex("""\b(?:HDR(?:10(?:\+|PLUS)?)?|HLG|DOLBY[\s_-]*VISION|DV|DVHE|DVH1|DOVI)(?=$|[^A-Z0-9])""", RegexOption.IGNORE_CASE)),
    HDR10_PLUS_FIRST("HDR10+ primeiro", Regex("""\bHDR[\s_-]*10(?:\+|PLUS)""", RegexOption.IGNORE_CASE)),
    DOLBY_VISION_FIRST("Dolby Vision primeiro", Regex("""\b(?:DOLBY[\s_-]*VISION|DV|DVHE|DVH1|DOVI)\b""", RegexOption.IGNORE_CASE)),
    HEVC_FIRST("HEVC / H.265 primeiro", Regex("""\b(?:HEVC|H[.\s_-]?265|X265)\b""", RegexOption.IGNORE_CASE)),
    AV1_FIRST("AV1 primeiro", Regex("""\bAV1\b""", RegexOption.IGNORE_CASE)),
    H264_FIRST("H.264 / AVC primeiro", Regex("""\b(?:H[.\s_-]?264|X264|AVC)\b""", RegexOption.IGNORE_CASE)),
    VP9_FIRST("VP9 primeiro", Regex("""\bVP9\b""", RegexOption.IGNORE_CASE)),
    ATMOS_FIRST("Dolby Atmos primeiro", Regex("""\b(?:DOLBY[\s_-]*ATMOS|ATMOS)\b""", RegexOption.IGNORE_CASE)),
    DTS_FIRST("DTS primeiro", Regex("""\bDTS(?:[\s_-]*(?:HD|X))?\b""", RegexOption.IGNORE_CASE)),
    DOLBY_DIGITAL_FIRST("Dolby Digital primeiro", Regex("""\b(?:DOLBY[\s_-]*DIGITAL(?:[\s_-]*PLUS)?|E[\s_-]?AC[\s_-]?3|AC[\s_-]?3|DDP)\b""", RegexOption.IGNORE_CASE)),
    TRUEHD_FIRST("TrueHD primeiro", Regex("""\bTRUE[\s_-]?HD\b""", RegexOption.IGNORE_CASE)),
    AAC_FIRST("AAC primeiro", Regex("""\bAAC\b""", RegexOption.IGNORE_CASE)),
    FLAC_FIRST("FLAC primeiro", Regex("""\bFLAC\b""", RegexOption.IGNORE_CASE))
}

data class LeSilHomeData(
    val movieCategories: List<CatalogEntry> = emptyList(),
    val featuredMovie: CatalogEntry? = null,
    val featuredDetails: MovieDetails? = null,
    val featuredCategory: String? = null,
    val fourKCategory: CatalogEntry? = null,
    val fourKMovies: List<CatalogEntry> = emptyList(),
    val recentMovies: List<CatalogEntry> = emptyList(),
    val continueWatching: List<CatalogEntry> = emptyList(),
    val featuredSeries: List<SeriesEntry> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
    val errorActionLabel: String = "Tentar novamente"
)

data class LeSilHomeActions(
    val onNavigate: (String) -> Unit,
    val onRetry: () -> Unit,
    val onPlay: (CatalogEntry) -> Unit,
    val onMovieDetails: (CatalogEntry) -> Unit,
    val onMovieCategory: (CatalogEntry) -> Unit,
    val onMovieFocus: (CatalogEntry, (MovieDetails?) -> Unit) -> Unit,
    val onSeriesCategory: (CatalogEntry) -> Unit,
    val onSeriesDetails: (SeriesEntry) -> Unit
)

/** Native Views home shell. It deliberately leaves the existing Media3 player Activity intact. */
class LeSilHomeScreen(private val context: Context, private val actions: LeSilHomeActions) {
    private val density = context.resources.displayMetrics.density
    private val screenWidthDp = context.resources.configuration.screenWidthDp
    private val screenHeightDp = context.resources.configuration.screenHeightDp
    private val shortScreen = screenHeightDp < 500
    private val sidebarWidth = dp(
        if (shortScreen) (screenWidthDp * 0.19f).toInt().coerceIn(170, 190)
        else (screenWidthDp * 0.18f).toInt().coerceIn(190, 250)
    )
    private val categoryPaneWidth = dp((screenWidthDp * 0.23f).toInt().coerceIn(205, 270))

    fun home(data: LeSilHomeData): View {
        val content = verticalScroll()
        content.addView(hero(data))

        if (data.loading) {
            content.addView(stateLabel("Carregando catálogo do provedor…"))
        } else if (data.errorMessage != null) {
            content.addView(statePanel(data.errorMessage, data.errorActionLabel, actions.onRetry))
        } else {
            if (data.continueWatching.isNotEmpty()) {
                content.addView(movieRow("Continuar assistindo", data.continueWatching) { actions.onPlay(it) })
            }
            val fourK = data.fourKCategory
            if (fourK != null) {
                content.addView(movieRow("Filmes 4K", data.fourKMovies) { actions.onMovieDetails(it) })
            } else {
                content.addView(infoSection("Filmes 4K", "Categoria 4K não identificada pelo nome no catálogo."))
            }
            content.addView(movieRow("Adicionados recentemente", data.recentMovies) { actions.onMovieDetails(it) })
            content.addView(seriesRow(data.featuredSeries))
        }
        return shell("Início", content)
    }

    fun catalogLoadingScreen(): View {
        val root = FrameLayout(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF08111F.toInt(), 0xFF092A5A.toInt(), 0xFF050B18.toInt()))
        }
        val center = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }
        center.addView(ImageView(context).apply {
            setImageResource(R.drawable.lesil_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Le&Sil Stream"
        }, LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(20) })
        center.addView(TextView(context).apply {
            text = "Atualizando conteúdo"
            textSize = 29f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2))
        center.addView(TextView(context).apply {
            text = "Buscando filmes e séries no servidor…"
            textSize = 16f
            setTextColor(0xFFBDD5EC.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(9) })
        center.addView(ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(CYAN)
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { topMargin = dp(24) })
        root.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        return root
    }

    fun categoryPicker(title: String, selectedNav: String, categories: List<CatalogEntry>, onCategory: (CatalogEntry) -> Unit): View {
        val content = verticalScroll()
        content.addView(pageTitle(title))
        if (categories.isEmpty()) {
            content.addView(stateLabel("Nenhuma categoria foi informada pelo catálogo."))
        } else {
            categories.forEach { category ->
                val item = actionButton("›   ${category.title}", false) { onCategory(category) }
                item.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                content.addView(item, LinearLayout.LayoutParams(-1, dp(60)).apply {
                    bottomMargin = dp(8)
                    marginEnd = dp(22)
                })
            }
        }
        return shell(selectedNav, content)
    }

    fun movieCatalog(
        title: String,
        selectedNav: String,
        categories: List<CatalogEntry>,
        selectedCategoryId: Int?,
        movies: List<CatalogEntry>,
        initialDetails: MovieDetails?,
        initialDetailsMovieId: Int?,
        onCategory: (CatalogEntry) -> Unit,
        onMovie: (CatalogEntry) -> Unit,
        onMovieFocus: (CatalogEntry, (MovieDetails?) -> Unit) -> Unit,
        favoriteIds: Set<Int> = emptySet(),
        onToggleFavorite: ((CatalogEntry) -> Boolean)? = null,
        sortOrder: MovieSortOrder = MovieSortOrder.TITLE_ASC,
        onSort: ((MovieSortOrder) -> Unit)? = null
    ): View {
        val seriesMode = selectedNav == "Séries"
        val categoryPanel = categoryColumn("CATEGORIAS", categories, selectedCategoryId, onCategory)
        val content = FrameLayout(context).apply {
            background = catalogSurface()
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipChildren = false
        }
        val hero = movieCatalogHero(seriesMode)
        content.addView(hero.root, FrameLayout.LayoutParams(-1, -1))

        val filterButton = catalogIconButton(android.R.drawable.ic_menu_sort_by_size,
            if (seriesMode) "Ordenar séries" else "Ordenar filmes") {
            val options = if (seriesMode) MovieSortOrder.entries.take(4) else MovieSortOrder.entries
            AlertDialog.Builder(context)
                .setTitle(if (seriesMode) "Ordenar séries" else "Ordenar filmes · rótulos do servidor")
                .setSingleChoiceItems(options.map { it.label }.toTypedArray(), options.indexOf(sortOrder)) { dialog, index ->
                    dialog.dismiss()
                    onSort?.invoke(options[index])
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        var activeFilter = ""
        val searchButton = catalogIconButton(android.R.drawable.ic_menu_search,
            if (seriesMode) "Buscar série" else "Buscar filme") { }
        filterButton.visibility = if (onSort == null) View.GONE else View.VISIBLE
        val catalogHorizontalInsetDp = 20
        val rightmostPosterInsetDp = (
            screenWidthDp - COLLAPSED_SIDEBAR_WIDTH_DP - catalogHorizontalInsetDp -
                (GRID_COLUMNS - 1) * (CATALOG_POSTER_WIDTH_DP + CATALOG_POSTER_GAP_DP) -
                CATALOG_POSTER_WIDTH_DP + 3
        ).coerceAtLeast(0)
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(searchButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(4) })
            addView(filterButton, LinearLayout.LayoutParams(dp(38), dp(38)))
        }
        content.addView(controls, FrameLayout.LayoutParams(-2, dp(46), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(14)
            rightMargin = dp(rightmostPosterInsetDp)
        })

        val catalogHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sectionTitle(title).apply {
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -1, 1f))
            addView(hero.position, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(10)
                marginEnd = dp((rightmostPosterInsetDp - catalogHorizontalInsetDp).coerceAtLeast(0))
            })
        }
        var visibleMovies = movies
        val currentFavorites = favoriteIds.toMutableSet()
        val grid = object : GridView(context) {
            private var hold: Runnable? = null
            private var downX = 0f
            private var downY = 0f
            private var longFired = false

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        hold?.let(::removeCallbacks)
                        longFired = false
                        downX = event.x; downY = event.y
                        val position = pointToPosition(event.x.toInt(), event.y.toInt())
                        val target = visibleMovies.getOrNull(position)
                        if (target != null && onToggleFavorite != null) {
                            val request = Runnable {
                                if (isAttachedToWindow) {
                                    longFired = true
                                    val active = onToggleFavorite(target)
                                    if (active) currentFavorites.add(target.id) else currentFavorites.remove(target.id)
                                    val tile = getChildAt(position - firstVisiblePosition) as? ViewGroup
                                    (tile?.getChildAt(1) as? TextView)?.text = favoriteCaption(target.title, active)
                                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                }
                            }
                            hold = request
                            postDelayed(request, 3000)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val slop = ViewConfiguration.get(context).scaledTouchSlop
                        if (kotlin.math.abs(event.x - downX) > slop ||
                            kotlin.math.abs(event.y - downY) > slop) {
                            hold?.let(::removeCallbacks)
                            hold = null
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        hold?.let(::removeCallbacks)
                        hold = null
                        if (longFired) {
                            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                            longFired = false
                            return true
                        }
                    }
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            numColumns = GRID_COLUMNS
            columnWidth = dp(CATALOG_POSTER_WIDTH_DP)
            horizontalSpacing = dp(CATALOG_POSTER_GAP_DP)
            verticalSpacing = dp(8)
            stretchMode = GridView.NO_STRETCH
            gravity = Gravity.START
            isVerticalScrollBarEnabled = false
            isSmoothScrollbarEnabled = true
            isFocusable = true
            isFocusableInTouchMode = false
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            cacheColorHint = Color.TRANSPARENT
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setPadding(0, dp(CATALOG_FOCUS_HEADROOM_DP), 0, 0)
            clipToPadding = false
        }
        val catalogBottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalInset = dp(catalogHorizontalInsetDp)
            setPadding(horizontalInset, 0, horizontalInset, dp(8))
            clipChildren = true
            addView(catalogHeader, LinearLayout.LayoutParams(-1, dp(44)))
            addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val catalogHeight = dp((context.resources.configuration.screenHeightDp * 0.53f).toInt().coerceAtLeast(220) + 8)
        content.addView(catalogBottom, FrameLayout.LayoutParams(-1, catalogHeight, Gravity.BOTTOM))
        val focusAnchor = View(context).apply {
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.addView(focusAnchor, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.START))
        var activeMovieId: Int? = null
        var activeMovieDetails: MovieDetails? = null
        var focusedMoviePosition = 0
        var visibleMovieCount = movies.size
        var focusRequestGeneration = 0
        var posterRefreshGeneration = 0
        var deferMovieChildFocusUntil = 0L
        fun showMovie(movie: CatalogEntry, details: MovieDetails?, position: Int, total: Int) {
            activeMovieId = movie.id
            activeMovieDetails = details
            renderMovieCatalogHero(hero, movie, details, position, total, categories)
        }
        fun refreshVisiblePosters() {
            val first = grid.firstVisiblePosition
            for (index in 0 until grid.childCount) {
                val movie = visibleMovies.getOrNull(first + index) ?: continue
                val poster = (grid.getChildAt(index) as? ViewGroup)?.getChildAt(0) as? ImageView ?: continue
                if (poster.drawable == null && !movie.posterUrl.isNullOrBlank()) {
                    LeSilArtworkLoader.into(movie.posterUrl, poster, 420)
                }
            }
        }
        fun focusMovie(movie: CatalogEntry, position: Int, total: Int) {
            focusRequestGeneration++
            focusedMoviePosition = position - 1
            val refreshRequest = ++posterRefreshGeneration
            grid.postDelayed({ if (refreshRequest == posterRefreshGeneration) refreshVisiblePosters() }, 700)
            grid.postDelayed({ if (refreshRequest == posterRefreshGeneration) refreshVisiblePosters() }, 2500)
            if (activeMovieId != movie.id) showMovie(movie, null, position, total)
            else renderMovieCatalogHero(hero, movie, activeMovieDetails, position, total, categories)
            onMovieFocus(movie) { details ->
                if (activeMovieId == movie.id) {
                    activeMovieDetails = details
                    renderMovieCatalogHero(hero, movie, details, position, total, categories)
                }
            }
        }
        fun requestMovieFocus(position: Int, attempt: Int = 0, requestId: Int = 0): Boolean {
            if (visibleMovieCount == 0) return searchButton.requestFocus()
            val target = position.coerceIn(0, visibleMovieCount - 1)
            if (attempt == 0 && (focusedMoviePosition != target || activeMovieId != visibleMovies[target].id)) {
                focusMovie(visibleMovies[target], target + 1, visibleMovieCount)
            }
            val activeRequest = if (attempt == 0) ++focusRequestGeneration else requestId
            if (attempt > 0 && (activeRequest != focusRequestGeneration ||
                        !(focusAnchor.hasFocus() || grid.hasFocus()))) return false
            focusedMoviePosition = target
            // The list holds focus while a target row is being attached. Mark its
            // left edge too, so LEFT still opens Categories during that interval.
            val edgeTag = if (target % GRID_COLUMNS == 0) CATALOG_LEFT_EDGE_TAG else null
            grid.tag = edgeTag
            focusAnchor.tag = edgeTag
            val waitForPanels = deferMovieChildFocusUntil - SystemClock.uptimeMillis()
            if (attempt == 0 && waitForPanels > 0) {
                grid.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                focusAnchor.isFocusableInTouchMode = true
                if (!focusAnchor.requestFocus()) {
                    grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    return false
                }
                grid.setSelection(target)
                grid.postDelayed({
                    if (activeRequest == focusRequestGeneration && focusAnchor.hasFocus()) requestMovieFocus(target)
                }, waitForPanels + 16)
                return true
            }
            val child = grid.getChildAt(target - grid.firstVisiblePosition)
            val fullyVisible = child?.isShown == true && child.top >= grid.paddingTop &&
                child.bottom <= grid.height - grid.paddingBottom
            if (attempt == 0 && fullyVisible && child?.requestFocus() == true) return true
            if (attempt == 0) {
                // This stable sibling stays focused while GridView detaches and
                // reattaches poster views for the next row.
                grid.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                focusAnchor.isFocusableInTouchMode = true
                if (!focusAnchor.requestFocus()) {
                    grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    return false
                }
                grid.setSelectionFromTop(target / GRID_COLUMNS * GRID_COLUMNS, grid.paddingTop)
            } else if (fullyVisible) {
                grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                if (child?.requestFocus() == true) return true
                grid.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                focusAnchor.requestFocus()
            }
            if (attempt < 20) {
                grid.postDelayed({ requestMovieFocus(target, attempt + 1, activeRequest) }, 50)
            } else {
                grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
            return focusAnchor.hasFocus() || grid.hasFocus()
        }
        fun navigateMovie(position: Int, keyCode: Int, parentHasFocus: Boolean): Boolean {
            val column = position % GRID_COLUMNS
            val target = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (column == 0) return parentHasFocus
                    position - 1
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (position + 1 >= visibleMovieCount) return true
                    position + 1
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (position < GRID_COLUMNS) {
                        val control = if (column == GRID_COLUMNS - 1 && onSort != null) filterButton else searchButton
                        if (control.requestFocus()) {
                            // A pending GridView retry must not pull focus back from the toolbar.
                            focusRequestGeneration++
                            deferMovieChildFocusUntil = 0L
                            grid.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                            focusAnchor.isFocusableInTouchMode = false
                        }
                        return true
                    }
                    position - GRID_COLUMNS
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val next = position + GRID_COLUMNS
                    if ((position / GRID_COLUMNS + 1) * GRID_COLUMNS >= visibleMovieCount) return true
                    next.coerceAtMost(visibleMovieCount - 1)
                }
                else -> return false
            }
            requestMovieFocus(target)
            return true
        }
        grid.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) navigateMovie(focusedMoviePosition, keyCode, parentHasFocus = true)
            else false
        }
        focusAnchor.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) navigateMovie(focusedMoviePosition, keyCode, parentHasFocus = true)
            else false
        }
        fun populate(filter: String) {
            val total = movies.count { it.title.contains(filter, ignoreCase = true) }
            val found = movies.asSequence()
                .filter { it.title.contains(filter, ignoreCase = true) }
                .toList()
            visibleMovieCount = found.size
            visibleMovies = found
            focusedMoviePosition = 0
            focusRequestGeneration++
            grid.tag = CATALOG_LEFT_EDGE_TAG
            focusAnchor.tag = CATALOG_LEFT_EDGE_TAG
            if (found.isEmpty()) {
                activeMovieId = null
                activeMovieDetails = null
                hero.position.text = "0 / 0"
                hero.title.text = if (movies.isEmpty()) {
                    if (seriesMode) "Nenhuma série nesta categoria" else "Nenhum filme nesta categoria"
                } else "Nenhum resultado"
                hero.metadata.text = ""
                hero.cast.text = ""
                hero.director.text = ""
                hero.plot.text = if (movies.isEmpty()) "Esta categoria não tem títulos disponíveis." else "Tente outro termo de busca."
                hero.displayedArtworkUrl = null
                LeSilArtworkLoader.into(null, hero.backdrop, 1200)
                grid.adapter = null
                return
            }
            showMovie(found.first(), initialDetails?.takeIf { found.first().id == initialDetailsMovieId }, 1, total)
            onMovieFocus(found.first()) { details ->
                if (activeMovieId == found.first().id) {
                    activeMovieDetails = details
                    renderMovieCatalogHero(hero, found.first(), details, 1, total, categories)
                }
            }
            grid.adapter = object : android.widget.BaseAdapter() {
                override fun getCount(): Int = found.size
                override fun getItem(position: Int): CatalogEntry = found[position]
                override fun getItemId(position: Int): Long = getItem(position).id.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val movie = getItem(position)
                    val tile = (convertView as? LinearLayout) ?: catalogTile(
                        title = movie.title,
                        imageUrl = movie.posterUrl,
                        rating = movie.rating,
                        onClick = { onMovie(movie) },
                        onFocus = { focused -> if (focused) focusMovie(movie, position + 1, total) }
                    ) as LinearLayout
                    bindCatalogTile(tile, movie, position + 1, total, onMovie, ::focusMovie,
                        movie.id in currentFavorites)
                    return tile.apply {
                        setTag(R.id.catalog_favorite_set, currentFavorites)
                        id = View.generateViewId()
                        // AbsListView requires its own LayoutParams subclass. A plain
                        // ViewGroup.LayoutParams crashes GridView when it fills rows
                        // during vertical scrolling (TCL: ClassCastException).
                        layoutParams = AbsListView.LayoutParams(dp(CATALOG_POSTER_WIDTH_DP), dp(CATALOG_TILE_HEIGHT_DP))
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                            navigateMovie(position, keyCode, parentHasFocus = false)
                        }
                        // GridView recycles each poster view. Clear the marker and
                        // explicit category target on every bind, otherwise a view
                        // reused from column 0 can send DPAD_LEFT to Categories.
                        if (position % GRID_COLUMNS == 0) {
                            tag = CATALOG_LEFT_EDGE_TAG
                            nextFocusLeftId = categoryPanel.selected?.id ?: View.NO_ID
                        } else {
                            tag = null
                            nextFocusLeftId = View.NO_ID
                        }
                    }
                }
            }
            grid.invalidateViews()
        }
        searchButton.setOnClickListener {
            lateinit var dialog: AlertDialog
            val input = EditText(context).apply {
                hint = if (seriesMode) "Digite o título da série" else "Digite o título do filme"
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or
                    android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                    android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
                setText(activeFilter)
                setSelection(text.length)
                setTextColor(WHITE)
                setHintTextColor(SECONDARY)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = rounded(0xFF0E367B.toInt(), 0xFF72B8FF.toInt(), 10)
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                        true
                    } else false
                }
            }
            dialog = AlertDialog.Builder(context)
                .setTitle("Buscar no catálogo")
                .setView(input)
                .setPositiveButton("Buscar") { _, _ ->
                    activeFilter = input.text.toString().trim()
                    populate(activeFilter)
                    grid.setSelection(0)
                }
                .setNeutralButton("Limpar") { _, _ ->
                    activeFilter = ""
                    populate("")
                    grid.setSelection(0)
                }
                .setNegativeButton("Cancelar", null)
                .create()
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            dialog.setOnShowListener {
                input.requestFocus()
                input.post {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                        ?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }
            dialog.show()
        }
        searchButton.id = View.generateViewId()
        filterButton.id = View.generateViewId()
        searchButton.nextFocusRightId = filterButton.id
        filterButton.nextFocusLeftId = searchButton.id
        listOf(searchButton, filterButton).forEach { button ->
            button.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> requestMovieFocus(focusedMoviePosition.coerceAtMost(GRID_COLUMNS - 1))
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (button === filterButton) searchButton.requestFocus() else false
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (button === searchButton && onSort != null) filterButton.requestFocus() else true
                    else -> false
                }
            }
        }
        populate("")
        return catalogShell(
            selectedNav, categoryPanel.view, categoryPanel.selected, content,
            onCategoriesRight = {
                deferMovieChildFocusUntil = SystemClock.uptimeMillis() + 210L
                requestMovieFocus(focusedMoviePosition)
            },
            onContentLeftEdge = {
                val focused = content.findFocus()
                focused !== searchButton && focused !== filterButton && focusedMoviePosition % GRID_COLUMNS == 0
            },
            onContentRefocus = { requestMovieFocus(focusedMoviePosition) }
        )
    }

    fun seriesCatalog(
        title: String,
        selectedNav: String,
        categories: List<CatalogEntry>,
        selectedCategoryId: Int?,
        series: List<SeriesEntry>,
        onCategory: (CatalogEntry) -> Unit,
        onSeries: (SeriesEntry) -> Unit,
        onSeriesFocus: (SeriesEntry, (MovieDetails?) -> Unit) -> Unit,
        favoriteIds: Set<Int> = emptySet(),
        onToggleFavorite: ((SeriesEntry) -> Boolean)? = null,
        initialDetails: MovieDetails? = null,
        sortOrder: MovieSortOrder = MovieSortOrder.TITLE_ASC,
        onSort: ((MovieSortOrder) -> Unit)? = null
    ): View {
        val byId = series.associateBy { it.id }
        val posters = series.map { item ->
            CatalogEntry(item.id, item.title, posterUrl = item.coverUrl,
                categoryId = item.categoryId, addedAt = item.addedAt, rating = item.rating, plot = item.plot)
        }
        return movieCatalog(
            title = title,
            selectedNav = selectedNav,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            movies = posters,
            initialDetails = initialDetails,
            initialDetailsMovieId = series.firstOrNull()?.id,
            onCategory = onCategory,
            onMovie = { entry -> byId[entry.id]?.let(onSeries) },
            onMovieFocus = { entry, deliver ->
                byId[entry.id]?.let { onSeriesFocus(it, deliver) } ?: deliver(null)
            },
            favoriteIds = favoriteIds,
            onToggleFavorite = onToggleFavorite?.let { toggle ->
                { entry -> byId[entry.id]?.let(toggle) ?: false }
            },
            sortOrder = sortOrder,
            onSort = onSort
        )
    }

    fun seriesSeasons(
        series: SeriesEntry,
        details: SeriesDetails,
        episodeProgress: Map<Int, WatchProgress>,
        onEpisode: (EpisodeEntry) -> Unit,
        onBack: () -> Unit
    ): View {
        val root = FrameLayout(context).apply { setBackgroundColor(0xFF14171B.toInt()) }
        val backdrop = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        LeSilArtworkLoader.into(details.backdropUrl ?: details.coverUrl ?: series.coverUrl, backdrop, 1500)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xF814171B.toInt(), 0xDD14171B.toInt(), 0x8814171B.toInt(), 0x2214171B))
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x0014171B, 0x0014171B, 0xF814171B.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))

        val hero = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(22), 0, 0)
            addView(TextView(context).apply {
                text = details.title ?: series.title
                textSize = 28f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setTextColor(WHITE)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val seasonCount = details.seasons.count { it.episodes.isNotEmpty() }
            addView(TextView(context).apply {
                text = listOfNotNull(details.year,
                    seasonCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "temporada" else "temporadas"}" },
                    details.genre).joinToString("  •  ")
                textSize = 15f
                setTextColor(0xFFB9B9B9.toInt())
                setPadding(0, dp(12), 0, 0)
            })
            details.cast?.takeIf { it.isNotBlank() }?.let { cast ->
                addView(TextView(context).apply {
                    text = "Elenco: $cast"
                    textSize = 14f
                    setTextColor(0xFFB9B9B9.toInt())
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(10), 0, 0)
                })
            }
            details.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                addView(TextView(context).apply {
                    text = plot
                    textSize = 14f
                    setTextColor(0xFFB9B9B9.toInt())
                    maxLines = 4
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(10), 0, 0)
                })
            }
        }
        root.addView(hero, FrameLayout.LayoutParams(dp((screenWidthDp * 0.55f).toInt()), -2,
            Gravity.TOP or Gravity.START).apply { leftMargin = dp(26) })

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(90), dp(24), dp(24))
        }
        val populatedSeasons = details.seasons.filter { it.episodes.isNotEmpty() }
        val firstEpisode = populatedSeasons.asSequence().flatMap { it.episodes.asSequence() }.firstOrNull()
        fun action(label: String, primary: Boolean, click: () -> Unit) = TextView(context).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            minHeight = dp(38)
            isFocusable = true
            isClickable = true
            background = rounded(if (primary) 0xFFE4E5E6.toInt() else 0xFF292C30.toInt(), Color.TRANSPARENT, 7)
            setTextColor(if (primary) 0xFF101214.toInt() else WHITE)
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) 0xFFE4E5E6.toInt() else if (primary) 0xFF292C30.toInt() else 0xFF292C30.toInt(),
                    if (focused) Color.WHITE else Color.TRANSPARENT, 7)
                (view as TextView).setTextColor(if (focused) 0xFF101214.toInt() else WHITE)
            }
            setOnClickListener { click() }
        }
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val play = action(firstEpisode?.let { "▶  Reproduzir S${it.seasonNumber} E${it.number}" }
            ?: "Sem episódios", true) { firstEpisode?.let(onEpisode) }
        play.id = View.generateViewId()
        actions.addView(play, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(10) })
        val seasonsButton = action("Temporadas", false) { }
        actions.addView(seasonsButton, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(10) })
        actions.addView(action("Voltar", false, onBack), LinearLayout.LayoutParams(-2, dp(40)))
        body.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(28) })

        val seasonRows = mutableListOf<List<View>>()
        populatedSeasons.forEach { season ->
            val section = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val heading = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = season.title
                    textSize = 17f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    setTextColor(WHITE)
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            val position = TextView(context).apply {
                text = if (season.episodes.isEmpty()) "0 / 0" else "1 / ${season.episodes.size}"
                textSize = 15f
                setTextColor(0xFFB9B9B9.toInt())
            }
            heading.addView(position)
            section.addView(heading, LinearLayout.LayoutParams(-1, dp(28)).apply { bottomMargin = dp(4) })
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val cards = season.episodes.mapIndexed { index, episode ->
                val progress = episodeProgress[episode.id]
                val watched = progress?.completed == true
                val percent = progress?.takeIf { !it.completed && it.durationMs > 0L && it.positionMs > 0L }
                    ?.let { (it.positionMs * 100L / it.durationMs).coerceIn(1L, 99L).toInt() }
                val card = LinearLayout(context).apply {
                    id = View.generateViewId()
                    orientation = LinearLayout.VERTICAL
                    isFocusable = true
                    isClickable = true
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                    background = rounded(0xFF24272B.toInt(), Color.TRANSPARENT, 10)
                    setOnFocusChangeListener { view, focused ->
                        view.background = rounded(if (focused) Color.WHITE else 0xFF24272B.toInt(),
                            if (focused) Color.WHITE else Color.TRANSPARENT, 10)
                        (getChildAt(1) as TextView).apply {
                            setTextColor(if (watched) WHITE else if (focused) 0xFF14171B.toInt() else WHITE)
                            setBackgroundColor(if (watched) 0xFF23864A.toInt() else if (focused) 0xFFE4E5E6.toInt() else 0xFF24272B.toInt())
                            ellipsize = if (focused) android.text.TextUtils.TruncateAt.MARQUEE
                                else android.text.TextUtils.TruncateAt.END
                            isSelected = focused
                        }
                        if (focused) position.text = "${index + 1} / ${season.episodes.size}"
                    }
                    setOnClickListener { onEpisode(episode) }
                    contentDescription = "${season.title}, episódio ${episode.number}: ${episode.title}"
                }
                val image = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    background = rounded(0xFF202327.toInt(), Color.TRANSPARENT, 8)
                }
                LeSilArtworkLoader.into(episode.imageUrl ?: season.coverUrl ?: details.backdropUrl
                    ?: details.coverUrl ?: series.coverUrl, image, 600)
                card.addView(image, LinearLayout.LayoutParams(-1, dp(112)))
                card.addView(TextView(context).apply {
                    text = when {
                        watched -> "✓  E${episode.number}. ${episode.title}"
                        percent != null -> "$percent%  E${episode.number}. ${episode.title}"
                        else -> "E${episode.number}. ${episode.title}"
                    }
                    textSize = 13f
                    setSingleLine(true)
                    marqueeRepeatLimit = -1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(6), 0, dp(6), 0)
                    setTextColor(WHITE)
                    if (watched) setBackgroundColor(0xFF23864A.toInt())
                }, LinearLayout.LayoutParams(-1, dp(28)))
                row.addView(card, LinearLayout.LayoutParams(dp(200), dp(144)).apply { marginEnd = dp(7) })
                card
            }
            val horizontal = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                addView(row)
            }
            section.addView(horizontal, LinearLayout.LayoutParams(-1, dp(150)))
            body.addView(section, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
            seasonRows += cards
        }
        seasonRows.forEachIndexed { seasonIndex, cards ->
            cards.forEachIndexed { index, card ->
                if (index > 0) card.nextFocusLeftId = cards[index - 1].id
                if (index + 1 < cards.size) card.nextFocusRightId = cards[index + 1].id
                val previous = seasonRows.getOrNull(seasonIndex - 1)
                val next = seasonRows.getOrNull(seasonIndex + 1)
                card.nextFocusUpId = previous?.getOrNull(index.coerceAtMost(previous.lastIndex))?.id ?: play.id
                next?.getOrNull(index.coerceAtMost(next.lastIndex))?.let { card.nextFocusDownId = it.id }
            }
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(body)
        }
        val firstCard = seasonRows.firstNotNullOfOrNull { it.firstOrNull() }
        seasonsButton.setOnClickListener {
            if (firstCard?.requestFocus() == true) scroll.smoothScrollTo(0, dp(185))
        }
        if (firstCard != null) {
            for (index in 0 until actions.childCount) {
                actions.getChildAt(index).apply {
                    nextFocusDownId = firstCard.id
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                            firstCard.requestFocus()
                            scroll.smoothScrollTo(0, dp(185))
                            true
                        } else false
                    }
                }
            }
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = dp((screenHeightDp * if (shortScreen) 0.48f else 0.58f).toInt())
        })
        play.post { play.requestFocus() }
        return root
    }

    fun messageScreen(selectedNav: String, title: String, message: String, actionLabel: String, action: () -> Unit): View {
        val content = verticalScroll()
        content.addView(pageTitle(title))
        content.addView(statePanel(message, actionLabel, action))
        return shell(selectedNav, content)
    }

    private data class CategoryPanel(val view: View, val selected: View?, val items: List<View>)

    private data class MovieCatalogHeroBinding(
        val root: FrameLayout,
        val backdrop: ImageView,
        val title: TextView,
        val position: TextView,
        val metadata: TextView,
        val formatLogos: LinearLayout,
        val cast: TextView,
        val director: TextView,
        val plot: TextView,
        var displayedArtworkUrl: String? = null
    )

    private fun catalogSurface(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF0754DF.toInt(), 0xFF0646C5.toInt(), 0xFF07369F.toInt())
    )

    private fun categoryColumn(
        heading: String,
        categories: List<CatalogEntry>,
        selectedCategoryId: Int?,
        onCategory: (CatalogEntry) -> Unit
    ): CategoryPanel {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(10), dp(10))
            background = sidebarSurface()
        }
        panel.addView(TextView(context).apply {
            text = heading
            textSize = 13f
            letterSpacing = 0.1f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(0xFFD7EAFF.toInt())
            setPadding(dp(8), dp(4), 0, dp(12))
        }, LinearLayout.LayoutParams(-1, -2))
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        var selectedView: View? = null
        val categoryViews = mutableListOf<View>()
        categories.forEach { category ->
            val selected = category.id == selectedCategoryId
            val item = TextView(context).apply {
                text = "›   ${category.title}"
                textSize = 14f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(5), dp(8), dp(5))
                setTextColor(if (selected) 0xFF0644AE.toInt() else WHITE)
                isFocusable = true
                isClickable = true
                id = View.generateViewId()
                contentDescription = category.title
                background = navBackground(selected, false)
                setOnFocusChangeListener { view, focused ->
                    view.background = navBackground(selected, focused)
                    (view as TextView).setTextColor(if (selected && !focused) 0xFF0644AE.toInt() else WHITE)
                    // Keep the selection inside its column; scale only vertically for focus feedback.
                    view.animate().scaleX(1f).scaleY(if (focused) 1.025f else 1f)
                        .setDuration(120).start()
                }
                setOnClickListener { onCategory(category) }
            }
            list.addView(item, LinearLayout.LayoutParams(-1, dp(48)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
                bottomMargin = dp(5)
            })
            categoryViews += item
            if (selected && selectedView == null) selectedView = item
        }
        categoryViews.forEachIndexed { index, item ->
            item.nextFocusUpId = categoryViews.getOrNull(index - 1)?.id ?: item.id
            item.nextFocusDownId = categoryViews.getOrNull(index + 1)?.id ?: item.id
        }
        if (categories.isEmpty()) list.addView(stateLabel("Nenhuma categoria disponível."))
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            addView(list, FrameLayout.LayoutParams(-1, -2))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return CategoryPanel(panel, selectedView ?: list.getChildAt(0), categoryViews)
    }

    private fun catalogShell(
        selectedNav: String,
        categoryPanel: View,
        selectedCategory: View?,
        body: View,
        onCategoriesRight: (() -> Boolean)? = null,
        onContentLeftEdge: (() -> Boolean)? = null,
        onContentRefocus: (() -> Boolean)? = null
    ): View {
        var focusZone = ""
        var panelAnimator: ValueAnimator? = null
        var applyFocusZone: (String) -> Unit = {}
        var requestCategoryFocus: () -> Unit = {}
        var requestMenuFocus: () -> Unit = {}
        var menuFocusRequested = false
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val focusedView = findFocus()
                    if (focusZone == "categories" && event.keyCode in listOf(
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN
                        ) && (focusedView == null || !isDescendant(categoryPanel, focusedView))) {
                        // The panel may be visible before its newly measured item
                        // can take focus. Do not treat a key from the old movie
                        // card as a second LEFT into the main menu.
                        requestCategoryFocus()
                        return true
                    }
                    when {
                        event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusZone == "content" &&
                            (focusedView?.tag == CATALOG_LEFT_EDGE_TAG || onContentLeftEdge?.invoke() == true) -> {
                            requestCategoryFocus()
                            return true
                        }
                        event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusZone == "categories" -> {
                            requestMenuFocus()
                            return true
                        }
                        event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusZone == "menu" -> {
                            requestCategoryFocus()
                            return true
                        }
                        event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusZone == "categories" &&
                            onCategoriesRight != null -> {
                            // Transfer focus while the category item still exists.
                            // Hiding it first lets Android choose the main menu as
                            // a fallback before the movie grid can take focus.
                            if (onCategoriesRight.invoke()) applyFocusZone("content")
                            return true
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0753D8.toInt(), 0xFF0645B9.toInt(), 0xFF052D84.toInt()))
        }
        val columns = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val sidePanels = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val nav = createSidebar(
            selectedNav = selectedNav,
            requestInitialFocus = false,
            initiallyCompact = true,
            onItemFocus = null
        )
        val navLayout = LinearLayout.LayoutParams(dp(COLLAPSED_SIDEBAR_WIDTH_DP), -1)
        val categoryLayout = LinearLayout.LayoutParams(categoryPaneWidth, -1)
        sidePanels.addView(nav.view, navLayout)
        sidePanels.addView(categoryPanel, categoryLayout)
        columns.addView(sidePanels, LinearLayout.LayoutParams(-2, -1))
        columns.addView(body, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(columns, FrameLayout.LayoutParams(-1, -1))

        applyFocusZone = { zone ->
            if (zone != focusZone) {
                focusZone = zone
                val menuExpanded = zone == "menu"
                val targetNavWidth = if (menuExpanded) sidebarWidth else dp(COLLAPSED_SIDEBAR_WIDTH_DP)
                val targetCategoryWidth = if (zone == "categories") categoryPaneWidth else 0
                val startNavWidth = navLayout.width
                val startCategoryWidth = categoryLayout.width
                panelAnimator?.cancel()
                nav.setCompact(!menuExpanded)
                if (targetCategoryWidth > 0) categoryPanel.visibility = View.VISIBLE

                fun setPanelWidths(navWidth: Int, categoryWidth: Int) {
                    navLayout.width = navWidth
                    categoryLayout.width = categoryWidth
                    sidePanels.requestLayout()
                }

                if (root.isAttachedToWindow &&
                    (startNavWidth != targetNavWidth || startCategoryWidth != targetCategoryWidth)) {
                    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 190L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { frame ->
                            val progress = frame.animatedValue as Float
                            setPanelWidths(
                                startNavWidth + ((targetNavWidth - startNavWidth) * progress).toInt(),
                                startCategoryWidth + ((targetCategoryWidth - startCategoryWidth) * progress).toInt()
                            )
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                if (panelAnimator !== animation) return
                                setPanelWidths(targetNavWidth, targetCategoryWidth)
                                if (targetCategoryWidth == 0) categoryPanel.visibility = View.GONE
                                panelAnimator = null
                            }
                        })
                    }
                    panelAnimator = animator
                    animator.start()
                } else {
                    setPanelWidths(targetNavWidth, targetCategoryWidth)
                    if (targetCategoryWidth == 0) categoryPanel.visibility = View.GONE
                    panelAnimator = null
                }
            }
        }
        fun focusSelectedCategory(attempt: Int = 0) {
            val item = selectedCategory ?: return
            if (focusZone != "categories" || item.requestFocus()) return
            if (attempt < 8) root.postDelayed({ focusSelectedCategory(attempt + 1) }, 40)
        }
        requestCategoryFocus = {
            applyFocusZone("categories")
            focusSelectedCategory()
        }
        requestMenuFocus = {
            // The compact menu item is focusable before its column expands.
            // Focus it first so collapsing Categories cannot orphan focus.
            menuFocusRequested = true
            if (nav.selectedItem?.requestFocus() == true) applyFocusZone("menu")
            menuFocusRequested = false
        }
        val focusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            when {
                newFocus == null -> Unit
                isDescendant(nav.view, newFocus) && onContentRefocus != null && !menuFocusRequested -> {
                    // During poster recycling or category expansion Android may
                    // choose the sidebar as fallback. Keep the intended panel.
                    when (focusZone) {
                        "content" -> if (!onContentRefocus.invoke()) root.post {
                            if (focusZone == "content") onContentRefocus.invoke()
                        }
                        "categories" -> requestCategoryFocus()
                    }
                }
                isDescendant(nav.view, newFocus) && focusZone != "menu" -> applyFocusZone("menu")
                isDescendant(categoryPanel, newFocus) && focusZone != "categories" -> applyFocusZone("categories")
                isDescendant(body, newFocus) && focusZone != "content" -> applyFocusZone("content")
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                panelAnimator?.cancel()
                if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnGlobalFocusChangeListener(focusListener)
            }
        })
        applyFocusZone("categories")
        selectedCategory?.post { if (!selectedCategory.hasFocus()) selectedCategory.requestFocus() }
        return root
    }

    private fun isDescendant(parent: View, child: View): Boolean {
        var current: View? = child
        while (current != null) {
            if (current === parent) return true
            current = current.parent as? View
        }
        return false
    }

    private fun movieCatalogHero(seriesMode: Boolean): MovieCatalogHeroBinding {
        val root = FrameLayout(context).apply {
            background = rounded(0xFF08245D.toInt(), Color.TRANSPARENT, 0)
        }
        val backdrop = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                if (seriesMode) intArrayOf(0xF5071C3A.toInt(), 0xDC071C3A.toInt(),
                    0xAA071C3A.toInt(), 0x55071C3A.toInt())
                else intArrayOf(0xF5071C3A.toInt(), 0xC8071C3A.toInt(),
                    0x44071C3A.toInt(), 0x11071C3A.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0x44031B48.toInt(), 0xDD031126.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(context).apply {
            textSize = 26f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
        }
        val metadata = TextView(context).apply { textSize = 14f; setTextColor(0xFFE3F0FF.toInt()); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val formatLogos = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        val metadataLine = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(metadata, LinearLayout.LayoutParams(-2, -2))
            addView(formatLogos, LinearLayout.LayoutParams(-2, dp(22)).apply { marginStart = dp(10) })
        }
        val cast = TextView(context).apply { textSize = 13f; setTextColor(0xFFE3F0FF.toInt()); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val director = TextView(context).apply { textSize = 13f; setTextColor(0xFFE3F0FF.toInt()); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val plot = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFFF0F5FB.toInt())
            maxLines = if (seriesMode) { if (shortScreen) 4 else 8 } else 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(
                dp(20),
                dp(14),
                dp((screenWidthDp * (if (seriesMode) 0.14f else 0.32f)).toInt()),
                dp(10)
            )
        }
        block.addView(title, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        block.addView(metadataLine, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        block.addView(cast, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(1) })
        block.addView(director, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        block.addView(plot, LinearLayout.LayoutParams(-1, -2))
        root.addView(block, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.START))
        val position = TextView(context).apply {
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rounded(0x99031B48.toInt(), Color.TRANSPARENT, 8)
        }
        return MovieCatalogHeroBinding(root, backdrop, title, position, metadata, formatLogos, cast, director, plot)
    }

    private fun catalogIconButton(iconResource: Int, description: String, action: () -> Unit): View =
        FrameLayout(context).apply {
            isFocusable = true
            isClickable = true
            contentDescription = description
            background = rounded(Color.TRANSPARENT, Color.TRANSPARENT, 12)
            val icon = ImageView(context).apply {
                setImageResource(iconResource)
                setColorFilter(WHITE, PorterDuff.Mode.SRC_IN)
                contentDescription = null
            }
            addView(icon, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(
                    if (focused) 0xFF1689F5.toInt() else Color.TRANSPARENT,
                    if (focused) 0xFFE4F3FF.toInt() else Color.TRANSPARENT,
                    12
                )
                view.animate().scaleX(if (focused) 1.08f else 1f).scaleY(if (focused) 1.08f else 1f)
                    .setDuration(120).start()
            }
            setOnClickListener { action() }
        }

    private fun renderMovieCatalogHero(
        binding: MovieCatalogHeroBinding,
        movie: CatalogEntry,
        details: MovieDetails?,
        position: Int,
        total: Int,
        categories: List<CatalogEntry>
    ) {
        val title = details?.title?.takeIf { it.isNotBlank() } ?: movie.title
        val categorySaysLegendado = categories.any { it.id == movie.categoryId &&
            it.title.contains("legendad", ignoreCase = true) }
        val titleSaysLegendado = Regex("(?i)\\[L\\]|\\(L\\)|(?:^|[^a-z])(?:legendad[oa]s?|leg)(?:[^a-z]|$)")
            .containsMatchIn(movie.title)
        binding.title.text = if ((categorySaysLegendado || titleSaysLegendado) &&
            !title.contains("legendad", ignoreCase = true)) {
            SpannableStringBuilder(title).apply {
                val start = length
                append("  • LEGENDADO")
                setSpan(RelativeSizeSpan(0.48f), start, length, 0)
                setSpan(ForegroundColorSpan(CYAN), start, length, 0)
            }
        } else title
        binding.position.text = "$position / $total"
        val rating = details?.rating?.takeIf { it.isNotBlank() && it != "0" } ?: movie.rating?.takeIf { it.isNotBlank() && it != "0" }
        binding.metadata.text = listOfNotNull(
            rating?.let { "★ $it" }, details?.year, details?.duration, details?.genre
        ).joinToString("   ·   ")
        val formatLogos = movieFormatLogos(movie, details)
        binding.formatLogos.removeAllViews()
        formatLogos.forEach { label ->
            val logo = codecLogo(label)
            val width = when (label) {
                "DOLBY VISION", "DOLBY ATMOS" -> dp(44)
                "AV1" -> dp(32)
                "4K UHD" -> dp(42)
                else -> -2
            }
            binding.formatLogos.addView(logo, LinearLayout.LayoutParams(width, dp(17)).apply {
                marginEnd = dp(8)
            })
        }
        binding.formatLogos.visibility = if (formatLogos.isEmpty()) View.GONE else View.VISIBLE
        binding.cast.text = details?.cast?.takeIf { it.isNotBlank() }?.let { "Elenco: $it" }.orEmpty()
        binding.director.text = details?.director?.takeIf { it.isNotBlank() }?.let { "Direção: $it" }.orEmpty()
        binding.plot.text = details?.plot?.takeIf { it.isNotBlank() } ?: movie.plot.orEmpty()
        binding.cast.visibility = if (binding.cast.text.isBlank()) View.GONE else View.VISIBLE
        binding.director.visibility = if (binding.director.text.isBlank()) View.GONE else View.VISIBLE
        binding.plot.visibility = if (binding.plot.text.isBlank()) View.GONE else View.VISIBLE
        if (details == null) return
        val artworkUrl = details.backdropUrl?.takeIf { it.isNotBlank() }
            ?: details.posterUrl?.takeIf { it.isNotBlank() }
            ?: movie.posterUrl?.takeIf { it.isNotBlank() }
        if (artworkUrl != binding.displayedArtworkUrl) {
            binding.displayedArtworkUrl = artworkUrl
            LeSilArtworkLoader.into(artworkUrl, binding.backdrop, 1100)
        }
    }

    private fun movieFormatLogos(movie: CatalogEntry, details: MovieDetails?): List<String> {
        val titleText = "${movie.title} ${details?.title.orEmpty()}".uppercase(java.util.Locale.ROOT)
        val videoText = details?.technicalVideo.orEmpty().uppercase(java.util.Locale.ROOT)
        val audioText = details?.technicalAudio.orEmpty().uppercase(java.util.Locale.ROOT)
        val all = "$titleText $videoText $audioText"
        fun has(pattern: String) = Regex(pattern).containsMatchIn(all)

        return buildList {
            when {
                has("(?:^|[^A-Z0-9])(?:4K|UHD|2160P|2160)(?:[^A-Z0-9]|$)") -> add("4K UHD")
                has("(?:^|[^A-Z0-9])1080P(?:[^A-Z0-9]|$)") -> add("1080P")
                has("(?:^|[^A-Z0-9])720P(?:[^A-Z0-9]|$)") -> add("720P")
            }
            when {
                has("DOLBY[\\s_-]*VISION|\\bDV\\b|DVHE|DVH1|DOVI") -> add("DOLBY VISION")
                has("HDR[\\s_-]*10(?:\\+|PLUS)") -> add("HDR10+")
                has("HDR[\\s_-]*10") -> add("HDR10")
                has("\\bHDR\\b|HLG|ST2084|SMPTE[ _-]*2084") -> add("HDR")
            }
            when {
                has("\\bHEVC\\b|H\\.?265") -> add("HEVC")
                has("\\bAV1\\b") -> add("AV1")
                has("\\bVP9\\b") -> add("VP9")
                has("H\\.?264|\\bAVC\\b") -> add("H.264")
            }
            when {
                has("DOLBY[\\s_-]*ATMOS|\\bATMOS\\b") -> add("DOLBY ATMOS")
                has("DTS[\\s_-]*X") -> add("DTS:X")
                has("DTS[\\s_-]*HD") -> add("DTS-HD")
                has("TRUE[\\s_-]*HD") -> add("TRUEHD")
                has("DOLBY[\\s_-]*DIGITAL[\\s_-]*PLUS|E[- ]?AC[- ]?3|\\bDDP\\b") -> add("DOLBY DIGITAL+")
                has("DOLBY[\\s_-]*DIGITAL|AC[- ]?3|\\bAC3\\b") -> add("DOLBY DIGITAL")
                has("\\bDTS\\b") -> add("DTS")
                has("\\bFLAC\\b") -> add("FLAC")
                has("\\bAAC\\b") -> add("AAC")
            }
            when {
                has("(?:^|[^0-9])7\\.1(?:[^0-9]|$)") -> add("7.1")
                has("(?:^|[^0-9])5\\.1(?:[^0-9]|$)") -> add("5.1")
            }
        }
    }

    private fun codecLogo(label: String): View {
        val officialLogo = when (label) {
            "DOLBY VISION" -> R.drawable.dolby_vision_vertical
            "DOLBY ATMOS" -> R.drawable.dolby_atmos_vertical
            "AV1" -> R.drawable.av1_logo_rgb
            else -> null
        }
        if (officialLogo != null) {
            return ImageView(context).apply {
                setImageResource(officialLogo)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = label
                // The downloaded marks are the unmodified official artwork: black Dolby marks
                // and the color AV1 mark. A solid light tile keeps them legible over the backdrop.
                background = rounded(0xFFFFFFFF.toInt(), Color.TRANSPARENT, 2)
                setPadding(dp(2), dp(1), dp(2), dp(1))
            }
        }

        return TextView(context).apply {
            text = label
            textSize = 10f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
        }
    }

    private fun seriesCatalogHero(initial: SeriesEntry?): Pair<View, (SeriesEntry) -> Unit> {
        val root = FrameLayout(context).apply {
            clipToOutline = true
            background = rounded(0xFF08245D.toInt(), Color.TRANSPARENT, 8)
        }
        val cover = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(cover, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xE8071C3A.toInt(), 0xA8071C3A.toInt(), 0x22071C3A.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val heading = TextView(context).apply {
            textSize = 30f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
        }
        val metadata = TextView(context).apply { textSize = 16f; setTextColor(0xFFE3F0FF.toInt()) }
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(16), dp(20), dp(16))
            addView(heading)
            addView(metadata, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        root.addView(text, FrameLayout.LayoutParams(-1, -1))
        val update: (SeriesEntry) -> Unit = { item ->
            heading.text = item.title
            metadata.text = listOfNotNull(item.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" }).joinToString("   ·   ")
            LeSilArtworkLoader.into(item.coverUrl, cover, 1100)
        }
        initial?.let(update)
        return root to update
    }

    private fun shell(selectedNav: String, body: View): View {
        val root = FrameLayout(context).apply { setBackgroundColor(BACKGROUND) }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply { leftMargin = sidebarWidth })
        val sidebar = sidebar(selectedNav)
        root.addView(sidebar, FrameLayout.LayoutParams(sidebarWidth, -1, Gravity.START))
        return root
    }

    private data class SidebarBinding(val view: View, val setCompact: (Boolean) -> Unit, val selectedItem: View?)

    private fun sidebar(selectedNav: String, requestInitialFocus: Boolean = true): View =
        createSidebar(selectedNav, requestInitialFocus, initiallyCompact = false, onItemFocus = null).view

    private fun createSidebar(
        selectedNav: String,
        requestInitialFocus: Boolean,
        initiallyCompact: Boolean,
        onItemFocus: ((Boolean) -> Unit)?
    ): SidebarBinding {
        val nav = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (shortScreen) 10 else 14), dp(if (shortScreen) 10 else 20),
                dp(if (shortScreen) 8 else 12), dp(if (shortScreen) 8 else 14))
            background = sidebarSurface()
        }
        val brand = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (shortScreen) 6 else 10), dp(if (shortScreen) 2 else 6), dp(4), dp(if (shortScreen) 10 else 24))
        }
        val logo = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = ImageView(context).apply {
            setImageResource(R.drawable.lesil_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Le&Sil Stream"
        }
        logo.addView(mark, LinearLayout.LayoutParams(dp(24), dp(24)).apply {
            marginEnd = dp(6)
        })
        val wordmark = TextView(context).apply {
            text = "Le&Sil Stream"
            textSize = if (shortScreen) 16f else 18f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(WHITE)
            maxLines = 1
        }
        logo.addView(wordmark)
        val tagline = TextView(context).apply {
            text = "FILMES E SÉRIES"
            textSize = if (shortScreen) 7f else 9f
            letterSpacing = 0.22f
            setTextColor(CYAN)
            setPadding(dp(36), dp(2), 0, 0)
        }
        brand.addView(logo)
        brand.addView(tagline)
        nav.addView(brand)

        val menu = listOf(
            "Início" to "⌂", "Filmes" to "◉", "Séries" to "▣",
            "Atualizar" to "↻", "Configurações" to "⚙"
        )
        val menuItems = mutableListOf<Triple<TextView, String, String>>()
        var activeView: View? = null
        menu.forEach { (name, icon) ->
            val item = TextView(context).apply {
                text = "$icon  $name"
                textSize = if (shortScreen) 13f else 15f
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (name == selectedNav) 0xFF0644AE.toInt() else WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
                isFocusable = true
                isClickable = true
                contentDescription = name
                background = navBackground(name == selectedNav, false)
                setOnFocusChangeListener { view, focused ->
                    view.background = navBackground(name == selectedNav, focused)
                    (view as TextView).setTextColor(if (name == selectedNav && !focused) 0xFF0644AE.toInt() else WHITE)
                    // Keep the selection background inside the sidebar column.
                    view.animate().scaleX(1f).scaleY(if (focused) 1.025f else 1f)
                        .setDuration(150).start()
                    onItemFocus?.invoke(focused)
                }
                setOnClickListener { actions.onNavigate(name) }
            }
            nav.addView(item, LinearLayout.LayoutParams(-1, dp(if (shortScreen) 40 else 46)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
                bottomMargin = dp(if (shortScreen) 2 else 3)
            })
            menuItems += Triple(item, name, icon)
            if (name == selectedNav) activeView = item
        }
        nav.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        val foot = TextView(context).apply {
            text = "VOD • EXPERIÊNCIA DE CINEMA"
            textSize = if (shortScreen) 7f else 9f
            letterSpacing = 0.08f
            setTextColor(SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        if (!shortScreen) nav.addView(foot)
        fun setCompact(compact: Boolean) {
            nav.setPadding(if (compact) dp(5) else dp(if (shortScreen) 10 else 14),
                dp(if (shortScreen) 10 else 20), if (compact) dp(5) else dp(if (shortScreen) 8 else 12),
                dp(if (shortScreen) 8 else 14))
            brand.setPadding(if (compact) dp(4) else dp(if (shortScreen) 6 else 10),
                dp(if (shortScreen) 2 else 6), dp(4), dp(if (compact) 8 else if (shortScreen) 10 else 24))
            logo.gravity = if (compact) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            wordmark.visibility = if (compact) View.GONE else View.VISIBLE
            tagline.visibility = if (compact) View.GONE else View.VISIBLE
            menuItems.forEach { (item, name, icon) ->
                item.text = if (compact) icon else "$icon  $name"
                item.gravity = if (compact) Gravity.CENTER else Gravity.CENTER_VERTICAL
                item.setPadding(if (compact) dp(2) else dp(14), 0, if (compact) dp(2) else dp(8), 0)
                item.maxLines = 1
            }
            foot.visibility = if (compact) View.GONE else View.VISIBLE
        }
        setCompact(initiallyCompact)
        if (requestInitialFocus) activeView?.post { if (activeView?.hasFocus() != true) activeView?.requestFocus() }
        return SidebarBinding(nav, ::setCompact, activeView)
    }

    private fun hero(data: LeSilHomeData): View {
        val movie = data.featuredMovie
        val details = data.featuredDetails
        val frame = FrameLayout(context).apply {
            background = rounded(0xFF081421.toInt(), Color.TRANSPARENT, 0)
            clipToOutline = true
        }
        val height = dp((screenHeightDp * 0.43f).toInt().coerceIn(185, 260))
        val artwork = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        LeSilArtworkLoader.into(details?.backdropUrl ?: details?.posterUrl ?: movie?.posterUrl, artwork, 1500)
        frame.addView(artwork, FrameLayout.LayoutParams(-1, -1))

        val leftShade = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xF5050A11.toInt(), 0xD9050A11.toInt(), 0x66050A11, 0x00050A11))
        }
        frame.addView(leftShade, FrameLayout.LayoutParams(-1, -1))
        val bottomShade = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0xC8050A11.toInt()))
        }
        frame.addView(bottomShade, FrameLayout.LayoutParams(-1, -1))

        val textBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(14), dp(14), dp(14))
        }
        val kicker = TextView(context).apply {
            text = when {
                data.loading -> "LE&SIL STREAM"
                movie?.addedAt != null -> "ADICIONADO RECENTEMENTE"
                movie != null -> "DESTAQUE DO CATÁLOGO"
                else -> "SUA BIBLIOTECA VOD"
            }
            textSize = 10f
            letterSpacing = 0.2f
            setTextColor(0xFFBFD8EA.toInt())
        }
        val title = TextView(context).apply {
            text = details?.title?.takeIf { it.isNotBlank() } ?: movie?.title ?: "Le&Sil Stream"
            textSize = if (shortScreen) 27f else 32f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
        }
        textBlock.addView(kicker)
        textBlock.addView(title)
        val metadata = listOfNotNull(details?.year, details?.duration, details?.genre)
        if (metadata.isNotEmpty()) {
            textBlock.addView(metadataLine(metadata.joinToString("   •   ")).apply {
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        } else if (data.featuredCategory != null) {
            textBlock.addView(metadataLine(data.featuredCategory).apply { textSize = 13f })
        }
        val description = details?.plot?.takeIf { it.isNotBlank() }
        if (description != null) {
            textBlock.addView(TextView(context).apply {
                text = description
                textSize = if (shortScreen) 12f else 14f
                setTextColor(0xFFE0EAF2.toInt())
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(1), dp(8), dp(8))
            })
        }

        val actionsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val play = actionButton("▶   Assistir", true) {
            movie?.let(actions.onPlay) ?: actions.onRetry()
        }
        play.textSize = 13f
        actionsRow.addView(play, LinearLayout.LayoutParams(dp(116), dp(42)).apply { marginEnd = dp(8) })
        if (movie != null) {
            actionsRow.addView(actionButton("ⓘ   Detalhes", false) { actions.onMovieDetails(movie) }.apply {
                textSize = 13f
            }, LinearLayout.LayoutParams(dp(112), dp(42)))
        }
        textBlock.addView(actionsRow)
        frame.addView(textBlock, FrameLayout.LayoutParams(
            dp((screenWidthDp * 0.57f).toInt().coerceAtLeast(240)), -1, Gravity.START))
        return frame.apply { layoutParams = LinearLayout.LayoutParams(-1, height) }
    }

    private fun movieRow(title: String, movies: List<CatalogEntry>, onMovie: (CatalogEntry) -> Unit): View {
        val section = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        section.addView(sectionTitle(title))
        if (movies.isEmpty()) {
            section.addView(stateLabel("Nenhum título disponível nesta seção."))
            return section
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cardWidth = if (shortScreen) 126 else 144
        val cardHeight = if (shortScreen) 192 else 218
        movies.take(HOME_ROW_LIMIT).forEach { movie ->
            row.addView(mediaCard(movie.title, movie.posterUrl, movie.rating) { onMovie(movie) },
                LinearLayout.LayoutParams(dp(cardWidth), dp(cardHeight)).apply { marginEnd = dp(10) })
        }
        val horizontal = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            addView(row)
        }
        section.addView(horizontal)
        return section
    }

    private fun seriesRow(series: List<SeriesEntry>): View {
        val section = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        section.addView(sectionTitle("Séries recentes"))
        if (series.isEmpty()) {
            section.addView(stateLabel("Nenhuma série disponível ou informada pelo provedor."))
            return section
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cardWidth = if (shortScreen) 126 else 144
        val cardHeight = if (shortScreen) 192 else 218
        series.take(HOME_ROW_LIMIT).forEach { item ->
            row.addView(mediaCard(item.title, item.coverUrl, item.rating) { actions.onSeriesDetails(item) },
                LinearLayout.LayoutParams(dp(cardWidth), dp(cardHeight)).apply { marginEnd = dp(10) })
        }
        section.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            addView(row)
        })
        return section
    }

    private fun categoryChips(categories: List<CatalogEntry>, selectedId: Int?, onCategory: (CatalogEntry) -> Unit): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        categories.take(40).forEach { category ->
            val chip = actionButton(category.title, category.id == selectedId) { onCategory(category) }
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(46)).apply { marginEnd = dp(8) })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            addView(row)
        }
    }

    private fun mediaCard(title: String, imageUrl: String?, rating: String?, onClick: () -> Unit): View {
        val card = FrameLayout(context).apply {
            background = rounded(CARD_SURFACE, 0xFF334961.toInt(), 12)
            isFocusable = true
            isClickable = true
            clipToOutline = true
            setOnClickListener { onClick() }
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) 0xFF10263A.toInt() else CARD_SURFACE,
                    if (focused) CYAN else 0xFF334961.toInt(), if (focused) 12 else 10)
                view.animate().scaleX(if (focused) 1.045f else 1f).scaleY(if (focused) 1.045f else 1f)
                    .setDuration(150).start()
                view.elevation = if (focused) dp(8).toFloat() else 0f
            }
        }
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        LeSilArtworkLoader.into(imageUrl, image, 720)
        card.addView(image, FrameLayout.LayoutParams(-1, -1))
        val shade = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0x12000000, 0xEE02070D.toInt()))
        }
        card.addView(shade, FrameLayout.LayoutParams(-1, -1))
        val label = TextView(context).apply {
            text = title
            textSize = 15f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(dp(12), dp(8), dp(10), dp(7))
            gravity = Gravity.BOTTOM
        }
        card.addView(label, FrameLayout.LayoutParams(-1, dp(62), Gravity.BOTTOM))
        rating?.takeIf { it.isNotBlank() && it != "0" }?.let {
            val ratingLabel = TextView(context).apply {
                text = "★ $it"
                textSize = 11f
                setTextColor(0xFFE8F3FC.toInt())
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = rounded(0xAA07111D.toInt(), Color.TRANSPARENT, 8)
            }
            card.addView(ratingLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                rightMargin = dp(8)
            })
        }
        card.contentDescription = "Abrir $title"
        return card
    }

    private fun catalogTile(
        title: String,
        imageUrl: String?,
        rating: String?,
        onClick: () -> Unit,
        onFocus: (Boolean) -> Unit = {}
    ): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = rounded(Color.TRANSPARENT, Color.TRANSPARENT, 10)
            setOnFocusChangeListener { view, focused ->
                updateCatalogTileFocus(view as LinearLayout, focused)
                view.animate().scaleX(if (focused) 1.025f else 1f).scaleY(if (focused) 1.025f else 1f)
                    .setDuration(150).start()
                if (focused) refreshFocusedPoster(view, imageUrl)
                onFocus(focused)
            }
            setOnClickListener { onClick() }
            contentDescription = "Abrir $title"
        }
        val poster = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(0xFF0D1A27.toInt(), 0xFF263A4E.toInt(), 8)
            clipToOutline = true
        }
        LeSilArtworkLoader.into(imageUrl, poster, 420)
        tile.addView(poster, LinearLayout.LayoutParams(-1, dp(CATALOG_POSTER_HEIGHT_DP)))
        val caption = TextView(context).apply {
            text = title
            textSize = 12f
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            marqueeRepeatLimit = -1
            setTextColor(WHITE)
            setPadding(dp(6), 0, dp(5), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        tile.addView(caption, LinearLayout.LayoutParams(-1, dp(CATALOG_CAPTION_HEIGHT_DP)))
        if (!rating.isNullOrBlank() && rating != "0") {
            caption.contentDescription = "$title, nota informada pelo servidor $rating"
        }
        return tile
    }

    private fun bindCatalogTile(
        tile: LinearLayout,
        movie: CatalogEntry,
        position: Int,
        total: Int,
        onMovie: (CatalogEntry) -> Unit,
        onFocus: (CatalogEntry, Int, Int) -> Unit,
        isFavorite: Boolean
    ) {
        val poster = tile.getChildAt(0) as? ImageView ?: return
        val caption = tile.getChildAt(1) as? TextView ?: return
        LeSilArtworkLoader.into(movie.posterUrl, poster, 420)
        caption.isSelected = false
        caption.text = favoriteCaption(movie.title, isFavorite)
        caption.contentDescription = if (!movie.rating.isNullOrBlank() && movie.rating != "0") {
            "${movie.title}, nota informada pelo servidor ${movie.rating}"
        } else movie.title
        tile.contentDescription = "Abrir ${movie.title}"
        tile.setOnClickListener { onMovie(movie) }
        tile.setOnFocusChangeListener { view, focused ->
            updateCatalogTileFocus(view as LinearLayout, focused)
            view.animate().scaleX(if (focused) 1.025f else 1f).scaleY(if (focused) 1.025f else 1f)
                .setDuration(150).start()
            if (focused) {
                refreshFocusedPoster(view, movie.posterUrl)
                onFocus(movie, position, total)
            }
        }
        updateCatalogTileFocus(tile, tile.hasFocus())
    }

    private fun favoriteCaption(title: String, favorite: Boolean): CharSequence {
        if (!favorite) return title
        return SpannableStringBuilder("★  $title").apply {
            setSpan(ForegroundColorSpan(0xFFFFD453.toInt()), 0, 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun setCatalogFavoriteMark(tile: View, id: Int, title: String, favorite: Boolean) {
        @Suppress("UNCHECKED_CAST")
        val favorites = tile.getTag(R.id.catalog_favorite_set) as? MutableSet<Int>
        if (favorite) favorites?.add(id) else favorites?.remove(id)
        ((tile as? ViewGroup)?.getChildAt(1) as? TextView)?.text = favoriteCaption(title, favorite)
    }

    private fun updateCatalogTileFocus(tile: LinearLayout, focused: Boolean) {
        tile.background = rounded(
            if (focused) Color.WHITE else Color.TRANSPARENT,
            if (focused) Color.WHITE else Color.TRANSPARENT,
            12
        )
        val caption = tile.getChildAt(1) as? TextView ?: return
        caption.setBackgroundColor(if (focused) 0xFFE6E6E6.toInt() else Color.TRANSPARENT)
        caption.setTextColor(if (focused) 0xFF171717.toInt() else WHITE)
        caption.ellipsize = if (focused) android.text.TextUtils.TruncateAt.MARQUEE
            else android.text.TextUtils.TruncateAt.END
        caption.isSelected = focused
    }

    private fun refreshFocusedPoster(tile: View, imageUrl: String?) {
        val poster = (tile as? ViewGroup)?.getChildAt(0) as? ImageView ?: return
        if (poster.drawable == null && !imageUrl.isNullOrBlank()) {
            LeSilArtworkLoader.into(imageUrl, poster, 420)
        }
    }

    private fun verticalScroll(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(26), dp(12), dp(14), dp(28))
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun sectionTitle(title: String) = TextView(context).apply {
        text = title
        textSize = 20f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(WHITE)
        setPadding(dp(2), dp(14), 0, dp(8))
    }

    private fun pageTitle(title: String) = TextView(context).apply {
        text = title
        textSize = 30f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(WHITE)
        setPadding(0, dp(10), 0, dp(18))
    }

    private fun metadataLine(text: String) = TextView(context).apply {
        this.text = text
        textSize = 15f
        setTextColor(0xFFD0DFEC.toInt())
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun stateLabel(message: String) = TextView(context).apply {
        text = message
        textSize = 16f
        setTextColor(SECONDARY)
        setPadding(dp(4), dp(7), dp(20), dp(14))
    }

    private fun statePanel(message: String, buttonText: String, action: () -> Unit): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = rounded(DARK_SURFACE, 0xFF24394D.toInt(), 14)
        }
        panel.addView(stateLabel(message))
        panel.addView(actionButton(buttonText, true, action), LinearLayout.LayoutParams(dp(210), dp(54)))
        return panel
    }

    private fun infoSection(title: String, message: String): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(sectionTitle(title))
        addView(stateLabel(message))
    }

    private fun actionButton(text: String, primary: Boolean, action: () -> Unit): TextView {
        val button = TextView(context).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            setTextColor(if (primary) 0xFF07111D.toInt() else WHITE)
            isFocusable = true
            isClickable = true
            background = rounded(if (primary) 0xFFEAF4FC.toInt() else 0xB80D1B2B.toInt(),
                if (primary) Color.TRANSPARENT else 0xFF496078.toInt(), 14)
            setOnFocusChangeListener { view, focused ->
                val fill = when {
                    primary -> if (focused) 0xFFB9E7FF.toInt() else 0xFFEAF4FC.toInt()
                    focused -> 0xFF113354.toInt()
                    else -> 0xB80D1B2B.toInt()
                }
                view.background = rounded(fill, if (focused) CYAN else if (primary) Color.TRANSPARENT else 0xFF496078.toInt(), 14)
                view.animate().scaleX(if (focused) 1.04f else 1f).scaleY(if (focused) 1.04f else 1f)
                    .setDuration(150).start()
            }
            setOnClickListener { action() }
        }
        return button
    }

    private fun sidebarSurface(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0xFF07111D.toInt(), 0xFF030810.toInt())
    )

    private fun navBackground(selected: Boolean, focused: Boolean): GradientDrawable {
        val fill = when {
            focused -> 0xFF1689F5.toInt()
            selected -> 0xFFF4F8FF.toInt()
            else -> Color.TRANSPARENT
        }
        val stroke = if (focused || selected) 0xFFD9EDFF.toInt() else Color.TRANSPARENT
        return rounded(fill, stroke, 15)
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val BACKGROUND = 0xFF050A11.toInt()
        const val DARK_SURFACE = 0xFF0B1724.toInt()
        const val CARD_SURFACE = 0xFF101D2A.toInt()
        const val BORDER = 0xFF334A60.toInt()
        const val WHITE = 0xFFF4F7FB.toInt()
        const val SECONDARY = 0xFFA9BDD0.toInt()
        const val CYAN = 0xFF2EA9FF.toInt()
        const val HOME_ROW_LIMIT = 16
        const val MAX_CATALOG_ITEMS = 80
        const val GRID_COLUMNS = 7
        const val CATALOG_POSTER_WIDTH_DP = 112
        const val CATALOG_POSTER_HEIGHT_DP = 168
        const val CATALOG_CAPTION_HEIGHT_DP = 26
        const val CATALOG_TILE_HEIGHT_DP = 200
        const val CATALOG_FOCUS_HEADROOM_DP = 5
        const val CATALOG_POSTER_GAP_DP = 8
        const val CATALOG_LEFT_EDGE_TAG = "lesil.catalog.leftEdge"
        const val COLLAPSED_SIDEBAR_WIDTH_DP = 68
    }
}
