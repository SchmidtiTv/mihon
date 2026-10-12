package eu.kanade.tachiyomi.data.track

import tachiyomi.domain.track.model.Track

interface CoverProvidingTracker {

    // Best quality first
    suspend fun getCovers(track: Track): List<String>
}
