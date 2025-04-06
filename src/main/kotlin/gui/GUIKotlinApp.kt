package com.hlianole.guikotlin.gui

import com.hlianole.guikotlin.run.ScriptRunner
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.Stage
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import org.fxmisc.richtext.StyleClassedTextArea
import java.util.concurrent.CompletableFuture

class GUIKotlinApp : Application()  {
    private lateinit var scriptArea : CodeArea
    private lateinit var outputArea: StyleClassedTextArea
    private lateinit var statusLabel : Label
    private lateinit var runButton : Button
    private lateinit var stopButton : Button
    private lateinit var cacheButton : RadioButton
    private lateinit var keywordsHighlighter: KeywordsHighlighter
    private var isUsingCache = false
    private var scriptRunner = ScriptRunner()

    override fun start(primaryStage: Stage) {
        Font.loadFont(javaClass.getResourceAsStream("/fonts/montserrat/Montserrat-SemiBold.ttf"), 16.0)
        Font.loadFont(javaClass.getResourceAsStream("/fonts/montserrat/Montserrat-Regular.ttf"), 16.0)
        scriptRunner.preload()

        val root = VBox(10.0).apply {
            padding = Insets(15.0)
            HBox.setHgrow(this, Priority.ALWAYS)
        }

        val scriptPanel = VBox(10.0).apply {
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        val outputPanel = VBox(10.0).apply {
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        val scriptTopBar = HBox(10.0).apply {
            minHeight = 30.0
            maxHeight = 30.0
            alignment = Pos.CENTER_LEFT
            padding = Insets(0.0, 0.0, 0.0, 10.0)
        }

        val outputTopBar = HBox(10.0).apply {
            minHeight = 30.0
            maxHeight = 30.0
            alignment = Pos.CENTER_LEFT
            spacing = 20.0
            padding = Insets(0.0, 0.0, 0.0, 10.0)
        }

        scriptArea = CodeArea().apply {
            styleClass.add("script-area")
            VBox.setVgrow(this, Priority.ALWAYS)

            paragraphGraphicFactory = LineNumberFactory.get(this)

            caretPositionProperty().addListener { _, _, _ ->
                Platform.runLater {
                    highlightCurrentLine()
                }
            }

            addEventHandler(KeyEvent.KEY_PRESSED) { event ->
                if (event.code == KeyCode.UP || event.code == KeyCode.DOWN) {
                    Platform.runLater {
                        highlightCurrentLine()
                    }
                }
            }

            addEventHandler(MouseEvent.MOUSE_PRESSED) {
                Platform.runLater {
                    highlightCurrentLine()
                }
            }

            addEventHandler(KeyEvent.KEY_TYPED) {
                Platform.runLater {
                    keywordsHighlighter.highlightKeywords()
                }
            }
        }

        keywordsHighlighter = KeywordsHighlighter(scriptArea = scriptArea)

        outputArea = StyleClassedTextArea().apply {
            styleClass.add("output-area")
            VBox.setVgrow(this, Priority.ALWAYS)
            isFocusTraversable = false
            isEditable = false
            isWrapText = true
        }

        statusLabel = Label("Ready").apply {
            styleClass.add("status-label")
        }

        runButton = Button("Run").apply {
            styleClass.add("run-button")
            setOnAction {
                runScript()
            }
        }

        stopButton = Button("Stop").apply {
            styleClass.add("stop-button")
            setOnAction {
                stopScript()
            }
        }

        cacheButton = RadioButton("Use caching").apply {
            styleClass.add("cache-button")
            isSelected = isUsingCache
            setOnAction {
                isUsingCache = isSelected
            }
        }


        scriptTopBar.children.addAll(
            Label("Kotlin script").apply {
                styleClass.add("header-label")
            }
        )

        scriptPanel.children.addAll(
            scriptTopBar,
            scriptArea
        )

        outputTopBar.children.addAll(
            runButton,
            stopButton,
            statusLabel,
            cacheButton
        )

        outputPanel.children.addAll(
            outputTopBar,
            outputArea
        )

        val splitPane = SplitPane(scriptPanel, outputPanel).apply {
            setDividerPositions(0.5)
            styleClass.add("split-pane")
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        splitPane.dividers[0].positionProperty().addListener { _, _, newValue ->
            val minPos = 0.15
            val maxPos = 0.85

            if (newValue.toDouble() < minPos) {
                Platform.runLater {
                    splitPane.setDividerPositions(minPos)
                }
            }
            if (newValue.toDouble() > maxPos) {
                Platform.runLater {
                    splitPane.setDividerPositions(maxPos)
                }
            }
        }

        root.children.addAll(
            splitPane
        )

        val scene = Scene(root, 1280.0, 720.0)
        val cssPath = javaClass.getResource("/styles.css")?.toExternalForm()
        if (cssPath != null) {
            scene.stylesheets.add(cssPath)
        }
        else {
            println("CSS file not found")
        }
        primaryStage.scene = scene
        primaryStage.title = "Kotlin script runner"

        primaryStage.minWidth = 400.0
        primaryStage.minHeight = 300.0

        primaryStage.show()
    }

    private fun runScript() {
        CompletableFuture.supplyAsync {
            scriptRunner.run(
                scriptArea = scriptArea,
                outputArea = outputArea,
                statusLabel = statusLabel,
                isUsingCache = isUsingCache,
                updateUI = ::updateUI
            )
        }
    }

    private fun stopScript() {
        Platform.runLater {
            scriptRunner.stop(
                statusLabel = statusLabel,
                updateUI = ::updateUI
            )
        }
    }

    private fun updateUI(runnable: () -> Unit) {
        Platform.runLater(runnable)
    }

    private fun highlightCurrentLine() {
        val currentParagraph = scriptArea.currentParagraph

        for (i in 0 until scriptArea.paragraphs.size) {
            scriptArea.setParagraphStyle(i, emptyList())
        }

        scriptArea.setStyleSpans(0, scriptArea.length,
            org.fxmisc.richtext.model.StyleSpansBuilder<Collection<String>>()
                .add(emptyList(), scriptArea.length)
                .create()
        )

        scriptArea.setParagraphStyle(currentParagraph,
            listOf("current-paragraph-style"))

    }
}