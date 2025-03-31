package com.hlianole.guikotlin.run

import javafx.scene.control.Label
import javafx.scene.control.TextArea
import org.fxmisc.richtext.CodeArea
import java.io.File
import java.util.concurrent.CompletableFuture

class ScriptRunner {
    private var cache = mutableMapOf<String, String>()
    private var lock = Any()
    private var currentProcess : Process? = null

    fun run(
        scriptArea: CodeArea,
        outputArea: TextArea,
        statusLabel: Label,
        isUsingCache: Boolean,
        updateUI: (runnable: () -> Unit) -> Unit
    ) {
        if (currentProcess != null) {
            return
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
                            outputArea.text = cached
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
                    updateUI {
                        outputArea.text = output.toString()
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
                    outputArea.text = e.toString()
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
        outputArea: TextArea,
        statusLabel: Label,
        updateUI: (runnable: () -> Unit) -> Unit
    ) {
        if (!isKotlinCompilerInstalled()) {
            updateUI {
                outputArea.text = "Error: Kotlin compiler not installed.\n" +
                        "Please, install Kotlin Compiler and add it to PATH."
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
}