package com.example.ta33.data.connectivity

import com.example.ta33.domain.model.NetworkType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.Network.nw_path_uses_interface_type
import platform.darwin.dispatch_queue_create

@OptIn(ExperimentalForeignApi::class)
class IosConnectivityMonitor : ConnectivityMonitor {
    private val queue = dispatch_queue_create("com.example.ta33.connectivity", null)
    private val types = MutableStateFlow(NetworkType.OTHER)

    // A single app-lifetime monitor feeds both current() and observe(); retained as a field so it is
    // not collected and so we never spawn a duplicate monitor per observer.
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_set_update_handler(monitor) { path -> path?.let { types.value = typeFrom(it) } }
        nw_path_monitor_start(monitor)
    }

    private fun typeFrom(path: nw_path_t): NetworkType {
        if (nw_path_get_status(path) != nw_path_status_satisfied) return NetworkType.NONE
        return when {
            nw_path_uses_interface_type(path, nw_interface_type_wifi) -> NetworkType.WIFI
            nw_path_uses_interface_type(path, nw_interface_type_cellular) -> NetworkType.CELLULAR
            else -> NetworkType.OTHER
        }
    }

    override fun current(): NetworkType = types.value

    override fun observe(): Flow<NetworkType> = types.asStateFlow()
}
