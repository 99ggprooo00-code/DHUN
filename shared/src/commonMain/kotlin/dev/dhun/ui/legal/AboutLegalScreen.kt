package dev.dhun.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunIconButton
import dev.dhun.design.components.GlassCard
import dev.dhun.legal.DhunAppInfo
import dev.dhun.legal.LegalContent
import dev.dhun.legal.LegalDocument
import dev.dhun.legal.LegalTokens
import dev.dhun.legal.MarkdownBlock
import dev.dhun.legal.MarkdownParser
import dev.dhun.legal.MarkdownSpan

/**
 * Settings → About & Legal, and the document reader behind it.
 *
 * Both are real screens on the app's existing
 * [dev.dhun.ui.shell.DetailRoute] stack — never dialogs. A policy is a long
 * document, and a modal makes it unreadable on a phone and unscrollable on a
 * desktop.
 *
 * Three deliberate omissions:
 *
 *  - **No animation.** Not one transition, tween or fade. These are long text
 *    pages, so a reader who asks for reduced motion gets exactly the same screen
 *    as everyone else — there is nothing to reduce.
 *  - **No blur behind text.** The frosted-glass vocabulary is kept (the banner is
 *    a [GlassCard], the surfaces use the design tokens), but `GlassCard`'s own
 *    contract is that content stays sharp, so body copy is never frosted.
 *  - **No network.** The documents are compiled into the build, so every page
 *    renders on a fresh install with the radio off.
 */

/* ------------------------------------------------------------------ the list */

/**
 * The page list, in the order the canonical source declares it.
 *
 * Rows are text-only on purpose. The icon set has no legal glyphs, and inventing
 * Material path data to fill the gap would put unverified art into a shipped UI;
 * a labelled text row is honest and it is how the rest of Settings already reads.
 */
@Composable
fun AboutLegalScreen(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LegalTopBar(title = "About & Legal", onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = DhunSpacing.legalContentMaxWidth),
            contentPadding = PaddingValues(bottom = DhunSpacing.xxl),
        ) {
            item(key = "about-legal-intro") {
                Text(
                    text = "These pages ship inside the app, so they open with no " +
                        "connection. Every statement carries a status tag saying " +
                        "whether it was read from the source, observed by running " +
                        "the app, taken from a third party, or is still awaiting a " +
                        "decision.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DhunColors.textSecondary,
                    modifier = Modifier.padding(
                        horizontal = DhunSpacing.screenPadding,
                        vertical = DhunSpacing.sm,
                    ),
                )
            }
            itemsIndexed(LegalContent.all, key = { _, doc -> doc.id }) { index, document ->
                Column {
                    LegalListRow(
                        document = document,
                        onClick = { onOpenDocument(document.id) },
                    )
                    if (index < LegalContent.all.lastIndex) LegalDivider()
                }
            }
        }
    }
}

@Composable
private fun LegalListRow(document: LegalDocument, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs),
    ) {
        Text(
            text = document.title,
            style = MaterialTheme.typography.bodyLarge,
            color = DhunColors.textPrimary,
        )
        Text(
            text = if (document.isDraft) {
                "Draft · effective ${document.effectiveDate}"
            } else {
                "Effective ${document.effectiveDate}"
            },
            style = MaterialTheme.typography.bodySmall,
            // A draft page is amber, not the ordinary secondary grey: the reader
            // should see "not yet in force" before they open it, not after.
            color = if (document.isDraft) DhunColors.warning else DhunColors.textSecondary,
        )
    }
}

@Composable
private fun LegalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DhunSpacing.screenPadding)
            .height(DhunSpacing.divider)
            .background(DhunColors.border),
    )
}

/* ---------------------------------------------------------------- the reader */

/**
 * One legal document, rendered from the bundled Markdown.
 *
 * Long documents are laid out in a [LazyColumn], one block per item: the
 * Open-Source Licenses page carries the whole GPL-3.0 plus Apache-2.0 (~48 KB),
 * and an eager `Column` would measure all of it on the first frame.
 *
 * Text is selectable. Note the honest limit: selection works within a block, not
 * across blocks, because each block is its own text layout. Copying a paragraph
 * or a licence clause works; selecting the entire GPL in one drag does not.
 */
@Composable
fun LegalDocumentScreen(
    documentId: String,
    appInfo: DhunAppInfo,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = remember(documentId) { LegalContent.byId(documentId) }
    if (document == null) {
        // Not a crash and not a blank screen: an id that the bundle does not
        // carry is a wiring bug, and saying so beats rendering nothing.
        Column(modifier = modifier.fillMaxSize()) {
            LegalTopBar(title = "About & Legal", onBack = onBack)
            Text(
                text = "This page is not in this build.",
                style = MaterialTheme.typography.bodyLarge,
                color = DhunColors.textSecondary,
                modifier = Modifier.padding(DhunSpacing.screenPadding),
            )
        }
        return
    }

    // Tokens are filled from this build's metadata before parsing, so no page can
    // show a version the build does not have.
    val markdown = remember(documentId, appInfo) { LegalTokens.substitute(document.markdown, appInfo) }
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val unresolved = remember(markdown) { LegalTokens.unsubstitutedTokens(markdown) }
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier.fillMaxSize()) {
        LegalTopBar(title = document.title, onBack = onBack)
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = DhunSpacing.legalContentMaxWidth),
                contentPadding = PaddingValues(
                    start = DhunSpacing.screenPadding,
                    end = DhunSpacing.screenPadding,
                    bottom = DhunSpacing.huge,
                ),
                verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
            ) {
                if (unresolved.isNotEmpty()) {
                    item(key = "unresolved-tokens") {
                        Text(
                            text = "This page still contains an unfilled placeholder " +
                                "(${unresolved.joinToString()}), so part of it may read " +
                                "incorrectly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.warning,
                        )
                    }
                }
                if (document.isDraft) {
                    item(key = "draft-banner") { DraftBanner(document = document) }
                }
                itemsIndexed(blocks, key = { index, _ -> "block-$index" }) { _, block ->
                    LegalBlock(block = block, onOpenUrl = { uriHandler.openUri(it) })
                }
            }
        }
    }
}

