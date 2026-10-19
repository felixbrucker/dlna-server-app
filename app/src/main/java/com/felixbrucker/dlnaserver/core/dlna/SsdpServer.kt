package com.felixbrucker.dlnaserver.core.dlna

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class SsdpServer @Inject constructor() {
    private val port: Int = 8192
    private var multicastSocket: MulticastSocket? = null
    private var listeningJob: Job? = null
    private var notifyJob: Job? = null

    @Volatile
    private var isRunning = false

    fun start(ipAddress: String, networkInterfaceName: String?, scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        scope.launch(Dispatchers.IO) {
            try {
                val group = InetAddress.getByName("239.255.255.250")
                val socket = MulticastSocket(1900)
                socket.timeToLive = 4
                multicastSocket = socket

                if (networkInterfaceName != null) {
                    val netIf = NetworkInterface.getByName(networkInterfaceName)
                    if (netIf != null) {
                        socket.networkInterface = netIf
                    }
                }

                socket.joinGroup(group)

                sendSsdpAlive(socket, group, ipAddress)

                notifyJob = launch {
                    while (isRunning) {
                        delay(30.seconds)
                        if (isRunning) {
                            sendSsdpAlive(socket, group, ipAddress)
                        }
                    }
                }

                val buffer = ByteArray(2048)
                while (isRunning && !socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: Exception) {
                        break
                    }

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (message.startsWith("M-SEARCH")) {
                        handleMSearch(socket, packet, message, ipAddress)
                    }
                }
            } catch (_: Exception) {
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false

        notifyJob?.cancel()
        notifyJob = null

        multicastSocket?.let { socket ->
            try {
                val group = InetAddress.getByName("239.255.255.250")
                sendSsdpByebye(socket, group)
                socket.leaveGroup(group)
                socket.close()
            } catch (_: Exception) {
            }
        }
        multicastSocket = null
        listeningJob?.cancel()
        listeningJob = null
    }

    private fun handleMSearch(
        socket: MulticastSocket,
        packet: DatagramPacket,
        message: String,
        ipAddress: String
    ) {
        val stHeader = message.lines()
            .firstOrNull { it.uppercase().startsWith("ST:") }
            ?.substringAfter(":")
            ?.trim() ?: return

        val location = "http://$ipAddress:$port/description.xml"
        val usnRoot = "uuid:550e8400-e29b-41d4-a716-446655440000::upnp:rootdevice"
        val usnDevice = "uuid:550e8400-e29b-41d4-a716-446655440000::urn:schemas-upnp-org:device:MediaServer:1"

        val targets = listOf(
            "ssdp:all",
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:service:ContentDirectory:1",
            "urn:schemas-upnp-org:service:ConnectionManager:1"
        )

        if (targets.any { stHeader.equals(it, ignoreCase = true) }) {
            val response = """HTTP/1.1 200 OK
CACHE-CONTROL: max-age=1800
DATE: ${java.util.Date()}
EXT:
LOCATION: $location
SERVER: Android/16 UPnP/1.0 DLNAServer/1.0
ST: $stHeader
USN: ${if (stHeader == "upnp:rootdevice") usnRoot else usnDevice}

""".replace("\n", "\r\n")

            val bytes = response.toByteArray(Charsets.UTF_8)
            val replyPacket = DatagramPacket(bytes, bytes.size, packet.address, packet.port)
            try {
                socket.send(replyPacket)
            } catch (_: Exception) {
            }
        }
    }

    private fun sendSsdpAlive(socket: MulticastSocket, group: InetAddress, ipAddress: String) {
        val location = "http://$ipAddress:$port/description.xml"
        val usn = "uuid:550e8400-e29b-41d4-a716-446655440000::urn:schemas-upnp-org:device:MediaServer:1"

        val notify = """NOTIFY * HTTP/1.1
HOST: 239.255.255.250:1900
CACHE-CONTROL: max-age=1800
LOCATION: $location
NT: urn:schemas-upnp-org:device:MediaServer:1
NTS: ssdp:alive
SERVER: Android/16 UPnP/1.0 DLNAServer/1.0
USN: $usn

""".replace("\n", "\r\n")

        val bytes = notify.toByteArray(Charsets.UTF_8)
        val packet = DatagramPacket(bytes, bytes.size, group, 1900)
        try {
            socket.send(packet)
        } catch (_: Exception) {
        }
    }

    private fun sendSsdpByebye(socket: MulticastSocket, group: InetAddress) {
        val usn = "uuid:550e8400-e29b-41d4-a716-446655440000::urn:schemas-upnp-org:device:MediaServer:1"

        val notify = """NOTIFY * HTTP/1.1
HOST: 239.255.255.250:1900
NT: urn:schemas-upnp-org:device:MediaServer:1
NTS: ssdp:byebye
USN: $usn

""".replace("\n", "\r\n")

        val bytes = notify.toByteArray(Charsets.UTF_8)
        val packet = DatagramPacket(bytes, bytes.size, group, 1900)
        try {
            socket.send(packet)
        } catch (_: Exception) {
        }
    }
}
