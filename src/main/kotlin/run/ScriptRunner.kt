package com.hlianole.guikotlin.run

import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.StyleClassedTextArea
import java.io.File
import java.util.concurrent.CompletableFuture

class ScriptRunner {
    private var cache = mutableMapOf<String, String>()
    private var lock = Any()
    private var currentProcess : Process? = null
    private val errorClickHandlers = mutableListOf<Pair<StyleClassedTextArea, (MouseEvent) -> Unit>>()

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

        CompletableFuture.supplyAsync {
            try {
                val tempDir = System.getProperty("java.io.tmpdir")
                val scriptFile = File("$tempDir/kotlin_script.kts")
                scriptFile.writeText(script)

                updateUI {
                    statusLabel.text = "Running..."
                    outputArea.clear()
                }

                val startTime = System.currentTimeMillis()

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


                while (reader?.readLine().also { line = it } != null) {
                    output.append(line).append('\n')
                    val lineNotNull = line.orEmpty()
                    updateUI {
                        appendOutput(outputArea, scriptArea, lineNotNull)
                    }
                }

                currentProcess?.waitFor()
                val endTime = System.currentTimeMillis()
                val exitCode = currentProcess?.exitValue()
                currentProcess = null
                val executionTime = endTime - startTime
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
            } catch (e: Exception) {
                updateUI {
                    statusLabel.text = "Error"
                    outputArea.appendText(e.message)
                }
            }
        }
    }

    fun stop(
        statusLabel: Label,
        updateUI: (runnable: () -> Unit) -> Unit
    ) {
        currentProcess?.let { process ->
            if (process.isAlive) {
                process.destroyForcibly()

                try {
                    process.waitFor()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }

                updateUI {
                    statusLabel.text = "Killed"
                }
            }
        }
        currentProcess = null
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
}