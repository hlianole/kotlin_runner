package com.hlianole.guikotlin.gui

import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.model.StyleSpansBuilder

class KeywordsHighlighter(
    private val scriptArea: CodeArea
) {
    private val keyWords1 = setOf(
        "val", "var", "import",
        "package", "private", "protected",
        "public", "class", "return",
        "break", "continue", "do",
        "if", "else", "for",
        "while", "when", "in",
        "until", "object", "final",
        "const", "lateinit", "true",
        "false", "null", "throw",
        "inline", "enum"
    )

    private val keyWords2 = setOf(
        "fun", "operator", "override"
    )

    private val keyWords3 = setOf(
        "char", "double", "float",
        "int", "long", "short",
        "byte", "String", "Long",
        "Float", "Double", "Char"
    )

    private val keywordPattern1 = "\\b(" + keyWords1.joinToString("|") + ")\\b"
    private val keywordPattern2 = "\\b(" + keyWords2.joinToString("|") + ")\\b"
    private val keywordPattern3 = "\\b(" + keyWords3.joinToString("|") + ")\\b"

    private val operatorPattern = "[+\\-*/%=^><!?]"

    private val singleLineCommentPattern = "//[^\n]*"
    private val multiLineCommentPattern = "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/"

    private val stringPattern = "\"([^\"\\\\]|\\\\.)*\"|\"([^\"\\\\]|\\\\.)*"

    private val pattern = "(?<KEYWORD1>$keywordPattern1)|" +
            "(?<KEYWORD2>$keywordPattern2)|" +
            "(?<KEYWORD3>$keywordPattern3)|" +
            "(?<SCOMMENT>$singleLineCommentPattern)|" +
            "(?<MCOMMENT>$multiLineCommentPattern)|" +
            "(?<STRING>$stringPattern)|" +
            "(?<OPERATOR>$operatorPattern)|"

    fun highlightKeywords() {
        val text = scriptArea.text
        if (text.isEmpty()) {
            return
        }

        val matcher = Regex(pattern).findAll(text)
        val spansBuilder = StyleSpansBuilder<Collection<String>>()
        var lastEnd = 0

        for (match in matcher) {
            val start = match.range.first
            val end = match.range.last + 1

            spansBuilder.add(emptyList(), start - lastEnd)
            when {
                match.groups["KEYWORD1"] != null -> spansBuilder.add(listOf("keyword1"), end - start)
                match.groups["KEYWORD2"] != null -> spansBuilder.add(listOf("keyword2"), end - start)
                match.groups["KEYWORD3"] != null -> spansBuilder.add(listOf("keyword3"), end - start)
                match.groups["SCOMMENT"] != null -> spansBuilder.add(listOf("comment"), end - start)
                match.groups["MCOMMENT"] != null -> spansBuilder.add(listOf("comment"), end - start)
                match.groups["STRING"] != null -> spansBuilder.add(listOf("string"), end - start)
                match.groups["OPERATOR"] != null -> spansBuilder.add(listOf("operator"), end - start)
            }
            lastEnd = end
        }

        spansBuilder.add(emptyList(), text.length - lastEnd)

        scriptArea.setStyleSpans(0, spansBuilder.create())
    }
}