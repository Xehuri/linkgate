package com.xehuri.linkgate.web

/**
 * 两指轻点的纯逻辑状态机（不含 Android 依赖，可单元测试）。
 *
 * 单次判定条件：两根手指按下、没有超过 [moveSlop]、按住时间不超过 [tapTimeout] 就抬起。
 * 捏合缩放会因位移超过阈值被判为非轻点，单指操作完全不受影响。
 *
 * 连击判定：多次两指轻点之间的间隔不能超过 [tapGapTimeout]，累计到 [requiredTaps] 次才算唤醒；
 * 中间夹了单指点击、捏合、三指或多指操作都会让连击重新开始。
 */
class TwoFingerTapRecognizer(
    private val moveSlop: Float = 30f,
    private val tapTimeout: Long = 400L,
    private val tapGapTimeout: Long = 900L,
    private val requiredTaps: Int = 1,
) {

    enum class Result {
        /** 这次抬手不算两指轻点（可能是滑动、捏合、或按太久） */
        IGNORED,

        /** 算了一次两指轻点，但还没达到需要连击的次数 */
        PROGRESS,

        /** 连击次数达标，应该执行唤醒动作 */
        TRIGGERED,
    }

    private var downTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var maxPointers = 0
    private var moved = false
    private var consumed = false

    private var chain = 0
    private var lastTapTime = 0L

    /** 当前连击已累计的次数 */
    fun tapsSoFar(): Int = chain

    fun onDown(eventTime: Long, x: Float, y: Float) {
        if (chain > 0 && eventTime - lastTapTime > tapGapTimeout) chain = 0
        downTime = eventTime
        startX = x
        startY = y
        maxPointers = 1
        moved = false
        consumed = false
    }

    fun onPointerDown(pointerCount: Int) {
        if (pointerCount > maxPointers) maxPointers = pointerCount
    }

    fun onMove(x: Float, y: Float) {
        if (kotlin.math.abs(x - startX) > moveSlop || kotlin.math.abs(y - startY) > moveSlop) {
            moved = true
        }
    }

    /** 手指抬起（ACTION_POINTER_UP 或 ACTION_UP）。 */
    fun onUp(eventTime: Long, isLastFinger: Boolean): Result {
        var result = Result.IGNORED
        if (!consumed && !moved && maxPointers == 2 && eventTime - downTime <= tapTimeout) {
            consumed = true
            chain += 1
            lastTapTime = eventTime
            result = if (chain >= requiredTaps) {
                chain = 0
                Result.TRIGGERED
            } else {
                Result.PROGRESS
            }
        }
        if (isLastFinger) {
            // 这根手指之前没有算过轻点 → 说明这是一个「无效手势」，连击链必须断掉，
            // 否则用户在网页上正常点击/滑动时会意外攒够次数把控制条唤出来。
            if (!consumed) chain = 0
            maxPointers = 0
            moved = false
            consumed = false
        }
        return result
    }

    fun onCancel() {
        maxPointers = 0
        moved = false
        consumed = false
        chain = 0
    }
}
