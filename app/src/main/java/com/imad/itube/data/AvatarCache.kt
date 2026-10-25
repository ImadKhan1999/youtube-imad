package com.imad.itube.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Some YouTube lists (the category feeds in particular) come without channel avatars.
 * This looks them up once per channel and remembers the answer for the session.
 */
class AvatarCache {
    private val results = ConcurrentHashMap<String, CompletableDeferred<String?>>()
    private val gate = Semaphore(4)

    fun cached(channelUrl: String): String? =
        results[channelUrl]?.takeIf { it.isCompleted }?.getCompleted()

    fun put(channelUrl: String, avatar: String) {
        results.putIfAbsent(channelUrl, CompletableDeferred(avatar))
    }

    suspend fun get(channelUrl: String): String? {
        val fresh = CompletableDeferred<String?>()
        val existing = results.putIfAbsent(channelUrl, fresh)
        if (existing != null) return existing.await()
        val avatar = runCatching {
            gate.withPermit {
                withContext(Dispatchers.IO) {
                    ChannelInfo.getInfo(ServiceList.YouTube, channelUrl).avatars.best(88)
                }
            }
        }.getOrNull()
        fresh.complete(avatar)
        if (avatar == null) results.remove(channelUrl) // allow a retry later
        return avatar
    }
}
