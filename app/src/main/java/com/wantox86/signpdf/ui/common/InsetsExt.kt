package com.wantox86.signpdf.ui.common

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Applies top system bar (status bar) inset as additional top padding on this view,
 * preserving its original XML padding even if insets are dispatched multiple times.
 */
fun View.applyTopSystemBarPadding() {
    val initialTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        view.updatePadding(top = initialTop + topInset)
        insets
    }
    requestApplyInsetsWhenAttached()
}

/**
 * Applies bottom system bar (navigation bar) inset as additional bottom padding on this view,
 * preserving its original XML padding.
 */
fun View.applyBottomSystemBarPadding() {
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        view.updatePadding(bottom = initialBottom + bottomInset)
        insets
    }
    requestApplyInsetsWhenAttached()
}

/**
 * Applies both top and bottom system bar insets as additional padding on this view.
 */
fun View.applySystemBarPadding() {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            top = initialTop + bars.top,
            bottom = initialBottom + bars.bottom
        )
        insets
    }
    requestApplyInsetsWhenAttached()
}

/**
 * Applies bottom system bar inset as additional bottom margin (useful for floating docks/FABs).
 */
fun View.applyBottomSystemBarMargin() {
    val initialBottomMargin = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = initialBottomMargin + bottomInset
        }
        insets
    }
    requestApplyInsetsWhenAttached()
}

private fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        requestApplyInsets()
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}
