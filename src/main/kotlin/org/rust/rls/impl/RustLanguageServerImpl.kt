package org.rust.rls.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ConcurrencyUtil
import org.rust.cargo.util.cargoProjectRoot
import org.rust.rls.ErrorInfo
import org.rust.rls.LineCol
import org.rust.rls.RustLanguageServer
import org.rust.rls.work.LspPosition
import org.rust.rls.work.LspPublishDiagnosticsParams
import org.rust.rls.work.RlsProtocol
import org.rust.rls.work.RlsProtocolListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class RlsConnection(
    projectRoot: String,
    rlsExecutablePath: String,
    val queue: (() -> Unit) -> Unit
) {
    private val process: OSProcessHandler

    private val protocol: RlsProtocol

    init {
        val cmd = GeneralCommandLine(rlsExecutablePath.split(" "))
            .withWorkDirectory(projectRoot)
            .withEnvironment("RUST_BACKTRACE", "true")
            .withEnvironment("RUST_LOG", "rls=trace")

        protocol = RlsProtocol(cmd, ProtocolListener())
        process = protocol.process
        protocol.callInitialize(projectRoot)
        process.startNotify()
    }

    /**
     * There is a single writer for [errorMap], but we need some synchronization anyway because of [errorsForFile].
     */
    private val errorMap: MutableMap<String, List<ErrorInfo>> = ConcurrentHashMap()

    private inner class ProtocolListener : RlsProtocolListener {
        override fun onPublishDiagnostics(params: LspPublishDiagnosticsParams) = queue {
            if (params.diagnostics.isEmpty()) {
                errorMap.remove(params.uri)
            } else {
                errorMap[params.uri.drop("file://".length)] = params.diagnostics.map {
                    ErrorInfo(
                        it.message,
                        it.range.start.asLineCol,
                        it.range.end.asLineCol
                    )
                }
            }
        }
    }

    fun close() {
        process.destroyProcess()
    }

    /**
     * Can be called from any thread
     */
    fun errorsForFile(path: String): List<ErrorInfo> {
        println(path)
        println(errorMap)
        println()
        return errorMap[path].orEmpty()
    }

    fun fileChanged(path: String, text: String) {
        println("FileChanged $path")
        protocol.notifyTextDocumentDidChange(path, 0, text)
    }
}

private val LspPosition.asLineCol: LineCol
    get() = LineCol(line, character)

class RustLanguageServerImpl(
    private val module: Module
) : RustLanguageServer, Disposable {

    override fun updateChangedFiles() {
        val fileDocumentManager = FileDocumentManager.getInstance()
        val unsavedFiles = fileDocumentManager.unsavedDocuments.mapNotNull { doc ->
            fileDocumentManager.getFile(doc)?.let { file ->
                file.path to doc.text
            }
        }
        executorService.submit {
            val connection = connection ?: return@submit

            for ((path, text) in unsavedFiles) {
                connection.fileChanged(path, text)
            }
        }
    }

    override fun dispose() {
        executorService.submit {
            stopServerProcess()
        }
    }

    private var connection: RlsConnection? = null
    private val executorService: ExecutorService = ConcurrencyUtil.newSingleThreadExecutor("Rust Language Server")

    override fun errorsForFile(file: VirtualFile): List<ErrorInfo> {
        return errorsForFile(file.path)
    }

    fun errorsForFile(file: String): List<ErrorInfo> {
        if (connection == null) {
            restart()
        }
        return connection?.errorsForFile(file).orEmpty()
    }

    private fun restart() {
        println("restarting")
        executorService.submit {
            println("exec restart")
            stopServerProcess()
            startServerProcess()
        }
    }

    private fun stopServerProcess() {
        connection?.close()
        connection == null
    }

    private fun startServerProcess() {
        check(connection == null)
        val root = module.cargoProjectRoot?.path ?: return
        connection = RlsConnection(root, "cargo run --manifest-path /home/user/projects/rls/Cargo.toml", { task ->
            executorService.submit(task)
        })
    }
}

//fun main(args: Array<String>) {
//    val rls = RustLanguageServerImpl(null)
//    rls.errorsForFile("/home/user/hello/src/lib.rs")
//    Thread.sleep(100000)
//}
