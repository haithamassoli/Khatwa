package com.haithamassoli.khatwa

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val settings by vm.store.settings.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Text("خطوة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            SceneCard(
                walk = false,
                icon = Icons.Filled.DirectionsCar,
                caption = "من الباب: السيارة تلفّ حول الجزيرة حتى أقرب استدارة",
                description = "رسم توضيحي: السيارة تلتقطك من جهتك ثم تلف مسافة طويلة حول الجزيرة الوسطية لتعود باتجاه وجهتك",
            )
            SceneCard(
                walk = true,
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                caption = "مع خطوة: تعبر الجسر للجهة الأخرى والسيارة تمشي مباشرة",
                description = "رسم توضيحي: تمشي عبر جسر المشاة إلى الجهة الأخرى من الشارع وتركب السيارة باتجاه وجهتك مباشرة",
            )
            Text(
                "امشِ خطوتين، ووفّر دقائق",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "نقترح نقطة ركوب أو نزول قريبة تختصر على السيارة اللفّات والإشارات.",
                    "أنت تحدد أقصى وقت مشي، ونقارن لك الوقت الكلي قبل أن تطلب.",
                    "ثم اطلب الرحلة من تطبيقك المعتاد إلى النقطة المقترحة.",
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            NightWalkSwitch(settings.noWalkAtNight) { v -> vm.updateSettings { it.copy(noWalkAtNight = v) } }
            Button(
                onClick = { vm.updateSettings { it.copy(onboarded = true) } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("ابدأ") }
        }
    }
}

@Composable
private fun SceneCard(walk: Boolean, icon: ImageVector, caption: String, description: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoadScene(walk, Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = description })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = if (walk) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(caption, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A divided road seen from above: either a long U-turn loop for the car, or a short walk over a footbridge. */
@Composable
private fun RoadScene(walk: Boolean, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val road = c.surfaceVariant
    val median = c.secondaryContainer
    val lane = c.outline
    val car = c.primary
    val window = c.onPrimary
    val person = c.tertiary
    val bridge = c.onSurfaceVariant
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val topY = h * 0.33f
        val bottomY = h * 0.67f
        drawRect(road, topLeft = Offset(0f, h * 0.2f), size = Size(w, h * 0.6f))
        // Median island with a U-turn gap at the far end.
        drawRoundRect(median, topLeft = Offset(w * 0.16f, h * 0.46f), size = Size(w * 0.84f, h * 0.08f), cornerRadius = CornerRadius(h * 0.04f))
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))
        drawLine(lane.copy(alpha = 0.5f), Offset(0f, h * 0.2f), Offset(w, h * 0.2f), 1.dp.toPx())
        drawLine(lane.copy(alpha = 0.5f), Offset(0f, h * 0.8f), Offset(w, h * 0.8f), 1.dp.toPx())
        val px = w * 0.62f
        val end = Offset(w * 0.95f, topY)
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        if (!walk) {
            val r = h * 0.17f
            val path = Path().apply {
                moveTo(px, bottomY)
                lineTo(w * 0.1f + r * 0.2f, bottomY)
                arcTo(Rect(center = Offset(w * 0.1f + r * 0.2f, h * 0.5f), radius = r), 90f, 180f, false)
                lineTo(end.x, end.y)
            }
            drawPath(path, car, style = stroke)
            drawArrowHead(end, car)
            drawCar(Offset(px, bottomY), car, window)
            drawPerson(Offset(px, h * 0.9f), person)
        } else {
            val bw = 18.dp.toPx()
            drawRect(bridge.copy(alpha = 0.18f), topLeft = Offset(px - bw / 2, h * 0.1f), size = Size(bw, h * 0.8f))
            drawRect(bridge.copy(alpha = 0.6f), topLeft = Offset(px - bw / 2, h * 0.1f), size = Size(bw, h * 0.8f), style = Stroke(1.5.dp.toPx()))
            drawLine(person, Offset(px, h * 0.86f), Offset(px, h * 0.16f), 3.dp.toPx(), StrokeCap.Round, dash)
            val carStart = Offset(px + 26.dp.toPx(), topY)
            drawLine(car, carStart, end, 4.dp.toPx(), StrokeCap.Round)
            drawArrowHead(end, car)
            drawCar(carStart, car, window)
            drawPerson(Offset(px, h * 0.15f), person)
        }
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, color: Color) {
    val l = 9.dp.toPx()
    val path = Path().apply {
        moveTo(tip.x + l * 0.3f, tip.y)
        lineTo(tip.x - l * 0.7f, tip.y - l * 0.6f)
        lineTo(tip.x - l * 0.7f, tip.y + l * 0.6f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawCar(center: Offset, body: Color, window: Color) {
    val cw = 24.dp.toPx()
    val ch = 13.dp.toPx()
    drawRoundRect(body, topLeft = Offset(center.x - cw / 2, center.y - ch / 2), size = Size(cw, ch), cornerRadius = CornerRadius(4.dp.toPx()))
    drawRoundRect(window, topLeft = Offset(center.x + cw * 0.1f, center.y - ch * 0.3f), size = Size(cw * 0.25f, ch * 0.6f), cornerRadius = CornerRadius(2.dp.toPx()))
}

private fun DrawScope.drawPerson(feet: Offset, color: Color) {
    val r = 3.dp.toPx()
    drawCircle(color, r, Offset(feet.x, feet.y - 9.dp.toPx()))
    drawLine(color, Offset(feet.x, feet.y - 5.dp.toPx()), Offset(feet.x, feet.y + 2.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
}