@Composable
private fun DraftBanner(document: LegalDocument) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = DhunSpacing.sm),
        contentPadding = PaddingValues(DhunSpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
            Text(
                text = "Draft — not yet in force",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = DhunColors.warning,
            )
            Text(
                text = "This page has not been reviewed by a lawyer and no contact " +
                    "channel has been published. It is effective ${document.effectiveDate}.",
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
            )
        }
    }
}

/* ------------------------------------------------------------- block render */

@Composable
private fun LegalBlock(block: MarkdownBlock, onOpenUrl: (String) -> Unit) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = LegalText(block.spans, onOpenUrl),
            style = headingStyle(block.level),
            color = DhunColors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DhunSpacing.md)
                .semantics { heading() },
        )

        is MarkdownBlock.Paragraph -> Text(
            text = LegalText(block.spans, onOpenUrl),
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )

        is MarkdownBlock.Quote -> GlassCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = DhunSpacing.sm),
            contentPadding = PaddingValues(DhunSpacing.lg),
            borderColor = DhunColors.glassEdge,
        ) {
            Text(
                text = LegalText(block.spans, onOpenUrl),
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is MarkdownBlock.Bullet -> ListRow(
            marker = "\u2022",
            indent = block.indent,
            spans = block.spans,
            onOpenUrl = onOpenUrl,
        )

        is MarkdownBlock.Numbered -> ListRow(
            marker = "${block.number}.",
            indent = block.indent,
            spans = block.spans,
            onOpenUrl = onOpenUrl,
        )

        is MarkdownBlock.Code -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = DhunColors.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .background(DhunColors.surfaceContainerLow)
                .padding(DhunSpacing.md),
        )

        is MarkdownBlock.Table -> LegalTable(table = block, onOpenUrl = onOpenUrl)

        MarkdownBlock.Rule -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = DhunSpacing.sm)
                .height(DhunSpacing.divider)
                .background(DhunColors.border),
        )
    }
}

/**
 * A table rendered as stacked label/value rows rather than a grid.
 *
 * The privacy and support pages carry four-column tables, and a four-column grid
 * on a 360dp phone is either illegible or horizontally scrollable — the brief
 * rules out horizontal overflow, and a sideways-scrolling policy is a policy
 * nobody finishes reading. One record per block, header as the label, keeps
 * every column readable at any width.
 */
@Composable
private fun LegalTable(table: MarkdownBlock.Table, onOpenUrl: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = DhunSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        table.rows.forEach { row ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(DhunSpacing.lg),
                elevated = false,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(DhunSpacing.xs)) {
                    row.forEachIndexed { column, cell ->
                        val label = table.headers.getOrNull(column)?.takeIf { it.isNotBlank() }
                        if (label != null) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = DhunColors.textTertiary,
                            )
                        }
                        Text(
                            text = LegalText(MarkdownParser.inline(cell), onOpenUrl),
                            style = MaterialTheme.typography.bodySmall,
                            color = DhunColors.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (column < row.lastIndex) Spacer(Modifier.height(DhunSpacing.xs))
                    }
                }
            }
        }
    }
}

@Composable
private fun ListRow(
    marker: String,
    indent: Int,
    spans: List<MarkdownSpan>,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = DhunSpacing.lg * indent),
        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textTertiary,
        )
        Text(
            text = LegalText(spans, onOpenUrl),
            style = MaterialTheme.typography.bodyMedium,
            color = DhunColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One heading level per depth; deeper levels clamp instead of shrinking away. */
@Composable
private fun headingStyle(level: Int) = when (level) {
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    else -> MaterialTheme.typography.titleMedium
}

/**
 * Styled inline text with working external links.
 *
 * [LinkAnnotation.Clickable] is given an explicit handler rather than relying on
 * the default interaction: on Android that resolves to an `ACTION_VIEW` intent,
 * on the JVM desktop to Compose's `DesktopUriHandler`, which uses
 * `java.awt.Desktop.browse` where supported and falls back to `xdg-open` /
 * `open`. Both are the platform's own handler, reached through one common API.
 */
@Composable
private fun LegalText(
    spans: List<MarkdownSpan>,
    onOpenUrl: (String) -> Unit,
): androidx.compose.ui.text.AnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = DhunColors.accent,
            textDecoration = TextDecoration.Underline,
        ),
    )
    return buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is MarkdownSpan.Link -> withLink(
                    LinkAnnotation.Clickable(
                        tag = span.url,
                        styles = linkStyles,
                        linkInteraction = { onOpenUrl(span.url) },
                    ),
                ) { append(span.text) }

                is MarkdownSpan.Plain -> withStyle(
                    SpanStyle(
                        fontWeight = if (span.bold) FontWeight.SemiBold else null,
                        fontFamily = if (span.code) FontFamily.Monospace else null,
                        textDecoration = if (span.strike) TextDecoration.LineThrough else null,
                    ),
                ) { append(span.text) }
            }
        }
    }
}

/* ------------------------------------------------------------------ chrome */

@Composable
internal fun LegalTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DhunSpacing.screenPadding, vertical = DhunSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DhunIconButton(
            onClick = onBack,
            modifier = Modifier.size(DhunSpacing.touchTarget),
            contentDescription = "Back",
        ) {
            DhunIconView(
                icon = DhunIcon.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(DhunSpacing.iconSize),
                tint = DhunColors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.width(DhunSpacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = DhunColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
