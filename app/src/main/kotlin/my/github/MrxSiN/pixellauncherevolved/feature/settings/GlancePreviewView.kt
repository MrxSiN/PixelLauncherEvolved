package my.github.MrxSiN.pixellauncherevolved.feature.settings

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.smartspace.SmartspaceTarget
import android.app.smartspace.uitemplatedata.BaseTemplateData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import java.util.Calendar
import java.util.Date
import java.util.Locale

import kotlin.math.abs
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellauncherevolved.R
import my.github.MrxSiN.pixellauncherevolved.feature.focus.recordedWallpaper
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceMoment
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlancePreview
import my.github.MrxSiN.pixellauncherevolved.feature.glance.content.GlanceWhen

/**
 * At a Glance over the top of the home wallpaper, as the home screen draws it: the list the launcher
 * last handed its At a Glance view ([GlancePreview]), this module's content included.
 *
 * - Pages swipe like the launcher's pager, with the wallpaper drifting behind them and the dots
 *   stretching as a page slides in; a tap moves to the next page. A change on the screen (a switch,
 *   an option) slides the new content in as soon as the home screen has it.
 * - **Live** shows the phone as it is. **Try** builds the list for a moment the person picks (a part
 *   of the day, music, headphones, charging, Wi-Fi, mobile data), so each "When to show" choice can
 *   be seen without waiting for it. Trying never changes the home screen.
 *
 * Drawn here rather than by the launcher's own view, which needs the Smartspace session around it:
 * text and icons as the launcher's template draws them, an app widget card as its remote views.
 */
internal class GlancePreviewView(private val ui: GlanceUi) : LinearLayout(ui.context) {

    private val context = ui.context
    private val backdrop = context.resources.displayMetrics.let { metrics ->
        Backdrop(recordedWallpaper(), ExpressiveRole.SURFACE_CONTAINER_HIGHEST.of(context), android.util.Size(metrics.widthPixels, metrics.heightPixels))
    }
    private val panel = FrameLayout(context)
    private val pager = Pager()
    private val dots = LinearLayout(context)
    private val badge = TextView(context)
    private val pulse = View(context)
    private val tryPanel = LinearLayout(context)
    private val timeChips = LinkedHashMap<String?, GlanceChip>()
    private val stateChips = LinkedHashMap<String, GlanceChip>()

    private var pages: List<SmartspaceTarget> = emptyList()
    private var signature = ""
    private var moment: GlanceMoment? = null
    private var period: String? = null

    /** A time picked in Demo with "Pick a time…", in minutes of the day; -1 when none. */
    private var picked = -1
    private lateinit var group: View
    private var pinned = false
    private var pulsing: ValueAnimator? = null

    private val update = Runnable { read(animate = true) }

