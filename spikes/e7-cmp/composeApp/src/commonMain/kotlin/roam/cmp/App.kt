package roam.cmp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 共享 UI：Android 与 Desktop 渲染的是同一段代码。 */
@Composable
fun App() {
    var count by remember { mutableStateOf(0) }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Roam · Compose Multiplatform", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Android / Desktop 共用同一套 commonMain")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { count++ }) { Text("点击 $count") }
        }
    }
}
