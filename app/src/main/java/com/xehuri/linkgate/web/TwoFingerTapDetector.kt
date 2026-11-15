package com.xehuri.linkgate.web

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * 把 Android 触摸事件喂给 [TwoFingerTapRecognizer]，命中连击时回调。
 *
 * - 每累计一次两指轻点给一次轻微触感反馈（[HapticFeedbackConstants.KEYBOARD_TAP]），
 *   让用户知道「还差一次」；
 * - 达标时给一次更明显的反馈（[HapticFeedbackConstants.LONG_PRESS]）并执行唤醒。
 *
 * 返回 false，事件继续交给 WebView 自己处理（单指滚动、点击完全不受影响）。
 */
class TwoFingerTapDetector(
    requiredTaps: Int,
    private val onProgress: (Int) -> Unit = {},
    private val onTriggered: () -> Unit,
) : View.OnTouchListener {

    private val recognizer = TwoFingerTapRecognizer(requiredTaps = requiredTaps)

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> recognizer.onDown(event.eventTime, event.x, event.y)
            MotionEvent.ACTION_POINTER_DOWN -> recognizer.onPointerDown(event.pointerCount)
            MotionEvent.ACTION_MOVE -> recognizer.onMove(event.x, event.y)
            MotionEvent.ACTION_POINTER_UP ->
                dispatch(view, recognizer.onUp(event.eventTime, isLastFinger = false))
            MotionEvent.ACTION_UP ->
                dispatch(view, recognizer.onUp(event.eventTime, isLastFinger = true))
            MotionEvent.ACTION_CANCEL -> recognizer.onCancel()
        }
        return false
    }

    private fun dispatch(view: View, result: TwoFingerTapRecognizer.Result) {
        when (result) {
            TwoFingerTapRecognizer.Result.IGNORED -> Unit
            TwoFingerTapRecognizer.Result.PROGRESS -> {
                haptic(view, HapticFeedbackConstants.KEYBOARD_TAP)
                onProgress(recognizer.tapsSoFar())
            }
            TwoFingerTapRecognizer.Result.TRIGGERED -> {
                haptic(view, HapticFeedbackConstants.LONG_PRESS)
                onTriggered()
            }
        }
    }

    private fun haptic(view: View, feedback: Int) {
        runCatching { view.performHapticFeedback(feedback) }
    }
}
