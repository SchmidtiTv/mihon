package eu.kanade.tachiyomi.data.track

import tachiyomi.domain.track.model.Track

interface BannerProvidingTracker {

    // Best quality first
    suspend fun getBanners(track: Track): List<String>
}
