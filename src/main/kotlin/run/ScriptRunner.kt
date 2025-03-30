package com.hlianole.guikotlin.run

import javafx.scene.control.Label
import javafx.scene.control.TextArea
import org.fxmisc.richtext.CodeArea
import java.io.File
import java.util.concurrent.CompletableFuture

object ScriptRunner {
    private var cache = mutableMapOf<String, String>()
    private var lock = Any()

    fun run(
        scriptArea: CodeArea,
        outputArea: TextArea,
        statusLabel: Label,
        isUsingCache: Boolean,
        updateUI: (runnable: () -> Unit) -> Unit
    ) {
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
                    statusLabel.text = "Compiling..."
                    outputArea.clear()
                }

                val startTime = System.currentTimeMillis()

                val processBuilder = ProcessBuilder(
                    "kotlinc",
                    "-J-Xms256m",
                    "-J-Xmx512m",
                    "-J-XX:+UseParallelGC",
                    "-script",
                    scriptFile.absolutePath
                )
                processBuilder.redirectErrorStream(true)

                val process = processBuilder.start()

                val output = StringBuilder()
                val reader = process.inputStream.bufferedReader()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    updateUI {
                        statusLabel.text = "Running..."
                    }
                    output.append(line).append('\n')
                    updateUI {
                        outputArea.text = output.toString()
                    }
                }

                updateUI {
                    statusLabel.text = "Running..."
                }

                process.waitFor()
                val endTime = System.currentTimeMillis()
                val exitCode = process.exitValue()
                val executionTime = endTime - startTime
                updateUI {
                    statusLabel.text = "Exit code: $exitCode (${millisecondsToSeconds(executionTime)})"
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