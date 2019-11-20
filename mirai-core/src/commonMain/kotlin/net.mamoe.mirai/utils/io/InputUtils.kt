@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package net.mamoe.mirai.utils.io

import kotlinx.io.core.*
import kotlinx.io.pool.useInstance
import kotlin.jvm.JvmName


fun ByteReadPacket.readRemainingBytes(
    n: Int = remaining.toInt()//not that safe but adequate
): ByteArray = ByteArray(n).also { readAvailable(it, 0, n) }

fun ByteReadPacket.readIoBuffer(
    n: Int = remaining.toInt()//not that safe but adequate
): IoBuffer = IoBuffer.Pool.borrow().also { this.readFully(it, n) }

fun ByteReadPacket.readIoBuffer(n: Short) = this.readIoBuffer(n.toInt())

fun Input.readIP(): String = buildString(4 + 3) {
    repeat(4) {
        val byte = readUByte()
        this.append(byte.toString())
        if (it != 3) this.append(".")
    }
}

fun Input.readPacket(length: Int): ByteReadPacket = this.readBytes(length).toReadPacket()

fun Input.readUVarIntLVString(): String = String(this.readUVarIntByteArray())

fun Input.readUShortLVString(): String = String(this.readUShortLVByteArray())

fun Input.readUVarIntByteArray(): ByteArray = this.readBytes(this.readUVarInt().toInt())

fun Input.readUShortLVByteArray(): ByteArray = this.readBytes(this.readUShort().toInt())

private inline fun <R> inline(block: () -> R): R = block()

@Suppress("DuplicatedCode")
fun Input.readTLVMap(expectingEOF: Boolean = false, tagSize: Int = 1): MutableMap<UInt, ByteArray> {
    val map = mutableMapOf<UInt, ByteArray>()
    var type: UShort = 0u

    while (inline {
            try {
                type = when (tagSize) {
                    1 -> readUByte().toUShort()
                    2 -> readUShort()
                    else -> error("Unsupported tag size: $tagSize")
                }
            } catch (e: EOFException) {
                if (expectingEOF) {
                    return map
                }
                throw e
            }
            type
        }.toUByte() != UByte.MAX_VALUE) {

        check(!map.containsKey(type.toUInt())) {
            "Count not readTLVMap: duplicated key 0x${type.toUInt().toUHexString("")}. " +
                    "map=$map" +
                    ", duplicating value=${this.readUShortLVByteArray()}" +
                    ", remaining=" + if (expectingEOF) this.readBytes().toUHexString() else "[Not expecting EOF]"
        }
        try {
            map[type.toUInt()] = this.readUShortLVByteArray()
        } catch (e: RuntimeException) { // BufferUnderflowException
            if (expectingEOF) {
                return map
            }
            throw e
        }
    }
    return map
}

/**
 * 读扁平的 tag-UVarInt map. 重复的 tag 将不会只保留最后一个
 *
 * tag: UByte
 * value: UVarint
 */
@Suppress("DuplicatedCode")
fun Input.readFlatTUVarIntMap(expectingEOF: Boolean = false, tagSize: Int = 1): MutableMap<UInt, UInt> {
    val map = mutableMapOf<UInt, UInt>()
    var type: UShort = 0u

    while (inline {
            try {
                type = when (tagSize) {
                    1 -> readUByte().toUShort()
                    2 -> readUShort()
                    else -> error("Unsupported tag size: $tagSize")
                }
            } catch (e: EOFException) {
                if (expectingEOF) {
                    return map
                }
                throw e
            }
            type
        }.toUByte() != UByte.MAX_VALUE) {

        if (map.containsKey(type.toUInt())) {
            map[type.toUInt()] = this.readUVarInt()
        } else {
            map[type.toUInt()] = this.readUVarInt()
        }
    }
    return map
}

fun Map<UInt, ByteArray>.printTLVMap(name: String = "", keyLength: Int = 1) =
    debugPrintln("TLVMap $name= " + this.mapValues { (_, value) -> value.toUHexString() }.mapKeys {
        when (keyLength) {
            1 -> it.key.toInt().toUByte().toUHexString()
            2 -> it.key.toInt().toUShort().toUHexString()
            4 -> it.key.toInt().toUInt().toUHexString()
            else -> illegalArgument("Expecting 1, 2 or 4 for keyLength")
        }
    })

@Suppress("NOTHING_TO_INLINE")
internal inline fun unsupported(): Nothing = error("Unsupported")

@Suppress("NOTHING_TO_INLINE")
internal inline fun illegalArgument(message: String? = null): Nothing = error(message ?: "Illegal argument passed")

@JvmName("printTLVStringMap")
fun Map<UInt, String>.printTLVMap(name: String = "") =
    debugPrintln("TLVMap $name= " + this.mapKeys { it.key.toInt().toUShort().toUHexString() })

fun Input.readString(length: Int): String = String(this.readBytes(length))
fun Input.readString(length: Long): String = String(this.readBytes(length.toInt()))
fun Input.readString(length: Short): String = String(this.readBytes(length.toInt()))
fun Input.readString(length: UShort): String = String(this.readBytes(length.toInt()))
fun Input.readString(length: Byte): String = String(this.readBytes(length.toInt()))

fun Input.readStringUntil(stopSignalExclude: UByte, expectingEOF: Boolean = false): String = readStringUntil(stopSignalExclude.toByte(), expectingEOF)

// TODO 应标记 JvmSynthetic 但 kotlin 有bug
@JvmName("readStringUntil0")
fun Input.readStringUntil(stopSignalExclude: Byte, expectingEOF: Boolean = false): String {
    ByteArrayPool.useInstance {
        var count = 0

        val buffer = byteArrayOf(1)
        while (readAvailable(buffer, 1) == 1) {
            if (buffer[0] == stopSignalExclude) {
                return buffer.encodeToString()
            }
            it[count++] = buffer[0]
        }
        if (!expectingEOF) {
            throw EOFException("Early EOF")
        }
        return buffer.encodeToString()
    }
}

private const val TRUE_BYTE_VALUE: Byte = 1
fun Input.readBoolean(): Boolean = this.readByte() == TRUE_BYTE_VALUE
fun Input.readLVNumber(): Number {
    return when (this.readShort().toInt()) {
        1 -> this.readByte()
        2 -> this.readShort()
        4 -> this.readInt()
        8 -> this.readLong()
        else -> throw UnsupportedOperationException()
    }
}