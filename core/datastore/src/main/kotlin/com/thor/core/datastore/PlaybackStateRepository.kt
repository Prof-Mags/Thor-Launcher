package com.thor.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.thor.core.model.PendingPlaySession
import com.thor.core.model.PlaybackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes this store from the settings one at the injection site. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackStore

/** Reads and writes [PlaybackState] as a small JSON document. */
class PlaybackStateSerializer @Inject constructor() : Serializer<PlaybackState> {

    override val defaultValue: PlaybackState = PlaybackState.EMPTY

    override suspend fun readFrom(input: InputStream): PlaybackState = try {
        json.decodeFromString(
            deserializer = PlaybackState.serializer(),
            string = input.readBytes().decodeToString(),
        )
    } catch (e: SerializationException) {
        throw CorruptionException("Unable to read THOR playback state", e)
    }

    override suspend fun writeTo(t: PlaybackState, output: OutputStream) {
        output.write(json.encodeToString(PlaybackState.serializer(), t).encodeToByteArray())
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}

/**
 * The open play session, if there is one.
 *
 * Its own store rather than a field on the settings document, because settings
 * are exported and restored as the user's configuration and an in-flight session
 * is machine state. Keeping them apart also means a settings write cannot lose a
 * session, and a session write cannot churn the settings file.
 */
@Singleton
class PlaybackStateRepository @Inject constructor(
    @PlaybackStore private val store: DataStore<PlaybackState>,
) {

    val state: Flow<PlaybackState> = store.data

    /** Opens a session, replacing any that was already open. */
    suspend fun begin(entryId: String, startedAtEpochMs: Long) {
        store.updateData {
            it.copy(
                pending = PendingPlaySession(
                    entryId = entryId,
                    startedAtEpochMs = startedAtEpochMs,
                ),
            )
        }
    }

    /**
     * Closes the open session and returns it.
     *
     * Read-and-clear in one update so a session cannot be credited twice if two
     * callers settle concurrently — which they can, since both process start and
     * the launcher regaining focus trigger a settle.
     */
    suspend fun takePending(): PendingPlaySession? {
        var taken: PendingPlaySession? = null
        store.updateData { current ->
            taken = current.pending
            if (current.pending == null) current else current.copy(pending = null)
        }
        return taken
    }

    /** The open session without clearing it. */
    suspend fun peek(): PendingPlaySession? = state.first().pending
}
