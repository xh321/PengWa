package app.pengwa.identity

import org.junit.Test
import kotlin.test.assertEquals

class OnboardingStepTest {
    @Test fun freshInstallStartsWithCardCreation() {
        assertEquals(OnboardingStep.CreateCard, OnboardingStep.restore(false, null))
    }

    @Test fun existingUserWithoutProgressKeepsAccessToHome() {
        assertEquals(OnboardingStep.Complete, OnboardingStep.restore(true, null))
    }

    @Test fun savedCardResumesAtEachPersistedStep() {
        for (step in OnboardingStep.entries) {
            assertEquals(step, OnboardingStep.restore(true, step.storedValue))
        }
    }

    @Test fun missingCardCannotSkipCreationEvenWithCompletedProgress() {
        for (stored in listOf(1, 2, 3, -1, 99)) {
            assertEquals(OnboardingStep.CreateCard, OnboardingStep.restore(false, stored))
        }
    }

    @Test fun unknownProgressRecoversToSavedCardPreview() {
        for (stored in listOf(-1, 4, Int.MAX_VALUE)) {
            assertEquals(OnboardingStep.PreviewCard, OnboardingStep.restore(true, stored))
        }
    }
}
