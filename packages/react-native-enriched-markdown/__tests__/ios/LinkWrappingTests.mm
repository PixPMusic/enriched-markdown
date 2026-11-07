#import "../../ios/attachments/ENRMLinkPillAttachment.h"
#import "../../ios/attachments/ENRMLinkPillText.h"
#import "../../ios/parser/MarkdownASTNode.h"
#import "../../ios/renderer/AttributedRenderer.h"
#import "../../ios/renderer/RenderContext.h"
#import "../../ios/styles/StyleConfig.h"
#import <XCTest/XCTest.h>

@interface ENRMLinkWrappingTests : XCTestCase
@end

@implementation ENRMLinkWrappingTests

- (void)testWrappedLabelsKeepTheirTextAndFullLinkRange
{
  NSString *url = @"https://github.com/example/project/pull/1";
  for (NSString *first in @[
         @"e\u0301", @"👩🏽‍💻", @"مرحبا", @"مَرْحَبًا", @"ܫܠܡܐ", @"ߊߘߌ", @"\u200Dمرحبا",
         [@"" stringByPaddingToLength:100 withString:@"م" startingAtIndex:0]
       ]) {
    NSString *label =
        [first stringByAppendingString:@" explicitly took over on October twenty second and continued the review"];
    for (NSNumber *fontSize in @[ @18, @24, @36 ]) {
      StyleConfig *config = [[StyleConfig alloc] init];
      [config setParagraphFontSize:fontSize.doubleValue];
      LinkPillConfig *pill = [[LinkPillConfig alloc] init];
      pill.wrap = YES;
      pill.label = @"";
      pill.iconUri = @"";
      pill.borderColor = UIColor.clearColor;
      LinkVariantConfig *variant = [[LinkVariantConfig alloc] init];
      variant.pattern = @"^https:";
      variant.color = UIColor.blueColor;
      variant.fontFamily = @"";
      variant.pill = pill;
      [config setLinkVariants:@[ variant ]];
      MarkdownASTNode *text = [[MarkdownASTNode alloc] initWithType:MarkdownNodeTypeText];
      text.content = label;
      MarkdownASTNode *link = [[MarkdownASTNode alloc] initWithType:MarkdownNodeTypeLink];
      link.attributes[@"url"] = url;
      [link addChild:text];
      MarkdownASTNode *paragraph = [[MarkdownASTNode alloc] initWithType:MarkdownNodeTypeParagraph];
      [paragraph addChild:link];
      AttributedRenderer *renderer = [[AttributedRenderer alloc] initWithConfig:config];
      NSAttributedString *rendered = [renderer renderNodes:@[ paragraph ]
                                                   context:[[RenderContext alloc] init]
                                                     block:nil];
      ENRMLinkPillAttachment *attachment = [rendered attribute:NSAttachmentAttributeName atIndex:0 effectiveRange:NULL];
      XCTAssertTrue([attachment isKindOfClass:ENRMLinkPillAttachment.class]);
      XCTAssertEqualObjects(attachment.originalText.string, @"");
      XCTAssertEqualObjects([rendered.string substringFromIndex:2], label);
      XCTAssertEqualObjects(ENRMStringByExpandingLinkPills(rendered), label);
      NSRange linkRange;
      XCTAssertEqualObjects([rendered attribute:NSLinkAttributeName atIndex:0 effectiveRange:&linkRange], url);
      XCTAssertEqual(linkRange.length, rendered.length);
      NSTextStorage *storage = [[NSTextStorage alloc] initWithAttributedString:rendered];
      NSLayoutManager *layout = [[NSLayoutManager alloc] init];
      NSTextContainer *container = [[NSTextContainer alloc] initWithSize:CGSizeMake(240, CGFLOAT_MAX)];
      container.lineFragmentPadding = 0;
      [layout addTextContainer:container];
      [storage addLayoutManager:layout];
      [layout ensureLayoutForTextContainer:container];
      __block NSUInteger lines = 0;
      [layout enumerateLineFragmentsForGlyphRange:[layout glyphRangeForTextContainer:container]
                                       usingBlock:^(CGRect rect, CGRect used, NSTextContainer *textContainer,
                                                    NSRange glyphs, BOOL *stop) {
                                         lines++;
                                         XCTAssertNotEqual(glyphs.length, 1);
                                         XCTAssertLessThanOrEqual(CGRectGetMaxX(used), 241);
                                       }];
      XCTAssertGreaterThan(lines, 1);
    }
  }
}

@end
