package io.legado.app.utils

fun formatReadDuration(mss: Long): String {
    val totalSeconds = mss / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "${hours}小时${minutes}分钟"
        minutes > 0 -> "${minutes}分钟${seconds}秒"
        else -> "${seconds}秒"
    }
}
