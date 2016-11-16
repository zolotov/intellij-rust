package org.rust.rls

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.util.ConcurrencyUtil
import java.util.*
import java.util.concurrent.ExecutorService

private fun log(data: String) {
    println(data)
}


data class LspPublishDiagnosticsParams(
    val uri: String,
    val diagnostics: List<LspDiagnostic>
)

data class LspDiagnostic(
    val range: LspRange,
    val severity: Int,
    val code: String,
    val message: String
)

data class LspRange(
    val start: LspPosition,
    val end: LspPosition
)

data class LspPosition(
    val line: Int,
    val character: Int
)

private val GSON = Gson()

/**
 * Handles communication with RLS. All methods should be called from a single thread.
 */
class RlsProtocol(
    private val process: OSProcessHandler
) {

    private val errorMap: MutableMap<String, List<LspDiagnostic>> = HashMap()

    init {
        process.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>?) {
                if (outputType != ProcessOutputTypes.STDOUT) return
                val json = extractJson(event.text) ?: return
                log("Recv $json")

                val method = json["method"]?.asString
                if (method != null) {
                    val params = json["params"]!!
                    dispatch(method, params)
                }
            }
        })
    }

    private inline fun <reified T : Any> handle(params: JsonElement, f: (T) -> Unit) {
        val arg: T = GSON.fromJson(params, T::class.java)
        f(arg)
    }

    private fun dispatch(method: String, params: JsonElement) {

        when (method) {
            "textDocument/publishDiagnostics" -> handle<LspPublishDiagnosticsParams>(params, { handlePublishDiagnostics(it) })
        }
    }

    private fun handlePublishDiagnostics(params: LspPublishDiagnosticsParams) {
        if (params.diagnostics.isEmpty()) {
            errorMap.remove(params.uri)
        } else {
            errorMap[params.uri] = params.diagnostics
        }
    }

    fun call(id: Int, method: String, params_json: String) {
        write("""{ "jsonrpc": "2.0", "id": $id, "method": "$method", "params": $params_json } """)
    }

    private fun extractJson(line: String): JsonObject? {
        if (!line.startsWith('{')) return null
        return JsonParser().parse(line).asJsonObject
    }

    private fun write(data: String) {
        val body = (data).toByteArray(Charsets.UTF_8)
        val header = "Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.UTF_8)
        process.processInput?.apply {
            write(header)
            write(body)
            flush()
            log("Send $data")
        }
    }
}

/**
 * Manages lifetime and synchronization around RLS external process
 */
class RlsClient(
    private val protocol: RlsProtocol,
    private val process: OSProcessHandler
) : Disposable {

    private val executorService: ExecutorService = ConcurrencyUtil.newSingleThreadExecutor("Rust Language Server")

    companion object {
        fun spawn(commandLine: String, projectRoot: String): RlsClient {
            val cmd = GeneralCommandLine(commandLine.split(" "))
                .withEnvironment("RUST_LOG", "rls=trace")

            val process = OSProcessHandler(cmd)
            val protocol = RlsProtocol(process)
            protocol.call(1, "initialize", """{"processId": 92, "rootPath": "$projectRoot"}""")
            process.startNotify()
            return RlsClient(protocol, process)
        }
    }

    fun frob() {
        executorService.submit {
            protocol.call(2, "initialize", """{"processId": 92, "rootPath": "oobar"}""")
        }
    }

    override fun dispose() {
        process.destroyProcess()
    }

}

fun main(args: Array<String>) {
    val rlsPath = "cargo run --manifest-path /home/user/projects/rls/Cargo.toml"
    val projectPath = "/home/user/hello"
    println("A")
    val rls = RlsClient.spawn(rlsPath, projectPath)
    println("B")
    try {
        work(rls)
        Thread.sleep(5000)
    } finally {
        rls.dispose()
    }
    println("Done")
}

fun work(rls: RlsClient) {
}
