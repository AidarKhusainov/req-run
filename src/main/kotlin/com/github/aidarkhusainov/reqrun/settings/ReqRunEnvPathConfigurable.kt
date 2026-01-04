package com.github.aidarkhusainov.reqrun.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class ReqRunEnvPathConfigurable(
    private val project: Project,
) : Configurable {
    private val scopeBox = ComboBox(arrayOf("Global", "Project"))
    private val sharedField = TextFieldWithBrowseButton()
    private val privateField = TextFieldWithBrowseButton()
    private val shortenHistoryUrls = JCheckBox("Shorten history URLs (hide host)")
    private var component: JComponent? = null

    override fun getDisplayName(): String = "ReqRun"

    override fun createComponent(): JComponent {
        if (component == null) {
            val sharedDescriptor =
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().apply {
                    title = "Select shared env file"
                }
            val privateDescriptor =
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().apply {
                    title = "Select private env file"
                }
            sharedField.addBrowseFolderListener(TextBrowseFolderListener(sharedDescriptor, project))
            privateField.addBrowseFolderListener(TextBrowseFolderListener(privateDescriptor, project))
            scopeBox.addActionListener { loadFields() }
            val form =
                FormBuilder
                    .createFormBuilder()
                    .addLabeledComponent("Scope:", scopeBox)
                    .addLabeledComponent("Shared env file:", sharedField)
                    .addLabeledComponent("Private env file:", privateField)
                    .addComponent(shortenHistoryUrls)
                    .panel
            component =
                JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.empty(10, 12)
                    add(form, BorderLayout.NORTH)
                }
        }
        return component!!
    }

    override fun isModified(): Boolean {
        val projectSettings = project.getService(ReqRunProjectEnvPathSettings::class.java)
        val globalSettings = ApplicationManager.getApplication().getService(ReqRunEnvPathSettings::class.java)
        val historySettings = ApplicationManager.getApplication().getService(ReqRunHistorySettings::class.java)

        val projectScopeSelected = scopeBox.selectedItem == "Project"
        if (projectScopeSelected != projectSettings.state.useProjectPaths) return true

        val (shared, privatePath) = currentStoredValues(projectScopeSelected, projectSettings, globalSettings)
        return sharedField.text.orEmpty() != shared.orEmpty() ||
            privateField.text.orEmpty() != privatePath.orEmpty() ||
            shortenHistoryUrls.isSelected != historySettings.state.shortenHistoryUrls
    }

    override fun apply() {
        val projectSettings = project.getService(ReqRunProjectEnvPathSettings::class.java)
        val globalSettings = ApplicationManager.getApplication().getService(ReqRunEnvPathSettings::class.java)
        val historySettings = ApplicationManager.getApplication().getService(ReqRunHistorySettings::class.java)
        val projectScopeSelected = scopeBox.selectedItem == "Project"

        projectSettings.state.useProjectPaths = projectScopeSelected

        if (projectScopeSelected) {
            projectSettings.state.sharedPath = sharedField.text.takeIf { it.isNotBlank() }
            projectSettings.state.privatePath = privateField.text.takeIf { it.isNotBlank() }
        } else {
            globalSettings.state.sharedPath = sharedField.text.takeIf { it.isNotBlank() }
            globalSettings.state.privatePath = privateField.text.takeIf { it.isNotBlank() }
        }
        historySettings.state.shortenHistoryUrls = shortenHistoryUrls.isSelected
    }

    override fun reset() {
        val projectSettings = project.getService(ReqRunProjectEnvPathSettings::class.java)
        val historySettings = ApplicationManager.getApplication().getService(ReqRunHistorySettings::class.java)
        scopeBox.selectedItem = if (projectSettings.state.useProjectPaths) "Project" else "Global"
        shortenHistoryUrls.isSelected = historySettings.state.shortenHistoryUrls
        loadFields()
    }

    private fun loadFields() {
        val projectSettings = project.getService(ReqRunProjectEnvPathSettings::class.java)
        val globalSettings = ApplicationManager.getApplication().getService(ReqRunEnvPathSettings::class.java)
        val projectScopeSelected = scopeBox.selectedItem == "Project"
        val (shared, privatePath) = currentStoredValues(projectScopeSelected, projectSettings, globalSettings)
        sharedField.text = shared.orEmpty()
        privateField.text = privatePath.orEmpty()
    }

    private fun currentStoredValues(
        projectScopeSelected: Boolean,
        projectSettings: ReqRunProjectEnvPathSettings,
        globalSettings: ReqRunEnvPathSettings,
    ): Pair<String?, String?> =
        if (projectScopeSelected) {
            projectSettings.state.sharedPath to projectSettings.state.privatePath
        } else {
            globalSettings.state.sharedPath to globalSettings.state.privatePath
        }
}
