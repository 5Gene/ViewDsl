package osp.spark.view.dsl

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import osp.spark.view.auxiliary.animator
import osp.spark.view.auxiliary.findAnimator
import osp.spark.view.auxiliary.view
import osp.spark.view.wings.dpf
import osp.spark.view.wings.isVisible
import osp.spark.view.wings.safeAs
import osp.spark.view.wings.visibility

import osp.sparkj.cartoon.wings.alpha
import osp.sparkj.cartoon.wings.transForm

interface FlipCard : ViewModifier {

    fun VModifier.vFlipFather(
        hover: Float = 36F, time: Long = 3111, stable: Boolean = false
    ) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = { locker ->
                attachAnimator(locker, false)
                with(locker.view()) {
                    this.safeAs<ViewGroup>()?.apply {
                        clipChildren = false
                        clipToPadding = false
                    }
                    this.parent.safeAs<ViewGroup>()?.apply {
                        clipChildren = false
                        clipToPadding = false
                    }
                }
                locker.animator(ANI_RATIO_TOUCHY).apply {
                    setCurrentFraction(1F)
                }
            },
            touchHandler = touchYAnimator
        )
    )

    /**
     * cardWidthFactor:卡片宽度比例
     */
    fun VModifier.vFlipExpand(cardWidthFactor: Float = 0.95F, topOffset: Float? = null) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = { attachAnimator(it, true) },
            drawHandler = { locker, canvas, superDraw ->
                val ani = locker.findAnimator(ANI_RATIO_TOUCHY)?.animatedValue.safeAs<Float>() ?: 1F
                if (ani > 0.9F) {
                    return@VDrawTouchModifier
                }
                //对canvas做变换
                val camera = locker.retrieve("camera") { Camera() }!!
                val height = canvas.height.toFloat()
                val bitmap = locker.retrieve("bitmap") {
                    Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888).apply {
                        superDraw(Canvas(this))
                    }
                }!!

                val topOffsetValue = (topOffset?.let { height / 2F - it } ?: (height / 4F)) * ani
                val widthScaleValue = 1 - (1 - cardWidthFactor) * ani

                canvas.transForm(
                    translateY = -topOffsetValue,
                    scaleX = widthScaleValue,
                    clip = { offsetX, offsetY ->
                        val top = interval(.5F, 1F, ani) / 2 * height
                        clipRect(offsetX, top, -offsetX, -offsetY)
                    }) {
                    drawBitmap(bitmap, 0F, 0F, null)
                }

                if (ani < 0.5F) {
                    canvas.transForm(
                        translateY = -topOffsetValue,
                        scaleX = widthScaleValue,
                        rotateX = -180F * ani,
                        locationZ = (-30).dpf(),
                        camera = camera,
                        clip = { offsetX, offsetY ->
                            clipRect(offsetX, offsetY, -offsetX, 0F)
                        }) {
                        drawBitmap(bitmap, 0F, 0F, null)
                        if (ani > 0F) {
                            drawColor(Color.BLACK.alpha(.6 * ani))
                        }
                    }
                }
            })
    )

    fun VModifier.vFlipCard(widthScale: Float = 0.1F, topOffset: Float? = null) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = { attachAnimator(it, true) },
            drawHandler = { locker, canvas, superDraw ->
                if (!locker.view().isVisible()) {
                    return@VDrawTouchModifier
                }
                //1-0.5
                val ani = 1 - (locker.findAnimator(ANI_RATIO_TOUCHY)?.animatedValue.safeAs<Float>() ?: 0F)
                val height = canvas.height.toFloat()
                val offsetTop = topOffset ?: (height / 4F)
                val topHeightLosting = offsetTop * ani * 2
                if (topHeightLosting >= offsetTop) {
                    return@VDrawTouchModifier
                }
                val topOffsetValue = (height / 2F - offsetTop) * ani
                val widthScaleValue = 1 + widthScale * ani

                //对canvas做变换
                val camera = locker.retrieve("camera") { Camera() }!!
                val bitmap = locker.retrieve("bitmap") {
                    Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888).apply {
                        superDraw(Canvas(this))
                    }
                }!!

                canvas.transForm(
                    translateY = topOffsetValue + topHeightLosting,
                    scaleX = widthScaleValue, heightFactor = 0F,
                    clip = { offsetX, _ ->
                        clipRect(offsetX, 0F, -offsetX, offsetTop - topHeightLosting)
                    }) {
                    drawBitmap(bitmap, 0F, 0F, null)
                }

                canvas.transForm(
                    translateY = topOffsetValue,
                    scaleX = widthScaleValue,
                    heightFactor = offsetTop / height,
                    rotateX = 180F * ani,
                    locationZ = (-30).dpf(),
                    camera = camera,
                    clip = { offsetX, offsetY ->
                        clipRect(offsetX, 0F, -offsetX, height + offsetY)
                    }) {
                    drawBitmap(bitmap, 0F, 0F, null)
                }
            })
    )

    fun VModifier.vFlipHeadView(listener: (View, Float) -> Unit = { _, _ -> }) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = {
                with(it.view()) {
                    val animator: ValueAnimator = it.findAnimator(ANI_RATIO_TOUCHY)!!
                    if (animator.animatedValue.safeAs<Float>() == 0F) {
                        visibility(false)
                    }
                    animator.addUpdateListener { valueAnimator ->
                        val v = 1 - (valueAnimator.animatedValue as Float)
                        visibility(v <= 0.5)
                        if (v > 0.5) {
                            return@addUpdateListener
                        }
                        val topOffsetValue = (parent.safeAs<View>()!!.height / 2 - top) * v
                        listener.invoke(this, v)
                        transForm(
//                            nscaleY = 1 - v * 2,
                            translateY = topOffsetValue,
                            locationZ = 60000F
                        )
                    }
                }
            })
    )

    fun VModifier.vFlipCardView(widthScale: Float = 0.95F) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = {
                with(it.view()) {
                    val animator: ValueAnimator = it.findAnimator(ANI_RATIO_TOUCHY)!!
                    if (animator.animatedValue.safeAs<Float>() == 0F) {
                        visibility(false)
                    }
                    animator.addUpdateListener { valueAnimator ->
                        val v = 1 - (valueAnimator.animatedValue as Float)
                        visibility(v <= 0.5)
                        if (v > 0.5) {
                            return@addUpdateListener
                        }
                        val topOffsetValue = (parent.safeAs<View>()!!.height / 2 - top) * v
                        transForm(
                            rotateX = 180F * v,
                            heightFactor = 0F,
                            nscaleX = 1 + (1 / widthScale - 1) * v,
                            translateY = topOffsetValue,
                            locationZ = 60000F
                        )
                    }
                }
            })
    )
}

