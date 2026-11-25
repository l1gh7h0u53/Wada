package com.example

import com.example.data.WadaCombination
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun dailyInspirationSeed_isConsistentPerDayAndVariesAcrossDays() {
    val combinations = (1..346).map { id ->
      WadaCombination(
        id = id,
        name = "Palette $id",
        colors = listOf("#1D2A44", "#E5BFB1"),
        part = 1,
        description = "Sample $id"
      )
    }

    fun pickFor(year: Int, dayOfYear: Int): WadaCombination {
      val seed = year * 1000L + dayOfYear
      return combinations[Random(seed).nextInt(combinations.size)]
    }

    val nov25First = pickFor(2026, 329)
    val nov25Second = pickFor(2026, 329)
    val nov26 = pickFor(2026, 330)

    assertEquals(nov25First.id, nov25Second.id)
    assertNotEquals(nov25First.id, nov26.id)
  }
}
