package com.example.selfapp.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.selfapp.data.dao.DnsBlockConfigDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject

@AndroidEntryPoint
class DnsBlockVpnService : VpnService(), Runnable {

    @Inject
    lateinit var dnsBlockConfigDao: DnsBlockConfigDao

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private var isRunning = false

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var blockedDomains = setOf<String>()

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            dnsBlockConfigDao.getAllBlockedDomainsFlow().collectLatest { configs ->
                blockedDomains = configs.filter { it.isBlocked }.map { it.domainName.lowercase().trim() }.toSet()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_DISCONNECT") {
            stopVpn()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            isRunning = true
            vpnThread = Thread(this, "DnsBlockVpnThread").apply { start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopVpn() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        vpnThread = null
    }

    override fun run() {
        try {
            // Establish the local VPN interface
            // Set local VPN IP to 10.0.0.1, set virtual DNS to 10.0.0.2, and route all 10.0.0.2 (DNS traffic)
            val builder = Builder()
                .setSession("SelfApp Dns Blocker")
                .addAddress("10.0.0.1", 32)
                .addRoute("10.0.0.2", 32)
                .addDnsServer("10.0.0.2")

            vpnInterface = builder.establish()
            if (vpnInterface == null) return

            val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)

            val packetBuffer = ByteBuffer.allocate(32767)
            val dnsSocket = DatagramSocket()

            while (isRunning) {
                packetBuffer.clear()
                val bytesRead = inputStream.read(packetBuffer.array())
                if (bytesRead > 0) {
                    packetBuffer.limit(bytesRead)
                    processPacket(packetBuffer, outputStream, dnsSocket)
                }
            }
        } catch (e: Exception) {
            Log.e("VpnService", "Error in VPN loop", e)
        }
    }