interface View3DModifier : FlipCard {

    fun VModifier.v3DTouch2(
        deep: Float = 15F
    ) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = { locker ->
                attachAnimator(locker, true)
                with(locker.view()) {
                    this.parent.safeAs<ViewGroup>()?.apply {
                        clipChildren = false
                        clipToPadding = false
                    }
                }
            },
            drawHandler = { locker, canvas, superDraw ->
                //对canvas做变换
                val camera = locker.retrieve("camera") { Camera() }!!
                locker.retrieve<PointF>(TOUCH_EVENT)?.let { point ->
                    val press_value = locker.animator(ANI_RATIO_PRESS).animatedValue.safeAs<Float>() ?: 0F
                    canvas.transForm(
                        camera = camera,
                        rotateY = (point.x - canvas.width / 2) / (canvas.width / 2) * deep * press_value,
                        rotateX = (canvas.height / 2 - point.y) / (canvas.height / 2) * deep * press_value
                    ) {
                        superDraw(this)
                    }
                } ?: superDraw(canvas)
            },
            touchHandler = { locker, event, superTouch ->
                touchPressAnimator(locker, event, superTouch)
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        locker.view().postInvalidate()
                    }
                }
                true
            }
        )
    )

    fun VModifier.v3DTouch(
        deep: Float = 15F
    ) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = { locker ->
                attachAnimator(locker, false)
                with(locker.view()) {
                    this.parent.safeAs<ViewGroup>()?.apply {
                        clipChildren = false
                        clipToPadding = false
                    }
                    locker.animator(ANI_RATIO_PRESS).addUpdateListener {
                        val v = it.animatedValue as Float
                        val point = locker.retrieve<PointF>(TOUCH_EVENT)!!
                        if (v < 1) {
                            rotationY = (point.x - width / 2) / (width / 2) * deep * v
                            rotationX = (height / 2 - point.y) / (height / 2) * deep * v
                        }
                    }
                }
            },
            touchHandler = { locker, event, superTouch ->
                touchPressAnimator(locker, event, superTouch)
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (!locker.animator(ANI_RATIO_PRESS).isRunning) {
                            with(locker.view()) {
                                val pointf = locker.retrieve<PointF>(TOUCH_EVENT)!!
                                rotationY = (pointf.x - width / 2) / (width / 2) * deep
                                rotationX = (height / 2 - pointf.y) / (height / 2) * deep
                            }
                        }
                    }
                }
                superTouch()
                true
            })
    )


    /**
     * 需要父级控件不剪切
     */
    fun VModifier.vRotateHover(
        hover: Float = 26F, time: Long = 3111, stable: Boolean = true
    ) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = { locker ->
                with(locker.view()) {
                    this.parent.safeAs<ViewGroup>()?.apply {
                        clipChildren = false
                        clipToPadding = false
                    }
                    locker.animator("hover_rotation") {
                        ValueAnimator.ofFloat(0F, 1F).apply {
                            duration = time
                            addUpdateListener {
                                postInvalidate()
                            }
                        }
                    }
                }
            },
            drawHandler = { locker, canvas, superDraw ->
                //对canvas做变换
                val camera = locker.retrieve("camera") { Camera() }!!
                val width = canvas.width.toFloat()
                val height = canvas.height.toFloat()
                val bitmap = if (!stable) {
                    locker.retrieve("bitmap") {
                        Bitmap.createBitmap(
                            canvas.width,
                            canvas.height,
                            Bitmap.Config.ARGB_8888
                        )
                    }!!.apply {
                        val newcanvas = locker.retrieve("newcanvas") { Canvas(this) }!!
                        superDraw(newcanvas)
                    }
                } else {
                    locker.retrieve("bitmap") {
                        Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888).apply {
                            superDraw(Canvas(this))
                        }
                    }!!
                }

                val ani = locker.animator("hover_rotation").animatedValue.safeAs<Float>()
                if (ani == null) {
                    canvas.drawBitmap(bitmap, 0F, 0F, null)
                    return@VDrawTouchModifier
                }

                val hovertime = 0.2F
                val hoverEnd = 1 - hovertime
                val hovering = if (ani <= hovertime) {
                    hover * interval(0F, .2F, ani)
                } else if (ani >= hoverEnd) {
                    hover - hover * interval(hoverEnd, 1F, ani)
                } else {
                    hover
                }
                val rotateing = 360F * interval(hovertime, hoverEnd, ani)

                canvas.transForm(
                    rotate = rotateing,
                    clip = { _, _ ->
                        clipRect(-width, -height, width, 0F)
                    }
                ) {
                    drawBitmap(bitmap, 0F, 0F, null)
                }

                canvas.transForm(
                    camera = camera,
                    rotate = rotateing,
                    rotateX = hovering,
                    clip = { _, _ ->
                        clipRect(-width, 0F, width, height)
                    }
                ) {
                    drawBitmap(bitmap, 0F, 0F, null)
                }

            },
            touchHandler = { locker, event, superTouch ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        with(locker.animator("hover_rotation")) {
                            if (!isRunning) {
                                cancel()
                                start()
                            } else {
                                reverse()
                            }
                        }
                    }
                }
                superTouch()
                true
            })
    )


    fun VModifier.vFold(
        hover: Float = 36F, time: Long = 3111, stable: Boolean = false
    ) = this.then(
        ViewModifier.VDrawTouchModifier(
            attachedHandler = { locker ->
                attachAnimator(locker, true)
                with(locker.view()) {
                    this.parent.safeAs<ViewGroup>()?.apply {
                        clipChildren = false
                        clipToPadding = false
                    }
                }
            },
            drawHandler = { locker, canvas, superDraw ->
                //对canvas做变换
                val camera = locker.retrieve("camera") { Camera() }!!
                val width = canvas.width.toFloat()
                val height = canvas.height.toFloat()
                val bitmap = if (!stable) {
                    locker.retrieve("bitmap") {
                        Bitmap.createBitmap(
                            canvas.width,
                            canvas.height,
                            Bitmap.Config.ARGB_8888
                        )
                    }!!.apply {
                        val newcanvas = locker.retrieve("newcanvas") { Canvas(this) }!!
                        superDraw(newcanvas)
//                        val bitmap = Bitmap.createBitmap(canvas.width,canvas.width,Bitmap.Config.ARGB_8888)
//                        superDraw(Canvas(bitmap))
                    }
                } else {
                    locker.retrieve("bitmap") {
                        Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888).apply {
                            superDraw(Canvas(this))
                        }
                    }!!
                }

                val ani = (locker.animator(ANI_RATIO_TOUCHY).animatedValue.safeAs<Float>() ?: 0F)

                canvas.save()
                canvas.clipRect(0F, height / 2F, width, height)
                canvas.drawBitmap(bitmap, 0F, 0F, null)
                canvas.restore()

                canvas.transForm(
                    camera = camera,
                    rotateX = -180F * ani,
                    locationZ = (-30).dpf(),
                    clip = { offsetX, offsetY ->
                        clipRect(offsetX, offsetY, -offsetX, 0F)
                    }
                ) {
                    drawBitmap(bitmap, 0F, 0F, null)
                }
            },
            touchHandler = touchYAnimator
        )
    )
}
