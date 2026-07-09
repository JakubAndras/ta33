package com.example.ta33.core

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform