package app.pengwa.identity

/** Stable storage values; installations with an existing card predate onboarding. */
enum class OnboardingStep(val storedValue: Int) {
    CreateCard(0), PreviewCard(1), LearnExchange(2), Complete(3);

    companion object {
        fun restore(hasIdentity: Boolean, storedValue: Int?): OnboardingStep = when {
            !hasIdentity -> CreateCard
            storedValue == null -> Complete
            else -> entries.firstOrNull { it.storedValue == storedValue } ?: PreviewCard
        }
    }
}
