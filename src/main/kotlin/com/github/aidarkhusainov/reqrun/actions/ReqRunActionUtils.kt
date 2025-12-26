package com.github.aidarkhusainov.reqrun.actions

import com.github.aidarkhusainov.reqrun.lang.ReqRunFileType
import com.intellij.openapi.vfs.VirtualFile

internal fun VirtualFile?.isReqRunHttpFile(): Boolean {
    val file = this ?: return false
    return file.extension.equals("http", ignoreCase = true) || file.fileType is ReqRunFileType
}
