package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class BackupArtwork(
    @ProtoNumber(1) var image: ByteArray,
    @ProtoNumber(2) var label: String? = null,
)
