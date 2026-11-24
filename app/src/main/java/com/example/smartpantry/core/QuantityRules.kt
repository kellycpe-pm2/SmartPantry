package com.example.smartpantry.core

internal val measuredUnits = setOf("g", "kg", "mL", "L")
internal fun validShoppingQuantity(value: Double, unit: String): Boolean =
    value.isFinite() && value > 0 && value <= 999 &&
            (if (unit in measuredUnits) java.math.BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 3
            else unit in setOf("pcs", "bag", "carton") && value % 1.0 == 0.0)
internal fun shoppingQuantityHint(unit: String): String = if (unit in measuredUnits)
    "More than 0, up to 999; up to 3 decimal places."
else "Whole number from 1 to 999."