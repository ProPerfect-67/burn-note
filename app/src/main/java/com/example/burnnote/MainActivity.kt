package com.example.burnnote

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class AppScreen {
    HOME, WRITE, OPEN
}

private val BgBlack = Color(0xFF09090B)
private val CardDark = Color(0xFF141418)
private val BorderDim = Color(0xFF27272E)
private val Crimson = Color(0xFFD93838)
private val TextPrimary = Color(0xFFE4E4E7)
private val TextMuted = Color(0xFF71717A)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep FLAG_SECURE active even on Home/Write so drafts cannot be screenshotted
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            BurnNoteApp(
                onLaunchSecureReader = { rawText ->
                    VolatileNoteHandoff.stage(rawText)
                    startActivity(Intent(this, SecureNoteActivity::class.java))
                }
            )
        }
    }
}

@Composable
private fun BurnNoteApp(onLaunchSecureReader: (String) -> Unit) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        currentScreen = AppScreen.HOME
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        when (currentScreen) {
            AppScreen.HOME -> HomeScreen(
                onWriteClick = { currentScreen = AppScreen.WRITE },
                onOpenClick = { currentScreen = AppScreen.OPEN },
                onSandboxClick = {
                    onLaunchSecureReader(
                        "TOP SECRET // BURN AFTER READING\n" +
                            "Meeting moved to Gate 4 at 19:30.\n" +
                            "Access code for the locker: 84-19-07.\n" +
                            "This demo note self-destructs permanently."
                    )
                }
            )

            AppScreen.WRITE -> WriteNoteScreen(
                onBack = { currentScreen = AppScreen.HOME },
                onPreviewInReader = { text -> onLaunchSecureReader(text) }
            )

            AppScreen.OPEN -> OpenNoteScreen(
                onBack = { currentScreen = AppScreen.HOME },
                onOpenDecoded = { text -> onLaunchSecureReader(text) }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onWriteClick: () -> Unit,
    onOpenClick: () -> Unit,
    onSandboxClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.padding(top = 36.dp)) {
            Text(
                text = "BURN NOTE",
                color = Crimson,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "7-LAYER EPHEMERAL READER",
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(
                onClick = onWriteClick,
                colors = ButtonDefaults.buttonColors(containerColor = Crimson),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "WRITE A BURN NOTE",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            OutlinedButton(
                onClick = onOpenClick,
                border = BorderStroke(1.dp, BorderDim),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "OPEN A BURN CODE",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            OutlinedButton(
                onClick = onSandboxClick,
                border = BorderStroke(1.dp, Color(0xFF1C1C22)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "TEST READER SANDBOX",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, BorderDim),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ACTIVE PROTECTIONS",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "FLAG_SECURE • Dark Render • 30s Timer • Anti-Camera Mask • Device Watermark • Single-Line Slit",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun WriteNoteScreen(
    onBack: () -> Unit,
    onPreviewInReader: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var draftText by remember { mutableStateOf("") }
    var generatedCode by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(title = "WRITE NOTE", onBack = onBack)

        OutlinedTextField(
            value = draftText,
            onValueChange = {
                if (it.length <= 600) {
                    draftText = it
                    generatedCode = null
                }
            },
            placeholder = {
                Text(
                    text = "Type your secret message...\nIt auto-wraps for the single-line reader.",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            minLines = 6,
            maxLines = 10,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Crimson,
                unfocusedBorderColor = BorderDim,
                cursorColor = Crimson,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${draftText.length} / 600 chars",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.End)
        )

        Button(
            onClick = {
                val trimmed = draftText.trim()
                if (trimmed.isNotEmpty()) {
                    val encoded = Base64.encodeToString(
                        trimmed.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                    generatedCode = "BURN1:$encoded"
                }
            },
            enabled = draftText.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Crimson),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "GENERATE BURN CODE",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = {
                val trimmed = draftText.trim()
                if (trimmed.isNotEmpty()) {
                    draftText = ""
                    generatedCode = null
                    onPreviewInReader(trimmed)
                }
            },
            enabled = draftText.isNotBlank(),
            border = BorderStroke(1.dp, BorderDim),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "OPEN DIRECTLY IN SECURE READER",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }

        generatedCode?.let { code ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, Crimson),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PHASE 1 PREVIEW CODE (ENCRYPTION IN PHASE 2)",
                        color = Crimson,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = code,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 4,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            Toast.makeText(context, "Burn code copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BorderDim),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "COPY CODE",
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenNoteScreen(
    onBack: () -> Unit,
    onOpenDecoded: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var inputCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(title = "OPEN BURN CODE", onBack = onBack)

        OutlinedTextField(
            value = inputCode,
            onValueChange = {
                inputCode = it
                errorMsg = null
            },
            placeholder = {
                Text(
                    text = "Paste BURN1:... code here",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            minLines = 4,
            maxLines = 8,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = Crimson,
                unfocusedBorderColor = BorderDim,
                cursorColor = Crimson,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = {
                val pasted = clipboard.getText()?.text
                if (!pasted.isNullOrBlank()) {
                    inputCode = pasted.trim()
                    errorMsg = null
                }
            },
            border = BorderStroke(1.dp, BorderDim),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text(
                text = "PASTE FROM CLIPBOARD",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }

        errorMsg?.let { err ->
            Text(
                text = err,
                color = Crimson,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Button(
            onClick = {
                val cleaned = inputCode.trim()
                if (!cleaned.startsWith("BURN1:")) {
                    errorMsg = "Invalid format. Code must start with BURN1:"
                } else {
                    try {
                        val payload = cleaned.removePrefix("BURN1:")
                        val decoded = String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
                        inputCode = ""
                        onOpenDecoded(decoded)
                    } catch (_: Exception) {
                        errorMsg = "Corrupted burn code."
                    }
                }
            },
            enabled = inputCode.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Crimson),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "UNLOCK & START 30s TIMER",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Warning: Screen brightness will drop to minimum and leaving the app immediately destroys the note.",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(1.dp, BorderDim),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "< BACK",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        Text(
            text = title,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}
