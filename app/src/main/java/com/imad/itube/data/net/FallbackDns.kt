package com.imad.itube.data.net

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Resolves with the phone's own DNS first and falls back to DNS-over-HTTPS (Cloudflare, then
 * Google) when that fails.
 *
 * YouTube serves video from per-edge hosts like `rr3---sn-8vv-cg06.googlevideo.com`. Some
 * networks, private-DNS providers and ad blockers fail to resolve those hosts while youtube.com
 * itself still works, so the video page loads and playback then dies with
 * ERROR_CODE_IO_NETWORK_CONNECTION_FAILED.
 */
object FallbackDns : Dns {

    private const val TAG = "FallbackDns"

    private val bootstrapClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val resolvers: List<Dns> by lazy {
        listOf(
            doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"),
            doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
        )
    }

    private fun doh(url: String, vararg bootstrap: String): Dns =
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(bootstrap.map { InetAddress.getByName(it) })
            .includeIPv6(true)
            .build()

    override fun lookup(hostname: String): List<InetAddress> {
        val systemError = try {
            val result = Dns.SYSTEM.lookup(hostname)
            if (result.isNotEmpty()) return result
            UnknownHostException("No address for $hostname")
        } catch (e: UnknownHostException) {
            e
        }
        for (resolver in resolvers) {
            try {
                val result = resolver.lookup(hostname)
                if (result.isNotEmpty()) {
                    Log.i(TAG, "System DNS failed for $hostname, resolved over HTTPS")
                    return result
                }
            } catch (e: Exception) {
                Log.w(TAG, "DNS-over-HTTPS lookup failed for $hostname", e)
            }
        }
        throw systemError
    }
}
