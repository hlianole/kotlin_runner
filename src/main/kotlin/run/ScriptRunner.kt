package com.hlianole.guikotlin.run

import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import kotlinx.coroutines.*
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.StyleClassedTextArea
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class ScriptRunner {
    private var cache = mutableMapOf<String, String>()
    private var lock = Any()
    private var currentProcess : Process? = null
    private val errorClickHandlers = mutableListOf<Pair<StyleClassedTextArea, (MouseEvent) -> Unit>>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    private var waitJob: Job? = null
    private val waitingToDestroy = AtomicBoolean(false)
    private val readingOutput = AtomicBoolean(false)
    private var cv = CompletableDeferred<Unit>()

    fun run(
        scriptArea: CodeArea,
        outputArea: StyleClassedTextArea,
        statusLabel: Label,
        isUsingCache: Boolean,
        updateUI: (runnable: () -> Unit) -> Unit
    ) {
        if (currentProcess != null) {
            return
        }

        clearErrorHandlers()
        updateUI {
            outputArea.clearStyle(0, outputArea.length)
        }

        checkKotlinCompiler(
            outputArea,
            statusLabel,
            updateUI,
        )

        val script = scriptArea.text
        if (script.isBlank()) {
            return
        }

        if (currentProcess?.isAlive == true) {
            currentProcess?.destroyForcibly()
            currentProcess?.waitFor()
            currentProcess = null
        }

        val scriptHash = script.hashCode().toString(16)
        if (isUsingCache) {
            synchronized(lock) {
                cache[scriptHash]?.let { cached ->
                    if (cached.isNotEmpty()) {
                        updateUI {
                            statusLabel.text = "From cache"
                            outputArea.clear()
                            appendOutput(outputArea, scriptArea, cached)
                        }
                        return
                    }
                }
            }
        }

        coroutineScope.launch {
            try {
                cv = CompletableDeferred()

                val tempDir = System.getProperty("java.io.tmpdir")
                val scriptFile = File("$tempDir/kotlin_script.kts")
                scriptFile.writeText(script)

                updateUI {
                    statusLabel.text = "Running..."
                    outputArea.clear()
                }

                val startTime = System.currentTimeMillis()
                var timeThreadWaiting = 0L

                val processBuilder = ProcessBuilder(
                    "kotlinc",
                    "-script",
                    scriptFile.absolutePath
                )
                processBuilder.redirectErrorStream(true)

                currentProcess = processBuilder.start()
                println("Process started with PID: ${currentProcess?.pid()}")

                val output = StringBuilder()
                val reader = currentProcess?.inputStream?.bufferedReader()
                var line: String?

                readJob = coroutineScope.launch {
                    readingOutput.set(true)
                    while (reader?.readLine().also { line = it } != null && !waitingToDestroy.get() && isActive) {
                        output.append(line).append('\n')
                        val lineNotNull = line.orEmpty()
                        updateUI {
                            appendOutput(outputArea, scriptArea, lineNotNull)
                        }
                        Thread.sleep(1)
                        timeThreadWaiting++
                    }
                    readingOutput.set(false)
                    notifyCondition()
                    readJob?.cancelAndJoin()
                }

                waitJob = coroutineScope.launch {
                    try {
                        currentProcess?.waitFor()
                        val exitCode = currentProcess?.exitValue()

                        if (readingOutput.get()) {
                            println("process is waiting for output reading")
                            waitForCondition()
                        }
                        val endTime = System.currentTimeMillis()

                        currentProcess = null
                        val executionTime = endTime - startTime - timeThreadWaiting
                        updateUI {
                            if (exitCode != 137) {
                                statusLabel.text = "Exit code: $exitCode (${millisecondsToSeconds(executionTime)})"
                            } else {
                                statusLabel.text = "Killed"
                            }
                        }
                        if (exitCode == 0) {
                            synchronized(lock) {
                                cache[scriptHash] = output.toString()
                            }
                        }

                        println("finishing all processes")

                        currentProcess = null
                        readJob = null
                        waitJob = null
                    } catch (e: CancellationException) {
                        println("Execution cancelled")
                        updateUI {
                            statusLabel.text = "Killed"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                updateUI {
                    statusLabel.text = "Error"
                    outputArea.appendText(e.message)
                }
                currentProcess = null
                readJob?.cancel()
                waitJob?.cancel()
                readJob = null
                waitJob = null
            }
        }
    }

    fun stop(
        statusLabel: Label,
        updateUI: (runnable: () -> Unit) -> Unit
    ) {
        coroutineScope.launch {
            try {
                currentProcess?.let { process ->
                    readJob?.cancel()
                    waitJob?.cancel()
                    if (process.isAlive) {
                        waitingToDestroy.set(true)
                        process.destroyForcibly()

                        try {
                            process.waitFor()
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }

                        println("Process interrupted")

                        updateUI {
                            statusLabel.text = "Killed"
                        }
                    }
                    if (readingOutput.get()) {
                        readingOutput.set(false)
                        notifyCondition()
                    }
                }
                currentProcess = null
                readJob = null
                waitJob = null
            } catch (e: Exception) {
                e.printStackTrace()
                updateUI {
                    statusLabel.text = "Error killing a process"
                }
            } finally {
                waitingToDestroy.set(false)
            }
        }
    }

    fun preload() {
        CompletableFuture.runAsync {
            try {
                ProcessBuilder("kotlinc", "-version").start().waitFor()
                println("Kotlin preloaded")
            } catch (e: Exception) {
                println("Error preloading Kotlin: ${e.message}")
            }
        }
    }

    private suspend fun waitForCondition() {
        cv.await()
    }

    private fun notifyCondition() {
        runBlocking {
            if (!cv.isCompleted) {
                cv.complete(Unit)
                println("Condition signaled")
            }
        }
    }

    private fun checkKotlinCompiler(
        outputArea: StyleClassedTextArea,
        statusLabel: Label,
        updateUI: (runnable: () -> Unit) -> Unit
    ) {
        if (!isKotlinCompilerInstalled()) {
            updateUI {
                outputArea.appendText("Error: Kotlin compiler not installed.\n" +
                        "Please, install Kotlin Compiler and add it to PATH.")
                statusLabel.text = "Error"
            }
            return
        }
    }

    private fun isKotlinCompilerInstalled(): Boolean {
        return try {
            val command = if (System.getProperty("os.name").lowercase().contains("win")) {
                "where kotlinc"
            } else {
                "which kotlinc"
            }
            val process = ProcessBuilder(command.split(" "))
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun millisecondsToSeconds (milliseconds: Long): String {
        val millis = milliseconds % 1000
        val seconds = milliseconds / 1000
        return "$seconds s, $millis m"
    }

    private fun clearErrorHandlers() {
        errorClickHandlers.forEach { (area, handler) ->
            area.removeEventHandler(MouseEvent.MOUSE_CLICKED, handler)
        }
        errorClickHandlers.clear()
    }

    private fun appendOutput(
        outputArea: StyleClassedTextArea,
        scriptArea: CodeArea,
        text: String
    ) {
        val regex = Regex("""^.*:(\d+):(\d+):.*""")
        val lines = text.split("\n")

        lines.forEach { currentLine ->
            val currentLineStrOffset = outputArea.length
            outputArea.appendText(currentLine + "\n")

            val match = regex.find(currentLine)
            if (match != null) {
                val (lineStr, colStr) = match.destructured
                val line = lineStr.toIntOrNull()?.minus(1) ?: return@forEach
                val column = colStr.toIntOrNull()?.minus(1) ?: return@forEach

                val relativeStart = match.range.first
                val relativeEnd = match.range.last + 1
                val absoluteStart = currentLineStrOffset + relativeStart
                val absoluteEnd = currentLineStrOffset + relativeEnd

                outputArea.setStyleClass(absoluteStart, absoluteEnd, "error-link")

                val handler = { _: MouseEvent ->
                    val caretPos = outputArea.caretPosition
                    if (caretPos in absoluteStart..absoluteEnd) {
                        scriptArea.moveTo(line, column)
                        scriptArea.requestFocus()
                    }
                }

                errorClickHandlers.add(Pair(outputArea, handler))

                outputArea.addEventHandler(MouseEvent.MOUSE_CLICKED, handler)
            }
        }
    }

    fun shutdown () {
        readJob?.cancel()
        waitJob?.cancel()
        coroutineScope.cancel()
    }
}