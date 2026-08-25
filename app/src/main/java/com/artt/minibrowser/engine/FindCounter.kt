package com.artt.minibrowser.engine

fun formatFindCounter(current: Int, total: Int): String =
    if (current > 0 && total > 0) "$current/$total" else ""
