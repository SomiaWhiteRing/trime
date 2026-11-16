/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

object CollectionHelper : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private lateinit var cltDb: Database
    private lateinit var cltDao: DatabaseDao

    private val mutex = Mutex()

    private var lastBean: DatabaseBean? = null

    fun init(context: Context) {
        cltDb =
            Room
                .databaseBuilder(context, Database::class.java, "collection.db")
                .addMigrations(Database.MIGRATION_3_4, Database.MIGRATION_4_5, Database.MIGRATION_5_6)
                .build()
        cltDao = cltDb.databaseDao()
    }

    suspend fun insert(bean: DatabaseBean) = cltDao.insert(bean)

    suspend fun get(id: Int) = cltDao.get(id)

    suspend fun haveUnpinned() = cltDao.haveUnpinned()

    fun allBeans() = cltDao.savedCollectionBeans()
    fun observeRecords() = cltDao.observeCollectionRecords()

    suspend fun pin(id: Int) = cltDao.updatePinned(id, true)

    suspend fun unpin(id: Int) = cltDao.updatePinned(id, false)

    suspend fun delete(id: Int) = cltDao.delete(id)
    suspend fun deletePhraseGroups(groupIds: List<String>) = cltDao.deletePhraseGroups(groupIds)

    suspend fun deleteAll(skipPinned: Boolean = true) {
        if (skipPinned) {
            cltDao.deleteAllUnpinned()
        } else {
            cltDao.deleteAll()
        }
    }

    suspend fun updateText(
        id: Int,
        text: String,
    ) {
        lastBean?.let {
            if (id == it.id) lastBean = it.copy(text = text)
        }
        cltDao.updateText(id, text)
    }

    suspend fun recordRecent(text: String): Int = mutex.withLock {
        cltDao.find(text)?.id ?: cltDao.insert(DatabaseBean(text = text, recentOnly = true)).toInt()
    }

    fun addNewBean(text: String) {
        launch {
            mutex.withLock {
                val bean = DatabaseBean(text = text)
                if (bean.text.isNullOrBlank()) return@withLock
                try {
                    cltDao.findDefaultPhrase(text)?.let {
                        val saved = it.copy(time = bean.time, recentOnly = false)
                        lastBean = saved
                        cltDao.update(saved)
                        return@withLock
                    }
                    val insertedBean = cltDb.withTransaction {
                        val rowId = cltDao.insert(bean)
                        cltDao.get(rowId) ?: bean
                    }
                    lastBean = insertedBean
                } catch (e: Exception) {
                    Timber.w("Failed to update collection database: $e")
                    lastBean = bean
                }
            }
        }
    }
}
