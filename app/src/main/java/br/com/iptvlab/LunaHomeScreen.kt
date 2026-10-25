package br.com.iptvlab

import android.content.Context
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

data class LunaHomeData(
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

data class LunaHomeActions(
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
class LunaHomeScreen(private val context: Context, private val actions: LunaHomeActions) {
    private val density = context.resources.displayMetrics.density
    private val screenWidthDp = context.resources.configuration.screenWidthDp
    private val sidebarWidth = dp((screenWidthDp * 0.18f).toInt().coerceIn(190, 250))
    private val categoryPaneWidth = dp((screenWidthDp * 0.23f).toInt().coerceIn(205, 270))

    fun home(data: LunaHomeData): View {
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
        onMovieFocus: (CatalogEntry, (MovieDetails?) -> Unit) -> Unit
    ): View {
        val categoryPanel = categoryColumn("CATEGORIAS", categories, selectedCategoryId, onCategory)
        val content = FrameLayout(context).apply {
            background = catalogSurface()
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipChildren = false
        }
        val hero = movieCatalogHero()
        content.addView(hero.root, FrameLayout.LayoutParams(-1, -1))

        val filterButton = catalogIconButton(android.R.drawable.ic_menu_sort_by_size, "Filtrar categoria") {
            if (categories.isNotEmpty()) {
                AlertDialog.Builder(context)
                    .setTitle("Categorias de filmes")
                    .setItems(categories.map { it.title }.toTypedArray()) { _, index -> onCategory(categories[index]) }
                    .setNegativeButton("Fechar", null)
                    .show()
            }
        }
        var activeFilter = ""
        val searchButton = catalogIconButton(android.R.drawable.ic_menu_search, "Buscar filme") { }
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
            topMargin = dp(46)
            rightMargin = dp(rightmostPosterInsetDp)
        })

        val catalogHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sectionTitle(title).apply {
                textSize = 16f
                setPadding(dp(2), 0, 0, dp(3))
            }, LinearLayout.LayoutParams(0, dp(34), 1f))
            addView(hero.position, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(10)
                marginEnd = dp((rightmostPosterInsetDp - catalogHorizontalInsetDp).coerceAtLeast(0))
            })
        }
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val gridScroll = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = true
            addView(grid, FrameLayout.LayoutParams(-1, -2))
        }
        val catalogBottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalInset = dp(catalogHorizontalInsetDp)
            setPadding(horizontalInset, 0, horizontalInset, dp(8))
            clipChildren = true
            addView(catalogHeader, LinearLayout.LayoutParams(-1, dp(36)))
            addView(gridScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        val catalogHeight = dp((context.resources.configuration.screenHeightDp * 0.53f).toInt().coerceAtLeast(220))
        content.addView(catalogBottom, FrameLayout.LayoutParams(-1, catalogHeight, Gravity.BOTTOM))
        var activeMovieId: Int? = null
        var activeMovieDetails: MovieDetails? = null
        fun showMovie(movie: CatalogEntry, details: MovieDetails?, position: Int, total: Int) {
            activeMovieId = movie.id
            activeMovieDetails = details
            renderMovieCatalogHero(hero, movie, details, position, total)
        }
        fun focusMovie(movie: CatalogEntry, position: Int, total: Int) {
            val focusedRow = (position - 1) / GRID_COLUMNS
            gridScroll.post {
                val row = grid.getChildAt(focusedRow)
                if (row != null) gridScroll.smoothScrollTo(0, row.top)
            }
            if (activeMovieId != movie.id) showMovie(movie, null, position, total)
            else renderMovieCatalogHero(hero, movie, activeMovieDetails, position, total)
            onMovieFocus(movie) { details ->
                if (activeMovieId == movie.id) {
                    activeMovieDetails = details
                    renderMovieCatalogHero(hero, movie, details, position, total)
                }
            }
        }
        fun populate(filter: String) {
            grid.removeAllViews()
            val total = movies.count { it.title.contains(filter, ignoreCase = true) }
            val found = movies.asSequence()
                .filter { it.title.contains(filter, ignoreCase = true) }
                .take(MAX_CATALOG_ITEMS)
                .toList()
            if (found.isEmpty()) {
                activeMovieId = null
                activeMovieDetails = null
                hero.position.text = "0 / 0"
                hero.title.text = if (movies.isEmpty()) "Nenhum filme nesta categoria" else "Nenhum resultado"
                hero.metadata.text = ""
                hero.cast.text = ""
                hero.director.text = ""
                hero.plot.text = if (movies.isEmpty()) "Esta categoria não tem títulos disponíveis." else "Tente outro termo de busca."
                hero.fullName.text = ""
                hero.fullName.visibility = View.GONE
                hero.displayedArtworkUrl = null
                LunaArtworkLoader.into(null, hero.backdrop, 1200)
                grid.addView(stateLabel(if (movies.isEmpty()) "Esta categoria não tem filmes disponíveis." else "Nenhum título corresponde à busca."))
                return
            }
            showMovie(found.first(), initialDetails?.takeIf { found.first().id == initialDetailsMovieId }, 1, total)
            onMovieFocus(found.first()) { details ->
                if (activeMovieId == found.first().id) {
                    activeMovieDetails = details
                    renderMovieCatalogHero(hero, found.first(), details, 1, total)
                }
            }
            val gridTiles = mutableListOf<View>()
            found.chunked(GRID_COLUMNS).forEachIndexed { rowIndex, rowItems ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                rowItems.forEachIndexed { columnIndex, movie ->
                    val position = rowIndex * GRID_COLUMNS + columnIndex + 1
                    val tile = catalogTile(
                        title = movie.title,
                        imageUrl = movie.posterUrl,
                        rating = movie.rating,
                        onClick = { onMovie(movie) },
                        onFocus = { focused -> if (focused) focusMovie(movie, position, total) }
                    ).apply {
                        id = View.generateViewId()
                        if (columnIndex == 0) tag = CATALOG_LEFT_EDGE_TAG
                    }
                    gridTiles += tile
                    row.addView(tile, LinearLayout.LayoutParams(dp(CATALOG_POSTER_WIDTH_DP), dp(CATALOG_TILE_HEIGHT_DP)).apply {
                        marginEnd = dp(CATALOG_POSTER_GAP_DP)
                    })
                }
                val horizontalRow = HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    isFocusable = false
                    isSmoothScrollingEnabled = true
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    addView(row, FrameLayout.LayoutParams(-2, dp(CATALOG_TILE_HEIGHT_DP)))
                }
                grid.addView(horizontalRow, LinearLayout.LayoutParams(-1, dp(CATALOG_TILE_HEIGHT_DP)).apply {
                    bottomMargin = dp(8)
                })
            }
            gridTiles.forEachIndexed { index, tile ->
                val column = index % GRID_COLUMNS
                if (column > 0) tile.nextFocusLeftId = gridTiles[index - 1].id
                if (column < GRID_COLUMNS - 1 && index + 1 < gridTiles.size) {
                    tile.nextFocusRightId = gridTiles[index + 1].id
                }
                if (index - GRID_COLUMNS >= 0) tile.nextFocusUpId = gridTiles[index - GRID_COLUMNS].id
                if (index + GRID_COLUMNS < gridTiles.size) tile.nextFocusDownId = gridTiles[index + GRID_COLUMNS].id
                if (column == 0) categoryPanel.selected?.let { tile.nextFocusLeftId = it.id }
            }
            categoryPanel.items.forEach { it.nextFocusRightId = gridTiles.firstOrNull()?.id ?: View.NO_ID }
            if (total > MAX_CATALOG_ITEMS) {
                grid.addView(stateLabel("Exibindo os primeiros $MAX_CATALOG_ITEMS títulos. Use a busca para filtrar."))
            }
        }
        searchButton.setOnClickListener {
            val input = EditText(context).apply {
                hint = "Digite o título do filme"
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                setText(activeFilter)
                setSelection(text.length)
                setTextColor(WHITE)
                setHintTextColor(SECONDARY)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = rounded(0xFF0E367B.toInt(), 0xFF72B8FF.toInt(), 10)
            }
            AlertDialog.Builder(context)
                .setTitle("Buscar no catálogo")
                .setView(input)
                .setPositiveButton("Buscar") { _, _ ->
                    activeFilter = input.text.toString().trim()
                    populate(activeFilter)
                    gridScroll.scrollTo(0, 0)
                }
                .setNeutralButton("Limpar") { _, _ ->
                    activeFilter = ""
                    populate("")
                    gridScroll.scrollTo(0, 0)
                }
                .setNegativeButton("Cancelar", null)
                .show()
                .window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            input.requestFocus()
        }
        populate("")
        return catalogShell(selectedNav, categoryPanel.view, categoryPanel.selected, content)
    }

    fun seriesCatalog(
        title: String,
        selectedNav: String,
        categories: List<CatalogEntry>,
        selectedCategoryId: Int?,
        series: List<SeriesEntry>,
        onCategory: (CatalogEntry) -> Unit,
        onSeries: (SeriesEntry) -> Unit
    ): View {
        val categoryPanel = categoryColumn("CATEGORIAS", categories, selectedCategoryId, onCategory)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(20), dp(22))
            background = catalogSurface()
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        content.addView(pageTitle(title).apply {
            textSize = 27f
            setPadding(0, dp(4), 0, dp(8))
        })
        val (seriesHero, updateSeriesHero) = seriesCatalogHero(series.firstOrNull())
        content.addView(seriesHero, LinearLayout.LayoutParams(-1, dp(224)).apply { bottomMargin = dp(8) })
        content.addView(sectionTitle("Séries nesta categoria").apply {
            textSize = 18f
            setPadding(0, dp(4), 0, dp(5))
        })
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val gridScroll = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
            addView(grid, FrameLayout.LayoutParams(-1, -2))
        }
        content.addView(gridScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val seriesTiles = mutableListOf<View>()
        if (series.isEmpty()) {
            grid.addView(stateLabel("Nenhuma série foi informada pelo catálogo."))
        } else {
            series.take(MAX_CATALOG_ITEMS).chunked(GRID_COLUMNS).forEachIndexed { rowIndex, rowItems ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                rowItems.forEachIndexed { columnIndex, item ->
                    val tile = catalogTile(
                        title = item.title,
                        imageUrl = item.coverUrl,
                        rating = item.rating,
                        onClick = { onSeries(item) },
                        onFocus = { focused -> if (focused) updateSeriesHero(item) }
                    ).apply {
                        id = View.generateViewId()
                        if (columnIndex == 0) tag = CATALOG_LEFT_EDGE_TAG
                    }
                    seriesTiles += tile
                    row.addView(tile, LinearLayout.LayoutParams(dp(CATALOG_POSTER_WIDTH_DP), dp(CATALOG_TILE_HEIGHT_DP)).apply {
                        marginEnd = dp(CATALOG_POSTER_GAP_DP)
                    })
                }
                val horizontalRow = HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    isFocusable = false
                    isSmoothScrollingEnabled = true
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    addView(row, FrameLayout.LayoutParams(-2, dp(CATALOG_TILE_HEIGHT_DP)))
                }
                grid.addView(horizontalRow, LinearLayout.LayoutParams(-1, dp(CATALOG_TILE_HEIGHT_DP)).apply {
                    bottomMargin = dp(8)
                })
            }
        }
        seriesTiles.forEachIndexed { index, tile ->
            val column = index % GRID_COLUMNS
            if (column > 0) tile.nextFocusLeftId = seriesTiles[index - 1].id
            if (column < GRID_COLUMNS - 1 && index + 1 < seriesTiles.size) tile.nextFocusRightId = seriesTiles[index + 1].id
            if (index - GRID_COLUMNS >= 0) tile.nextFocusUpId = seriesTiles[index - GRID_COLUMNS].id
            if (index + GRID_COLUMNS < seriesTiles.size) tile.nextFocusDownId = seriesTiles[index + GRID_COLUMNS].id
            if (column == 0) categoryPanel.selected?.let { tile.nextFocusLeftId = it.id }
        }
        categoryPanel.items.forEach { it.nextFocusRightId = seriesTiles.firstOrNull()?.id ?: View.NO_ID }
        return catalogShell(selectedNav, categoryPanel.view, categoryPanel.selected, content)
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
        val fullName: TextView,
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
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xD9082E91.toInt(), 0xD906287F.toInt()))
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
                background = rounded(if (selected) 0xFFF4F8FF.toInt() else 0x442A7DDF.toInt(),
                    if (selected) 0xFFB9DCFF.toInt() else 0x6639A7FF.toInt(), 11)
                setOnFocusChangeListener { view, focused ->
                    view.background = rounded(
                        when { focused -> 0xFF39A0F4.toInt(); selected -> 0xFFF4F8FF.toInt(); else -> 0x442A7DDF.toInt() },
                        if (focused || selected) 0xFFE4F3FF.toInt() else 0x6639A7FF.toInt(), 11)
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
        body: View
    ): View {
        var focusZone = "categories"
        var applyFocusZone: (String) -> Unit = {}
        var requestCategoryFocus: () -> Unit = {}
        var requestMenuFocus: () -> Unit = {}
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when {
                        event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusZone == "content" &&
                            findFocus()?.tag == CATALOG_LEFT_EDGE_TAG -> {
                            requestCategoryFocus()
                            return true
                        }
                        event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && focusZone == "categories" -> {
                            applyFocusZone("menu")
                            requestMenuFocus()
                            return true
                        }
                        event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && focusZone == "menu" -> {
                            requestCategoryFocus()
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
            focusZone = zone
            val menuExpanded = zone == "menu"
            nav.setCompact(!menuExpanded)
            navLayout.width = if (menuExpanded) sidebarWidth else dp(COLLAPSED_SIDEBAR_WIDTH_DP)
            nav.view.layoutParams = navLayout

            val categoriesExpanded = zone == "categories"
            categoryPanel.visibility = if (categoriesExpanded) View.VISIBLE else View.GONE
            categoryLayout.width = if (categoriesExpanded) categoryPaneWidth else 0
            categoryPanel.layoutParams = categoryLayout
            sidePanels.requestLayout()
        }
        requestCategoryFocus = {
            applyFocusZone("categories")
            root.post { selectedCategory?.requestFocus() }
        }
        requestMenuFocus = { root.post { nav.selectedItem?.requestFocus() } }
        val focusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            when {
                newFocus == null -> Unit
                isDescendant(nav.view, newFocus) && focusZone != "menu" -> applyFocusZone("menu")
                isDescendant(categoryPanel, newFocus) && focusZone != "categories" -> applyFocusZone("categories")
                isDescendant(body, newFocus) && focusZone != "content" -> applyFocusZone("content")
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
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

    private fun movieCatalogHero(): MovieCatalogHeroBinding {
        val root = FrameLayout(context).apply {
            background = rounded(0xFF08245D.toInt(), Color.TRANSPARENT, 0)
        }
        val backdrop = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xF5071C3A.toInt(), 0xC8071C3A.toInt(), 0x44071C3A.toInt(), 0x11071C3A.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00000000, 0x44031B48.toInt(), 0xDD031126.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(context).apply {
            textSize = 28f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
        }
        val metadata = TextView(context).apply { textSize = 15f; setTextColor(0xFFE3F0FF.toInt()); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
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
        val cast = TextView(context).apply { textSize = 14f; setTextColor(0xFFE3F0FF.toInt()); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val director = TextView(context).apply { textSize = 14f; setTextColor(0xFFE3F0FF.toInt()); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val plot = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFFF0F5FB.toInt())
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val fullName = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFFD7E7F7.toInt())
        }
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(
                dp(20),
                dp(54),
                dp((screenWidthDp * 0.32f).toInt()),
                dp(10)
            )
        }
        block.addView(title, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        block.addView(metadataLine, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) })
        block.addView(cast, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        block.addView(director, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) })
        block.addView(plot, LinearLayout.LayoutParams(-1, -2))
        block.addView(fullName, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        root.addView(block, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.START))
        val position = TextView(context).apply {
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(WHITE)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rounded(0x99031B48.toInt(), Color.TRANSPARENT, 8)
        }
        return MovieCatalogHeroBinding(root, backdrop, title, position, metadata, formatLogos, cast, director, plot, fullName)
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

    private fun renderMovieCatalogHero(binding: MovieCatalogHeroBinding, movie: CatalogEntry, details: MovieDetails?, position: Int, total: Int) {
        binding.title.text = details?.title?.takeIf { it.isNotBlank() } ?: movie.title
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
        binding.plot.text = details?.plot.orEmpty()
        binding.fullName.text = "Título completo: ${movie.title}"
        binding.cast.visibility = if (binding.cast.text.isBlank()) View.GONE else View.VISIBLE
        binding.director.visibility = if (binding.director.text.isBlank()) View.GONE else View.VISIBLE
        binding.plot.visibility = if (binding.plot.text.isBlank()) View.GONE else View.VISIBLE
        binding.fullName.visibility = if (movie.title.isBlank()) View.GONE else View.VISIBLE
        if (details == null) return
        val artworkUrl = details.backdropUrl?.takeIf { it.isNotBlank() }
            ?: details.posterUrl?.takeIf { it.isNotBlank() }
            ?: movie.posterUrl?.takeIf { it.isNotBlank() }
        if (artworkUrl != binding.displayedArtworkUrl) {
            binding.displayedArtworkUrl = artworkUrl
            LunaArtworkLoader.into(artworkUrl, binding.backdrop, 1100)
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
            LunaArtworkLoader.into(item.coverUrl, cover, 1100)
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
            setPadding(dp(14), dp(20), dp(12), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF07111D.toInt(), 0xFF030810.toInt()))
        }
        val brand = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(4), dp(24))
        }
        val logo = TextView(context).apply {
            text = "☾ LunaStream"
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(WHITE)
            maxLines = 1
        }
        val tagline = TextView(context).apply {
            text = "FILMES E SÉRIES"
            textSize = 9f
            letterSpacing = 0.22f
            setTextColor(CYAN)
            setPadding(dp(38), dp(2), 0, 0)
        }
        brand.addView(logo)
        brand.addView(tagline)
        nav.addView(brand)

        val menu = listOf(
            "Início" to "⌂", "Filmes" to "◉", "Séries" to "▣",
            "Continuar" to "◴", "Favoritos" to "♡", "Configurações" to "⚙"
        )
        val menuItems = mutableListOf<Triple<TextView, String, String>>()
        var activeView: View? = null
        menu.forEach { (name, icon) ->
            val item = TextView(context).apply {
                text = "$icon    $name"
                textSize = 17f
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
            nav.addView(item, LinearLayout.LayoutParams(-1, dp(50)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
                bottomMargin = dp(3)
            })
            menuItems += Triple(item, name, icon)
            if (name == selectedNav) activeView = item
        }
        nav.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        val foot = TextView(context).apply {
            text = "VOD • EXPERIÊNCIA DE CINEMA"
            textSize = 9f
            letterSpacing = 0.08f
            setTextColor(SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        nav.addView(foot)
        fun setCompact(compact: Boolean) {
            nav.setPadding(if (compact) dp(5) else dp(14), dp(20), if (compact) dp(5) else dp(12), dp(14))
            brand.setPadding(if (compact) dp(4) else dp(10), dp(6), dp(4), if (compact) dp(8) else dp(24))
            logo.text = if (compact) "☾" else "☾ LunaStream"
            logo.gravity = if (compact) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            tagline.visibility = if (compact) View.GONE else View.VISIBLE
            menuItems.forEach { (item, name, icon) ->
                item.text = if (compact) icon else "$icon    $name"
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

    private fun hero(data: LunaHomeData): View {
        val movie = data.featuredMovie
        val details = data.featuredDetails
        val frame = FrameLayout(context).apply {
            background = rounded(0xFF081421.toInt(), Color.TRANSPARENT, 0)
            clipToOutline = true
        }
        val height = dp((context.resources.configuration.screenHeightDp * 0.39f).toInt().coerceIn(300, 390))
        val artwork = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        LunaArtworkLoader.into(details?.backdropUrl ?: details?.posterUrl ?: movie?.posterUrl, artwork, 1500)
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
            setPadding(dp(30), dp(22), dp(40), dp(18))
        }
        val kicker = TextView(context).apply {
            text = when {
                data.loading -> "LUNASTREAM"
                movie?.addedAt != null -> "ADICIONADO RECENTEMENTE"
                movie != null -> "DESTAQUE DO CATÁLOGO"
                else -> "SUA BIBLIOTECA VOD"
            }
            textSize = 11f
            letterSpacing = 0.32f
            setTextColor(0xFFBFD8EA.toInt())
        }
        val title = TextView(context).apply {
            text = details?.title?.takeIf { it.isNotBlank() } ?: movie?.title ?: "LunaStream"
            textSize = 39f
            maxLines = 2
            setTextColor(WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(6))
        }
        val metadata = listOfNotNull(details?.year, details?.duration, details?.genre)
        if (metadata.isNotEmpty()) {
            textBlock.addView(metadataLine(metadata.joinToString("   •   ")))
        } else if (data.featuredCategory != null) {
            textBlock.addView(metadataLine(data.featuredCategory))
        }
        val description = details?.plot?.takeIf { it.isNotBlank() }
        textBlock.addView(kicker)
        textBlock.addView(title)
        if (description != null) {
            textBlock.addView(TextView(context).apply {
                text = description
                textSize = 16f
                setTextColor(0xFFE0EAF2.toInt())
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(2), dp(30), dp(10))
            })
        }

        val actionsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val play = actionButton("▶   Assistir", true) {
            movie?.let(actions.onPlay) ?: actions.onRetry()
        }
        actionsRow.addView(play, LinearLayout.LayoutParams(dp(160), dp(54)).apply { marginEnd = dp(12) })
        if (movie != null) {
            actionsRow.addView(actionButton("ⓘ   Detalhes", false) { actions.onMovieDetails(movie) },
                LinearLayout.LayoutParams(dp(150), dp(54)))
        }
        textBlock.addView(actionsRow)
        frame.addView(textBlock, FrameLayout.LayoutParams(-1, -1, Gravity.START))
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
        movies.take(HOME_ROW_LIMIT).forEach { movie ->
            row.addView(mediaCard(movie.title, movie.posterUrl, movie.rating) { onMovie(movie) },
                LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply { marginEnd = dp(12) })
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
        section.addView(sectionTitle("Séries em destaque"))
        if (series.isEmpty()) {
            section.addView(stateLabel("Nenhuma série disponível ou informada pelo provedor."))
            return section
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        series.take(HOME_ROW_LIMIT).forEach { item ->
            row.addView(mediaCard(item.title, item.coverUrl, item.rating) { actions.onSeriesDetails(item) },
                LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply { marginEnd = dp(12) })
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
        LunaArtworkLoader.into(imageUrl, image, 720)
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
                view.background = rounded(if (focused) 0xFF10263A.toInt() else Color.TRANSPARENT,
                    if (focused) CYAN else Color.TRANSPARENT, 10)
                view.animate().scaleX(if (focused) 1.025f else 1f).scaleY(if (focused) 1.025f else 1f)
                    .setDuration(150).start()
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
        LunaArtworkLoader.into(imageUrl, poster, 420)
        tile.addView(poster, LinearLayout.LayoutParams(-1, dp(CATALOG_POSTER_HEIGHT_DP)))
        val caption = TextView(context).apply {
            text = title
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(WHITE)
            setPadding(dp(6), dp(5), dp(5), 0)
        }
        tile.addView(caption, LinearLayout.LayoutParams(-1, dp(CATALOG_CAPTION_HEIGHT_DP)))
        if (!rating.isNullOrBlank() && rating != "0") {
            caption.contentDescription = "$title, nota informada pelo servidor $rating"
        }
        return tile
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
        const val CARD_WIDTH_DP = 160
        const val CARD_HEIGHT_DP = 246
        const val HOME_ROW_LIMIT = 16
        const val MAX_CATALOG_ITEMS = 80
        const val GRID_COLUMNS = 7
        const val CATALOG_POSTER_WIDTH_DP = 112
        const val CATALOG_POSTER_HEIGHT_DP = 168
        const val CATALOG_CAPTION_HEIGHT_DP = 26
        const val CATALOG_TILE_HEIGHT_DP = 200
        const val CATALOG_POSTER_GAP_DP = 8
        const val CATALOG_LEFT_EDGE_TAG = "lunastream.catalog.leftEdge"
        const val COLLAPSED_SIDEBAR_WIDTH_DP = 68
    }
}
