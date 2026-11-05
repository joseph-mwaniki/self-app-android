package com.example.selfapp.ui.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.selfapp.ui.theme.Black
import com.example.selfapp.ui.theme.NeonBlue
import com.example.selfapp.ui.theme.NeonRed
import com.example.selfapp.ui.theme.SelfAppTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class LockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra("EXTRA_PACKAGE_NAME") ?: "distracting_app"
        val lockedUntil = intent.getLongExtra("EXTRA_LOCKED_UNTIL", 0L)

        setContent {
            SelfAppTheme {
                LockOverlayScreen(
                    packageName = packageName,
                    lockedUntil = lockedUntil,
                    onGoHome = {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LockOverlayScreen(
    packageName: String,
    lockedUntil: Long,
    onGoHome: () -> Unit
) {
    var timeLeft by remember { mutableStateOf(calculateTimeLeft(lockedUntil)) }

    LaunchedEffect(lockedUntil) {
        while (true) {
            timeLeft = calculateTimeLeft(lockedUntil)
            delay(1000)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(lockedUntil) { dateFormatter.format(Date(lockedUntil)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Neon Red Shield Border
        Box(
            modifier = Modifier
                .size(120.dp)
                .border(2.dp, Brush.linearGradient(listOf(NeonRed, Color.Magenta)), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🔒",
                fontSize = 50.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "ACCESS DENIED",
            color = NeonRed,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "The application \"$packageName\" is currently locked as part of your self-discipline purge.",
            color = Color.LightGray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Countdown Timer Block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                .border(1.dp, NeonBlue, RoundedCornerShape(8.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "LOCK EXPIRY COUNTDOWN",
                    color = NeonBlue,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = timeLeft,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Locked Until: $formattedDate",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGoHome,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(2.dp, NeonBlue, RoundedCornerShape(25.dp))
        ) {
            Text(
                text = "RETURN TO HOME SCREEN",
                color = NeonBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

fun calculateTimeLeft(expiry: Long): String {
    val diff = expiry - System.currentTimeMillis()
    if (diff <= 0) return "00:00:00:00"

    val seconds = (diff / 1000) % 60
    val minutes = (diff / (1000 * 60)) % 60
    val hours = (diff / (1000 * 60 * 60)) % 24
    val days = diff / (1000 * 60 * 60 * 24)

    return String.format("%02d d : %02d h : %02d m : %02d s", days, hours, minutes, seconds)
}
