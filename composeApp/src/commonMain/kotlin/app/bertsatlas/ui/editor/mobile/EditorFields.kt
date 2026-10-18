package app.bertsatlas.ui.editor.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.bertsatlas.ui.AtlasText
import app.bertsatlas.ui.AtlasTheme

private val FieldShape = RoundedCornerShape(14.dp)

@Composable
fun EditorLabel(label: String, help: String? = null, required: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AtlasText(if (required) label else "$label (optional)", style = AtlasTheme.typography.label)
        help?.let { AtlasText(it, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum) }
    }
}

@Composable
fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = field.copy(text = value, selection = TextRange(value.length))
    EditorTextFieldValue(field, { field = it; onValueChange(it.text) }, label, modifier, singleLine, placeholder)
}

@Composable
fun EditorTextFieldValue(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    val colors = AtlasTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 52.dp else 96.dp)
            .background(colors.paper, FieldShape)
            .border(1.5.dp, colors.outline, FieldShape)
            .semantics { contentDescription = label }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        textStyle = AtlasTheme.typography.body.copy(color = colors.plum),
        singleLine = singleLine,
        cursorBrush = SolidColor(colors.plum),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty() && placeholder != null) {
                    AtlasText(placeholder, color = colors.mutedPlum.copy(alpha = .6f))
                }
                inner()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String?, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) -> EditorChip(label, id == selected) { onSelect(id) } }
    }
}

@Composable
fun EditorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AtlasTheme.colors
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(FieldShape)
            .background(if (selected) colors.seaGlass else colors.paper, FieldShape)
            .border(1.5.dp, if (selected) colors.success else colors.outline.copy(alpha = .7f), FieldShape)
            .semantics {
                role = Role.Button
                contentDescription = label
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AtlasText(label, style = AtlasTheme.typography.label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PackAccents.all.forEachIndexed { index, hex ->
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(hex.toAccentColor(), CircleShape)
                    .border(if (isSelected) 4.dp else 1.dp, if (isSelected) AtlasTheme.colors.plum else AtlasTheme.colors.outline, CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Colour ${index + 1}"
                        this.selected = isSelected
                    }
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) AtlasText("✓", style = AtlasTheme.typography.heading, color = AtlasTheme.colors.white)
            }
        }
    }
}

fun String.toAccentColor(): Color = runCatching { Color(0xFF000000 or removePrefix("#").toLong(16)) }.getOrDefault(Color(0xFF27635B))