    private fun processPacket(
        packet: ByteBuffer,
        outputStream: FileOutputStream,
        dnsSocket: DatagramSocket
    ) {
        val packetArray = packet.array()
        val limit = packet.limit()

        // Verify it is an IPv4 packet
        if (limit < 20 || (packetArray[0].toInt() and 0xF0) != 0x45) return

        val protocol = packetArray[9].toInt()
        val ipHeaderLength = (packetArray[0].toInt() and 0x0F) * 4

        // We only care about UDP (protocol 17)
        if (protocol != 17) return

        // Extract ports
        val srcPort = ((packetArray[ipHeaderLength].toInt() and 0xFF) shl 8) or (packetArray[ipHeaderLength + 1].toInt() and 0xFF)
        val destPort = ((packetArray[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or (packetArray[ipHeaderLength + 3].toInt() and 0xFF)

        // We only intercept DNS queries (destPort 53)
        if (destPort != 53) return

        val udpHeaderLength = 8
        val dnsPayloadOffset = ipHeaderLength + udpHeaderLength

        if (limit < dnsPayloadOffset + 12) return

        // Parse DNS Question Domain
        val domainName = parseDnsQuestion(packetArray, dnsPayloadOffset, limit)
        if (domainName.isEmpty()) return

        val isBlocked = shouldBlock(domainName)

        if (isBlocked) {
            // Blocked: Construct local NXDOMAIN DNS response packet and send back to client
            val response = constructNxDomainResponse(packetArray, ipHeaderLength, dnsPayloadOffset, limit)
            outputStream.write(response)
        } else {
            // Allowed: Forward actual request to Google DNS (8.8.8.8) and proxy response back
            Thread {
                try {
                    val dnsPayloadLength = limit - dnsPayloadOffset
                    val dnsQueryBytes = ByteArray(dnsPayloadLength)
                    System.arraycopy(packetArray, dnsPayloadOffset, dnsQueryBytes, 0, dnsPayloadLength)

                    val sendPacket = DatagramPacket(
                        dnsQueryBytes,
                        dnsPayloadLength,
                        InetAddress.getByName("8.8.8.8"),
                        53
                    )
                    dnsSocket.send(sendPacket)

                    val recvBytes = ByteArray(2048)
                    val recvPacket = DatagramPacket(recvBytes, recvBytes.size)
                    dnsSocket.receive(recvPacket)

                    val responsePacketLength = recvPacket.length
                    val fullResponsePacket = constructVpnResponsePacket(
                        packetArray,
                        ipHeaderLength,
                        recvPacket.data,
                        responsePacketLength
                    )
                    synchronized(outputStream) {
                        outputStream.write(fullResponsePacket)
                    }
                } catch (e: Exception) {
                    Log.e("VpnService", "Failed to proxy DNS query for $domainName", e)
                }
            }.start()
        }
    }

    private fun shouldBlock(domain: String): Boolean {
        val lowerDomain = domain.lowercase().trim()
        if (blockedDomains.contains(lowerDomain)) return true
        // Check subdomains
        for (blocked in blockedDomains) {
            if (lowerDomain.endsWith(".$blocked")) return true
        }
        return false
    }

    private fun parseDnsQuestion(packetArray: ByteArray, dnsPayloadOffset: Int, limit: Int): String {
        var offset = dnsPayloadOffset + 12 // DNS header is 12 bytes
        val sb = StringBuilder()
        while (offset < limit) {
            val labelLength = packetArray[offset].toInt() and 0xFF
            if (labelLength == 0) break
            if (offset + 1 + labelLength > limit) return ""
            if (sb.isNotEmpty()) sb.append(".")
            sb.append(String(packetArray, offset + 1, labelLength, Charsets.US_ASCII))
            offset += 1 + labelLength
        }
        return sb.toString()
    }

    private fun constructNxDomainResponse(
        reqPacket: ByteArray,
        ipHeaderLength: Int,
        dnsPayloadOffset: Int,
        limit: Int
    ): ByteArray {
        val dnsPayloadLength = limit - dnsPayloadOffset
        val respDnsBytes = ByteArray(dnsPayloadLength)
        System.arraycopy(reqPacket, dnsPayloadOffset, respDnsBytes, 0, dnsPayloadLength)

        // Set DNS Flags to NXDOMAIN (Name Error)
        // Header bytes 2 and 3 control flags
        // Bit 15: Response (1), Bit 11-14: Opcode (0), Bit 10: AA (1), Bit 9: TC (0), Bit 8: RD (1), Bit 7: RA (1), Bit 0-3: RCODE (3 for Name Error)
        respDnsBytes[2] = 0x85.toByte() // Response, Authoritative, Recursion Desired
        respDnsBytes[3] = 0x83.toByte() // Recursion Available, RCODE = 3 (Name Error)

        return constructVpnResponsePacket(reqPacket, ipHeaderLength, respDnsBytes, dnsPayloadLength)
    }

    private fun constructVpnResponsePacket(
        reqPacket: ByteArray,
        ipHeaderLength: Int,
        dnsPayloadBytes: ByteArray,
        dnsPayloadLength: Int
    ): ByteArray {
        val ipLength = ipHeaderLength + 8 + dnsPayloadLength
        val response = ByteArray(ipLength)

        // 1. Copy IP Header and swap source/dest IPs
        System.arraycopy(reqPacket, 0, response, 0, ipHeaderLength)
        
        // Swap IPs in response header (Source IP offset: 12-15, Dest IP offset: 16-19)
        System.arraycopy(reqPacket, 12, response, 16, 4) // Copy req source IP to resp dest IP
        System.arraycopy(reqPacket, 16, response, 12, 4) // Copy req dest IP to resp source IP

        // Update IP total length field (offset 2-3)
        response[2] = ((ipLength ushr 8) and 0xFF).toByte()
        response[3] = (ipLength and 0xFF).toByte()

        // Reset IP Checksum to 0 for recalculation (offset 10-11)
        response[10] = 0
        response[11] = 0

        // Calculate IP checksum
        val ipChecksum = calculateChecksum(response, 0, ipHeaderLength)
        response[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        response[11] = (ipChecksum and 0xFF).toByte()

        // 2. Build UDP Header (offset ipHeaderLength, size 8)
        // Swap source and destination ports
        val srcPortByte1 = reqPacket[ipHeaderLength + 2]
        val srcPortByte2 = reqPacket[ipHeaderLength + 3]
        val destPortByte1 = reqPacket[ipHeaderLength]
        val destPortByte2 = reqPacket[ipHeaderLength + 1]

        response[ipHeaderLength] = srcPortByte1
        response[ipHeaderLength + 1] = srcPortByte2
        response[ipHeaderLength + 2] = destPortByte1
        response[ipHeaderLength + 3] = destPortByte2

        // Set UDP Length (offset 4-5 from UDP header)
        val udpLength = 8 + dnsPayloadLength
        response[ipHeaderLength + 4] = ((udpLength ushr 8) and 0xFF).toByte()
        response[ipHeaderLength + 5] = (udpLength and 0xFF).toByte()

        // UDP Checksum is optional in IPv4 (can set to 0)
        response[ipHeaderLength + 6] = 0
        response[ipHeaderLength + 7] = 0

        // 3. Copy DNS Payload
        System.arraycopy(dnsPayloadBytes, 0, response, ipHeaderLength + 8, dnsPayloadLength)

        return response
    }

    private fun calculateChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        var len = length
        while (len > 1) {
            sum += (((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF))
            i += 2
            len -= 2
        }
        if (len > 0) {
            sum += ((buf[i].toInt() and 0xFF) shl 8)
        }
        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
