package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

class AddEnvVariableAction(private val isPrivate: Boolean) :
    AnAction(if (isPrivate) "Variable in Private File" else "Variable in Public File"),
    DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val envService = project.getService(ReqRunEnvironmentService::class.java)
        val file = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE)
        val path = envService.ensureEnvFile(file, isPrivate) ?: return
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return
        VfsUtil.markDirtyAndRefresh(false, false, false, vFile)

        val editors = FileEditorManager.getInstance(project).openFile(vFile, true)
        val textEditor = editors.filterIsInstance<TextEditor>().firstOrNull() ?: return
        val document = textEditor.editor.document

        WriteCommandAction.runWriteCommandAction(project) {
            val envName = envService.getSelectedEnvironment() ?: "local"
            if (envService.getSelectedEnvironment() == null) {
                envService.setSelectedEnvironment(envName)
            }
            val updated = addVariable(document.text, envName)
            document.setText(updated.text)
            val caretOffset = updated.caretOffset.coerceAtMost(document.textLength)
            textEditor.editor.caretModel.moveToOffset(caretOffset)
            textEditor.editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.MAKE_VISIBLE)
            textEditor.editor.contentComponent.requestFocusInWindow()
        }
    }

    private data class UpdateResult(val text: String, val caretOffset: Int)

    private fun addVariable(text: String, envName: String): UpdateResult {
        val root = parseRoot(text)
        val envObj = if (root.has(envName) && root.get(envName).isJsonObject) {
            root.getAsJsonObject(envName)
        } else {
            JsonObject().also { root.add(envName, it) }
        }
        val key = nextKey(envObj, "newVar")
        envObj.addProperty(key, "value")

        val gson = GsonBuilder().setPrettyPrinting().create()
        val updatedText = gson.toJson(root) + "\n"
        val marker = "\"$key\""
        val caret = updatedText.indexOf(marker).let { if (it == -1) updatedText.length else it + 1 }
        return UpdateResult(updatedText, caret)
    }

    private fun parseRoot(text: String): JsonObject {
        return try {
            val parsed = JsonParser.parseString(text)
            if (parsed.isJsonObject) parsed.asJsonObject else JsonObject()
        } catch (_: Throwable) {
            JsonObject()
        }
    }

    private fun nextKey(envObj: JsonObject, base: String): String {
        if (!envObj.has(base)) return base
        var index = 1
        while (true) {
            val key = "$base$index"
            if (!envObj.has(key)) return key
            index += 1
        }
    }
}
