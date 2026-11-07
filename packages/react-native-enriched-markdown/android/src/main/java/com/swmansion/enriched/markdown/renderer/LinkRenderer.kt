package com.swmansion.enriched.markdown.renderer

import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ReplacementSpan
import com.swmansion.enriched.markdown.parser.MarkdownASTNode
import com.swmansion.enriched.markdown.spans.LinkPillSpan
import com.swmansion.enriched.markdown.spans.LinkSpan
import com.swmansion.enriched.markdown.spans.SpoilerSpan
import com.swmansion.enriched.markdown.utils.text.extensions.applyBlockStyleFont
import com.swmansion.enriched.markdown.utils.text.span.SPAN_FLAGS_EXCLUSIVE_EXCLUSIVE

class LinkRenderer(
  private val config: RendererConfig,
) : NodeRenderer {
  // Reused to resolve each pill's typeface; a renderer serves one render at a time.
  private val fontProbe = TextPaint()

  override fun render(
    node: MarkdownASTNode,
    builder: SpannableStringBuilder,
    onLinkPress: ((String) -> Unit)?,
    onLinkLongPress: ((String) -> Unit)?,
    factory: RendererFactory,
  ) {
    val url = node.getAttribute("url") ?: return
    val recognizedLink = node.getAttribute("recognizedLink") == "true"

    factory.renderWithSpan(builder, { factory.renderChildren(node, builder, onLinkPress, onLinkLongPress) }) { start, end, blockStyle ->
      var linkEnd = end
      val variant = factory.styleCache.resolvedVariantForUrl(url)
      if (variant?.pill != null && canPresentAsPill(builder, start, end)) {
        val fontFamily = variant.fontFamily.ifEmpty { factory.styleCache.linkFontFamily }.ifEmpty { blockStyle.fontFamily }
        val font = fontProbe.apply { applyBlockStyleFont(blockStyle.copy(fontFamily = fontFamily), factory.context) }
        val content = config.style.linkPillContent[url]
        val iconOnly =
          variant.pill.wrap && content?.label.isNullOrEmpty() &&
            variant.pill.content.label
              .isEmpty()
        if (iconOnly) {
          // Keep all label characters in native shaping; the joiner binds its icon to the first line.
          builder.insert(start, "\uFFFC\u2060")
          factory.shiftDeferredSpans(start, 2)
          builder.setSpan(LinkPillSpan.IconText(), start, start + 2, SPAN_FLAGS_EXCLUSIVE_EXCLUSIVE)
          linkEnd += 2
        }
        val pillEnd = if (iconOnly) start + 1 else end
        val pill =
          LinkPillSpan(
            variant,
            font.typeface,
            blockStyle.fontSize,
            if (iconOnly) "" else builder.subSequence(start, pillEnd).toString(),
            factory.context,
            content,
            config.style.imageRequestHeaders,
            followsRunTypeface = variant.fontFamily.isEmpty() && factory.styleCache.linkFontFamily.isEmpty(),
          )
        // Set directly, not deferred: the pill measures with its own font, so it does not
        // need to come after the block spans, and a late insertion makes the builder re-sort.
        builder.setSpan(pill, start, pillEnd, SPAN_FLAGS_EXCLUSIVE_EXCLUSIVE)
        factory.runAfterRender { text -> pill.resolveLeadingMargin(text, text.getSpanStart(pill), text.getSpanEnd(pill)) }
      }
      builder.setSpan(
        LinkSpan(
          url,
          onLinkPress,
          onLinkLongPress,
          factory.styleCache,
          blockStyle,
          factory.context,
          recognizedLink = recognizedLink,
          keepsCodeFont = recognizedLink && node.children.any { it.type == MarkdownASTNode.NodeType.Code },
        ),
        start,
        linkEnd,
        SPAN_FLAGS_EXCLUSIVE_EXCLUSIVE,
      )
    }
  }

  /**
   * A pill replaces one unbroken run. Links that already hold a replacement (image,
   * math) or span a line break stay ordinary links: a replacement straddling two
   * paragraphs would be drawn once per paragraph. So do links holding a spoiler,
   * whose hidden text the label would show.
   */
  private fun canPresentAsPill(
    builder: SpannableStringBuilder,
    start: Int,
    end: Int,
  ): Boolean {
    if (start == end) return false
    if (builder.getSpans(start, end, ReplacementSpan::class.java).isNotEmpty()) return false
    if (builder.getSpans(start, end, SpoilerSpan::class.java).isNotEmpty()) return false
    for (index in start until end) {
      val char = builder[index]
      if (char == '\n' || char == '\r' || char == '\u2028' || char == '\u2029') return false
    }
    return true
  }
}
