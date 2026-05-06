package com.resdownloader.data.model

data class VersionInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val assets: List<AssetInfo>
)

data class AssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long
)
