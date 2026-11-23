package com.example

import com.example.data.model.CurrencyCode
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun multiCurrencyFormatting_isAccurate() {
        assertEquals("$120.00", CurrencyCode.USD.formatFromUsdCents(12000L))
        assertEquals("€110.40", CurrencyCode.EUR.formatFromUsdCents(12000L))
        assertEquals("¥18,480", CurrencyCode.JPY.formatFromUsdCents(12000L))
    }
}
