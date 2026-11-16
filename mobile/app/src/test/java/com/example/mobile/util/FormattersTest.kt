package com.example.mobile.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {
    @Test fun parseAmount_acceptsCommaAndDot() {
        assertEquals("10.50", parseAmountInput("10,5"))
        assertEquals("10.50", parseAmountInput("10.50"))
        assertEquals("7.00", parseAmountInput(" 7 "))
    }

    @Test fun parseAmount_rejectsInvalidValues() {
        assertNull(parseAmountInput(""))
        assertNull(parseAmountInput("0"))
        assertNull(parseAmountInput("0,00"))
        assertNull(parseAmountInput("-5"))
        assertNull(parseAmountInput("1,234"))
        assertNull(parseAmountInput("abc"))
    }

    @Test fun normalizePhone_addsBrazilCountryCode() {
        assertEquals("+5511999999999", normalizePhone("(11) 99999-9999"))
        assertEquals("+5511999999999", normalizePhone("+55 11 99999-9999"))
        assertEquals("", normalizePhone(""))
    }
}
