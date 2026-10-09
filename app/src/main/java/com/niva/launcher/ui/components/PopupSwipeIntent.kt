package com.niva.launcher.ui.components

/** Direction changes control the destination, never the animation's progress. */
internal class PopupSwipeIntent(private val reversalSlop: Float) {
    var revealed = false
        private set
    var expanded = false
        private set
    private var reverseTravel = 0f

    // The row recognizer has already crossed Android's horizontal touch slop.
    fun drag(amount: Float): Boolean? {
        if (!revealed) {
            if (amount <= 0f) return null
            revealed = true
            expanded = true
            return true
        }
        val reversal = if (expanded) -amount else amount
        reverseTravel = (reverseTravel + reversal).coerceAtLeast(0f)
        if (reverseTravel < reversalSlop) return null
        reverseTravel = 0f
        expanded = !expanded
        return expanded
    }

    fun reset() {
        revealed = false
        expanded = false
        reverseTravel = 0f
    }
}
