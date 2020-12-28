package com.epam.drill.plugins.test2code.util

import com.github.luben.zstd.Zstd as ZstdFacade

object Zstd {
    fun compress(
        input: ByteArray
    ): ByteArray = ZstdFacade.compress(input)

    fun decompress(
        input: ByteArray
    ): ByteArray = run {
        val decompressedSize = ZstdFacade.decompressedSize(input).toInt()
        ZstdFacade.decompress(input, decompressedSize)
    }
}
