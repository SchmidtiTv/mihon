package eu.kanade.tachiyomi.data.cover

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
class BannerLibrary(context: Context) : ArtworkLibrary(context, BANNER_LIBRARY_DIR)

private const val BANNER_LIBRARY_DIR = "banners/library"
