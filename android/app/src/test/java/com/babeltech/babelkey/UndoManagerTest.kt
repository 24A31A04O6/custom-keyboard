package com.babeltech.babelkey

import org.junit.Test
import org.junit.Assert.*
import com.babeltech.babelkey.core.suggestion.UndoManager

class UndoManagerTest {
    @Test fun undoRevertsCorrection() {
        val u = UndoManager()
        u.record("bagundhi","bagundi")
        assertTrue(u.canUndo)
        assertEquals("bagundhi", u.undo())
        assertFalse(u.canUndo)
    }
    @Test fun undoNoOpWhenSame() {
        val u = UndoManager()
        u.record("hello","hello")
        assertFalse(u.canUndo)
        assertNull(u.undo())
    }
    @Test fun undoConsumedOnce() {
        val u = UndoManager()
        u.record("teh","the")
        assertEquals("teh", u.undo())
        assertNull(u.undo())
    }
}
