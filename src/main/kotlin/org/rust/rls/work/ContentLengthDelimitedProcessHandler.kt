package org.rust.rls.work

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.util.io.BaseDataReader
import java.io.DataInputStream
import java.util.concurrent.Future


/**
 * Process handler capable of framing message of the LSP format:
 *
 * ```
 * Content Length: length-in-bytes\r\n
 * \r\n
 * {some-json}
 * ```
 */
class ContentLengthDelimitedProcessHandler(
    cmd: GeneralCommandLine,
    val callback: (String) -> Unit
) : OSProcessHandler(cmd) {

    override fun createOutputDataReader(policy: BaseDataReader.SleepingPolicy) = object : BaseDataReader(policy) {
        private val stream = DataInputStream(myProcess.inputStream)

        init {
            check(mySleepingPolicy == BaseDataReader.SleepingPolicy.BLOCKING)
            start()
        }

        override fun close() {
            stream.close()
        }

        override fun executeOnPooledThread(runnable: Runnable): Future<*> =
            this@ContentLengthDelimitedProcessHandler.executeOnPooledThread(runnable)

        override fun readAvailable(): Boolean {
            var read = false
            frame@ while (true) {
                val contentLength = StringBuilder()
                header@ while (true) {
                    val b = stream.read();
                    if (b == -1) break@frame
                    read = true
                    val c = b.toChar()
                    if (c == '\r') {
                        break@header
                    }
                    contentLength.append(c)
                }
                val len = contentLength.toString().split(" ").last().toInt()
                stream.skipBytes(3) // \n\r\n

                val buffer = ByteArray(len)
                stream.readFully(buffer)
                callback(String(buffer))
            }
            return read
        }

    }

}


