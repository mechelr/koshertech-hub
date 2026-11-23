package com.koshertech.hub

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.HorizontalScrollView

/**
 * Three full-width pages side by side that snap one page at a time when swiped
 * (Home → My Apps → Parental Controls). Every page is created up front, so all
 * views on every page can be found right away.
 */
class SwipePager @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    var onPage: ((Int) -> Unit)? = null
    var page = 0
        private set
    private var downX = 0f
    private var flingVx = 0

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
    }

    private fun count(): Int = (getChildAt(0) as? ViewGroup)?.childCount ?: 1

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val row = getChildAt(0) as? ViewGroup ?: return
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            val lp = c.layoutParams; lp.width = w; c.layoutParams = lp
        }
        post { scrollTo(page * w, 0) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { downX = ev.x; flingVx = 0 }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) { downX = ev.x; flingVx = 0 }
        val handled = super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            val w = width.coerceAtLeast(1)
            val dx = ev.x - downX
            val target = when {
                flingVx > 900 -> page + 1
                flingVx < -900 -> page - 1
                dx < -w * 0.18f -> page + 1
                dx > w * 0.18f -> page - 1
                else -> Math.round(scrollX.toFloat() / w)
            }
            goTo(target)
        }
        return handled
    }

    // Replace free flinging with page snapping (direction is read in onTouchEvent).
    override fun fling(velocityX: Int) { flingVx = velocityX }

    fun goTo(p: Int, animate: Boolean = true) {
        page = p.coerceIn(0, count() - 1)
        if (animate) smoothScrollTo(page * width, 0) else scrollTo(page * width, 0)
        onPage?.invoke(page)
    }
}
