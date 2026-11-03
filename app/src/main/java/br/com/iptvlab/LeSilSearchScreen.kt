package br.com.iptvlab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.Normalizer
import java.util.Locale

/** One TV-friendly search surface: remote keyboard and live, vertically oriented posters. */
class LeSilSearchScreen(
    private val context: Context,
    movies: List<CatalogEntry>,
    series: List<SeriesEntry>,
    private val movieProgress: Map<Int, WatchProgress>,
    initialQuery: String,
    private val onQueryChanged: (String) -> Unit,
    private val onMovie: (CatalogEntry) -> Unit,
    private val onSeries: (SeriesEntry) -> Unit,
    private val onHoldMovie: (CatalogEntry) -> Unit,
    private val onHoldSeries: (SeriesEntry) -> Unit,
    private val onBack: () -> Unit,
    private val focusMovieId: Int? = null,
    private val focusSeriesId: Int? = null
) {
    private data class Result(
        val title: String,
        val posterUrl: String?,
        val normalizedTitle: String,
        val movie: CatalogEntry? = null,
        val series: SeriesEntry? = null
    )

    private data class TileHolder(
        val poster: ImageView,
        val caption: TextView,
        val kind: TextView,
        val progress: ProgressStrip
    )

    private val density = context.resources.displayMetrics.density
    private val screenWidthDp = context.resources.configuration.screenWidthDp
    private val shortScreen = context.resources.configuration.screenHeightDp < 500
    private val railWidthDp = 54
    private val keyboardWidthDp = if (shortScreen) 226 else 250
    private val posterWidthDp = if (shortScreen) 94 else 108
    private val posterHeightDp = (posterWidthDp * 1.5f).toInt()
    private val tileHeightDp = posterHeightDp + 28
    private val resultColumns = ((screenWidthDp - railWidthDp - keyboardWidthDp - 42 + 8) /
        (posterWidthDp + 8)).coerceIn(3, 6)
    private val movieResults = movies.map { Result(it.title, it.posterUrl, normalize(it.title), movie = it) }
    private val seriesResults = series.map { Result(it.title, it.coverUrl, normalize(it.title), series = it) }
    private val catalog = movieResults + seriesResults
    private val suggestions = buildList {
        for (index in 0 until 20) {
            movieResults.getOrNull(index)?.let(::add)
            if (index % 2 == 0) seriesResults.getOrNull(index / 2)?.let(::add)
        }
    }
    private var query = initialQuery.take(80)
    private var visibleResults: List<Result> = emptyList()
    private val keyboardRows = mutableListOf<List<TextView>>()
    private var lastKeyboardKey: TextView? = null
    private lateinit var backButton: TextView
    private lateinit var queryLabel: TextView
    private lateinit var resultsLabel: TextView
    private lateinit var emptyLabel: TextView
    private lateinit var resultsGrid: GridView
    private lateinit var resultsAdapter: BaseAdapter
    private var holdTile: View? = null
    private var holdResult: Result? = null
    private var holdRunnable: Runnable? = null
    private var holdTriggered = false
    private var posterRefreshGeneration = 0

    fun create(): View {
        val root = object : LinearLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && !event.isCtrlPressed && !event.isAltPressed) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DEL -> { changeQuery(query.dropLast(1)); return true }
                        KeyEvent.KEYCODE_SPACE -> { changeQuery("$query "); return true }
                        else -> {
                            val character = event.unicodeChar
                            if (character > 0 && character.toChar().isLetterOrDigit()) {
                                changeQuery(query + character.toChar())
                                return true
                            }
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF07162D.toInt(), 0xFF071323.toInt(), 0xFF050B18.toInt()))
        }

        val rail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(22), dp(6), dp(12))
            setBackgroundColor(0xFF0A2A62.toInt())
        }
        rail.addView(ImageView(context).apply {
            setImageResource(R.drawable.lesil_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Le&Sil Stream"
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { bottomMargin = dp(24) })
        rail.addView(ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
            contentDescription = "Busca"
            background = rounded(0xFF1274DC.toInt(), 0xFF78C8FF.toInt(), 10)
            setPadding(dp(11), dp(11), dp(11), dp(11))
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        rail.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        backButton = keyButton("Voltar") { onBack() }.apply {
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) Color.WHITE else 0xFF12386F.toInt(),
                    if (focused) Color.WHITE else 0xFF406BAA.toInt(), 5)
                (view as TextView).setTextColor(if (focused) 0xFF081D3C.toInt() else Color.WHITE)
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    keyboardRows.lastOrNull()?.firstOrNull()?.requestFocus() ?: false
                } else false
            }
        }
        rail.addView(backButton, LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(rail, LinearLayout.LayoutParams(dp(railWidthDp), -1))

        val keyboardPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(25), dp(8), dp(10))
        }
        keyboardPane.addView(TextView(context).apply {
            text = "BUSCAR"
            textSize = 11f
            letterSpacing = 0.12f
            setTextColor(0xFFB9D5EE.toInt())
        }, LinearLayout.LayoutParams(-1, dp(25)))
        val labels = listOf(
            listOf("a", "b", "c", "d", "e", "f"),
            listOf("g", "h", "i", "j", "k", "l"),
            listOf("m", "n", "o", "p", "q", "r"),
            listOf("s", "t", "u", "v", "w", "x"),
            listOf("y", "z", "1", "2", "3", "4"),
            listOf("5", "6", "7", "8", "9", "0"),
            listOf("⌫", "Espaço", "Limpar")
        )
        labels.forEach { rowLabels ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val keys = rowLabels.map { label ->
                keyButton(label) {
                    when (label) {
                        "⌫" -> changeQuery(query.dropLast(1))
                        "Espaço" -> changeQuery("$query ")
                        "Limpar" -> changeQuery("")
                        else -> changeQuery(query + label)
                    }
                }.also { key ->
                    row.addView(key, LinearLayout.LayoutParams(0, dp(if (shortScreen) 35 else 41), 1f)
                        .apply { marginEnd = dp(3); bottomMargin = dp(3) })
                }
            }
            keyboardRows.add(keys)
            keyboardPane.addView(row)
        }
        wireKeyboardNavigation()
        root.addView(keyboardPane, LinearLayout.LayoutParams(dp(keyboardWidthDp), -1))

        val resultPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(18), dp(10), dp(8))
        }
        queryLabel = TextView(context).apply {
            textSize = if (shortScreen) 20f else 25f
            setTextColor(Color.WHITE)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.START
        }
        resultPane.addView(queryLabel, LinearLayout.LayoutParams(-1, dp(37)))
        resultsLabel = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFFDCEBFA.toInt())
            setPadding(0, dp(1), 0, dp(7))
        }
        resultPane.addView(resultsLabel, LinearLayout.LayoutParams(-1, dp(28)))
        val resultArea = FrameLayout(context)
        resultsGrid = GridView(context).apply {
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            numColumns = resultColumns
            columnWidth = dp(posterWidthDp)
            horizontalSpacing = dp(8)
            verticalSpacing = dp(7)
            stretchMode = GridView.NO_STRETCH
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(2), dp(2), dp(2), dp(12))
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    lastKeyboardKey?.requestFocus() ?: false
                } else false
            }
        }
        resultsAdapter = object : BaseAdapter() {
            override fun getCount(): Int = visibleResults.size
            override fun getItem(position: Int): Result = visibleResults[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                bindTile(convertView, getItem(position), position)
        }
        resultsGrid.adapter = resultsAdapter
        resultsGrid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) schedulePosterRefresh()
            }

            override fun onScroll(view: AbsListView, firstVisibleItem: Int,
                                  visibleItemCount: Int, totalItemCount: Int) {
                schedulePosterRefresh()
            }
        })
        resultArea.addView(resultsGrid, FrameLayout.LayoutParams(-1, -1))
        emptyLabel = TextView(context).apply {
            text = "Nenhum título encontrado. Tente outro termo."
            textSize = 16f
            setTextColor(0xFFB8CEE2.toInt())
            visibility = View.GONE
        }
        resultArea.addView(emptyLabel, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        resultPane.addView(resultArea, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(resultPane, LinearLayout.LayoutParams(0, -1, 1f))

        renderResults()
        val focusPosition = visibleResults.indexOfFirst { item ->
            (focusMovieId != null && item.movie?.id == focusMovieId) ||
                (focusSeriesId != null && item.series?.id == focusSeriesId)
        }
        if (focusPosition >= 0) {
            resultsGrid.post { focusResultAt(focusPosition) }
        } else keyboardRows.firstOrNull()?.firstOrNull()?.post { keyboardRows.first()[0].requestFocus() }
        return root
    }

    private fun wireKeyboardNavigation() {
        keyboardRows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { column, key ->
                key.setOnFocusChangeListener { view, focused ->
                    view.background = rounded(if (focused) Color.WHITE else 0xFF12386F.toInt(),
                        if (focused) Color.WHITE else 0xFF406BAA.toInt(), 5)
                    (view as TextView).setTextColor(if (focused) 0xFF081D3C.toInt() else Color.WHITE)
                    if (focused) lastKeyboardKey = view
                }
                key.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (column > 0) row[column - 1].requestFocus()
                            else backButton.requestFocus()
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (column < row.lastIndex) row[column + 1].requestFocus()
                            else focusFirstResult()
                        KeyEvent.KEYCODE_DPAD_UP -> if (rowIndex > 0)
                            keyboardRows[rowIndex - 1][column.coerceAtMost(keyboardRows[rowIndex - 1].lastIndex)].requestFocus()
                            else true
                        KeyEvent.KEYCODE_DPAD_DOWN -> if (rowIndex < keyboardRows.lastIndex)
                            keyboardRows[rowIndex + 1][column.coerceAtMost(keyboardRows[rowIndex + 1].lastIndex)].requestFocus()
                            else true
                        else -> false
                    }
                }
            }
        }
    }

    private fun focusFirstResult(): Boolean = focusResultAt(0)

    private fun focusResultAt(position: Int, attempt: Int = 0): Boolean {
        if (visibleResults.isEmpty()) return true
        val target = position.coerceIn(0, visibleResults.lastIndex)
        val child = resultsGrid.getChildAt(target - resultsGrid.firstVisiblePosition)
        val fullyVisible = child?.isShown == true && child.top >= resultsGrid.paddingTop &&
            child.bottom <= resultsGrid.height - resultsGrid.paddingBottom
        if (fullyVisible && child.requestFocus()) return true
        if (attempt == 0) resultsGrid.setSelection(target)
        if (attempt < 20) {
            resultsGrid.requestFocus()
            resultsGrid.postDelayed({ focusResultAt(target, attempt + 1) }, 50)
        }
        return true
    }

    private fun changeQuery(next: String) {
        val limited = next.take(80)
        if (query == limited) return
        query = limited
        onQueryChanged(query)
        renderResults()
    }

    private fun renderResults() {
        val needle = normalize(query.trim())
        visibleResults = if (needle.isEmpty()) suggestions else {
            val matches = catalog.filter { it.normalizedTitle.contains(needle) }
            matches.filter { it.normalizedTitle.startsWith(needle) } +
                matches.filterNot { it.normalizedTitle.startsWith(needle) }
        }
        queryLabel.text = if (query.isBlank()) "⌕  Encontre filmes e séries" else "⌕  $query"
        resultsLabel.text = if (needle.isEmpty()) "Recomendados para você"
            else "Resultados · ${visibleResults.size} títulos"
        emptyLabel.visibility = if (visibleResults.isEmpty()) View.VISIBLE else View.GONE
        resultsAdapter.notifyDataSetChanged()
        resultsGrid.setSelection(0)
        schedulePosterRefresh()
    }

    private fun schedulePosterRefresh() {
        val request = ++posterRefreshGeneration
        resultsGrid.postDelayed({ if (request == posterRefreshGeneration) refreshVisiblePosters() }, 400)
        resultsGrid.postDelayed({ if (request == posterRefreshGeneration) refreshVisiblePosters() }, 2200)
        resultsGrid.postDelayed({ if (request == posterRefreshGeneration) refreshVisiblePosters() }, 7000)
    }

    private fun refreshVisiblePosters() {
        val first = resultsGrid.firstVisiblePosition
        for (index in 0 until resultsGrid.childCount) {
            val position = first + index
            val result = visibleResults.getOrNull(position) ?: continue
            val holder = resultsGrid.getChildAt(index)?.tag as? TileHolder ?: continue
            if (holder.poster.drawable == null && !result.posterUrl.isNullOrBlank()) {
                LeSilArtworkLoader.into(result.posterUrl, holder.poster, 420,
                    priority = position % resultColumns == 0)
            }
        }
    }

    private fun bindTile(convertView: View?, item: Result, position: Int): View {
        val tile = (convertView as? LinearLayout) ?: createTile()
        // GridView may rebind its selected first child while OK is held.
        // Keep the timer if the tile still represents the same result.
        if (holdTile === tile && holdResult != item) finishHold(tile)
        val holder = tile.tag as TileHolder
        LeSilArtworkLoader.into(item.posterUrl, holder.poster, 420,
            priority = position % resultColumns == 0)
        holder.caption.text = item.title
        holder.caption.isSelected = false
        holder.kind.text = if (item.movie != null) "FILME" else "SÉRIE"
        holder.progress.setProgress(item.movie?.let { movieProgress[it.id] })
        tile.contentDescription = "${if (item.movie != null) "Filme" else "Série"}: ${item.title}"
        tile.setOnClickListener {
            if (holdTile !== tile || !holdTriggered) {
                item.movie?.let(onMovie) ?: item.series?.let(onSeries)
            }
        }
        tile.setOnFocusChangeListener { view, focused ->
            view.background = rounded(if (focused) 0xFF123466.toInt() else Color.TRANSPARENT,
                if (focused) Color.WHITE else Color.TRANSPARENT, 9)
            holder.caption.ellipsize = if (focused) TextUtils.TruncateAt.MARQUEE else TextUtils.TruncateAt.END
            holder.caption.isSelected = focused
            if (focused) LeSilArtworkLoader.into(item.posterUrl, holder.poster, 420, priority = true)
            else if (!holdTriggered && holdTile === tile) finishHold(tile)
        }
        tile.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                            startHold(tile, item)
                            true
                        } else true
                        KeyEvent.ACTION_UP -> {
                            val wasHolding = holdTile === tile
                            val triggered = finishHold(tile)
                            if (wasHolding && !triggered) tile.performClick()
                            true
                        }
                        else -> false
                    }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (event.action == KeyEvent.ACTION_DOWN) {
                    if (position % resultColumns == 0) {
                        lastKeyboardKey?.requestFocus() ?: keyboardRows.first()[0].requestFocus()
                    } else focusResultAt(position - 1)
                } else false
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.action == KeyEvent.ACTION_DOWN) {
                    if (position % resultColumns < resultColumns - 1 && position < visibleResults.lastIndex)
                        focusResultAt(position + 1)
                    else true
                } else false
                KeyEvent.KEYCODE_DPAD_UP -> if (event.action == KeyEvent.ACTION_DOWN) {
                    if (position >= resultColumns) focusResultAt(position - resultColumns) else true
                } else false
                KeyEvent.KEYCODE_DPAD_DOWN -> if (event.action == KeyEvent.ACTION_DOWN) {
                    if (position + resultColumns <= visibleResults.lastIndex)
                        focusResultAt(position + resultColumns)
                    else true
                } else false
                else -> false
            }
        }
        tile.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tile.requestFocus()
                    startHold(tile, item)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val wasHolding = holdTile === tile
                    val triggered = finishHold(tile)
                    if (wasHolding && !triggered && event.x >= 0 && event.x < tile.width &&
                        event.y >= 0 && event.y < tile.height) tile.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { finishHold(tile); true }
                MotionEvent.ACTION_MOVE -> {
                    if (event.x < -dp(10) || event.x > tile.width + dp(10) ||
                        event.y < -dp(10) || event.y > tile.height + dp(10)) finishHold(tile)
                    true
                }
                else -> true
            }
        }
        return tile.apply { layoutParams = AbsListView.LayoutParams(dp(posterWidthDp), dp(tileHeightDp)) }
    }

    private fun startHold(tile: View, item: Result) {
        holdTile?.let(::finishHold)
        holdTile = tile
        holdResult = item
        holdTriggered = false
        val request = Runnable {
            if (holdTile === tile && holdResult == item && item in visibleResults) {
                holdTriggered = true
                tile.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                item.movie?.let(onHoldMovie) ?: item.series?.let(onHoldSeries)
            }
        }
        holdRunnable = request
        resultsGrid.postDelayed(request, 3000)
    }

    private fun finishHold(tile: View): Boolean {
        if (holdTile !== tile) return false
        val triggered = holdTriggered
        holdRunnable?.let(resultsGrid::removeCallbacks)
        holdRunnable = null
        holdTile = null
        holdResult = null
        holdTriggered = false
        return triggered
    }

    private fun createTile(): LinearLayout {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val artwork = FrameLayout(context)
        val poster = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(0xFF102444.toInt(), 0xFF345B88.toInt(), 7)
            clipToOutline = true
        }
        artwork.addView(poster, FrameLayout.LayoutParams(-1, -1))
        val kind = TextView(context).apply {
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            background = rounded(0xD9081A35.toInt(), Color.TRANSPARENT, 3)
        }
        artwork.addView(kind, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            topMargin = dp(5); leftMargin = dp(5)
        })
        val progress = ProgressStrip(context)
        artwork.addView(progress, FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM))
        tile.addView(artwork, LinearLayout.LayoutParams(-1, dp(posterHeightDp)))
        val caption = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            marqueeRepeatLimit = -1
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(3), 0)
        }
        tile.addView(caption, LinearLayout.LayoutParams(-1, dp(24)))
        tile.tag = TileHolder(poster, caption, kind, progress)
        return tile
    }

    private fun keyButton(label: String, click: () -> Unit) = TextView(context).apply {
        text = label
        textSize = if (label.length > 1) 11f else 15f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(0xFF12386F.toInt(), 0xFF406BAA.toInt(), 5)
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setOnClickListener { click() }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.requestFocus()
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (event.x >= 0 && event.x < view.width && event.y >= 0 && event.y < view.height) {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    true
                }
                else -> true
            }
        }
        contentDescription = when (label) { "⌫" -> "Apagar"; else -> label }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * density).toInt()

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(ACCENT_MARKS, "").lowercase(Locale.ROOT)

    private companion object {
        val ACCENT_MARKS = Regex("\\p{Mn}+")
    }

    private class ProgressStrip(context: Context) : View(context) {
        private var fraction = 0f
        private var completed = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun setProgress(progress: WatchProgress?) {
            completed = progress?.completed == true
            fraction = when {
                completed -> 1f
                progress != null && progress.positionMs > 0 && progress.durationMs > 0 ->
                    (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 0.99f)
                progress != null && progress.positionMs > 0 -> 0.02f
                else -> 0f
            }
            visibility = if (fraction > 0f) VISIBLE else GONE
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            paint.color = if (completed) 0xFF23864A.toInt() else 0xFF1689F5.toInt()
            canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), paint)
        }
    }
}
