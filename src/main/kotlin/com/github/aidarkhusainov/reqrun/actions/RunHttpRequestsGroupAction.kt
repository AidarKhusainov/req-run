package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.core.HttpRequestParser
import com.github.aidarkhusainov.reqrun.core.RequestExtractor
import com.github.aidarkhusainov.reqrun.lang.ReqRunFileType
import com.github.aidarkhusainov.reqrun.notification.ReqRunNotifier
import com.github.aidarkhusainov.reqrun.services.ReqRunRequestSource
import com.github.aidarkhusainov.reqrun.services.ReqRunRunner
import com.github.aidarkhusainov.reqrun.services.ReqRunServiceContributor
import com.intellij.execution.services.ServiceViewManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

class RunHttpRequestsGroupAction : AnAction(), DumbAware {
    private val log = logger<RunHttpRequestsGroupAction>()
    private val requestLinePattern = Regex("(?m)^[ \\t]*[A-Za-z]+\\s+\\S+.*$")

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val hasEditorHttp = editor != null && file?.isHttpFile() == true
        val hasFileSelection = files?.any { it.isHttpFile() } == true
        val visible = project != null && (hasEditorHttp || hasFileSelection)
        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible
        if (!visible) return

        val text = when {
            editor?.selectionModel?.hasSelection() == true -> "Run Selected Requests"
            files != null && files.size > 1 -> "Run Selected Files"
            files != null && files.size == 1 && files[0].isHttpFile() -> "Run 'All in ${files[0].name}'"
            file?.isHttpFile() == true -> "Run 'All in ${file.name}'"
            else -> "Run Selected Requests"
        }
        e.presentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        val editorBlocks = when {
            editor != null && file?.isHttpFile() == true -> blocksFromEditor(file, editor)
            else -> null
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Executing HTTP requests", true) {
            override fun run(indicator: ProgressIndicator) {
                val blocks = when {
                    editorBlocks != null -> editorBlocks
                    files != null -> ReadAction.compute<List<Block>, Throwable> { blocksFromFiles(files, indicator) }
                    else -> emptyList()
                }

                if (blocks.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        ReqRunNotifier.warn(project, "No HTTP requests found to run.")
                    }
                    return
                }

                indicator.isIndeterminate = false
                val runner = project.getService(ReqRunRunner::class.java)
                var parseErrors = 0
                var execErrors = 0
                var cancelled = false
                val total = blocks.size

                for (index in blocks.indices) {
                    val block = blocks[index]
                    if (indicator.isCanceled) {
                        cancelled = true
                        break
                    }
                    indicator.fraction = ((index + 1).toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
                    indicator.text = "Executing ${index + 1}/$total"

                    val spec = HttpRequestParser.parse(block.text)
                    if (spec == null) {
                        parseErrors += 1
                        log.warn("ReqRun: failed to parse request block (length=${block.text.length})")
                        continue
                    }

                    val source = block.sourceOffset?.let { ReqRunRequestSource(block.file, it) }
                    try {
                        val result = runner.run(spec, source, indicator)
                        if (result.status == ReqRunRunner.Status.ERROR) {
                            execErrors += 1
                            log.warn(
                                "ReqRun: failed ${spec.method} ${spec.url}: ${result.execution.error ?: "unknown error"}"
                            )
                        }
                        ApplicationManager.getApplication().invokeLater({
                            if (project.isDisposed) return@invokeLater
                            ServiceViewManager.getInstance(project)
                                .select(result.execution, ReqRunServiceContributor::class.java, true, true)
                        }, ModalityState.any())
                    } catch (t: ProcessCanceledException) {
                        cancelled = true
                        val exec = runner.addCancelledExecution(spec, source)
                        log.info("ReqRun: cancelled ${spec.method} ${spec.url}")
                        ApplicationManager.getApplication().invokeLater({
                            if (project.isDisposed) return@invokeLater
                            ServiceViewManager.getInstance(project)
                                .select(exec, ReqRunServiceContributor::class.java, true, true)
                        }, ModalityState.any())
                        throw t
                    }
                    if (cancelled) break
                }

                if (cancelled) {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        ReqRunNotifier.info(project, "Request execution cancelled")
                    }
                    return
                }
                if (parseErrors > 0) {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        ReqRunNotifier.warn(project, "Skipped $parseErrors request(s) due to parse errors.")
                    }
                }
                if (execErrors > 0) {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        ReqRunNotifier.error(project, "Completed with errors: $execErrors/$total request(s).")
                    }
                }
            }
        })
    }

    private fun blocksFromEditor(file: VirtualFile, editor: com.intellij.openapi.editor.Editor): List<Block> {
        val blocks = RequestExtractor.extractAll(editor)
        return blocks.mapNotNull { block ->
            val requestOffset = findRequestLineOffset(block.text) ?: return@mapNotNull null
            Block(
                text = block.text,
                file = file,
                sourceOffset = block.startOffset + requestOffset
            )
        }
    }

    private fun blocksFromFiles(files: Array<VirtualFile>, indicator: ProgressIndicator?): List<Block> {
        val result = mutableListOf<Block>()
        val httpFiles = files
            .flatMap { collectHttpFiles(it, indicator) }
            .distinctBy { it.path }
            .sortedBy { it.path }
        for (httpFile in httpFiles) {
            if (indicator?.isCanceled == true) return result
            val text = VfsUtilCore.loadText(httpFile)
            val blocks = RequestExtractor.extractAllFromText(text)
            blocks.forEach { block ->
                val requestOffset = findRequestLineOffset(block.text) ?: return@forEach
                result.add(Block(block.text, httpFile, block.startOffset + requestOffset))
            }
        }
        return result
    }

    private fun collectHttpFiles(file: VirtualFile, indicator: ProgressIndicator?): List<VirtualFile> {
        if (!file.isValid) return emptyList()
        if (!file.isDirectory) {
            return if (file.isHttpFile()) listOf(file) else emptyList()
        }
        val result = mutableListOf<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(file, null) {
            if (indicator?.isCanceled == true) return@iterateChildrenRecursively false
            if (it.isHttpFile()) {
                result.add(it)
            }
            true
        }
        return result
    }

    private fun findRequestLineOffset(text: String): Int? {
        val match = requestLinePattern.find(text) ?: return null
        return match.range.first
    }

    private fun VirtualFile.isHttpFile(): Boolean =
        extension.equals("http", ignoreCase = true) || fileType is ReqRunFileType

    private data class Block(
        val text: String,
        val file: VirtualFile,
        val sourceOffset: Int?,
    )
}
