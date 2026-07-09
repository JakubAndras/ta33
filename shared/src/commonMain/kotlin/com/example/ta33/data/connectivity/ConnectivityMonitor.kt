package com.example.ta33.data.connectivity

import com.example.ta33.domain.model.NetworkType
import kotlinx.coroutines.flow.Flow

interface ConnectivityMonitor {
    fun current(): NetworkType
    fun observe(): Flow<NetworkType>
}
