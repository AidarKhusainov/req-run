package com.github.aidarkhusainov.reqrun.ui

import com.github.aidarkhusainov.reqrun.lang.ReqRunFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.ui.EditorNotificationProvider
import javax.swing.JComponent
import java.util.function.Function

class ReqRunToolbarNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (!file.isHttpFile()) return null
        return Function { editor ->
            if (editor is TextEditor) ReqRunToolbarPanel(project, editor, file) else null
        }
    }

    private fun VirtualFile.isHttpFile(): Boolean =
        extension.equals("http", ignoreCase = true) || fileType is ReqRunFileType
}
