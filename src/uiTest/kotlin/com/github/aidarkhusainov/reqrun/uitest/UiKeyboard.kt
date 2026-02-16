package com.github.aidarkhusainov.reqrun.uitest

import java.awt.AWTException
import java.awt.Robot
import java.awt.event.KeyEvent

object UiKeyboard {
    private val robot =
        try {
            Robot().apply { autoDelay = 30 }
        } catch (e: AWTException) {
            throw IllegalStateException("UI tests require AWT Robot support.", e)
        }

    fun shortcut(vararg keys: Int) {
        keys.forEach { robot.keyPress(it) }
        keys.reversed().forEach { robot.keyRelease(it) }
    }

    fun pasteText(text: String) {
        ClipboardUtils.setText(text)
        shortcut(KeyEvent.VK_CONTROL, KeyEvent.VK_V)
    }

    fun pressEnter() {
        shortcut(KeyEvent.VK_ENTER)
    }

    fun pressEsc() {
        shortcut(KeyEvent.VK_ESCAPE)
    }

    fun selectAll() {
        shortcut(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
    }

    fun copy() {
        shortcut(KeyEvent.VK_CONTROL, KeyEvent.VK_C)
    }

    fun undo() {
        shortcut(KeyEvent.VK_CONTROL, KeyEvent.VK_Z)
    }

    fun selectDown(lines: Int) {
        if (lines <= 0) return
        robot.keyPress(KeyEvent.VK_SHIFT)
        repeat(lines) {
            robot.keyPress(KeyEvent.VK_DOWN)
            robot.keyRelease(KeyEvent.VK_DOWN)
        }
        robot.keyRelease(KeyEvent.VK_SHIFT)
    }
}
