package com.swmansion.enriched.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Spannable
import android.text.StaticLayout
import android.text.TextPaint
import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.swmansion.enriched.markdown.parser.MarkdownASTNode
import com.swmansion.enriched.markdown.spans.LinkPillSpan
import com.swmansion.enriched.markdown.spans.LinkSpan
import com.swmansion.enriched.markdown.styles.LinkPillContent
import com.swmansion.enriched.markdown.test.MarkdownRenderTestSupport.render
import com.swmansion.enriched.markdown.test.MarkdownRenderTestSupport.styleConfig
import com.swmansion.enriched.markdown.test.TestAstFactory.listItem
import com.swmansion.enriched.markdown.test.TestAstFactory.paragraph
import com.swmansion.enriched.markdown.test.TestAstFactory.text
import com.swmansion.enriched.markdown.test.TestAstFactory.unorderedList
import com.swmansion.enriched.markdown.utils.text.span.prepareWidthAwareSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LinkWrappingTest {
  @Test
  fun decoratedProseKeepsItsIconTextLinkTargetAndShapingContext() {
    val context = RuntimeEnvironment.getApplication()
    val iconFile = File(context.cacheDir, "link-wrapping-icon.png")
    val icon = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
    iconFile.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
    try {
      for (fontScale in listOf(1f, 1.3f, 2f)) {
        for ((first, canSplit) in listOf(
          "e\u0301" to true,
          "👩🏽‍💻" to true,
          "مرحبا" to false,
          "مَرْحَبًا" to false,
          "ܫܠܡܐ" to false,
          "ߊߘߌ" to false,
          "\u200Dمرحبا" to false,
        )) {
          for (inList in listOf(false, true)) {
            val label = "$first explicitly took over on October twenty second and continued the review"
            val variant =
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
                JavaOnlyMap.of("enabled", true, "wrap", true, "paddingHorizontal", 0.0, "paddingVertical", 0.0),
              )
            val style =
              styleConfig(
                list = mapOf("fontSize" to (18.0 * fontScale)),
                linkVariants = JavaOnlyArray.of(variant),
              )
            val url = "https://github.com/example/project/pull/1"
            style.linkPillContent = mapOf(url to LinkPillContent(iconUri = iconFile.toURI().toString()))
            val link =
              MarkdownASTNode(
                MarkdownASTNode.NodeType.Link,
                attributes = mapOf("url" to url),
                children = listOf(text(label)),
              )
            val block = paragraph(text("Review: "), link, text(" afterward."))
            val rendered =
              style.withParagraphOverride(style.paragraphStyle.copy(fontSize = 18f * fontScale)) {
                render(style, if (inList) unorderedList(listItem(block)) else block)
              }
            val pill = rendered.getSpans(0, rendered.length, LinkPillSpan::class.java).single()
            val target = rendered.getSpans(0, rendered.length, LinkSpan::class.java).single()
            val start = rendered.getSpanStart(target)
            assertEquals("Review: $label afterward.", rendered.toString())
            assertEquals(url, target.url)
            assertEquals(label.length, rendered.getSpanEnd(target) - start)
            assertEquals(start, rendered.getSpanStart(pill))
            assertEquals(if (canSplit) first.length else label.length, rendered.getSpanEnd(pill) - start)
            if (canSplit) {
              assertIconAndWrappedLines(rendered, 240, 18f * fontScale, "$first / $fontScale / list $inList")
            }
          }
        }
      }
    } finally {
      iconFile.delete()
      icon.recycle()
    }
  }

  private fun assertIconAndWrappedLines(
    rendered: Spannable,
    width: Int,
    fontSize: Float,
    case: String,
  ) {
    prepareWidthAwareSpans(rendered, width)
    val layout =
      StaticLayout.Builder
        .obtain(rendered, 0, rendered.length, TextPaint().apply { textSize = fontSize }, width)
        .setIncludePad(false)
        .build()
    assertTrue("The full label must wrap", layout.lineCount > 1)
    for (line in 0 until layout.lineCount) {
      assertTrue(
        "$case / Line $line exceeds $width: ${layout.getLineRight(
          line,
        )} / ${rendered.subSequence(layout.getLineStart(line), layout.getLineEnd(line))}",
        layout.getLineRight(line) <= width + 1,
      )
    }
    val bitmap = Bitmap.createBitmap(width, layout.height, Bitmap.Config.ARGB_8888)
    layout.draw(Canvas(bitmap))
    val pixels = IntArray(width * layout.height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, layout.height)
    assertTrue("The leading icon must still be drawn", pixels.any { it == Color.GREEN })
    bitmap.recycle()
  }
}
