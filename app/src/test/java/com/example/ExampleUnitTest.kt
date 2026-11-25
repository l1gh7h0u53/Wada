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

  @Test
  fun exactColorAndRotationNormalization_isConsistent() {
    fun normalizeRotation(deg: Int): Int = ((deg % 360) + 360) % 360

    assertEquals(0, normalizeRotation(0))
    assertEquals(90, normalizeRotation(90))
    assertEquals(270, normalizeRotation(-90))
    assertEquals(0, normalizeRotation(360))
    assertEquals(180, normalizeRotation(540))

    val parsed = com.example.util.ColorUtils.parseSafeColor("#4e6d53")
    assertEquals("#4E6D53", com.example.util.ColorUtils.toHexString(parsed))

    val shorthand = com.example.util.ColorUtils.parseSafeColor("abc")
    assertEquals("#AABBCC", com.example.util.ColorUtils.toHexString(shorthand))
  }

  @Test
  fun primaryColorGroupClassifier_mapsExpectedGroups() {
    assertEquals(com.example.util.PrimaryColorGroup.BLUE, com.example.util.ColorGroupClassifier.classifyHex("#1E2C3D"))
    assertEquals(com.example.util.PrimaryColorGroup.GREEN, com.example.util.ColorGroupClassifier.classifyHex("#4E6D53"))
    assertEquals(com.example.util.PrimaryColorGroup.RED, com.example.util.ColorGroupClassifier.classifyHex("#C9303E"))
    assertEquals(com.example.util.PrimaryColorGroup.BROWN, com.example.util.ColorGroupClassifier.classifyHex("#7A5238"))
    assertEquals(com.example.util.PrimaryColorGroup.BLACK, com.example.util.ColorGroupClassifier.classifyHex("#151414"))
    assertEquals(com.example.util.PrimaryColorGroup.WHITE, com.example.util.ColorGroupClassifier.classifyHex("#F5F3EE"))
    assertEquals(com.example.util.PrimaryColorGroup.GRAY, com.example.util.ColorGroupClassifier.classifyHex("#808080"))
    assertEquals(com.example.util.PrimaryColorGroup.YELLOW, com.example.util.ColorGroupClassifier.classifyHex("#E6B830"))
  }

  @Test
  fun weatherOutfitAdvisor_suggestsWeatherAwareMatches() {
    val clothes = listOf(
      com.example.data.ClothingItem(id = 1, filePath = "", category = "Tops", hexColor = "#F4F0E8", colorGroup = "White"),
      com.example.data.ClothingItem(id = 2, filePath = "", category = "Tops", hexColor = "#1E2C3D", colorGroup = "Blue"),
      com.example.data.ClothingItem(id = 3, filePath = "", category = "Bottoms", hexColor = "#3B3633", colorGroup = "Black")
    )
    val combo = WadaCombination(
      id = 1,
      name = "Wada Test",
      colors = listOf("#1E2C3D", "#F4F0E8"),
      part = 1,
      description = "Test"
    )

    val coldRain = com.example.util.WeatherInfo(
      temperatureCelsius = 7.0,
      condition = com.example.util.WeatherService.mapWmoCodeToCondition(61),
      windSpeedKmh = 14.0
    )
    val warmClear = com.example.util.WeatherInfo(
      temperatureCelsius = 28.0,
      condition = com.example.util.WeatherService.mapWmoCodeToCondition(0),
      windSpeedKmh = 6.0
    )

    val coldMatches = com.example.util.WeatherOutfitAdvisor.suggestMatches(clothes, combo, coldRain)
    val warmMatches = com.example.util.WeatherOutfitAdvisor.suggestMatches(clothes, combo, warmClear)

    assertEquals(2, coldMatches.size)
    assertEquals(2, coldMatches.first { it.item.category == "Tops" }.item.id)
    assertEquals(1, warmMatches.first { it.item.category == "Tops" }.item.id)
    assertTrue(com.example.util.WeatherOutfitAdvisor.buildAdvice(coldRain).contains("Rain"))
  }

  @Test
  fun weatherOutfitAdvisor_respectsThermalTypeToggle() {
    // Even if a light-colored cream sweater is WARM and a dark navy t-shirt is LIGHT,
    // the weather advisor must pick WARM in cold weather and LIGHT in hot weather.
    val clothes = listOf(
      com.example.data.ClothingItem(
        id = 10,
        filePath = "",
        category = "Tops",
        hexColor = "#F4F0E8",
        colorGroup = "White",
        thermalType = "WARM"
      ),
      com.example.data.ClothingItem(
        id = 11,
        filePath = "",
        category = "Tops",
        hexColor = "#1E2C3D",
        colorGroup = "Blue",
        thermalType = "LIGHT"
      )
    )
    val combo = WadaCombination(
      id = 1,
      name = "Wada Contrast",
      colors = listOf("#1E2C3D", "#F4F0E8"),
      part = 1,
      description = "Test"
    )

    val freezing = com.example.util.WeatherInfo(
      temperatureCelsius = 4.0,
      condition = com.example.util.WeatherCondition.SNOW,
      windSpeedKmh = 18.0
    )
    val normalMild = com.example.util.WeatherInfo(
      temperatureCelsius = 18.0,
      condition = com.example.util.WeatherCondition.PARTLY_CLOUDY,
      windSpeedKmh = 10.0
    )
    val hotSummer = com.example.util.WeatherInfo(
      temperatureCelsius = 30.0,
      condition = com.example.util.WeatherCondition.CLEAR,
      windSpeedKmh = 5.0
    )

    val coldPick = com.example.util.WeatherOutfitAdvisor.suggestMatches(clothes, combo, freezing).first()
    val normalPick = com.example.util.WeatherOutfitAdvisor.suggestMatches(clothes, combo, normalMild).first()
    val hotPick = com.example.util.WeatherOutfitAdvisor.suggestMatches(clothes, combo, hotSummer).first()

    assertEquals(10, coldPick.item.id)
    assertTrue(coldPick.reason.contains("Warm"))
    assertEquals(11, normalPick.item.id)
    assertTrue(normalPick.reason.contains("Light"))
    assertEquals(11, hotPick.item.id)
    assertTrue(hotPick.reason.contains("Light"))
  }
}
