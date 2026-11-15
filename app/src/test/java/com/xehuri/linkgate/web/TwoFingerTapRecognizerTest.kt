package com.xehuri.linkgate.web

import org.junit.Assert.assertEquals
import org.junit.Test

class TwoFingerTapRecognizerTest {

    private fun recognizer(requiredTaps: Int = 1) =
        TwoFingerTapRecognizer(moveSlop = 30f, tapTimeout = 400L, tapGapTimeout = 900L, requiredTaps = requiredTaps)

    /** 模拟一次完整的两指轻点（POINTER_UP 抬起 + 随后 ACTION_UP） */
    private fun TwoFingerTapRecognizer.tap(time: Long, startX: Float = 100f, startY: Float = 100f):
        TwoFingerTapRecognizer.Result {
        onDown(time, startX, startY)
        onPointerDown(2)
        val result = onUp(time + 80, isLastFinger = false)
        onUp(time + 80, isLastFinger = true)
        return result
    }

    @Test
    fun `两指快速轻点会被识别`() {
        val r = recognizer()
        assertEquals(TwoFingerTapRecognizer.Result.TRIGGERED, r.tap(0L))
    }

    @Test
    fun `单指抬起不会触发`() {
        val r = recognizer()
        r.onDown(0L, 100f, 100f)
        assertEquals(TwoFingerTapRecognizer.Result.IGNORED, r.onUp(100L, isLastFinger = true))
    }

    @Test
    fun `有位移时（捏合缩放）不触发`() {
        val r = recognizer()
        r.onDown(0L, 100f, 100f)
        r.onPointerDown(2)
        r.onMove(160f, 140f)
        assertEquals(TwoFingerTapRecognizer.Result.IGNORED, r.onUp(200L, isLastFinger = false))
    }

    @Test
    fun `按压超时不触发`() {
        val r = recognizer()
        r.onDown(0L, 100f, 100f)
        r.onPointerDown(2)
        assertEquals(TwoFingerTapRecognizer.Result.IGNORED, r.onUp(900L, isLastFinger = false))
    }

    @Test
    fun `三指按下不触发`() {
        val r = recognizer()
        r.onDown(0L, 100f, 100f)
        r.onPointerDown(2)
        r.onPointerDown(3)
        assertEquals(TwoFingerTapRecognizer.Result.IGNORED, r.onUp(150L, isLastFinger = false))
    }

    @Test
    fun `取消后不会误触发`() {
        val r = recognizer()
        r.onDown(0L, 100f, 100f)
        r.onPointerDown(2)
        r.onCancel()
        assertEquals(TwoFingerTapRecognizer.Result.IGNORED, r.onUp(150L, isLastFinger = true))
    }

    @Test
    fun `单次两指轻点只计一次不重复触发`() {
        val r = recognizer()
        r.onDown(0L, 100f, 100f)
        r.onPointerDown(2)
        assertEquals(TwoFingerTapRecognizer.Result.TRIGGERED, r.onUp(100L, isLastFinger = false))
        // 随后同一手势的 ACTION_UP 不应该再触发一次
        assertEquals(TwoFingerTapRecognizer.Result.IGNORED, r.onUp(100L, isLastFinger = true))
    }

    // ---------------- 连击（默认两次双指点击才唤醒） ----------------

    @Test
    fun `需要两次时必须连点两次才唤醒`() {
        val r = recognizer(requiredTaps = 2)
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(0L))
        assertEquals(1, r.tapsSoFar())
        assertEquals(TwoFingerTapRecognizer.Result.TRIGGERED, r.tap(300L))
        assertEquals("触发后计数归零", 0, r.tapsSoFar())
    }

    @Test
    fun `两次之间间隔太久会重新计数`() {
        val r = recognizer(requiredTaps = 2)
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(0L))
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(5_000L))
        assertEquals(TwoFingerTapRecognizer.Result.TRIGGERED, r.tap(5_300L))
    }

    @Test
    fun `中间夹一次单指点按会打断连击`() {
        val r = recognizer(requiredTaps = 2)
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(0L))
        r.onDown(300L, 100f, 100f)
        r.onUp(360L, isLastFinger = true)
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(600L))
        assertEquals(TwoFingerTapRecognizer.Result.TRIGGERED, r.tap(900L))
    }

    @Test
    fun `中间夹一次捏合会打断连击`() {
        val r = recognizer(requiredTaps = 2)
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(0L))
        r.onDown(300L, 100f, 100f)
        r.onPointerDown(2)
        r.onMove(220f, 220f)
        r.onUp(380L, isLastFinger = false)
        r.onUp(380L, isLastFinger = true)
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(600L))
    }

    @Test
    fun `三次模式需要连点三次`() {
        val r = recognizer(requiredTaps = 3)
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(0L))
        assertEquals(TwoFingerTapRecognizer.Result.PROGRESS, r.tap(300L))
        assertEquals(TwoFingerTapRecognizer.Result.TRIGGERED, r.tap(600L))
    }

    @Test
    fun `requiredTaps 为一时每次都会触发`() {
        val r = recognizer(requiredTaps = 1)
        assertEquals(TwoFingerTapRecognizer.Result.TRIGGERED, r.tap(0L))
        assertEquals(TwoFingerTapRecognizer.Result.TRIGGERED, r.tap(1_000L))
    }
}
