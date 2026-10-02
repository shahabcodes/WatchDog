package com.fms.parentalcontrol.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing and shape tokens tuned to iOS metrics. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 32.dp

    /** Standard leading/trailing inset for grouped lists. */
    val listHorizontal = 16.dp
    val rowMinHeight = 44.dp
}

object Shapes {
    /** iOS grouped-card corner radius. */
    val card = RoundedCornerShape(10.dp)
    val button = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(10.dp)
}