    init {
        orientation = VERTICAL
        panel.background = backdrop
        panel.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, dp(32f).toFloat())
        }
        panel.clipToOutline = true
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(24f), dp(44f), dp(24f), dp(20f))
            addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, pageHeight()))
            addView(dots, LayoutParams(LayoutParams.WRAP_CONTENT, dp(12f)).apply { topMargin = dp(6f) })
        }
        panel.addView(column)
        panel.addView(badgeView(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(14f)
            marginEnd = dp(14f)
        })
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        group = GlanceButtonGroup(context, listOf(ui.string(R.string.glance_preview_live), ui.string(R.string.glance_preview_try)), 0) { index ->
            setTrying(index == 1)
        }
        addView(group, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })

        buildTryPanel()
        addView(tryPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        read(animate = false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GlancePreview.listen(update)
        read(animate = false)
        drawBadge()
    }

    override fun onDetachedFromWindow() {
        GlancePreview.unlisten(update)
        pulsing?.cancel()
        pulsing = null
        super.onDetachedFromWindow()
    }

    /** The panel's own height: what stays in view while the screen scrolls under it. */
    val panelHeight: Int get() = panel.height

    /**
     * While the screen scrolls past, the panel stays in view at the top ([GlanceScreen] moves this
     * view); the controls under it step aside so the tiles scrolling beneath stay usable, and the
     * panel lifts with a shadow.
     */
    fun setPinned(pinned: Boolean) {
        if (pinned == this.pinned) return
        this.pinned = pinned
        // Pinned, the page's colour fills behind the panel's upper half, so rows scrolling under it
        // never show through its rounded corners at the top.
        band.panelHeight = panel.height
        if (pinned) {
            // The bar is often still collapsing as the panel pins: read its colour again once it settles.
            for (delay in BAR_SAMPLE_DELAYS) postDelayed({ if (this.pinned) titleBarColor()?.let { band.color = it } }, delay)
        }
        band.fade(pinned)
        panel.animate().translationZ(if (pinned) dp(3f).toFloat() else 0f).setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.MOVE).start()
        for (view in arrayOf(group, tryPanel)) {
            if (view === tryPanel && moment == null) continue
            if (pinned) {
                view.animate().alpha(0f).setDuration(GlanceMotion.SHORT).withEndAction { if (this.pinned) view.visibility = INVISIBLE }.start()
            } else {
                view.visibility = VISIBLE
                view.animate().alpha(1f).setDuration(GlanceMotion.MEDIUM).start()
            }
        }
    }

    // ---- Live and Try ------------------------------------------------------------------------

    private fun badgeView(): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(12f).toFloat(); setColor(0x59000000) }
        setPadding(dp(10f), dp(4f), dp(12f), dp(4f))
        addView(pulse.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        }, LayoutParams(dp(8f), dp(8f)).apply { marginEnd = dp(6f) })
        addView(badge.apply {
            ExpressiveType.LABEL_MEDIUM.applyTo(this, ExpressiveRole.ON_SURFACE)
            setTextColor(Color.WHITE)
        })
    }

    private fun drawBadge() {
        val live = moment == null
        badge.text = ui.string(if (live) R.string.glance_preview_badge_live else R.string.glance_preview_badge_try)
        (pulse.background as GradientDrawable).setColor(if (live) LIVE_DOT else Color.WHITE)
        pulsing?.cancel()
        pulse.alpha = 1f
        if (live && isAttachedToWindow) {
            pulsing = ValueAnimator.ofFloat(1f, 0.25f).apply {
                duration = 900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { pulse.alpha = it.animatedValue as Float }
                start()
            }
        }
    }

    private fun buildTryPanel() {
        tryPanel.orientation = VERTICAL
        tryPanel.visibility = GONE
        tryPanel.setPadding(dp(4f), dp(8f), dp(4f), 0)
        fun heading(text: Int) = TextView(context).apply {
            this.text = ui.string(text)
            ExpressiveType.LABEL_LARGE.applyTo(this, ExpressiveRole.ON_SURFACE_VARIANT)
            setPadding(dp(4f), dp(12f), 0, dp(8f))
        }
        tryPanel.addView(heading(R.string.glance_try_time))
        val times = ChipFlow(context, dp(8f))
        for (value in listOf(null, GlanceWhen.MORNING, GlanceWhen.AFTERNOON, GlanceWhen.EVENING, GlanceWhen.NIGHT)) {
            val chip = GlanceChip(context, ui.string(if (value == null) R.string.glance_try_now else GlanceSourceDialog.shortLabel(value)))
            chip.setOnClickListener {
                period = value
                picked = -1
                timeChips.forEach { (key, other) -> other.select(key == value) }
                retry()
            }
            timeChips[value] = chip
            times.addView(chip)
        }
        // Any minute of the day, to try a custom time's edges.
        val pick = GlanceChip(context, ui.string(R.string.glance_try_pick))
        pick.setOnClickListener {
            val start = if (picked >= 0) picked else minuteNow()
            android.app.TimePickerDialog(context, { _, hour, minute ->
                picked = hour * 60 + minute
                period = null
                timeChips.forEach { (_, other) -> other.select(false) }
                pick.label(GlanceSourceDialog.clock(context, picked))
                pick.select(true)
                retry()
            }, start / 60, start % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
        }
        timeChips[PICK] = pick
        times.addView(pick)
        tryPanel.addView(times)
        tryPanel.addView(heading(R.string.glance_try_state))
        val states = ChipFlow(context, dp(8f))
        for (value in listOf(GlanceWhen.MUSIC, GlanceWhen.HEADPHONES, GlanceWhen.CHARGING, GlanceWhen.WIFI, GlanceWhen.MOBILE)) {
            val chip = GlanceChip(context, ui.string(GlanceSourceDialog.shortLabel(value)))
            chip.setOnClickListener {
                chip.select(!chip.selectedChip)
                retry()
            }
            stateChips[value] = chip
            states.addView(chip)
        }
        tryPanel.addView(states)
    }

    private fun setTrying(trying: Boolean) {
        (parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it, AutoTransition().setDuration(GlanceMotion.MEDIUM).setInterpolator(GlanceMotion.MOVE)) }
        if (trying) {
            // Start from the phone as it is, so the first change is the one the person makes.
            val now = GlancePreview.now() ?: GlanceMoment(minuteNow())
            period = null
            picked = -1
            timeChips[PICK]?.label(ui.string(R.string.glance_try_pick))
            timeChips.forEach { (key, chip) -> chip.select(key == null, animate = false) }
            stateChips[GlanceWhen.MUSIC]?.select(now.music, animate = false)
            stateChips[GlanceWhen.HEADPHONES]?.select(now.headphones, animate = false)
            stateChips[GlanceWhen.CHARGING]?.select(now.charging, animate = false)
            stateChips[GlanceWhen.WIFI]?.select(now.wifi, animate = false)
            stateChips[GlanceWhen.MOBILE]?.select(now.mobile, animate = false)
            tryPanel.visibility = VISIBLE
            retry()
        } else {
            tryPanel.visibility = GONE
            moment = null
            read(animate = true)
        }
        drawBadge()
    }

    private fun retry() {
        moment = GlanceMoment(
            minuteOfDay = if (picked >= 0) picked else period?.let { GlanceMoment.minuteOf(it) } ?: minuteNow(),
            dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY,
            music = stateChips[GlanceWhen.MUSIC]?.selectedChip == true,
            headphones = stateChips[GlanceWhen.HEADPHONES]?.selectedChip == true,
            charging = stateChips[GlanceWhen.CHARGING]?.selectedChip == true,
            wifi = stateChips[GlanceWhen.WIFI]?.selectedChip == true,
            mobile = stateChips[GlanceWhen.MOBILE]?.selectedChip == true,
        )
        read(animate = true)
    }

    private fun minuteNow(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    // ---- Pages -------------------------------------------------------------------------------

    private fun read(animate: Boolean) {
        val list = moment?.let { GlancePreview.at(it) } ?: GlancePreview.targets
        // ponytail: the launcher's card adapter also drops some template types; a target with no
        // remote views, template or header is the only kind left out here.
        val next = list.filterIsInstance<SmartspaceTarget>().filter { it.remoteViews != null || it.templateData != null || it.headerAction != null }
        val sign = next.joinToString("\u0001") { describe(it) }
        if (sign == signature && pager.childCount > 0) return
        signature = sign
        pages = next
        pager.fill(animate)
    }

    /** What a page shows, to tell a real change from the same list handed again. */
    private fun describe(target: SmartspaceTarget): String {
        val data = target.templateData
        return "${target.smartspaceTargetId}|${data?.primaryItem?.text?.text}|${data?.subtitleItem?.text?.text}|" +
            "${data?.subtitleSupplementalItem?.text?.text}|${target.headerAction?.title}|${target.remoteViews != null}"
    }

    private fun pageOf(target: SmartspaceTarget?): View {
        if (target == null) return template(null, emptyList())
        target.remoteViews?.let { views -> return views.apply(context, pager) }
        val data = target.templateData
        if (data != null) return template(data.primaryItem?.text?.text, listOfNotNull(data.subtitleItem, data.subtitleSupplementalItem).map { chip(it) })
        val header = target.headerAction
        return template(header?.title, listOf(Chip(header?.subtitle, header?.icon?.loadDrawable(context), true)))
    }

    private fun chip(item: BaseTemplateData.SubItemInfo): Chip {
        val icon = item.icon
        return Chip(item.text?.text, icon?.icon?.loadDrawable(context), icon?.shouldTint() ?: true)
    }

    /** One page: a title (today's date when the target has none, as the launcher's date card) and its items. */
    private fun template(title: CharSequence?, chips: List<Chip>): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(title?.takeIf { it.isNotEmpty() } ?: today(), ExpressiveType.HEADLINE_SMALL))
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (chip in chips) {
            if (chip.text.isNullOrEmpty() && chip.icon == null) continue
            chip.icon?.let { drawable ->
                if (chip.tint) drawable.mutate().setTint(Color.WHITE)
                line.addView(ImageView(context).apply { setImageDrawable(drawable) }, LayoutParams(dp(20f), dp(20f)).apply { marginEnd = dp(6f) })
            }
            if (!chip.text.isNullOrEmpty()) {
                line.addView(text(chip.text, ExpressiveType.BODY_LARGE), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(14f) })
            }
        }
        if (line.childCount > 0) addView(line, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) })
    }

    private fun text(value: CharSequence, type: ExpressiveType) = TextView(context).apply {
        text = value
        type.applyTo(this, ExpressiveRole.ON_SURFACE)
        setTextColor(Color.WHITE)
        setShadowLayer(dp(2f).toFloat(), 0f, dp(1f).toFloat(), SHADOW)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun today(): String =
        DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), DATE_SKELETON), Date()).toString()

    /** The launcher's own At a Glance pager height, by resource name; [PAGE_DP] if this build has none. */
    private fun pageHeight(): Int {
        val res = context.resources
        val id = res.getIdentifier(PAGER_HEIGHT, "dimen", context.packageName)
        return if (id != 0) res.getDimensionPixelSize(id) else dp(PAGE_DP)
    }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private class Chip(val text: CharSequence?, val icon: Drawable?, val tint: Boolean)

    private val band = PinnedBand()

    /** Draws the pinned band as [strip]'s background: a full-width view this preview sits in. */
    fun bandOn(strip: View) {
        strip.background = band
    }

    /**
     * The colour the screen's title bar is drawn in now (collapsed, as it is while the page scrolls),
     * read off the bar itself: one pixel near its bottom, drawn into a 1x1 bitmap. Null outside a
     * Settings screen, or when the launcher names its bar differently.
     */
    private fun titleBarColor(): Int? = runCatching {
        val activity = context.activityOrNull() ?: return null
        val id = activity.resources.getIdentifier(TITLE_BAR, "id", activity.packageName).takeIf { it != 0 } ?: return null
        val bar = activity.findViewById<View>(id)?.takeIf { it.width > 0 && it.height > 2 } ?: return null
        // The bar's scrolled colour is an overlay on what is under it, so the window is drawn, not the bar.
        val at = IntArray(2).also { bar.getLocationInWindow(it) }
        val root = activity.window.decorView
        val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pixel)
        canvas.translate(-(at[0] + bar.width - dp(8f)).toFloat(), -(at[1] + bar.height - 2f))
        root.draw(canvas)
        pixel.getPixel(0, 0).also { pixel.recycle() }.takeIf { Color.alpha(it) == 255 }
    }.getOrNull()

    /**
     * Behind the pinned panel: a band of the page's colour as wide as the screen, joined to the
     * status bar strip above it, ending under the panel in large rounded corners with a soft shadow
     * on the rows passing beneath, as a Material 3 Expressive top bar sits over scrolled content.
     * It fades in as the panel pins and out as it lets go.
     */
    private inner class PinnedBand : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ExpressiveRole.SURFACE_CONTAINER.of(context) }

        /** The title bar's colour when it could be read, so band and bar read as one surface. */
        var color: Int
            get() = fill.color
            set(value) {
                fill.color = value
                invalidateSelf()
            }
        private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = android.graphics.Path()
        private val rect = android.graphics.RectF()
        var panelHeight = 0
        private var level = 0f
        private var fading: ValueAnimator? = null

        fun fade(on: Boolean) {
            fading?.cancel()
            fading = ValueAnimator.ofFloat(level, if (on) 1f else 0f).apply {
                duration = GlanceMotion.MEDIUM
                interpolator = GlanceMotion.MOVE
                addUpdateListener {
                    level = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
        }

        override fun draw(canvas: Canvas) {
            if (level <= 0f || panelHeight <= 0) return
            // The strip this is drawn in spans the screen; the band fills its top down past the panel.
            val side = dp(SIDE_DP).toFloat()
            val bottom = panelHeight + dp(BAND_BELOW_DP).toFloat()
            val radius = dp(BAND_CORNER_DP).toFloat()
            val left = 0f
            val right = bounds.width().toFloat()
            val alpha = (level * 255).roundToInt()
            // The shadow: a few soft layers just under the band's rounded bottom.
            for (i in 1..SHADOW_STEPS) {
                shade.color = Color.BLACK
                shade.alpha = (alpha * SHADOW_ALPHA / (i + 1)).roundToInt()
                rect.set(left, -side, right, bottom + i * dp(1.5f))
                canvas.drawRoundRect(rect, radius, radius, shade)
            }
            path.reset()
            rect.set(left, -side, right, bottom)
            path.addRoundRect(rect, floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius), android.graphics.Path.Direction.CW)
            fill.alpha = alpha
            canvas.drawPath(path, fill)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /**
     * The pages side by side: [position] is the page in view, fractional while one slides. A drag
     * moves them with the finger; letting go settles on the nearest page, or the next one when the
     * drag was quick. Pages fade and shrink slightly away from the centre, the dots stretch with the
     * position, and the wallpaper drifts a fraction of the distance behind them.
     */
    @SuppressLint("ViewConstructor")
    private inner class Pager : FrameLayout(context) {

        private var position = 0f
        private var downX = 0f
        private var downPosition = 0f
        private var dragging = false
        private var downTime = 0L
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var settle: ValueAnimator? = null

        /** Children being faded out, kept out of [place]. */
        private val leaving = HashSet<View>()

        init {
            isClickable = true
        }

        fun fill(animate: Boolean) {
            val count = pages.size.coerceAtLeast(1)
            if (position > count - 1) position = (count - 1).toFloat()
            val views = (0 until count).map { index ->
                runCatching { pageOf(pages.getOrNull(index)) }
                    .onFailure { ui.logger.warn("The At a Glance preview could not draw a page", it) }
                    .getOrNull() ?: template(null, emptyList())
            }
            for (view in current()) {
                if (animate) {
                    leaving += view
                    view.animate().alpha(0f).translationYBy(-dp(8f).toFloat()).setDuration(GlanceMotion.SHORT)
                        .withEndAction { leaving -= view; removeView(view) }.start()
                } else {
                    removeView(view)
                }
            }
            views.forEachIndexed { index, view ->
                addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                if (animate) {
                    view.alpha = 0f
                    view.translationY = dp(12f).toFloat()
                    view.animate().alpha(alphaFor(index)).translationY(0f).setStartDelay(GlanceMotion.SHORT / 2)
                        .setDuration(GlanceMotion.LONG).setInterpolator(GlanceMotion.ENTER).start()
                }
            }
            buildDots()
            place(keepAlpha = animate)
            announce()
        }

        private fun current(): List<View> = (0 until childCount).map { getChildAt(it) }.filter { it !in leaving }

        private fun alphaFor(index: Int) = (1f - abs(index - position) * 0.6f).coerceIn(0f, 1f)

        /** Runs inside layout and animation frames of the launcher's own process: a failure stays here. */
        private fun place(keepAlpha: Boolean = false) {
            runCatching { placeNow(keepAlpha) }.onFailure { ui.logger.warn("The At a Glance preview could not lay out its pages", it) }
        }

        private fun placeNow(keepAlpha: Boolean) {
            val width = width.toFloat()
            if (width <= 0f) {
                post { place() }
                return
            }
            current().forEachIndexed { i, view ->
                val distance = i - position
                view.translationX = distance * width
                val scale = 1f - abs(distance).coerceAtMost(1f) * 0.08f
                view.scaleX = scale
                view.scaleY = scale
                if (!keepAlpha) view.alpha = alphaFor(i)
            }
            for (i in 0 until dots.childCount) {
                val near = 1f - abs(i - position).coerceAtMost(1f)
                val dot = dots.getChildAt(i)
                (dot.layoutParams as LinearLayout.LayoutParams).width = dp(6f + 12f * near)
                dot.alpha = 0.45f + 0.55f * near
                dot.requestLayout()
            }
            backdrop.drift = position * DRIFT
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            if (changed) place()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    settle?.cancel()
                    downX = event.x
                    downPosition = position
                    downTime = event.eventTime
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    if (!dragging && abs(dx) > slop) {
                        dragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        val last = (pages.size - 1).coerceAtLeast(0).toFloat()
                        var next = downPosition - dx / width
                        // Rubber band past either end.
                        if (next < 0f) next *= 0.3f
                        if (next > last) next = last + (next - last) * 0.3f
                        position = next
                        place()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        // A tap: the next page, round to the first after the last.
                        val count = pages.size
                        if (count > 1) settleTo(((position.roundToInt() + 1) % count).toFloat())
                        performClick()
                    } else {
                        val dx = event.x - downX
                        val quick = event.eventTime - downTime < FLING_MS && abs(dx) > width * 0.12f
                        val target = when {
                            quick && dx < 0 -> downPosition.roundToInt() + 1
                            quick && dx > 0 -> downPosition.roundToInt() - 1
                            else -> position.roundToInt()
                        }.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                        settleTo(target.toFloat())
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    settleTo(position.roundToInt().coerceIn(0, (pages.size - 1).coerceAtLeast(0)).toFloat())
                    dragging = false
                }
            }
            return true
        }

        override fun performClick(): Boolean = super.performClick()

        fun settleTo(target: Float) {
            settle?.cancel()
            settle = ValueAnimator.ofFloat(position, target).apply {
                duration = GlanceMotion.LONG
                interpolator = GlanceMotion.SPRING
                addUpdateListener {
                    position = it.animatedValue as Float
                    place()
                }
                start()
            }
            postDelayed({ announce() }, GlanceMotion.SHORT)
        }

        private fun announce() {
            val count = pages.size.coerceAtLeast(1)
            contentDescription = ui.string(R.string.glance_preview_page, position.roundToInt().coerceIn(0, count - 1) + 1, count)
        }

        private fun buildDots() {
            dots.removeAllViews()
            if (pages.size < 2) return
            for (index in pages.indices) {
                dots.addView(View(context).apply {
                    background = GradientDrawable().apply { cornerRadius = dp(3f).toFloat(); setColor(Color.WHITE) }
                    setOnClickListener { settleTo(index.toFloat()) }
                }, LinearLayout.LayoutParams(dp(6f), dp(6f)).apply { marginEnd = dp(5f); gravity = Gravity.CENTER_VERTICAL })
            }
        }
    }

    /** The top of the home wallpaper, one screen of it, drifting sideways with the pages. */
    private class Backdrop(private val wallpaper: Bitmap?, private val fallback: Int, private val screen: android.util.Size) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val source = Rect()

        /** How far to slide, as a share of one screen of wallpaper. */
        var drift = 0f
            set(value) {
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            val area = bounds
            val bitmap = wallpaper
            if (bitmap == null || bitmap.isRecycled || area.width() <= 0) {
                canvas.drawColor(fallback)
                return
            }
            // The wallpaper spans every page; the first page's slice is one screen wide, at its
            // left edge. The strip is taken from where At a Glance sits, near the top.
            val width = (bitmap.height.toLong() * screen.width / screen.height).toInt().coerceIn(1, bitmap.width)
            val height = (width.toLong() * area.height() / area.width()).toInt().coerceAtMost(bitmap.height)
            val top = (bitmap.height * TOP_SHARE).toInt().coerceAtMost(bitmap.height - height)
            val left = (drift * width).toInt().coerceIn(0, bitmap.width - width)
            source.set(left, top, left + width, top + height)
            canvas.drawBitmap(bitmap, source, area, paint)
            // A light scrim keeps white text and dots readable on a bright wallpaper.
            canvas.drawColor(SCRIM)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
    }

    private companion object {
        const val PAGER_HEIGHT = "enhanced_smartspace_height"
        /** Only when the launcher has no [PAGER_HEIGHT]: its value on CP4A. */
        const val PAGE_DP = 104f
        const val DATE_SKELETON = "EEEMMMd"
        const val SHADOW = 0x66000000
        const val SCRIM = 0x14000000
        /** Where At a Glance's strip starts down the wallpaper, as a share of its height. */
        const val TOP_SHARE = 0.03f
        /** How far the wallpaper slides per page, as a share of one screen of it. */
        const val DRIFT = 0.12f
        const val FLING_MS = 250L
        const val LIVE_DOT = 0xFF7CE38B.toInt()
        /** The screen's column padding the pinned band reaches out over, to the screen edges. */
        const val SIDE_DP = 16f
        /** The Settings screen's collapsing title bar, by resource name. */
        const val TITLE_BAR = "collapsing_toolbar"
        val BAR_SAMPLE_DELAYS = longArrayOf(0L, 250L, 700L)
        const val BAND_BELOW_DP = 16f
        const val BAND_CORNER_DP = 32f
        const val SHADOW_STEPS = 4
        const val SHADOW_ALPHA = 0.18f
        /** The time chips' key for "Pick a time…". */
        const val PICK = "\u0000pick"
    }
}
