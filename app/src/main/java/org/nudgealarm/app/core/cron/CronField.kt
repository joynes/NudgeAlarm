package org.nudgealarm.app.core.cron

sealed class CronField {
    abstract fun matches(value: Int): Boolean
    abstract fun nextMatch(current: Int, max: Int): Int?

    data class Single(val value: Int) : CronField() {
        override fun matches(value: Int) = this.value == value
        override fun nextMatch(current: Int, max: Int): Int? =
            if (value >= current && value <= max) value else null
    }

    data class Range(val start: Int, val end: Int) : CronField() {
        override fun matches(value: Int) = value in start..end
        override fun nextMatch(current: Int, max: Int): Int? {
            val effectiveStart = maxOf(start, current)
            return if (effectiveStart <= end && effectiveStart <= max) effectiveStart else null
        }
    }

    data class List(val values: kotlin.collections.List<Int>) : CronField() {
        override fun matches(value: Int) = value in values
        override fun nextMatch(current: Int, max: Int): Int? =
            values.filter { it >= current && it <= max }.minOrNull()
    }

    data object Any : CronField() {
        override fun matches(value: Int) = true
        override fun nextMatch(current: Int, max: Int): Int? = current
    }

    /** Matches values at regular intervals: start, start+step, start+2*step, ... */
    data class Step(val start: Int, val end: Int, val step: Int) : CronField() {
        override fun matches(value: Int) = value in start..end && (value - start) % step == 0
        override fun nextMatch(current: Int, max: Int): Int? {
            var v = if (current <= start) start else {
                val offset = current - start
                val remainder = offset % step
                if (remainder == 0) current else current + (step - remainder)
            }
            return if (v <= end && v <= max) v else null
        }
    }

    companion object {
        fun parse(field: String, min: Int, max: Int): CronField {
            // Handle step syntax first: */N or N-M/S
            if (field.contains("/")) {
                val slashParts = field.split("/")
                if (slashParts.size != 2) throw IllegalArgumentException("Invalid step: $field")
                val stepVal = slashParts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid step value: ${slashParts[1]}")
                if (stepVal <= 0) throw IllegalArgumentException("Step must be positive: $field")
                val (start, end) = when {
                    slashParts[0] == "*" -> min to max
                    slashParts[0].contains("-") -> {
                        val rangeParts = slashParts[0].split("-")
                        val s = rangeParts[0].toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid range start: ${rangeParts[0]}")
                        val e = rangeParts[1].toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid range end: ${rangeParts[1]}")
                        s to e
                    }
                    else -> {
                        val s = slashParts[0].toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid start: ${slashParts[0]}")
                        s to max
                    }
                }
                if (start < min || end > max) throw IllegalArgumentException("Step range out of bounds: $field")
                return Step(start, end, stepVal)
            }

            return when {
                field == "*" -> Any
                field.contains("-") && !field.contains(",") -> {
                    val parts = field.split("-")
                    if (parts.size != 2) throw IllegalArgumentException("Invalid range: $field")
                    val start = parts[0].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid range start: ${parts[0]}")
                    val end = parts[1].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid range end: ${parts[1]}")
                    if (start < min || end > max || start > end) {
                        throw IllegalArgumentException("Range out of bounds: $field")
                    }
                    Range(start, end)
                }
                field.contains(",") -> {
                    val values = field.split(",").flatMap { part ->
                        if (part.contains("-")) {
                            val rangeParts = part.split("-")
                            val start = rangeParts[0].toIntOrNull()
                                ?: throw IllegalArgumentException("Invalid value: ${rangeParts[0]}")
                            val end = rangeParts[1].toIntOrNull()
                                ?: throw IllegalArgumentException("Invalid value: ${rangeParts[1]}")
                            (start..end).toList()
                        } else {
                            val value = part.toIntOrNull()
                                ?: throw IllegalArgumentException("Invalid value: $part")
                            listOf(value)
                        }
                    }
                    if (values.any { it < min || it > max }) {
                        throw IllegalArgumentException("Values out of bounds: $field")
                    }
                    List(values.sorted())
                }
                else -> {
                    val value = field.toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid value: $field")
                    if (value < min || value > max) {
                        throw IllegalArgumentException("Value out of bounds: $field")
                    }
                    Single(value)
                }
            }
        }
    }
}
