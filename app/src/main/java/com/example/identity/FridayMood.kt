package com.example.identity

/**
 * FRIDAY Interaction Moods:
 * - CALM: Composed, steady, and ready.
 * - HAPPY: Upbeat, pleased with smooth execution.
 * - FOCUSED: Engaged in multi-step or precise tasks.
 * - CONCERNED: Noticed a security anomaly or suspicious environment.
 * - SERIOUS: Enforcing strict security policy or blocking hazardous operations.
 * - APOLOGETIC: When an action fails or input is misunderstood.
 * - PLAYFUL: Witty, natural, and confident.
 */
enum class FridayMood(val label: String) {
    CALM("Calm"),
    HAPPY("Happy"),
    FOCUSED("Focused"),
    CONCERNED("Concerned"),
    SERIOUS("Serious"),
    APOLOGETIC("Apologetic"),
    PLAYFUL("Playful")
}
