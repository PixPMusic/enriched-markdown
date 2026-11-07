package com.swmansion.enriched.markdown

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.widget.TextView
import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.swmansion.enriched.markdown.accessibility.AccessibleMarkdownTextView
import com.swmansion.enriched.markdown.parser.MarkdownASTNode
import com.swmansion.enriched.markdown.renderer.Renderer
import com.swmansion.enriched.markdown.spans.LinkSpan
import com.swmansion.enriched.markdown.styles.StyleConfig
import com.swmansion.enriched.markdown.test.MarkdownRenderTestSupport
import com.swmansion.enriched.markdown.test.TestAstFactory.paragraph
import com.swmansion.enriched.markdown.test.TestAstFactory.text
import com.swmansion.enriched.markdown.utils.common.serialization.MarkdownASTSerializer
import com.swmansion.enriched.markdown.utils.text.conversion.HTMLGenerator
import com.swmansion.enriched.markdown.utils.text.view.setupAsMarkdownTextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ClipboardExportTest {
  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun tableLineBreaksAreExportedAsHtmlBreaks() {
    for (separator in listOf("\n", "\r", "\r\n", "\u2028", "\u2029")) {
      for (text in listOf<CharSequence>(SpannableString("first${separator}second"), "first${separator}second")) {
        val rows = listOf(listOf(Triple(text, false, Layout.Alignment.ALIGN_NORMAL)))
        val html = HTMLGenerator.generateTableHTML(rows, style())
        assertTrue(html, html.contains("first<br>second"))
        assertFalse(html, html.contains("<br><br>"))
      }
    }
  }

  @Test
  fun escapedCharactersAndLineBreaksShareOneRun() {
    val rows = listOf(listOf(Triple<CharSequence, Boolean, Layout.Alignment>("a & b\nc <d>", false, Layout.Alignment.ALIGN_NORMAL)))
    val html = HTMLGenerator.generateTableHTML(rows, style())
    assertTrue(html, html.contains("a &amp; b<br>c &lt;d&gt;"))
  }

  @Test
  fun codeBlockNewlinesRemainLiteral() {
    val style = style()
    val codeBlock =
      MarkdownASTNode(
        MarkdownASTNode.NodeType.CodeBlock,
        content = "first\nsecond",
        children = listOf(MarkdownASTNode(MarkdownASTNode.NodeType.Text, "first\nsecond")),
      )
    val renderer = Renderer()
    renderer.configure(style, context)
    val html = HTMLGenerator.generateHTML(renderer.renderContent(listOf(codeBlock)), style)
    assertFalse(html, html.contains("<br>"))
    assertTrue(html, html.contains("first\nsecond"))
  }

  @Test
  fun serializedTableFormulaKeepsItsInlineDelimiters() {
    val formula =
      MarkdownASTNode(MarkdownASTNode.NodeType.LatexMathInline, children = listOf(MarkdownASTNode(MarkdownASTNode.NodeType.Text, "x_1")))
    val cell = MarkdownASTNode(MarkdownASTNode.NodeType.TableCell, children = listOf(formula))
    assertEquals("\$x_1\$", MarkdownASTSerializer.serializeChildren(cell))

    val strong = MarkdownASTNode(MarkdownASTNode.NodeType.Strong, children = listOf(formula))
    val nestedCell = MarkdownASTNode(MarkdownASTNode.NodeType.TableCell, children = listOf(strong))
    assertEquals("**\$x_1\$**", MarkdownASTSerializer.serializeChildren(nestedCell))
  }

  @Test
  fun keyboardCopyOmitsLinkIconDecorations() {
    val label = "مرحبا بالعالم"
    val url = "https://github.com/example/project/pull/1"
    val style =
      MarkdownRenderTestSupport.styleConfig(
        linkVariants =
          JavaOnlyArray.of(
            JavaOnlyMap.of(
              "pattern",
              "^https:",
              "fontFamily",
              "sans-serif",
              "color",
              Color.BLUE,
              "underline",
              false,
              "backgroundColor",
              Color.TRANSPARENT,
              "pill",
              JavaOnlyMap.of("enabled", true, "wrap", true),
            ),
          ),
      )
    val link = MarkdownASTNode(MarkdownASTNode.NodeType.Link, attributes = mapOf("url" to url), children = listOf(text(label)))
    val rendered = MarkdownRenderTestSupport.render(style, paragraph(text("Review: "), link, text(" afterward.")))
    val target = rendered.getSpans(0, rendered.length, LinkSpan::class.java).single()
    val view =
      object : AccessibleMarkdownTextView(ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)) {}.apply {
        setupAsMarkdownTextView()
        setText(rendered, TextView.BufferType.SPANNABLE)
      }
    val linkStart = rendered.getSpanStart(target)
    val linkEnd = rendered.getSpanEnd(target)
    val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    for ((start, end) in listOf(linkStart to linkEnd, linkEnd to linkStart)) {
      Selection.setSelection(view.text as Spannable, start, end)
      assertTrue(view.onKeyShortcut(KeyEvent.KEYCODE_C, event))
      assertEquals(
        "$start -> $end",
        label,
        clipboard.primaryClip!!
          .getItemAt(0)
          .text
          .toString(),
      )
    }
  }

  private fun style(): StyleConfig {
    com.facebook.react.uimanager.DisplayMetricsHolder
      .initDisplayMetricsIfNotInitialized(context)

    fun element() =
      JavaOnlyMap().apply {
        listOf(
          "aspectRatio",
          "bulletSize",
          "checkboxBorderRadius",
          "checkboxSize",
          "height",
          "itemSpacing",
          "markerMinWidth",
          "maxHeight",
          "cellPaddingHorizontal",
          "cellPaddingVertical",
          "horizontalOverflow",
          "lineHeight",
          "marginTop",
          "marginBottom",
          "padding",
          "borderRadius",
          "borderWidth",
          "gapWidth",
          "marginLeft",
        ).forEach {
          putDouble(it, 0.0)
        }
        listOf(
          "bulletColor",
          "checkedColor",
          "checkedTextColor",
          "checkmarkColor",
          "headerBackgroundColor",
          "headerTextColor",
          "markerColor",
          "rowEvenBackgroundColor",
          "rowOddBackgroundColor",
          "color",
          "borderColor",
        ).forEach {
          putInt(it, Color.BLACK)
        }
        putString("resizeMode", "contain")
        putString("fontFamily", "")
        putString("fontWeight", "normal")
        putDouble("fontSize", 16.0)
        putDouble("fontScale", 0.8)
        putDouble("baselineOffsetScale", 0.5)
        putBoolean("underline", false)
        putInt("backgroundColor", Color.TRANSPARENT)
      }
    val style = JavaOnlyMap()
    listOf(
      "table",
      "paragraph",
      "codeBlock",
      "code",
      "blockquote",
      "list",
      "link",
      "strong",
      "em",
      "strikethrough",
      "underline",
      "highlight",
      "image",
      "superscript",
      "subscript",
    ).forEach { style.putMap(it, element()) }
    style.putMap(
      "spoiler",
      element().apply {
        putMap("particles", JavaOnlyMap.of("density", 0.5, "speed", 1.0))
        putMap("solid", JavaOnlyMap.of("borderRadius", 0.0))
      },
    )
    for (level in 1..6) style.putMap("h$level", element())
    style.putMap(
      "taskList",
      JavaOnlyMap.of(
        "checkedColor",
        Color.BLACK,
        "borderColor",
        Color.BLACK,
        "checkmarkColor",
        Color.BLACK,
        "checkedTextColor",
        Color.BLACK,
        "checkboxSize",
        16.0,
        "checkboxBorderRadius",
        0.0,
        "checkedStrikethrough",
        false,
      ),
    )
    return StyleConfig(style, context, false, 0f)
  }
}
