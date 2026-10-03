package app.ascend.mobile.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class StorageKeyUnavailable : IllegalStateException("Local storage key unavailable; explicit recovery or deletion required")
class StorageDataUnavailable : IllegalStateException("Local database unavailable while encrypted artifacts remain; explicit recovery or deletion required")

/** Format 1: magic, formatVersion, keyVersion, 12-byte nonce, ciphertext including 128-bit tag. */
internal class EncryptedFiles(private val alias: String) {
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun initializeForEmptyStore() {
        val store = keyStore()
        if (store.containsAlias(alias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        generator.generateKey()
    }

    private fun key(): SecretKey = keyStore().getKey(alias, null) as? SecretKey ?: throw StorageKeyUnavailable()

    fun deleteAfterExplicitReset() { keyStore().deleteEntry(alias) }

    fun encrypt(plaintext: ByteArray, purpose: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val header = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { it.writeInt(MAGIC); it.writeInt(1); it.writeInt(1); it.write(cipher.iv) }
        }.toByteArray()
        cipher.updateAAD(header)
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return header + cipher.doFinal(plaintext)
    }

    fun decrypt(envelope: ByteArray, purpose: String): ByteArray {
        require(envelope.size >= HEADER_BYTES + 16) { "Invalid encrypted envelope" }
        val input = DataInputStream(ByteArrayInputStream(envelope))
        require(input.readInt() == MAGIC && input.readInt() == 1 && input.readInt() == 1) { "Unsupported encrypted envelope version" }
        val nonce = ByteArray(12).also(input::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, nonce))
        cipher.updateAAD(envelope.copyOfRange(0, HEADER_BYTES))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(envelope, HEADER_BYTES, envelope.size - HEADER_BYTES)
    }

    companion object {
        private const val MAGIC = 0x41534331
        private const val HEADER_BYTES = 24

        fun atomicWrite(file: File, bytes: ByteArray) {
            check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                stream.write(bytes)
                atomic.finishWrite(stream)
            } catch (failure: Throwable) {
                atomic.failWrite(stream)
                throw failure
            }
        }

        fun read(file: File, maxBytes: Int): ByteArray {
            AtomicFile(file).openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= maxBytes) { "Encrypted file exceeds storage policy" }
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        }
    }
}

internal class PhotoFiles(private val directory: File, private val crypto: EncryptedFiles, private val maxBytes: Int) {
    init { check(directory.isDirectory || directory.mkdirs()) }
    private fun file(id: String): File {
        require(id.matches(Regex("[0-9a-f-]{36}")))
        return File(directory, "$id.enc")
    }
    fun write(id: String, bytes: ByteArray) {
        require(bytes.size <= maxBytes)
        EncryptedFiles.atomicWrite(file(id), crypto.encrypt(bytes, "photo:$id"))
    }
    fun read(id: String): ByteArray = crypto.decrypt(EncryptedFiles.read(file(id), maxBytes + 40), "photo:$id")
    fun delete(id: String) { AtomicFile(file(id)).delete(); check(!file(id).exists()) }
    fun cleanup(liveIds: Set<String>) {
        directory.listFiles()?.forEach { item ->
            val id = item.name.removeSuffix(".bak").removeSuffix(".new").removeSuffix(".enc")
            if (id !in liveIds) check(item.delete() || !item.exists())
        }
    }
}
