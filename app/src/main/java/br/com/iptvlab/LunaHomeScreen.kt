package br.com.iptvlab

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
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
    val featuredSeries: List<SeriesEntry> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null
)

data class LunaHomeActions(
    val onNavigate: (String) -> Unit,
    val onRetry: () -> Unit,
    val onPlay: (CatalogEntry) -> Unit,
    val onMovieDetails: (CatalogEntry) -> Unit,
    val onMovieCategory: (CatalogEntry) -> Unit,
    val onSeriesCategory: (CatalogEntry) -> Unit,
    val onSeriesDetails: (SeriesEntry) -> Unit
)

/** Native Views home shell. It deliberately leaves the existing Media3 player Activity intact. */
class LunaHomeScreen(private val context: Context, private val actions: LunaHomeActions) {
    private val density = context.resources.displayMetrics.density
    private val screenWidthDp = context.resources.configuration.screenWidthDp
    private val sidebarWidth = dp((screenWidthDp * 0.18f).toInt().coerceIn(190, 250))

    fun home(data: LunaHomeData): View {
        val content = verticalScroll()
        content.addView(hero(data))

        if (data.loading) {
            content.addView(stateLabel("Carregando catálogo do provedor…"))
        } else if (data.errorMessage != null) {
            content.addView(statePanel(data.errorMessage, "Tentar novamente", actions.onRetry))
        } else {
            // Continue assistindo fica oculto até existir progresso local real.
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
        onCategory: (CatalogEntry) -> Unit,
        onMovie: (CatalogEntry) -> Unit
    ): View {
        val content = verticalScroll()
        content.addView(pageTitle(title))
        if (categories.isNotEmpty()) content.addView(categoryChips(categories, selectedCategoryId, onCategory))

        val search = EditText(context).apply {
            hint = "Buscar título no catálogo"
            setSingleLine(true)
            textSize = 19f
            setTextColor(WHITE)
            setHintTextColor(SECONDARY)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = rounded(DARK_SURFACE, BORDER, 14)
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = "Buscar filme"
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(14)
            bottomMargin = dp(14)
            marginEnd = dp(22)
        })

        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { marginEnd = dp(18) })
        fun populate(filter: String) {
            grid.removeAllViews()
            val found = movies.asSequence()
                .filter { it.title.contains(filter, ignoreCase = true) }
                .take(MAX_CATALOG_ITEMS)
                .toList()
            if (found.isEmpty()) {
                grid.addView(stateLabel(if (movies.isEmpty()) "Esta categoria não tem filmes disponíveis." else "Nenhum título corresponde à busca."))
                return
            }
            found.chunked(GRID_COLUMNS).forEach { rowItems ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                rowItems.forEach { movie ->
                    row.addView(catalogTile(movie.title, movie.posterUrl, movie.rating) { onMovie(movie) },
                        LinearLayout.LayoutParams(0, dp(190), 1f).apply {
                            marginEnd = dp(10)
                            bottomMargin = dp(12)
                        })
                }
                repeat(GRID_COLUMNS - rowItems.size) {
                    row.addView(View(context), LinearLayout.LayoutParams(0, dp(190), 1f).apply { marginEnd = dp(10) })
                }
                grid.addView(row)
            }
            if (movies.size > MAX_CATALOG_ITEMS) {
                grid.addView(stateLabel("Exibindo os primeiros $MAX_CATALOG_ITEMS títulos. Use a busca para filtrar."))
            }
        }
        populate("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = populate(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        return shell(selectedNav, content)
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
        val content = verticalScroll()
        content.addView(pageTitle(title))
        if (categories.isNotEmpty()) content.addView(categoryChips(categories, selectedCategoryId, onCategory))
        if (series.isEmpty()) {
            content.addView(stateLabel("Nenhuma série foi informada pelo catálogo."))
        } else {
            val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            series.take(MAX_CATALOG_ITEMS).chunked(GRID_COLUMNS).forEach { rowItems ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                rowItems.forEach { item ->
                    row.addView(catalogTile(item.title, item.coverUrl, item.rating) { onSeries(item) },
                        LinearLayout.LayoutParams(0, dp(190), 1f).apply {
                            marginEnd = dp(10)
                            bottomMargin = dp(12)
                        })
                }
                repeat(GRID_COLUMNS - rowItems.size) {
                    row.addView(View(context), LinearLayout.LayoutParams(0, dp(190), 1f).apply { marginEnd = dp(10) })
                }
                grid.addView(row)
            }
            content.addView(grid)
        }
        return shell(selectedNav, content)
    }

    fun messageScreen(selectedNav: String, title: String, message: String, actionLabel: String, action: () -> Unit): View {
        val content = verticalScroll()
        content.addView(pageTitle(title))
        content.addView(statePanel(message, actionLabel, action))
        return shell(selectedNav, content)
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

    private fun sidebar(selectedNav: String): View {
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
            "Início" to "⌂", "Filmes" to "◉", "Séries" to "▣", "Filmes 4K" to "4K",
            "Continuar" to "◴", "Favoritos" to "♡", "Configurações" to "⚙"
        )
        var activeView: View? = null
        menu.forEach { (name, icon) ->
            val item = TextView(context).apply {
                text = "$icon    $name"
                textSize = 17f
                setTextColor(WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
                isFocusable = true
                isClickable = true
                contentDescription = name
                background = navBackground(name == selectedNav, false)
                setOnFocusChangeListener { view, focused ->
                    view.background = navBackground(name == selectedNav, focused)
                    view.animate().scaleX(if (focused) 1.025f else 1f).scaleY(if (focused) 1.025f else 1f)
                        .setDuration(150).start()
                }
                setOnClickListener { actions.onNavigate(name) }
            }
            nav.addView(item, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(4) })
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
        activeView?.post { if (activeView?.hasFocus() != true) activeView?.requestFocus() }
        return nav
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

    private fun catalogTile(title: String, imageUrl: String?, rating: String?, onClick: () -> Unit): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            isFocusable = true
            isClickable = true
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = rounded(Color.TRANSPARENT, Color.TRANSPARENT, 10)
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) 0xFF10263A.toInt() else Color.TRANSPARENT,
                    if (focused) CYAN else Color.TRANSPARENT, 10)
                view.animate().scaleX(if (focused) 1.035f else 1f).scaleY(if (focused) 1.035f else 1f)
                    .setDuration(150).start()
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
        tile.addView(poster, LinearLayout.LayoutParams(-1, dp(126)))
        val caption = TextView(context).apply {
            text = title
            textSize = 14f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(WHITE)
            setPadding(dp(6), dp(6), dp(6), 0)
        }
        tile.addView(caption, LinearLayout.LayoutParams(-1, dp(42)))
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
            focused -> 0xFF163B5B.toInt()
            selected -> 0xFF102B44.toInt()
            else -> Color.TRANSPARENT
        }
        val stroke = if (focused || selected) CYAN else Color.TRANSPARENT
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
        const val CARD_WIDTH_DP = 236
        const val CARD_HEIGHT_DP = 132
        const val HOME_ROW_LIMIT = 16
        const val MAX_CATALOG_ITEMS = 80
        const val GRID_COLUMNS = 4
    }
}
