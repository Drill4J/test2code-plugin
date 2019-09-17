package com.epam.drill.plugins.coverage.storage

import jetbrains.exodus.database.*
import kotlinx.dnq.store.container.*
import java.io.*
import java.util.concurrent.*

const val DATABASE_HOME = "xodus-storage"

class XodusStorageManager {

    companion object {
        private val openedEnvironments: ConcurrentHashMap<String, TransientEntityStore> = ConcurrentHashMap()

        fun storage(environmentName: String): TransientEntityStore {
            val storage = openedEnvironments[environmentName] ?: StaticStoreContainer.init(
                dbFolder = File(DATABASE_HOME),
                environmentName = environmentName
            )
            openedEnvironments[environmentName] = storage
            return storage
        }
    }
}