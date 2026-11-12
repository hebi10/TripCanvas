package com.hebi.tripcanvas.qa

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Real-device UIAutomator tap: only tap a currently visible, unobscured,
 * re-resolved clickable node. Never interpret mere accessibility-node
 * presence as proof of a successful user action.
 */
internal class QaUiNavigator(private val device: UiDevice) {
    fun clickVisible(
        selector: BySelector,
        label: String,
        expectedAfter: String? = null
    ) {
        val height = device.displayHeight
        val safeTop = height / 10
        val safeBottom = height * 83 / 100
        var lastObservation = "화면에 클릭 대상이 없습니다"

        repeat(16) { attempt ->
            try {
                val node = device.wait(Until.findObject(selector), 1200)
                if (node == null) {
                    lastObservation = "대상 미발견"
                    scroll(down = attempt < 10)
                    return@repeat
                }

                var target = node
                var depth = 0
                while (!target.isClickable) {
                    val parent = target.parent
                        ?: throw AssertionError("클릭 가능한 요소가 없습니다: $label")
                    target = parent
                    depth++
                    if (depth > 12) {
                        throw AssertionError("클릭 가능한 부모를 찾지 못했습니다: $label")
                    }
                }

                val bounds = target.visibleBounds
                if (bounds.width() <= 0 || bounds.height() <= 0) {
                    lastObservation = "대상 노출 영역 없음"
                    scroll(down = true)
                    return@repeat
                }
                if (bounds.bottom > safeBottom) {
                    lastObservation = "하단에 걸친 대상"
                    scroll(down = true)
                    return@repeat
                }
                if (bounds.top < safeTop) {
                    lastObservation = "상단에 걸친 대상"
                    scroll(down = false)
                    return@repeat
                }

                target.click()
                device.waitForIdle()
                if (expectedAfter == null ||
                    device.wait(Until.hasObject(By.text(expectedAfter)), 5000)
                ) {
                    return
                }

                lastObservation = "클릭 뒤 '$expectedAfter' 화면 상태 미확인"
                SystemClock.sleep(200)
            } catch (_: StaleObjectException) {
                lastObservation = "UI 요소 갱신으로 노드 무효화"
                SystemClock.sleep(200)
            }
        }

        throw AssertionError("안정적인 화면 클릭 실패: $label ($lastObservation)")
    }

    private fun scroll(down: Boolean) {
        val middle = device.displayWidth / 2
        val height = device.displayHeight
        if (down) {
            device.swipe(middle, height * 73 / 100, middle, height * 36 / 100, 25)
        } else {
            device.swipe(middle, height * 36 / 100, middle, height * 73 / 100, 25)
        }
        device.waitForIdle()
    }
}
