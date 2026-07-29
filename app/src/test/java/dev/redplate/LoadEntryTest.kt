package dev.redplate

import dev.redplate.workout.SetLoggingUiState
import dev.redplate.workout.formatLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typing the load in. The rule the whole feature turns on: what the user types is what
 * gets logged, with nothing rounded, snapped or second-guessed on the way.
 */
class LoadEntryTest {

    @Test
    fun `the readout shows the load until something is typed`() {
        val idle = SetLoggingUiState(loadKg = 102.5)
        assertFalse(idle.isEnteringLoad)
        assertEquals("102.5", idle.loadDisplay)

        val typing = idle.copy(loadEntry = "37")
        assertTrue(typing.isEnteringLoad)
        assertEquals("37", typing.loadDisplay)
    }

    /** A half-typed "10." must survive as text; parsing it early would round it away. */
    @Test
    fun `a partial entry is representable`() {
        assertEquals("10.", SetLoggingUiState(loadEntry = "10.").loadDisplay)
    }

    @Test
    fun `an empty or malformed entry cannot be committed`() {
        assertFalse(SetLoggingUiState(loadEntry = "").canCommitLoadEntry)
        assertFalse(SetLoggingUiState(loadEntry = ".").canCommitLoadEntry)
        assertFalse("Nothing open, nothing to commit", SetLoggingUiState().canCommitLoadEntry)
        assertTrue(SetLoggingUiState(loadEntry = "0").canCommitLoadEntry)
        assertTrue(SetLoggingUiState(loadEntry = "37.5").canCommitLoadEntry)
    }

    @Test
    fun `the unit label defaults to kilograms and can be overridden`() {
        assertEquals("KG", SetLoggingUiState().loadUnitLabel)
        assertEquals("LEVEL", SetLoggingUiState(loadUnitLabel = "LEVEL").loadUnitLabel)
    }

    @Test
    fun `whole-number equipment hides the decimal point`() {
        assertFalse(SetLoggingUiState().loadIsWholeNumber)
        assertTrue(SetLoggingUiState(loadIsWholeNumber = true).loadIsWholeNumber)
    }

    /** A readout has to be legible from two metres; a trailing ".0" is noise. */
    @Test
    fun `the load formats without trailing zeros`() {
        assertEquals("100", formatLoad(100.0))
        assertEquals("102.5", formatLoad(102.5))
        assertEquals("7", formatLoad(7.0))
        assertEquals("1.25", formatLoad(1.25))
        assertEquals("0", formatLoad(0.0))
    }
}
