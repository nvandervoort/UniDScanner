package com.capstone.unid.unidscanner

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View

/**
 * LoginListRecyclerView.kt
 * Created by nathanvandervoort on 12/16/17.
 *
 * Imlements a custom RecylerView that expands the height up to [R.dimen.login_list_height]
 */
class LoginListRecyclerView : RecyclerView {

    constructor(context: Context) :
            super(context)


    constructor(context: Context, attrs: AttributeSet?) :
            super(context, attrs)


    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxHeight = View.MeasureSpec.makeMeasureSpec(resources.getDimensionPixelSize(R.dimen.login_list_height), View.MeasureSpec.AT_MOST)
        super.onMeasure(widthSpec, maxHeight)
    }
}