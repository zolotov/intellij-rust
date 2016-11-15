package org.rust.rls

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key

class RlsClient(
    var process: OSProcessHandler
) : Disposable {
    companion object {

        fun spawn(commandLine: String, projectRoot: String): RlsClient {
            val cmd = GeneralCommandLine(commandLine.split(" "))
                .withEnvironment("RUST_LOG", "rls=trace")

            val process = OSProcessHandler(cmd)
            process.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>?) {
                    println("> ${event.text}")
                }
            })

            val result = RlsClient(process)
            result.call(1, "initialize", """{"processId": 92, "rootPath": "$projectRoot"}""")
            result.process.startNotify()
            return result
        }
    }

    fun call(id: Int, method: String, params_json: String) {
        write("""{ "jsonrpc": "2.0", "id": $id, "method": "$method", "params": $params_json } """)
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

    override fun dispose() {
//        process.destroyProcess()
    }

    private fun log(s: String) = println(s)
}

fun main(args: Array<String>) {
    val rlsPath = "cargo run --manifest-path /home/user/projects/rls/Cargo.toml"
    val projectPath = "/home/user/hello"
    val rls = RlsClient.spawn(rlsPath, projectPath)
    try {
        work(rls)
        rls.process.waitFor()
    } finally {
        rls.dispose()
    }
    println("Done")
}

fun work(rls: RlsClient) {


}
