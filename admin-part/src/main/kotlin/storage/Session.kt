package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@Serializable
internal class StoredSession(
    @Id val id: String,
    val scopeId: String,
    val data: ByteArray
)

internal suspend fun StoreClient.loadSessions(
    scopeId: String
): List<FinishedSession> = findBy<StoredSession> {
    StoredSession::scopeId eq scopeId
}.map { ProtoBuf.load(FinishedSession.serializer(), Zstd.decompress(it.data)) }

internal suspend fun StoreClient.storeSession(
    scopeId: String,
    session: FinishedSession
) {
    val data = ProtoBuf.dump(FinishedSession.serializer(), session)
    store(
        StoredSession(
            id = session.id,
            scopeId = scopeId,
            data = Zstd.compress(data)
        )
    )
}

internal suspend fun StoreClient.deleteSessions(
    scopeId: String
) = deleteBy<StoredSession> {
    StoredSession::scopeId eq scopeId
}
