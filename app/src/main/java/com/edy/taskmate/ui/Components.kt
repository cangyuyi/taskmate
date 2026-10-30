package com.edy.taskmate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edy.taskmate.data.Dates
import com.edy.taskmate.data.RepeatRule
import com.edy.taskmate.data.SectionTint
import com.edy.taskmate.data.Task
import com.edy.taskmate.data.TodayBuckets
import kotlin.math.roundToInt

/** 一组圆角卡片（对应 iOS 的 inset grouped list）。 */
@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = cardColor(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
fun RowDivider(startIndent: Dp = 44.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(0.6.dp)
            .background(hairline())
    )
}

@Composable
fun SectionHeader(
    title: String,
    tint: SectionTint = SectionTint.NONE,
    count: Int? = null,
    trailing: String? = null
) {
    val resolved = when (tint) {
        SectionTint.RED -> redColor()
        SectionTint.ACCENT -> accentColor()
        SectionTint.NONE -> secondaryLabel()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tint != SectionTint.NONE) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(resolved))
            Spacer(Modifier.width(6.dp))
        }
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = resolved)
        if (count != null) {
            Spacer(Modifier.width(5.dp))
            Text("$count", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tertiaryLabel())
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, fontSize = 12.sp, color = tertiaryLabel())
        }
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp = 58.dp, strokeWidth: Dp = 6.dp, color: Color = Color.White) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = color.copy(alpha = 0.24f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
            color = color,
            fontSize = (size.value * 0.25f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun HeroCard(buckets: TodayBuckets, onAdd: () -> Unit) {
    val headline = when {
        buckets.total == 0 -> "今天还没有安排"
        buckets.remaining == 0 -> "今天的任务全部完成"
        else -> "还有 ${buckets.remaining} 件事待完成"
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(HeroGradient)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Dates.fullText(System.currentTimeMillis()),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        headline,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 26.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                ProgressRing(buckets.progress, size = 60.dp, strokeWidth = 6.dp)
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroStat("${buckets.remaining}", "待处理")
                Spacer(Modifier.width(20.dp))
                HeroStat("${buckets.doneCount}", "已完成")
                Spacer(Modifier.width(20.dp))
                HeroStat("${buckets.overdue.size}", "逾期")
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onAdd() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加任务", tint = Color(0xFF4250DB), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
    }
}

@Composable
fun CheckCircle(done: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (done) {
                    Modifier.background(accentColor())
                } else {
                    Modifier.border(1.6.dp, tertiaryLabel(), CircleShape)
                }
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Icon(Icons.Filled.Check, contentDescription = "已完成", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun TaskRow(task: Task, showDue: Boolean = true, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(task.done, onToggle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontSize = 17.sp,
                fontWeight = if (task.priority == Task.PRIORITY_HIGH && !task.done) FontWeight.SemiBold else FontWeight.Normal,
                color = if (task.done) secondaryLabel() else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp
            )
            if (!task.done) {
                Spacer(Modifier.height(3.dp))
                TaskMeta(task, showDue)
            }
        }
        if (task.isOverdue) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(redColor()))
        }
    }
}

@Composable
private fun TaskMeta(task: Task, showDue: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val overdued = task.isOverdue
        if (showDue && task.dueAt != null) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = if (overdued) redColor() else secondaryLabel()
            )
            Spacer(Modifier.width(3.dp))
            Text(
                dueText(task.dueAt),
                fontSize = 12.sp,
                color = if (overdued) redColor() else secondaryLabel()
            )
        }
        if (task.remindAt != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = secondaryLabel()
            )
            Spacer(Modifier.width(3.dp))
            Text(Dates.timeText(task.remindAt), fontSize = 12.sp, color = secondaryLabel())
        }
        if (task.repeat != RepeatRule.NONE) {
            Spacer(Modifier.width(8.dp))
            Text(task.repeat.label, fontSize = 12.sp, color = secondaryLabel())
        }
        if (task.priority == Task.PRIORITY_HIGH) {
            Spacer(Modifier.width(8.dp))
            Text("重要", fontSize = 12.sp, color = orangeColor())
        }
    }
}

private fun dueText(dueAt: Long?): String {
    if (dueAt == null) return ""
    return when {
        Dates.isToday(dueAt) -> Dates.timeText(dueAt)
        Dates.isTomorrow(dueAt) -> "明天 " + Dates.timeText(dueAt)
        else -> Dates.monthDayText(dueAt) + " " + Dates.timeText(dueAt)
    }
}

@Composable
fun AddTaskRow(text: String = "添加任务", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = accentColor(), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = accentColor(), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun EmptyState(title: String, message: String, actionTitle: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(HeroGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(message, fontSize = 13.sp, color = secondaryLabel(), lineHeight = 19.sp)
        if (actionTitle != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accentColor().copy(alpha = 0.12f))
                    .clickable { onAction() }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(actionTitle, color = accentColor(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun WeekStrip(days: List<Long>, selected: Long, remainingCounts: Map<Long, Int>, onSelect: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        days.forEach { day ->
            val isSelected = Dates.isSameDay(day, selected)
            val isToday = Dates.isToday(day)
            val count = remainingCounts[day] ?: 0

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (isSelected) Modifier.background(accentColor()) else Modifier)
                    .clickable { onSelect(day) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    Dates.weekdayShort(day),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isSelected -> Color.White.copy(alpha = 0.85f)
                        isToday -> accentColor()
                        else -> secondaryLabel()
                    }
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    Dates.dayNumberText(day),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                count <= 0 -> Color.Transparent
                                isSelected -> Color.White.copy(alpha = 0.9f)
                                else -> accentColor()
                            }
                        )
                )
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accentColor() else subtleFill())
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingsRow(title: String, value: String? = null, valueColor: Color? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(value, fontSize = 14.sp, color = valueColor ?: secondaryLabel())
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun ThinProgressBar(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(subtleFill())
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(accentColor())
        )
    }
}