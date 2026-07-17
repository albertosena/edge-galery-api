/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.R

val appFontFamily =
  FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_extralight, FontWeight.ExtraLight),
    Font(R.font.nunito_light, FontWeight.Light),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
  )

val baseline = Typography()

val AppTypography =
  Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = appFontFamily, fontSize = 64.sp, lineHeight = 72.sp),
    displayMedium = baseline.displayMedium.copy(fontFamily = appFontFamily, fontSize = 51.sp, lineHeight = 59.sp),
    displaySmall = baseline.displaySmall.copy(fontFamily = appFontFamily, fontSize = 41.sp, lineHeight = 49.sp),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = appFontFamily, fontSize = 36.sp, lineHeight = 44.sp),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = appFontFamily, fontSize = 32.sp, lineHeight = 40.sp),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = appFontFamily, fontSize = 27.sp, lineHeight = 35.sp),
    titleLarge = baseline.titleLarge.copy(fontFamily = appFontFamily, fontSize = 25.sp, lineHeight = 32.sp),
    titleMedium = baseline.titleMedium.copy(fontFamily = appFontFamily, fontSize = 19.sp, lineHeight = 27.sp),
    titleSmall = baseline.titleSmall.copy(fontFamily = appFontFamily, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = appFontFamily, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = appFontFamily, fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = baseline.bodySmall.copy(fontFamily = appFontFamily, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = baseline.labelLarge.copy(fontFamily = appFontFamily, fontSize = 16.sp, lineHeight = 22.sp),
    labelMedium = baseline.labelMedium.copy(fontFamily = appFontFamily, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = baseline.labelSmall.copy(fontFamily = appFontFamily, fontSize = 13.sp, lineHeight = 18.sp),
  )

val titleMediumNarrow =
  baseline.titleMedium.copy(fontFamily = appFontFamily, letterSpacing = 0.0.sp)

val titleSmaller =
  baseline.titleSmall.copy(
    fontFamily = appFontFamily,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
  )

val labelSmallNarrow = baseline.labelSmall.copy(fontFamily = appFontFamily, letterSpacing = 0.0.sp)

val labelSmallNarrowMedium =
  baseline.labelSmall.copy(
    fontFamily = appFontFamily,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.0.sp,
  )

val bodySmallNarrow = baseline.bodySmall.copy(fontFamily = appFontFamily, letterSpacing = 0.0.sp)

val bodySmallMediumNarrow =
  baseline.bodySmall.copy(fontFamily = appFontFamily, letterSpacing = 0.0.sp, fontSize = 14.sp)

val bodySmallMediumNarrowBold =
  baseline.bodySmall.copy(
    fontFamily = appFontFamily,
    letterSpacing = 0.0.sp,
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold,
  )

val homePageTitleStyle =
  baseline.displayMedium.copy(
    fontFamily = appFontFamily,
    fontSize = 48.sp,
    lineHeight = 48.sp,
    letterSpacing = -1.sp,
    fontWeight = FontWeight.Medium,
  )

val bodyLargeNarrow = baseline.bodyLarge.copy(letterSpacing = 0.2.sp)
val bodyMediumMedium = baseline.bodyMedium.copy(fontWeight = FontWeight.Medium)

val headlineLargeMedium = baseline.headlineLarge.copy(fontWeight = FontWeight.Medium)

val emptyStateTitle = baseline.headlineSmall.copy(fontSize = 37.sp, lineHeight = 50.sp)
val emptyStateContent = baseline.headlineSmall.copy(fontSize = 16.sp, lineHeight = 22.sp)
