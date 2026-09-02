package com.jooshin.diary.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.NestedScrollingParent
import androidx.core.view.NestedScrollingParentHelper

/**
 * activity_main.xml 의 최상위 컨테이너.
 *
 * 일기 목록(RecyclerView)이 맨 위까지 스크롤된 뒤에도 계속 아래로 당기면, 목록 자신은 더 이상
 * 스크롤할 곳이 없어서 그 "남은" 드래그량(dyUnconsumed)을 안드로이드가 부모에게 알려준다.
 * 여기서 그 남은 드래그량을 받아 접혀 있던 달력 영역(headerContainer)을 다시 펼치는 데 쓴다.
 *
 * 예전에 CoordinatorLayout + AppBarLayout 을 썼을 때는 AppBarLayout 이 "이 영역을 손가락으로
 * 바로 드래그하면 접는다"는 동작까지 같이 가지고 있어서, 달력의 좌우 스와이프(월 이동) 제스처와
 * 터치를 서로 먼저 가로채려고 충돌했었다. 이 클래스는 그런 터치 가로채기는 전혀 하지 않고,
 * RecyclerView 가 스스로 알려주는 "중첩 스크롤(nested scroll)" 신호만 받아서 쓰기 때문에
 * 달력 쪽 터치 처리와는 부딪히지 않는다.
 */
class MainRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), NestedScrollingParent {

    private val parentHelper = NestedScrollingParentHelper(this)

    /** 목록이 맨 위에서 더 아래로 당겨질 때(overscroll) 그 여분의 드래그량(px, 양수)을 알려준다. */
    var onOverscrollDown: ((Int) -> Unit)? = null

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return nestedScrollAxes and View.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedScrollAccepted(child: View, target: View, nestedScrollAxes: Int) {
        parentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes)
    }

    override fun onStopNestedScroll(target: View) {
        parentHelper.onStopNestedScroll(target)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
        // dyUnconsumed < 0 : 목록이 이미 맨 위라 더 못 내려가는데도 계속 아래로 당기는 중
        if (dyUnconsumed < 0) onOverscrollDown?.invoke(-dyUnconsumed)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        // 목록이 먼저 다 쓰고 남은 것만 받아 쓰므로, 미리 가로채는 것은 없다.
    }

    override fun getNestedScrollAxes(): Int = parentHelper.nestedScrollAxes

    override fun onNestedFling(target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean = false

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean = false
}
