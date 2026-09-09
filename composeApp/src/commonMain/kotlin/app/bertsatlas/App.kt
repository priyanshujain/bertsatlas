package app.bertsatlas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App() {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF7F0DF)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText("Bert's Atlas", style = TextStyle(color = Color(0xFF3E2447), fontSize = 36.sp))
        BasicText(
            "Getting nerdier every day.",
            modifier = Modifier.padding(vertical = 16.dp),
            style = TextStyle(color = Color(0xFF3E2447), fontSize = 18.sp),
        )
        BasicText(
            "Start",
            modifier = Modifier
                .heightIn(min = 48.dp)
                .background(Color(0xFF19647E))
                .clickable(onClick = {})
                .padding(horizontal = 32.dp, vertical = 14.dp),
            style = TextStyle(color = Color.White, fontSize = 18.sp),
        )
    }
}
