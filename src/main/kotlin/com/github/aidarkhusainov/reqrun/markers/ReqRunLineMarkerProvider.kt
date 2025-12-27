package com.github.aidarkhusainov.reqrun.markers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.FunctionUtil

class ReqRunLineMarkerProvider : LineMarkerProviderDescriptor(), DumbAware {
    private val methodPattern =
        Regex("^\\s*(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\\s+\\S+", RegexOption.IGNORE_CASE)
    private val separatorPattern = Regex("^\\s*###.*$")

    override fun getName(): String = "ReqRun Run Marker"

    override fun getIcon(): javax.swing.Icon = AllIcons.Actions.Execute

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Use collectSlowLineMarkers to compute markers once per file.
        return null
    }

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        val file = elements.first().containingFile ?: return
        if (!isReqRunFile(file)) return
        val project = file.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val allowed = elements.toHashSet()
        val lineCount = document.lineCount
        for (line in 0 until lineCount) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            val match = methodPattern.find(lineText) ?: continue
            if (!isFirstInBlock(document, line)) continue
            val anchor = file.findElementAt(lineStart) ?: continue
            if (anchor !in allowed) continue
            val methodStart = lineStart + match.range.first
            val methodEnd = lineStart + match.range.last + 1
            val handler = GutterIconNavigationHandler<PsiElement> { _, _ ->
                ApplicationManager.getApplication().invokeLater {
                    invokeRunAction(project, file.virtualFile, methodStart)
                }
            }
            result.add(
                LineMarkerInfo(
                    anchor,
                    TextRange(methodStart, methodEnd),
                    AllIcons.Actions.Execute,
                    FunctionUtil.constant("Run HTTP request"),
                    handler,
                    com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.LEFT,
                    { "Run HTTP request" }
                )
            )
        }
    }

    private fun isReqRunFile(file: PsiFile): Boolean =
        file.virtualFile?.extension.equals("http", ignoreCase = true)

    private fun isFirstInBlock(document: com.intellij.openapi.editor.Document, line: Int): Boolean {
        var current = line - 1
        while (current >= 0) {
            val text = document.getText(
                TextRange(document.getLineStartOffset(current), document.getLineEndOffset(current))
            )
            if (separatorPattern.matches(text)) return true
            if (text.isNotBlank()) {
                if (methodPattern.matches(text)) return false
            }
            current--
        }
        return true
    }

    private fun invokeRunAction(project: Project?, file: VirtualFile, offset: Int) {
        project ?: return
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
            ?: FileEditorManager.getInstance(project).allEditors
                .asSequence()
                .mapNotNull { (it as? com.intellij.openapi.fileEditor.TextEditor)?.editor }
                .firstOrNull { it.document == doc }
            ?: return

        editor.caretModel.moveToOffset(offset)

        val action = ActionManager.getInstance()
            .getAction("com.github.aidarkhusainov.reqrun.actions.RunHttpRequestAction") ?: return
        ActionManager.getInstance()
            .tryToExecute(action, null, editor.contentComponent, ActionPlaces.UNKNOWN, true)
    }
}
