package org.rust.rls

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

/**
 * Once instance per module.
 * Any method can be called from any thread.
 */
interface RustLanguageServer {
    fun errorsForFile(file: VirtualFile): List<ErrorInfo>
    fun updateChangedFiles() {

    }
}

data class LineCol(val line: Int, val col: Int) {
    fun toOffset(document: Document): Int =
        document.getLineStartOffset(line) + col
}

data class ErrorInfo(
    val message: String,
    val start: LineCol,
    val end: LineCol
) {
    fun textRange(document: Document): TextRange = TextRange(
        start.toOffset(document),
        end.toOffset(document)
    )
}
