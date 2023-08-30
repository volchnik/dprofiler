package org.volchnik.dprofiler

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {

    @Test
    fun `WHEN calculating insert position for empty container THEN first position returned`() {
        val container = listOf<Long>()
        val result = calcInsertPosition(size = 0L, from = 0, to = container.size, container = container) { i -> get(i) }
        assertEquals(expected = 0, actual = result)
    }

    @Test
    fun `WHEN calculating insert position for single element container THEN correct pos return`() {
        val container = listOf(10L)

        val resultSmall =
            calcInsertPosition(size = 0L, from = 0, to = container.size, container = container) { i -> get(i) }
        assertEquals(expected = 1, actual = resultSmall)

        val resultBig =
            calcInsertPosition(size = 100L, from = 0, to = container.size, container = container) { i -> get(i) }
        assertEquals(expected = 0, actual = resultBig)
    }

    @Test
    fun `WHEN calculating insert position for multiple element container THEN correct pos return`() {
        val containerTwo = listOf(10L, 100L).sortedWith(reverseOrder())

        val resultTwoSmall =
            calcInsertPosition(size = 0L, from = 0, to = containerTwo.size, container = containerTwo) { i -> get(i) }
        assertEquals(expected = 2, actual = resultTwoSmall)

        val resultTwoMid =
            calcInsertPosition(size = 100L, from = 0, to = containerTwo.size, container = containerTwo) { i -> get(i) }
        assertEquals(expected = 1, actual = resultTwoMid)

        val resultTwoBig =
            calcInsertPosition(size = 101L, from = 0, to = containerTwo.size, container = containerTwo) { i -> get(i) }
        assertEquals(expected = 0, actual = resultTwoBig)

        val containerBig = LongArray(100) { 100L - it }
        val resultBigSmall =
            calcInsertPosition(size = 0L, from = 0, to = containerBig.size, container = containerBig) { i -> get(i) }
        assertEquals(expected = 100, actual = resultBigSmall)

        val resultBigMid =
            calcInsertPosition(size = 90, from = 0, to = containerBig.size, container = containerBig) { i -> get(i) }
        assertEquals(expected = 11, actual = resultBigMid)

        val resultBigBig =
            calcInsertPosition(size = 101L, from = 0, to = containerBig.size, container = containerBig) { i -> get(i) }
        assertEquals(expected = 0, actual = resultBigBig)
    }
}