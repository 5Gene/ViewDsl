//package osp.sparkj.viewdsl.widget
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.Canvas
//import android.graphics.Color
//import android.graphics.Matrix
//import android.graphics.Paint
//import android.graphics.PointF
//import android.graphics.RectF
//import android.graphics.drawable.Drawable
//import android.util.AttributeSet
//import android.view.MotionEvent
//import android.view.View
//import android.view.ViewConfiguration
//import androidx.core.content.ContextCompat
//import kotlin.math.atan2
//import kotlin.math.hypot
//import kotlin.math.max
//
///**
// * Serializable snapshot of a single sticker's state in boundary-local coordinates.
// * Use [OStickerView.captureStickers] to create and [OStickerView.restoreStickers] to apply.
// *
// * @param id       Identifier of the source file.
// * @param bitmap   The sticker image.
// * @param scale    Uniform scale factor (view-pixels per bitmap-pixel).
// * @param rotation Rotation angle in degrees around the sticker centre.
// * @param centerX  Centre X relative to [OStickerView.boundaryRect] left edge; -1 uses default placement.
// * @param centerY  Centre Y relative to [OStickerView.boundaryRect] top edge; -1 uses default placement.
// */
//data class StickerSnapshot(
//    val id: String = "",
//    val path: String = "",
//    val bitmap: Bitmap,
//    val scale: Float = 1f,
//    val rotation: Float = 0f,
//    val centerX: Float = -1f,
//    val centerY: Float = -1f
//)
//
///**
// * Self-contained sticker canvas view.
// *
// * Features:
// * - Multiple stickers; only one sticker holds focus at a time
// * - Focused sticker shows a border + three corner icons (delete / rotate / scale)
// * - Drag to translate, top-right icon to rotate (around sticker centre),
// *   bottom-right icon to scale (top-left corner stays fixed)
// * - Tap blank area to deselect; tap sticker to focus
// * - Cascade default placement: new sticker offsets right-down from the previous
// *   one only when the previous has not been moved; otherwise centred
// * - [saveAsBitmap] / [saveAsBitmapWithoutFocusUi] composite stickers onto a transparent Bitmap
// * - All visual parameters exposed through [Config]
// */
//class OStickerView @JvmOverloads constructor(
//    context: Context,
//    attrs: AttributeSet? = null,
//    defStyleAttr: Int = 0
//) : View(context, attrs, defStyleAttr) {
//
//    // ── Config ────────────────────────────────────────────────────────
//
//    /**
//     * @param borderStrokeWidthDp  Width of the selection border in dp.
//     * @param borderPaddingDp      Gap between the sticker image edge and the border line in dp.
//     *                             This value is visually constant regardless of sticker scale.
//     * @param minScale             Absolute minimum scale (view-pixels per source-bitmap-pixel).
//     * @param maxScale             Absolute maximum scale.
//     * @param baseStickerLongEdgeDp 业务 scale=1 时贴纸长边目标尺寸（dp）。
//     * @param cascadeOffsetDp      Right-bottom offset (dp) applied to each newly added sticker
//     *                             when the previous sticker is still at its default position.
//     */
//    data class Config(
//        val borderStrokeWidthDp: Float = 1f,
//        val borderPaddingDp: Float = 4f,
//        val minScale: Float = 0.2f,
//        val maxScale: Float = 4f,
//        val baseStickerLongEdgeDp: Float = 100f,
//        val cascadeOffsetDp: Float = 16f
//    )
//
//    var config: Config = Config()
//        set(value) {
//            field = value
//            invalidate()
//        }
//
//    // ── StickerItem ───────────────────────────────────────────────────
//
//    /**
//     * Represents one sticker on the canvas.
//     *
//     * [matrix] maps bitmap local coordinates (0,0)–(bmpW,bmpH) to View coordinates.
//     * All geometric helpers derive their values from this single matrix, so position,
//     * rotation, and scale are always consistent.
//     */
//    inner class StickerItem internal constructor(
//        val bitmap: Bitmap,
//        val id: String = "",
//    ) {
//
//        /** Transform from bitmap local space → View space. */
//        val matrix = Matrix()
//
//        /**
//         * True until the sticker is first moved/rotated/scaled by the user.
//         * Used by the cascade placement algorithm to decide the next sticker's default position.
//         */
//        var isAtDefaultPosition: Boolean = true
//
//        internal val bmpW get() = bitmap.width.toFloat()
//        internal val bmpH get() = bitmap.height.toFloat()
//        private val baseScale: Float =
//            // 将不同原图尺寸统一映射到同一视觉基准：业务 scale=1 时长边一致
//            (config.baseStickerLongEdgeDp * density).coerceAtLeast(1f) /
//                    max(bmpW, bmpH).coerceAtLeast(1f)
//
//        private fun mapPt(lx: Float, ly: Float): PointF {
//            val arr = floatArrayOf(lx, ly)
//            matrix.mapPoints(arr)
//            return PointF(arr[0], arr[1])
//        }
//
//        /** Centre of the sticker in View coordinates. */
//        fun getCenter(): PointF = mapPt(bmpW / 2f, bmpH / 2f)
//
//        /** Top-left corner in View coordinates. */
//        fun getTL(): PointF = mapPt(0f, 0f)
//
//        /** Top-right corner in View coordinates. */
//        fun getTR(): PointF = mapPt(bmpW, 0f)
//
//        /** Bottom-right corner in View coordinates. */
//        fun getBR(): PointF = mapPt(bmpW, bmpH)
//
//        /**
//         * Returns true when [x]/[y] (View coordinates) are within the sticker's
//         * axis-aligned bounding rectangle in bitmap local space.
//         */
//        fun hitTest(x: Float, y: Float): Boolean {
//            val inv = Matrix()
//            if (!matrix.invert(inv)) return false
//            val p = floatArrayOf(x, y)
//            inv.mapPoints(p)
//            return p[0] >= 0f && p[0] <= bmpW && p[1] >= 0f && p[1] <= bmpH
//        }
//
//        /** 提取渲染缩放（绝对矩阵缩放，含 baseScale）。 */
//        fun extractRenderScale(): Float {
//            val v = FloatArray(9)
//            matrix.getValues(v)
//            return hypot(v[Matrix.MSCALE_X], v[Matrix.MSKEW_Y])
//        }
//
//        /** 提取业务缩放（归一化缩放）：业务 scale=1 时不同贴纸视觉大小一致。 */
//        fun extractScale(): Float = extractRenderScale() / baseScale
//
//        /** Initialise [matrix] to place the sticker centred at ([cx],[cy]) with scale [scale]. */
//        internal fun initAt(cx: Float, cy: Float, scale: Float) {
//            val renderScale = (scale.coerceAtLeast(0f)) * baseScale
//            matrix.reset()
//            matrix.postScale(renderScale, renderScale)
//            matrix.postTranslate(cx - bmpW * renderScale / 2f, cy - bmpH * renderScale / 2f)
//        }
//    }
//
//    // ── Icons ─────────────────────────────────────────────────────────
//
//    private enum class IconHit { DELETE, ROTATE, SCALE }
//
//    private val deleteIcon: Drawable? by lazy {
//        ContextCompat.getDrawable(context, R.drawable.watch_face_ic_sticker_delete)
//    }
//    private val rotateIcon: Drawable? by lazy {
//        ContextCompat.getDrawable(context, R.drawable.watch_face_ic_sticker_rotate)
//    }
//    private val scaleIcon: Drawable? by lazy {
//        ContextCompat.getDrawable(context, R.drawable.watch_face_ic_sticker_scale)
//    }
//
//    // ── Paints ────────────────────────────────────────────────────────
//
//    private val density = context.resources.displayMetrics.density
//    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
//
//    /** Touch hit radius for each corner icon (view pixels). */
//    private val iconHitRadiusPx = 24f * density
//
//    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        style = Paint.Style.STROKE
//        color = Color.WHITE
//        strokeJoin = Paint.Join.ROUND
//    }
//
//    private val borderShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        style = Paint.Style.STROKE
//        color = 0x4C000000.toInt()
//        strokeJoin = Paint.Join.ROUND
//    }
//
//    // ── State ─────────────────────────────────────────────────────────
//
//    private val stickers = mutableListOf<StickerItem>()
//    private var focusedSticker: StickerItem? = null
//
//    // Touch tracking
//    private enum class OpMode { NONE, TRANSLATE, ROTATE, SCALE }
//
//    private var opMode = OpMode.NONE
//    private var downX = 0f
//    private var downY = 0f
//    private var lastX = 0f
//    private var lastY = 0f
//    private var isClickEvent = true
//    private var pressedIcon: IconHit? = null
//
//    // Gesture snapshots – saved once at gesture start, reused every MOVE frame
//    // to avoid floating-point drift from incremental updates.
//    private val savedMatrix = Matrix()
//    private val gesturePivot = PointF()   // ROTATE → sticker centre; SCALE → sticker TL
//    private var gestureInitialAngle = 0f  // ROTATE: angle from pivot to initial touch
//    private var gestureInitialDist = 0f   // SCALE:  distance from TL to initial touch
//    private var savedAbsoluteScale = 1f   // SCALE:  scale factor when gesture began
//
//    // ── Boundary ──────────────────────────────────────────────────────
//
//    /**
//     * Optional constraint region in View coordinates.
//     * During translate/rotate/scale the sticker's centre must remain inside this rect.
//     * When null, the full View bounds are used.
//     */
//    var boundaryRect: RectF? = null
//
//    private fun boundary(): RectF =
//        boundaryRect ?: RectF(0f, 0f, width.toFloat(), height.toFloat())
//
//    /**
//     * When true, only the focused sticker is drawn; all others are hidden.
//     * Defaults to false (all stickers always visible).
//     */
//    var showFocusedOnly: Boolean = true
//        set(value) {
//            field = value
//            invalidate()
//        }
//
//    var showRecentSticker: Boolean = false
//        set(value) {
//            field = value
//            postInvalidate()
//        }
//
//    var recentSticker: StickerItem? = null
//
//    // ── Callbacks ─────────────────────────────────────────────────────
//
//    /** Invoked whenever the focused sticker changes. Passes null when nothing is selected. */
//    var onStickerFocusChanged: ((StickerItem?) -> Unit)? = null
//
//    /** Invoked just before a sticker is removed (either via the delete icon or [deleteSelectedSticker]). */
//    var onStickerDeleted: ((StickerItem) -> Unit)? = null
//
//    // ── Public API ────────────────────────────────────────────────────
//
//    /**
//     * Add a new sticker from [snapshot].
//     *
//     * When [StickerSnapshot.centerX] and [StickerSnapshot.centerY] are both -1, position is
//     * computed automatically (cascade or centre). Otherwise the sticker is placed at the
//     * boundary-local coordinates given in the snapshot.
//     *
//     * Scale and rotation come from [snapshot].
//     * The new sticker automatically receives focus.
//     * If the View has not been laid out yet, the call is deferred until layout completes.
//     */
//    fun addSticker(snapshot: StickerSnapshot) {
//        if (width == 0 || height == 0) {
//            post { addSticker(snapshot) }
//            return
//        }
//        val item = StickerItem(snapshot.bitmap, snapshot.id)
//        val hasPosition = snapshot.centerX != -1f && snapshot.centerY != -1f
//        if (hasPosition) {
//            val b = boundary()
//            val cx = snapshot.centerX + b.left
//            val cy = snapshot.centerY + b.top
//            item.initAt(cx, cy, snapshot.scale)
//        } else {
//            val center = computeDefaultCenter()
//            item.initAt(center.x, center.y, snapshot.scale)
//        }
//        if (snapshot.rotation != 0f) {
//            val c = item.getCenter()
//            item.matrix.postRotate(snapshot.rotation, c.x, c.y)
//        }
//        stickers.add(item)
//        setFocus(item)
//    }
//
//    /** Delete the currently focused sticker (no-op if nothing is focused). */
//    fun deleteSelectedSticker() {
//        val sticker = focusedSticker ?: return
//        removeStickerInternal(sticker)
//    }
//
//    /** Deselect the currently focused sticker without deleting it. */
//    fun deselectAll() {
//        if (focusedSticker != null) setFocus(null)
//    }
//
//    /** Remove all stickers and clear focus. */
//    fun clearStickers() {
//        stickers.clear()
//        setFocus(null)
//        invalidate()
//    }
//
//    /** Returns the currently focused [StickerItem], or null. */
//    fun getSelectedSticker(): StickerItem? = focusedSticker
//
//
//    /**
//     * Returns snapshots of stickers whose centre lies within [boundary()].
//     * Each snapshot stores scale, rotation, and centre position relative to
//     * [boundaryRect]'s top-left corner, so positions are independent of View
//     * layout and can be restored after configuration changes.
//     */
//    fun captureStickers(): List<StickerSnapshot> {
//        val b = boundary()
//        return stickers
//            .map { item ->
//                val center = item.getCenter()
//                val values = FloatArray(9)
//                item.matrix.getValues(values)
//                val rotation = Math.toDegrees(
//                    atan2(values[Matrix.MSKEW_X].toDouble(), values[Matrix.MSCALE_X].toDouble())
//                ).toFloat()
//                StickerSnapshot(
//                    id = item.id,
//                    bitmap = item.bitmap,
//                    scale = item.extractScale(),
//                    rotation = rotation,
//                    centerX = center.x - b.left,
//                    centerY = center.y - b.top
//                )
//            }
//    }
//
//    /**
//     * Clears all current stickers and restores from [snapshots].
//     * Each snapshot's centre is assumed to be in boundary-local coordinates and is
//     * translated back to View coordinates using the current [boundary()].
//     */
//    fun restoreStickers(snapshots: List<StickerSnapshot>) {
//        stickers.clear()
//        val b = boundary()
//        for (snapshot in snapshots) {
//            val item = StickerItem(snapshot.bitmap, snapshot.id)
//            val cx = snapshot.centerX + b.left
//            val cy = snapshot.centerY + b.top
//            item.initAt(cx, cy, snapshot.scale)
//            if (snapshot.rotation != 0f) {
//                item.matrix.postRotate(snapshot.rotation, cx, cy)
//            }
//            item.isAtDefaultPosition = false
//            stickers.add(item)
//        }
//        setFocus(null)
//        invalidate()
//    }
//
//    /**
//     * Composites stickers whose centre lies within [boundary()] onto a transparent
//     * bitmap sized to the boundary rect.
//     *
//     * Each sticker's matrix is translated so the boundary top-left becomes (0,0),
//     * then the result is returned as a new [Bitmap].
//     */
//    fun saveAsBitmap(): Bitmap {
//        val b = boundary()
//        val w = b.width().toInt().coerceAtLeast(1)
//        val h = b.height().toInt().coerceAtLeast(1)
//        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(out)
//        for (sticker in stickers) {
//            val m = Matrix(sticker.matrix)
//            m.postTranslate(-b.left, -b.top)
//            canvas.drawBitmap(sticker.bitmap, m, null)
//        }
//        return out
//    }
//
//    fun saveNoFocusAsBitmap(): Bitmap {
//        val b = boundary()
//        val w = b.width().toInt().coerceAtLeast(1)
//        val h = b.height().toInt().coerceAtLeast(1)
//        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(out)
//        for (sticker in stickers) {
//            if (sticker != focusedSticker) {
//                val m = Matrix(sticker.matrix)
//                m.postTranslate(-b.left, -b.top)
//                canvas.drawBitmap(sticker.bitmap, m, null)
//            }
//        }
//        return out
//    }
//
//    // ── Focus management ──────────────────────────────────────────────
//
//    private fun setFocus(sticker: StickerItem?) {
//        focusedSticker = sticker
//        if (sticker != null) {
//            recentSticker = sticker
//        }
//        onStickerFocusChanged?.invoke(sticker)
//        invalidate()
//    }
//
//    private fun removeStickerInternal(sticker: StickerItem) {
//        onStickerDeleted?.invoke(sticker)
//        stickers.remove(sticker)
//        setFocus(null)
//        invalidate()
//    }
//
//    // ── Drawing ───────────────────────────────────────────────────────
//
//    override fun onDraw(canvas: Canvas) {
//        super.onDraw(canvas)
//        if (showRecentSticker) {
//            recentSticker?.let {
//                canvas.drawBitmap(it.bitmap, it.matrix, null)
//            }
//            return
//        }
//        for (sticker in stickers) {
//            if (showFocusedOnly && sticker !== focusedSticker) continue
//            canvas.drawBitmap(sticker.bitmap, sticker.matrix, null)
//            if (sticker === focusedSticker) {
//                drawBorderAndIcons(canvas, sticker)
//            }
//        }
//    }
//
//    private fun drawBorderAndIcons(canvas: Canvas, sticker: StickerItem) {
//        val borderPadPx = config.borderPaddingDp * density
//        val borderStrokePx = config.borderStrokeWidthDp * density
//        val s = sticker.extractRenderScale().coerceAtLeast(0.001f)
//
//        // Convert pixel measurements to local (bitmap) space so that the visual size
//        // remains constant regardless of the sticker's current scale.
//        val localPad = borderPadPx / s
//        val localStroke = borderStrokePx / s
//        val localCornerRadius = BORDER_CORNER_RADIUS_DP * density / s
//
//        // Draw rounded border rect in local space (after concat).
//        canvas.save()
//        canvas.concat(sticker.matrix)
//        val rect = RectF(-localPad, -localPad, sticker.bmpW + localPad, sticker.bmpH + localPad)
//        borderShadowPaint.strokeWidth = localStroke + density / s
//        canvas.drawRoundRect(rect, localCornerRadius, localCornerRadius, borderShadowPaint)
//        borderPaint.strokeWidth = localStroke
//        canvas.drawRoundRect(rect, localCornerRadius, localCornerRadius, borderPaint)
//        canvas.restore()
//
//        // Compute icon centres in View space (map local corners through matrix).
//        val pts = floatArrayOf(
//            -localPad, -localPad,              // DELETE → TL
//            sticker.bmpW + localPad, -localPad,            // ROTATE → TR
//            sticker.bmpW + localPad, sticker.bmpH + localPad  // SCALE  → BR
//        )
//        sticker.matrix.mapPoints(pts)
//
//        // Draw icons at fixed visual size in View space (unaffected by sticker scale).
//        drawIconAt(canvas, deleteIcon, pts[0], pts[1])
//        drawIconAt(canvas, rotateIcon, pts[2], pts[3])
//        drawIconAt(canvas, scaleIcon, pts[4], pts[5])
//    }
//
//    private fun drawIconAt(canvas: Canvas, icon: Drawable?, cx: Float, cy: Float) {
//        icon ?: return
//        val hw = icon.intrinsicWidth / 2
//        val hh = icon.intrinsicHeight / 2
//        icon.setBounds((cx - hw).toInt(), (cy - hh).toInt(), (cx + hw).toInt(), (cy + hh).toInt())
//        icon.draw(canvas)
//    }
//
//    // ── Touch handling ────────────────────────────────────────────────
//
//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        return when (event.actionMasked) {
//            MotionEvent.ACTION_DOWN -> handleDown(event.x, event.y)
//            MotionEvent.ACTION_MOVE -> {
//                handleMove(event.x, event.y); true
//            }
//
//            MotionEvent.ACTION_UP -> {
//                handleUp(); true
//            }
//
//            MotionEvent.ACTION_CANCEL -> {
//                handleCancel(); true
//            }
//
//            else -> false
//        }
//    }
//
//    /**
//     * Returns true when the touch was consumed:
//     * - an icon or sticker body was hit, or
//     * - a blank-area tap cleared an existing focus.
//     * Returns false when there is nothing to interact with (no stickers focused, no sticker hit).
//     */
//    private fun handleDown(x: Float, y: Float): Boolean {
//        downX = x; downY = y; lastX = x; lastY = y
//        isClickEvent = true
//        opMode = OpMode.NONE
//        pressedIcon = null
//
//        // 1. Check corner icons of the focused sticker first.
//        val focused = focusedSticker
//        if (focused != null) {
//            val icon = hitTestIcons(focused, x, y)
//            if (icon != null) {
//                pressedIcon = icon
//                when (icon) {
//                    IconHit.ROTATE -> {
//                        opMode = OpMode.ROTATE
//                        val c = focused.getCenter()
//                        gesturePivot.x = c.x; gesturePivot.y = c.y
//                        gestureInitialAngle = atan2(y - gesturePivot.y, x - gesturePivot.x)
//                        savedMatrix.set(focused.matrix)
//                    }
//
//                    IconHit.SCALE -> {
//                        opMode = OpMode.SCALE
//                        val tl = focused.getTL()
//                        gesturePivot.x = tl.x; gesturePivot.y = tl.y
//                        gestureInitialDist = hypot(x - gesturePivot.x, y - gesturePivot.y)
//                        savedAbsoluteScale = focused.extractScale()
//                        savedMatrix.set(focused.matrix)
//                    }
//
//                    IconHit.DELETE -> { /* handled as click in handleUp */
//                    }
//                }
//                invalidate()
//                return true
//            }
//        }
//
//        // 2. Hit-test all sticker bodies (top-most first).
//        for (i in stickers.indices.reversed()) {
//            val sticker = stickers[i]
//            if (hitTestBody(sticker, x, y)) {
//                if (sticker !== focusedSticker) setFocus(sticker)
//                opMode = OpMode.TRANSLATE
//                invalidate()
//                return true
//            }
//        }
//
//        // 3. Blank area → clear focus if one existed and consume the event.
//        if (focusedSticker != null) {
//            setFocus(null)
//            return true
//        }
//
//        return false
//    }
//
//    private fun handleMove(x: Float, y: Float) {
//        // Detect when movement exceeds touch slop and this is no longer a tap.
//        if (isClickEvent && hypot(x - downX, y - downY) > touchSlopPx) {
//            isClickEvent = false
//            pressedIcon = null  // drag cancels click-only action (DELETE)
//        }
//
//        val sticker = focusedSticker ?: run { lastX = x; lastY = y; return }
//
//        when (opMode) {
//            OpMode.TRANSLATE -> {
//                val b = boundary()
//                val center = sticker.getCenter()
//                val rawDx = x - lastX
//                val rawDy = y - lastY
//                val newCx = center.x + rawDx
//                val newCy = center.y + rawDy
//                val dx = rawDx + (newCx.coerceIn(b.left, b.right) - newCx)
//                val dy = rawDy + (newCy.coerceIn(b.top, b.bottom) - newCy)
//                sticker.matrix.postTranslate(dx, dy)
//                sticker.isAtDefaultPosition = false
//                invalidate()
//            }
//
//            // Rotate and Scale only activate once the user has dragged past touchSlop,
//            // preventing jitter when pressing (but not dragging) the icon.
//            OpMode.ROTATE -> if (!isClickEvent) {
//                val angle = atan2(y - gesturePivot.y, x - gesturePivot.x)
//                val deltaDeg = Math.toDegrees((angle - gestureInitialAngle).toDouble()).toFloat()
//                sticker.matrix.set(savedMatrix)
//                sticker.matrix.postRotate(deltaDeg, gesturePivot.x, gesturePivot.y)
//                val c = sticker.getCenter()
//                if (boundary().contains(c.x, c.y)) {
//                    sticker.isAtDefaultPosition = false
//                    invalidate()
//                } else {
//                    sticker.matrix.set(savedMatrix)
//                }
//            }
//
//            OpMode.SCALE -> if (!isClickEvent && gestureInitialDist > 0f) {
//                val dist = hypot(x - gesturePivot.x, y - gesturePivot.y)
//                // Recompute total scale from gesture snapshot every frame to avoid drift.
//                val rawFactor = dist / gestureInitialDist
//                val proposed = savedAbsoluteScale * rawFactor
//                val clamped = proposed.coerceIn(config.minScale, config.maxScale)
//                val actualFactor = clamped / savedAbsoluteScale
//                sticker.matrix.set(savedMatrix)
//                // postScale with the TL pivot keeps the top-left corner stationary.
//                sticker.matrix.postScale(actualFactor, actualFactor, gesturePivot.x, gesturePivot.y)
//                val c = sticker.getCenter()
//                if (boundary().contains(c.x, c.y)) {
//                    sticker.isAtDefaultPosition = false
//                    invalidate()
//                } else {
//                    sticker.matrix.set(savedMatrix)
//                }
//            }
//
//            OpMode.NONE -> { /* no active operation */
//            }
//        }
//
//        lastX = x; lastY = y
//    }
//
//    private fun handleUp() {
//        // A tap (no move) on the DELETE icon removes the sticker.
//        if (isClickEvent && pressedIcon == IconHit.DELETE) {
//            val sticker = focusedSticker
//            if (sticker != null) removeStickerInternal(sticker)
//        }
//        opMode = OpMode.NONE
//        pressedIcon = null
//        invalidate()
//    }
//
//    private fun handleCancel() {
//        opMode = OpMode.NONE
//        pressedIcon = null
//        invalidate()
//    }
//
//    // ── Hit testing ───────────────────────────────────────────────────
//
//    /**
//     * Returns which corner icon was hit, or null if none.
//     * Icon hit areas use [iconHitRadiusPx] around each icon centre in View space.
//     */
//    private fun hitTestIcons(sticker: StickerItem, x: Float, y: Float): IconHit? {
//        val s = sticker.extractRenderScale().coerceAtLeast(0.001f)
//        val localPad = (config.borderPaddingDp * density) / s
//        val pts = floatArrayOf(
//            -localPad, -localPad,
//            sticker.bmpW + localPad, -localPad,
//            sticker.bmpW + localPad, sticker.bmpH + localPad
//        )
//        sticker.matrix.mapPoints(pts)
//        val r = iconHitRadiusPx
//        if (hypot(x - pts[0], y - pts[1]) <= r) return IconHit.DELETE
//        if (hypot(x - pts[2], y - pts[3]) <= r) return IconHit.ROTATE
//        if (hypot(x - pts[4], y - pts[5]) <= r) return IconHit.SCALE
//        return null
//    }
//
//    /** Returns true when ([x],[y]) falls inside the sticker's transformed bitmap bounds. */
//    private fun hitTestBody(sticker: StickerItem, x: Float, y: Float): Boolean {
//        val inv = Matrix()
//        if (!sticker.matrix.invert(inv)) return false
//        val p = floatArrayOf(x, y)
//        inv.mapPoints(p)
//        return p[0] >= 0f && p[0] <= sticker.bmpW && p[1] >= 0f && p[1] <= sticker.bmpH
//    }
//
//    // ── Default placement ─────────────────────────────────────────────
//
//    /**
//     * Computes the centre point for a newly added sticker.
//     *
//     * Starting from the View centre, tries up to [MAX_CASCADE_RETRIES] positions
//     * offset [Config.cascadeOffsetDp] dp right-and-down from the previous position,
//     * provided the previous sticker is still at its default position and the candidate
//     * is within the View bounds.  Falls back to the View centre otherwise.
//     */
//    private fun computeDefaultCenter(): PointF {
//        val center = PointF(width / 2f, height / 2f)
//        val last = stickers.lastOrNull() ?: return center
//        if (!last.isAtDefaultPosition) return center
//
//        val offsetPx = config.cascadeOffsetDp * density
//
//        for (i in 1..MAX_CASCADE_RETRIES) {
//            val candidate = PointF(center.x + offsetPx * i, center.y + offsetPx * i)
//            if (candidate.x > width || candidate.y > height) break
//            if (stickers.none { stickerCentreNear(it, candidate) }) return candidate
//        }
//        return center
//    }
//
//    /** Returns true when sticker's current centre is within [NEAR_THRESHOLD_PX] of [point]. */
//    private fun stickerCentreNear(sticker: StickerItem, point: PointF): Boolean {
//        val c = sticker.getCenter()
//        return hypot(c.x - point.x, c.y - point.y) < NEAR_THRESHOLD_PX
//    }
//
//    // ── Constants ─────────────────────────────────────────────────────
//
//    private companion object {
//        const val MAX_CASCADE_RETRIES = 6
//        const val NEAR_THRESHOLD_PX = 4f
//        const val BORDER_CORNER_RADIUS_DP = 0f
//    }
//}
