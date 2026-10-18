package app.bertopia.desktop.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bertopia.ui.BertopiaText
import app.bertopia.ui.BertopiaTheme

val EditorControlShape = RoundedCornerShape(10.dp)
val EditorPanelShape = RoundedCornerShape(16.dp)

enum class EditorButtonStyle { Primary, Secondary, Quiet, Danger }

object EditorText {
    val body: TextStyle @Composable get() = BertopiaTheme.typography.detail.copy(fontSize = 14.sp, lineHeight = 19.sp)
    val strong: TextStyle @Composable get() = body.copy(fontWeight = FontWeight.SemiBold)
    val small: TextStyle @Composable get() = BertopiaTheme.typography.detail.copy(fontSize = 12.sp, lineHeight = 16.sp)
    val heading: TextStyle @Composable get() = BertopiaTheme.typography.heading.copy(fontSize = 18.sp, lineHeight = 24.sp)
    val overline: TextStyle @Composable get() = small.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
}

@Composable
fun EditorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EditorButtonStyle = EditorButtonStyle.Secondary,
    enabled: Boolean = true,
) {
    val colors = BertopiaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        !enabled -> colors.outline.copy(alpha = .18f)
        style == EditorButtonStyle.Primary -> colors.bertopiaBlue
        style == EditorButtonStyle.Quiet -> if (hovered) colors.paper else Color.Transparent
        else -> if (hovered) colors.seaGlass.copy(alpha = .55f) else colors.paper
    }
    val foreground = when {
        !enabled -> colors.mutedPlum.copy(alpha = .55f)
        style == EditorButtonStyle.Primary -> colors.white
        style == EditorButtonStyle.Danger -> colors.orangeAccent
        else -> colors.plum
    }
    val outline = when (style) {
        EditorButtonStyle.Secondary -> Modifier.border(1.dp, colors.outline.copy(alpha = .55f), EditorControlShape)
        EditorButtonStyle.Danger -> Modifier.border(1.dp, colors.orangeAccent.copy(alpha = .8f), EditorControlShape)
        else -> Modifier
    }
    Box(
        modifier
            .heightIn(min = 32.dp)
            .then(outline)
            .clip(EditorControlShape)
            .background(background)
            .semantics {
                role = Role.Button
                contentDescription = text
                if (!enabled) disabled()
            }
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BertopiaText(text, style = EditorText.strong, color = foreground)
    }
}

@Composable
fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    monospace: Boolean = false,
    label: String? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val colors = BertopiaTheme.colors
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val baseStyle = EditorText.body.copy(color = colors.plum)
    val textStyle = if (monospace) baseStyle.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp) else baseStyle
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Tab && !event.isMetaPressed) {
                    focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                    true
                } else false
            }
            .semantics { if (label != null) contentDescription = label },
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.bertopiaBlue),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (enabled) colors.white else colors.paper, EditorControlShape)
                    .border(1.dp, colors.outline.copy(alpha = .6f), EditorControlShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) BertopiaText(placeholder, style = textStyle, color = colors.mutedPlum.copy(alpha = .5f))
                inner()
            }
        },
    )
}

@Composable
fun EditorChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BertopiaTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.bertopiaBlue else colors.paper)
            .border(1.dp, if (selected) colors.bertopiaBlue else colors.outline.copy(alpha = .5f), RoundedCornerShape(50))
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = text
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        BertopiaText(text, style = EditorText.small, color = if (selected) colors.white else colors.plum)
    }
}

@Composable
fun SidebarRow(
    text: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    indent: Int = 0,
    marker: Color? = null,
    filled: Boolean = selected,
) {
    val colors = BertopiaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .clip(EditorControlShape)
            .background(
                when {
                    filled -> colors.seaGlass
                    hovered -> colors.paper
                    else -> Color.Transparent
                },
            )
            .semantics {
                role = Role.Button
                this.selected = selected
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = (10 + indent * 14).dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        marker?.let { Box(Modifier.size(10.dp).background(it, androidx.compose.foundation.shape.CircleShape)) }
        BertopiaText(text, Modifier.weight(1f), style = if (selected) EditorText.strong else EditorText.body)
        detail?.let { BertopiaText(it, style = EditorText.small, color = colors.mutedPlum.copy(alpha = .75f)) }
    }
}

@Composable
fun EditorSectionLabel(text: String, modifier: Modifier = Modifier) {
    BertopiaText(text.uppercase(), modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp), style = EditorText.overline, color = BertopiaTheme.colors.mutedPlum.copy(alpha = .7f))
}

@Composable
fun EditorPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .background(BertopiaTheme.colors.paper.copy(alpha = .55f), EditorPanelShape)
            .border(1.dp, BertopiaTheme.colors.outline.copy(alpha = .25f), EditorPanelShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}
