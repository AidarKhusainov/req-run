package com.github.aidarkhusainov.reqrun.uitest

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class ReqRunUiRobot(
    private val remoteRobot: RemoteRobot,
) {
    private val timeout = Duration.ofSeconds(20)
    private val reqRunEditorActionIdsByText =
        mapOf(
            "Run HTTP Request" to "com.github.aidarkhusainov.reqrun.actions.RunHttpRequestsGroupAction",
            "Run Selected Requests" to "com.github.aidarkhusainov.reqrun.actions.RunHttpRequestsGroupAction",
            "Run HTTP Requests (Group)" to "com.github.aidarkhusainov.reqrun.actions.RunHttpRequestsGroupAction",
            "Convert to cURL and Copy" to "com.github.aidarkhusainov.reqrun.actions.CopyCurlAction",
            "Paste cURL as HTTP" to "com.github.aidarkhusainov.reqrun.actions.PasteCurlAction",
        )

    fun ensureIdeFrame() {
        if (isIdeFrameVisible()) return
        waitFor("Welcome frame visible") { isWelcomeFrameVisible() }
    }

    fun openProject(projectPath: Path) {
        if (isIdeFrameVisible()) return
        val name = projectPath.fileName.toString()
        val item =
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[contains(@class,'Recent') and contains(@text,'$name')]"),
                timeout,
            )
        item.doubleClick()
        waitFor("IDE frame visible") { isIdeFrameVisible() }
    }

    fun reloadUnsavedDocuments() {
        remoteRobot.runJs(
            """
            const FileDocumentManager = Packages.com.intellij.openapi.fileEditor.FileDocumentManager;
            const manager = FileDocumentManager.getInstance();
            const unsaved = manager.getUnsavedDocuments();
            for (let i = 0; i < unsaved.length; i++) {
                manager.reloadFromDisk(unsaved[i]);
            }
            """.trimIndent(),
            true,
        )
    }

    fun closeProject() {
        invokeAction("Close Project")
        waitFor("Welcome frame visible") { isWelcomeFrameVisible() }
    }

    fun openFile(fileName: String) {
        val escapedFileName = fileName.replace("\\", "\\\\").replace("'", "\\'")
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const LocalFileSystem = Packages.com.intellij.openapi.vfs.LocalFileSystem;
            const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const path = project.getBasePath() + "/$escapedFileName";
            const file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
            if (!file) throw "Cannot find file: " + path;
            FileEditorManager.getInstance(project).openFile(file, true);
            """.trimIndent(),
            true,
        )
    }

    fun setEnvironment(name: String?) {
        val value = name?.replace("\\", "\\\\")?.replace("'", "\\'")?.let { "'$it'" } ?: "null"
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const envClass = loader.loadClass("com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService");
            const envService = project.getService(envClass);
            envService.setSelectedEnvironment($value);
            """.trimIndent(),
            true,
        )
    }

    fun goToLine(line: Int) {
        focusTextEditor()
        val targetLine = (line - 1).coerceAtLeast(0)
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
            const LogicalPosition = Packages.com.intellij.openapi.editor.LogicalPosition;
            const ScrollType = Packages.com.intellij.openapi.editor.ScrollType;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (!editor) throw "No selected text editor";
            const line = Math.min($targetLine, Math.max(0, editor.getDocument().getLineCount() - 1));
            editor.getSelectionModel().removeSelection();
            editor.getCaretModel().removeSecondaryCarets();
            editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, 0));
            editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
            """.trimIndent(),
            true,
        )
    }

    fun selectLines(from: Int, to: Int) {
        focusTextEditor()
        val startLine = (from - 1).coerceAtLeast(0)
        val endLine = (to - 1).coerceAtLeast(startLine)
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
            const ScrollType = Packages.com.intellij.openapi.editor.ScrollType;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (!editor) throw "No selected text editor";
            const document = editor.getDocument();
            const maxLine = Math.max(0, document.getLineCount() - 1);
            const startLine = Math.min($startLine, maxLine);
            const endLine = Math.min($endLine, maxLine);
            const startOffset = document.getLineStartOffset(startLine);
            const endOffset = document.getLineEndOffset(endLine);
            editor.getCaretModel().removeSecondaryCarets();
            editor.getCaretModel().moveToOffset(startOffset);
            editor.getSelectionModel().setSelection(startOffset, endOffset);
            editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
            """.trimIndent(),
            true,
        )
    }

    fun runRequestAtCaret() {
        selectRequestBlockAtCaret()
        invokeReqRunEditorAction("com.github.aidarkhusainov.reqrun.actions.RunHttpRequestsGroupAction")
    }

    fun invokeAction(name: String) {
        val reqRunActionId = reqRunEditorActionIdsByText[name]
        if (reqRunActionId != null) {
            invokeReqRunEditorAction(reqRunActionId)
            return
        }
        val actionQuery =
            when (name) {
                // Group action text is dynamic; stable search key makes lookup reliable.
                "Run Selected Requests" -> "Run HTTP Requests (Group)"
                else -> name
            }
        focusTextEditor()
        UiKeyboard.shortcut(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_SHIFT, java.awt.event.KeyEvent.VK_A)
        UiKeyboard.pasteText(actionQuery)
        UiKeyboard.pressEnter()
    }

    fun openSettings() {
        focusIde()
        UiKeyboard.shortcut(java.awt.event.KeyEvent.VK_CONTROL, java.awt.event.KeyEvent.VK_ALT, java.awt.event.KeyEvent.VK_S)
    }

    fun searchSettings(text: String) {
        val search =
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[contains(@class,'SearchTextField')]"),
                timeout,
            )
        search.click()
        UiKeyboard.pasteText(text)
        UiKeyboard.pressEnter()
    }

    fun setSettingsScope(scope: String) {
        val combo =
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[@text='Scope:']/following::div[contains(@class,'ComboBox')][1]"),
                timeout,
            )
        combo.click()
        actionMenuItem(scope).click()
    }

    fun setSettingsField(label: String, value: String) {
        val field =
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[@text='$label']/following::div[contains(@class,'TextFieldWithBrowseButton')][1]"),
                timeout,
            )
        field.click()
        UiKeyboard.selectAll()
        UiKeyboard.pasteText(value)
    }

    fun toggleSettingsCheckbox(label: String) {
        val box =
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[contains(@class,'JCheckBox') and @text='$label']"),
                timeout,
            )
        box.click()
    }

    fun confirmSettings() {
        val ok =
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[contains(@class,'JButton') and @text='OK']"),
                timeout,
            )
        ok.click()
    }

    fun clickToolbarAction(tooltip: String) {
        actionButtonByTooltip(tooltip).click()
    }

    fun clickToolbarActionByText(text: String) {
        actionButtonByText(text).click()
    }

    fun clickMenuItem(text: String) {
        actionMenuItem(text).click()
    }

    fun insertHttpTemplate(method: String) {
        focusTextEditor()
        val escapedMethod = method.replace("\\", "\\\\").replace("'", "\\'")
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
            const FileDocumentManager = Packages.com.intellij.openapi.fileEditor.FileDocumentManager;
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const AnActionEvent = Packages.com.intellij.openapi.actionSystem.AnActionEvent;
            const CommonDataKeys = Packages.com.intellij.openapi.actionSystem.CommonDataKeys;
            const SimpleDataContext = Packages.com.intellij.openapi.actionSystem.impl.SimpleDataContext;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (!editor) throw "No selected text editor";
            const file = FileDocumentManager.getInstance().getFile(editor.getDocument());
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const actionClass = loader.loadClass("com.github.aidarkhusainov.reqrun.actions.InsertHttpRequestTemplateAction");
            const ctor = actionClass.getConstructors()[0];
            const action = ctor.newInstance('$escapedMethod');
            let builder = SimpleDataContext.builder();
            builder = builder.add(CommonDataKeys.PROJECT, project);
            builder = builder.add(CommonDataKeys.EDITOR, editor);
            if (file) {
                builder = builder.add(CommonDataKeys.VIRTUAL_FILE, file);
            }
            const event = AnActionEvent.createFromAnAction(action, null, "RemoteRobot", builder.build());
            action.update(event);
            if (!event.getPresentation().isEnabledAndVisible()) {
                throw "Template action is disabled: $escapedMethod";
            }
            action.actionPerformed(event);
            """.trimIndent(),
            true,
        )
    }

    fun openEnvSelector() {
        envSelectorButton().click()
    }

    fun selectEnvironment(name: String) {
        setEnvironment(name)
    }

    fun selectEnvironmentFromSelector(name: String) {
        openEnvSelector()
        actionMenuItem(name).click()
        waitFor("Environment '$name' selected") { hasEnvironmentButtonText(name) }
    }

    fun setShortenHistoryUrls(enabled: Boolean) {
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const app = Packages.com.intellij.openapi.application.ApplicationManager.getApplication();
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const settingsClass = loader.loadClass("com.github.aidarkhusainov.reqrun.settings.ReqRunHistorySettings");
            const service = app.getService(settingsClass);
            if (!service) throw "ReqRunHistorySettings service not found";
            service.getState().setShortenHistoryUrls(${if (enabled) "true" else "false"});
            """.trimIndent(),
            true,
        )
    }

    fun isShortenHistoryUrlsEnabled(): Boolean =
        remoteRobot.runJs(
            """
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const app = Packages.com.intellij.openapi.application.ApplicationManager.getApplication();
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const settingsClass = loader.loadClass("com.github.aidarkhusainov.reqrun.settings.ReqRunHistorySettings");
            const service = app.getService(settingsClass);
            if (!service) throw "ReqRunHistorySettings service not found";
            service.getState().getShortenHistoryUrls();
            """.trimIndent(),
            true,
        ).toString().toBoolean()

    fun setProjectEnvPaths(
        sharedPath: String?,
        privatePath: String?,
        useProjectPaths: Boolean,
    ) {
        val sharedExpr = sharedPath?.replace("\\", "\\\\")?.replace("'", "\\'")?.let { "'$it'" } ?: "null"
        val privateExpr = privatePath?.replace("\\", "\\\\")?.replace("'", "\\'")?.let { "'$it'" } ?: "null"
        val useExpr = if (useProjectPaths) "true" else "false"
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const settingsClass = loader.loadClass("com.github.aidarkhusainov.reqrun.settings.ReqRunProjectEnvPathSettings");
            const service = project.getService(settingsClass);
            if (!service) throw "ReqRunProjectEnvPathSettings service not found";
            const state = service.getState();
            state.setUseProjectPaths($useExpr);
            state.setSharedPath($sharedExpr);
            state.setPrivatePath($privateExpr);
            """.trimIndent(),
            true,
        )
    }

    fun environmentNamesForCurrentFile(): List<String> {
        val raw =
            remoteRobot.runJs(
                """
                const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
                const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
                const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
                const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
                const project = ProjectManager.getInstance().getOpenProjects()[0];
                if (!project) throw "No open project";
                const file = FileEditorManager.getInstance(project).getSelectedFiles()[0] || null;
                const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
                if (!plugin) throw "ReqRun plugin descriptor not found";
                const loader = plugin.getPluginClassLoader();
                const envClass = loader.loadClass("com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService");
                const envService = project.getService(envClass);
                if (!envService) throw "ReqRunEnvironmentService not found";
                const names = envService.getEnvironmentNames(file);
                let out = "";
                for (let i = 0; i < names.size(); i++) {
                    if (i > 0) out += "\n";
                    out += names.get(i);
                }
                out;
                """.trimIndent(),
                true,
            ).toString()
        return raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun executionCount(): Int =
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const execClass = loader.loadClass("com.github.aidarkhusainov.reqrun.services.ReqRunExecutionService");
            const execService = project.getService(execClass);
            if (!execService) throw "ReqRunExecutionService not found";
            execService.list().size();
            """.trimIndent(),
            true,
        ).toString().toIntOrNull() ?: 0

    fun clearExecutionHistory() {
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const execClass = loader.loadClass("com.github.aidarkhusainov.reqrun.services.ReqRunExecutionService");
            const execService = project.getService(execClass);
            if (!execService) throw "ReqRunExecutionService not found";
            execService.clearAll();
            """.trimIndent(),
            true,
        )
    }

    fun latestExecutionRequestUrl(): String =
        latestExecutionValue(
            """
            const request = last.getRequest();
            request ? request.getUrl() : "";
            """.trimIndent(),
        )

    fun latestExecutionStatusLine(): String =
        latestExecutionValue(
            """
            const response = last.getResponse();
            response ? response.getStatusLine() : "";
            """.trimIndent(),
        )

    fun latestExecutionError(): String =
        latestExecutionValue(
            """
            const error = last.getError();
            error ? error : "";
            """.trimIndent(),
        )

    fun latestExecutionBody(): String =
        latestExecutionValue(
            """
            const response = last.getResponse();
            response ? response.getBody() : "";
            """.trimIndent(),
        )

    fun latestExecutionFormattedJsonBody(): String =
        latestExecutionValue(
            """
            const response = last.getResponse();
            const body = response ? response.getFormattedBody() : "";
            body ? body : "";
            """.trimIndent(),
        )

    fun latestExecutionFormattedXmlBody(): String =
        latestExecutionValue(
            """
            const response = last.getResponse();
            const body = response ? response.getFormattedXml() : "";
            body ? body : "";
            """.trimIndent(),
        )

    fun latestExecutionFormattedHtmlBody(): String =
        latestExecutionValue(
            """
            const response = last.getResponse();
            const body = response ? response.getFormattedHtml() : "";
            body ? body : "";
            """.trimIndent(),
        )

    fun setResponseViewSettings(
        showLineNumbers: Boolean,
        showRequestMethod: Boolean,
        foldHeadersByDefault: Boolean,
    ) {
        remoteRobot.runJs(
            """
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const app = Packages.com.intellij.openapi.application.ApplicationManager.getApplication();
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const settingsClass = loader.loadClass("com.github.aidarkhusainov.reqrun.settings.ReqRunResponseViewSettings");
            const service = app.getService(settingsClass);
            if (!service) throw "ReqRunResponseViewSettings service not found";
            const state = service.getState();
            state.setShowLineNumbers(${if (showLineNumbers) "true" else "false"});
            state.setShowRequestMethod(${if (showRequestMethod) "true" else "false"});
            state.setFoldHeadersByDefault(${if (foldHeadersByDefault) "true" else "false"});
            """.trimIndent(),
            true,
        )
    }

    fun responseViewSettingsSnapshot(): String =
        remoteRobot.runJs(
            """
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const app = Packages.com.intellij.openapi.application.ApplicationManager.getApplication();
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const settingsClass = loader.loadClass("com.github.aidarkhusainov.reqrun.settings.ReqRunResponseViewSettings");
            const service = app.getService(settingsClass);
            if (!service) throw "ReqRunResponseViewSettings service not found";
            const state = service.getState();
            state.getShowLineNumbers() + "|" + state.getShowRequestMethod() + "|" + state.getFoldHeadersByDefault();
            """.trimIndent(),
            true,
        ).toString()

    fun openSharedEnvFile() {
        openEnvFileFromService(isPrivate = false)
    }

    fun openPrivateEnvFile() {
        openEnvFileFromService(isPrivate = true)
    }

    fun openServicesToolWindow() {
        val byText = findOptional(byXpath("//div[contains(@class,'StripeButton') and @text='Services']"))
        if (byText != null) {
            byText.click()
            return
        }
        val byTooltip = findOptional(byXpath("//div[contains(@class,'StripeButton') and contains(@tooltip,'Services')]"))
        if (byTooltip != null) {
            byTooltip.click()
            return
        }
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const ToolWindowManager = Packages.com.intellij.openapi.wm.ToolWindowManager;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Services");
            if (!toolWindow) throw "Services tool window not found";
            toolWindow.activate(null, true, true);
            """.trimIndent(),
            true,
        )
    }

    fun clearHistoryIfPresent() {
        openServicesToolWindow()
        val clear =
            findOptional(
                byXpath("//div[@class='ActionButton' and @tooltip='Clear History']"),
            )
        clear?.click()
    }

    fun rerunLastIfPresent() {
        openServicesToolWindow()
        val rerun =
            findOptional(
                byXpath("//div[@class='ActionButton' and @tooltip='Rerun']"),
            )
        rerun?.click()
    }

    fun copyResponseBody() {
        actionButtonByTooltip("Copy Response Body").click()
    }

    fun focusResponseViewer() {
        openServicesToolWindow()
        val editors =
            try {
                remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='EditorComponentImpl']"))
            } catch (_: Throwable) {
                emptyList()
            }
        if (editors.isNotEmpty()) {
            editors.last().click()
        }
    }

    fun compareWithClipboardFromResponseViewer() {
        openServicesToolWindow()
        val editors =
            try {
                remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='EditorComponentImpl']"))
            } catch (_: Throwable) {
                emptyList()
            }
        if (editors.isNotEmpty()) {
            editors.last().rightClick()
            actionMenuItem("Compare with Clipboard").click()
        }
    }

    fun openResponseViewSettings() {
        actionButtonByTooltip("Response View Settings").click()
    }

    fun selectViewAs(mode: String) {
        openResponseViewSettings()
        actionMenuItem("View As").click()
        actionMenuItem(mode).click()
    }

    fun toggleSoftWraps() {
        actionButtonByTooltip("Toggle Soft Wraps").click()
    }

    fun toggleShowLineNumbers() {
        openResponseViewSettings()
        actionMenuItem("Show Line Numbers").click()
    }

    fun toggleFoldHeaders() {
        openResponseViewSettings()
        actionMenuItem("Fold Headers of Non-Empty Responses by Default").click()
    }

    fun toggleShowRequestMethod() {
        openResponseViewSettings()
        actionMenuItem("Show Request Method (Rerun to Apply)").click()
    }

    fun clickGutterRunIcon() {
        val icon =
            remoteRobot.find<ComponentFixture>(
                byXpath("//div[contains(@class,'Gutter') and contains(@tooltip,'Run')]"),
                timeout,
            )
        icon.click()
    }

    fun hasReqRunToolbar(): Boolean =
        exists(byXpath("//div[contains(@class,'ActionButton') and @tooltip='Run All Requests']")) ||
            exists(byXpath("//div[contains(@class,'ActionButton') and @tooltip='Add to HTTP Client...']")) ||
            exists(byXpath("//div[contains(@class,'ActionButton') and @text='No Environment']")) ||
            exists(byXpath("//div[contains(@class,'ActionButton') and @text='dev']")) ||
            exists(byXpath("//div[contains(@class,'ActionButton') and contains(@text,'Environment')]")) ||
            exists(byXpath("//div[@text='Examples']"))

    fun hasGutterRunIcon(): Boolean =
        exists(byXpath("//div[contains(@class,'Gutter') and contains(@tooltip,'Run')]"))

    fun hasTextInUi(text: String): Boolean =
        exists(
            byXpath(
                "//*[(@text='$text' or contains(@text,'$text') or @accessiblename='$text' or contains(@accessiblename,'$text'))]",
            ),
        ) || hasEnvironmentNamed(text)

    fun hasMenuItem(text: String): Boolean =
        findActionMenuItem(text) != null ||
            exists(
                byXpath(
                    "//*[(@text='$text' or contains(@text,'$text') or @accessiblename='$text' or contains(@accessiblename,'$text'))]",
                ),
            )

    fun hasActionButtonTooltip(tooltip: String): Boolean =
        exists(byXpath("//div[contains(@class,'ActionButton') and @tooltip='$tooltip']"))

    fun isMenuItemChecked(text: String): Boolean =
        exists(byXpath("//div[contains(@class,'ActionMenuItem') and @text='$text' and @selected='true']")) ||
            exists(byXpath("//div[contains(@class,'ActionMenuItem') and @text='$text' and @checked='true']"))

    fun waitForNotification(text: String) {
        waitFor("Notification containing '$text'") {
            exists(byXpath("//*[contains(@text,'$text')]"))
        }
    }

    fun waitForNoNotification(text: String, timeout: Duration = Duration.ofSeconds(4)) {
        waitFor("No notification containing '$text'", timeout = timeout) {
            !exists(byXpath("//*[contains(@text,'$text')]"))
        }
    }

    fun editorHasTab(title: String): Boolean =
        exists(
            byXpath(
                "//div[(contains(@class,'EditorTab') or contains(@class,'TabLabel') or contains(@class,'SingleHeightLabel')) and @text='$title']",
            ),
        ) || exists(byXpath("//div[@text='$title']")) || isFileOpened(title)

    fun editorContains(text: String): Boolean {
        val escaped = text.replace("\\", "\\\\").replace("'", "\\'")
        return try {
            remoteRobot.runJs(
                """
                const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
                const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
                const project = ProjectManager.getInstance().getOpenProjects()[0];
                let hasText = false;
                if (project) {
                    const editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                    if (editor) {
                        hasText = editor.getDocument().getText().contains('$escaped');
                    }
                }
                hasText;
                """.trimIndent(),
                true,
            ).toString().toBoolean()
        } catch (_: Throwable) {
            false
        }
    }

    fun findTreeNode(text: String): Boolean =
        exists(byXpath("//div[contains(@class,'SimpleColoredComponent') and @text='$text']"))

    fun focusIde() {
        if (isIdeFrameVisible()) {
            ideFrame().click()
        }
    }

    fun focusTextEditor() {
        focusIde()
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                const editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                if (editor) {
                    const component = editor.getContentComponent();
                    component.requestFocusInWindow();
                }
            }
            """.trimIndent(),
            true,
        )
    }

    private fun invokeReqRunEditorAction(actionId: String) {
        focusTextEditor()
        val escapedActionId = actionId.replace("\\", "\\\\").replace("'", "\\'")
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
            const FileDocumentManager = Packages.com.intellij.openapi.fileEditor.FileDocumentManager;
            const ActionManager = Packages.com.intellij.openapi.actionSystem.ActionManager;
            const AnActionEvent = Packages.com.intellij.openapi.actionSystem.AnActionEvent;
            const CommonDataKeys = Packages.com.intellij.openapi.actionSystem.CommonDataKeys;
            const SimpleDataContext = Packages.com.intellij.openapi.actionSystem.impl.SimpleDataContext;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (!editor) throw "No selected text editor";
            const file = FileDocumentManager.getInstance().getFile(editor.getDocument());
            const action = ActionManager.getInstance().getAction('$escapedActionId');
            if (!action) throw "Cannot find action: $escapedActionId";
            let builder = SimpleDataContext.builder();
            builder = builder.add(CommonDataKeys.PROJECT, project);
            builder = builder.add(CommonDataKeys.EDITOR, editor);
            if (file) {
                builder = builder.add(CommonDataKeys.VIRTUAL_FILE, file);
            }
            const event = AnActionEvent.createFromAnAction(action, null, "RemoteRobot", builder.build());
            action.update(event);
            if (!event.getPresentation().isEnabledAndVisible()) {
                throw "Action is disabled: $escapedActionId";
            }
            action.actionPerformed(event);
            """.trimIndent(),
            true,
        )
    }

    private fun selectRequestBlockAtCaret() {
        focusTextEditor()
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (!editor) throw "No selected text editor";
            const document = editor.getDocument();
            const lineCount = document.getLineCount();
            if (lineCount === 0) throw "Empty document";
            const caretLine = Math.min(Math.max(editor.getCaretModel().getLogicalPosition().line, 0), lineCount - 1);
            const isSeparator = (line) => {
                const start = document.getLineStartOffset(line);
                const end = document.getLineEndOffset(line);
                const text = document.getText(new Packages.com.intellij.openapi.util.TextRange(start, end));
                return text.trimStart().startsWith("###");
            };
            let startLine = caretLine;
            while (startLine > 0 && !isSeparator(startLine)) {
                startLine--;
            }
            if (isSeparator(startLine)) {
                startLine = Math.min(startLine + 1, lineCount - 1);
            }
            let endLine = caretLine;
            while (endLine + 1 < lineCount && !isSeparator(endLine + 1)) {
                endLine++;
            }
            const startOffset = document.getLineStartOffset(startLine);
            const endOffset = document.getLineEndOffset(endLine);
            editor.getCaretModel().removeSecondaryCarets();
            editor.getCaretModel().moveToOffset(startOffset);
            editor.getSelectionModel().setSelection(startOffset, endOffset);
            """.trimIndent(),
            true,
        )
    }

    private fun ideFrame(): ComponentFixture =
        remoteRobot.find(byXpath("//div[@class='IdeFrameImpl']"), timeout)

    private fun isIdeFrameVisible(): Boolean =
        exists(byXpath("//div[@class='IdeFrameImpl']"))

    private fun isWelcomeFrameVisible(): Boolean =
        exists(byXpath("//div[@class='FlatWelcomeFrame']"))

    private fun actionButtonByTooltip(tooltip: String): ComponentFixture =
        findActionButtonByTooltipOrText(tooltip)
            ?: remoteRobot.find(
                byXpath(
                    "//div[contains(@class,'ActionButton') and " +
                        "(contains(@tooltip,'$tooltip') or contains(@text,'$tooltip'))]",
                ),
                timeout,
            )

    private fun findActionButtonByTooltipOrText(label: String): ComponentFixture? {
        val variants = linkedSetOf(label, label.replace("...", "…"), label.replace("…", "..."))
        for (variant in variants) {
            val byTooltip = findOptional(byXpath("//div[contains(@class,'ActionButton') and @tooltip='$variant']"))
            if (byTooltip != null) return byTooltip
            val byText = findOptional(byXpath("//div[contains(@class,'ActionButton') and @text='$variant']"))
            if (byText != null) return byText
        }
        return null
    }

    private fun actionButtonByText(text: String): ComponentFixture =
        remoteRobot.find(byXpath("//div[contains(@class,'ActionButton') and @text='$text']"), timeout)

    private fun envSelectorButton(): ComponentFixture {
        val candidates = listOf("No Environment", "dev", "prod", "stage", "stage2", "stage3")
        for (candidate in candidates) {
            val locator =
                byXpath(
                    "//div[(contains(@class,'ActionButton') or contains(@class,'ComboBox')) and @text='$candidate']",
                )
            val found = findOptional(locator)
            if (found != null) return found
        }
        return remoteRobot.find(
            byXpath(
                "//div[(contains(@class,'ActionButton') or contains(@class,'ComboBox')) and (contains(@text,'Env') or contains(@text,'Environment'))]",
            ),
            timeout,
        )
    }

    private fun hasEnvironmentButtonText(name: String): Boolean =
        exists(
            byXpath(
                "//div[(contains(@class,'ActionButton') or contains(@class,'ComboBox')) and @text='$name']",
            ),
        )

    private fun actionMenuItem(text: String): ComponentFixture =
        findActionMenuItem(text)
            ?: remoteRobot.find(
                byXpath(
                    "//*[(@text='$text' or contains(@text,'$text') or @accessiblename='$text' or contains(@accessiblename,'$text'))]",
                ),
                timeout,
            )

    private fun findActionMenuItem(text: String): ComponentFixture? =
        findOptional(
            byXpath(
                "//div[contains(@class,'ActionMenuItem') and " +
                    "(@text='$text' or contains(@text,'$text') or @accessiblename='$text' or contains(@accessiblename,'$text'))]",
            ),
        ) ?: findOptional(
            byXpath(
                "//div[contains(@class,'JBMenuItem') and " +
                    "(@text='$text' or contains(@text,'$text') or @accessiblename='$text' or contains(@accessiblename,'$text'))]",
            ),
        ) ?: findOptional(byXpath("//div[@text='$text']"))

    private fun latestExecutionValue(valueScript: String): String =
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const execClass = loader.loadClass("com.github.aidarkhusainov.reqrun.services.ReqRunExecutionService");
            const execService = project.getService(execClass);
            if (!execService) throw "ReqRunExecutionService not found";
            const list = execService.list();
            if (list.isEmpty()) {
                "";
            } else {
                const last = list.get(list.size() - 1);
                $valueScript
            }
            """.trimIndent(),
            true,
        ).toString()

    private fun hasEnvironmentNamed(name: String): Boolean {
        val envFiles =
            listOf(
                UiTestProject.sharedEnvFile,
                UiTestProject.privateEnvFile,
                UiTestProject.altSharedEnvFile,
                UiTestProject.altPrivateEnvFile,
            )
        val marker = "\"$name\""
        return envFiles.any { path ->
            if (!Files.exists(path)) return@any false
            runCatching { Files.readString(path, StandardCharsets.UTF_8) }
                .getOrNull()
                ?.contains("$marker:")
                ?: false
        }
    }

    private fun openEnvFileFromService(isPrivate: Boolean) {
        remoteRobot.runJs(
            """
            const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
            const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
            const FileDocumentManager = Packages.com.intellij.openapi.fileEditor.FileDocumentManager;
            const PluginManagerCore = Packages.com.intellij.ide.plugins.PluginManagerCore;
            const PluginId = Packages.com.intellij.openapi.extensions.PluginId;
            const project = ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) throw "No open project";
            const file = FileEditorManager.getInstance(project).getSelectedFiles()[0] || null;
            const plugin = PluginManagerCore.getPlugin(PluginId.getId("com.github.aidarkhusainov.reqrun"));
            if (!plugin) throw "ReqRun plugin descriptor not found";
            const loader = plugin.getPluginClassLoader();
            const envClass = loader.loadClass("com.github.aidarkhusainov.reqrun.services.ReqRunEnvironmentService");
            const envService = project.getService(envClass);
            const path = envService.ensureEnvFile(file, ${if (isPrivate) "true" else "false"});
            if (!path) throw "Cannot resolve env file";
            const nioPath = path.toAbsolutePath().toString();
            const vFile = Packages.com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath(nioPath);
            if (!vFile) throw "Cannot find env file: " + nioPath;
            FileDocumentManager.getInstance().reloadFiles(vFile);
            FileEditorManager.getInstance(project).openFile(vFile, true);
            """.trimIndent(),
            true,
        )
    }

    private fun isFileOpened(fileName: String): Boolean {
        val escapedFileName = fileName.replace("\\", "\\\\").replace("'", "\\'")
        return try {
            remoteRobot.runJs(
                """
                const ProjectManager = Packages.com.intellij.openapi.project.ProjectManager;
                const FileEditorManager = Packages.com.intellij.openapi.fileEditor.FileEditorManager;
                const project = ProjectManager.getInstance().getOpenProjects()[0];
                let opened = false;
                if (project) {
                    const files = FileEditorManager.getInstance(project).getOpenFiles();
                    for (let i = 0; i < files.length; i++) {
                        const file = files[i];
                        if (file && file.getName && file.getName() === '$escapedFileName') {
                            opened = true;
                            break;
                        }
                    }
                }
                opened;
                """.trimIndent(),
                true,
            ).toString().toBoolean()
        } catch (_: Throwable) {
            false
        }
    }

    private fun exists(locator: com.intellij.remoterobot.search.locators.Locator): Boolean =
        try {
            remoteRobot.find<ComponentFixture>(locator, Duration.ofSeconds(2))
            true
        } catch (_: Throwable) {
            false
        }

    private fun findOptional(locator: com.intellij.remoterobot.search.locators.Locator): ComponentFixture? =
        try {
            remoteRobot.find(locator, Duration.ofSeconds(2))
        } catch (_: Throwable) {
            null
        }
}
