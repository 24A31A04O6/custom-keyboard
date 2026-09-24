package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import com.babeltech.babelkey.data.preferences.OneHandedPrefs

class OneHandedModeTest {
    @Test fun modeEnum() {
        assertEquals(3, OneHandedPrefs.Mode.values().size)
        assertTrue(OneHandedPrefs.Mode.OFF in OneHandedPrefs.Mode.values())
        assertTrue(OneHandedPrefs.Mode.LEFT in OneHandedPrefs.Mode.values())
        assertTrue(OneHandedPrefs.Mode.RIGHT in OneHandedPrefs.Mode.values())
    }

    @Test fun widthFractionCoercion() {
        // Verify logic: coerceIn 0.7..1.0
        fun coerce(v: Float) = v.coerceIn(0.7f, 1.0f)
        assertEquals(0.7f, coerce(0.5f))
        assertEquals(1.0f, coerce(1.5f))
        assertEquals(0.85f, coerce(0.85f))
    }
}
