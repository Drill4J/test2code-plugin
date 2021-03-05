/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
