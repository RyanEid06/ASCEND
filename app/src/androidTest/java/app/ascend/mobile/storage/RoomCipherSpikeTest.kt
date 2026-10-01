package app.ascend.mobile.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.SecureRandom
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Phase 0 compatibility proof only; not the production scan database schema. */
@RunWith(AndroidJUnit4::class)
class RoomCipherSpikeTest {
    private lateinit var context: Context
    private val databaseNames = mutableListOf<String>()
    private val keyAliases = mutableListOf<String>()

    @Before fun loadSqlCipher() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher")
    }

    @After fun removeDatabases() {
        databaseNames.forEach(context::deleteDatabase)
        keyAliases.forEach { keyStore().deleteEntry(it) }
    }

    @Test fun createReopenAndWrongKey() = runBlocking {
        val name = newDatabaseName()
        val passphrase = randomPassphrase()
        val first = openV1(name, passphrase)
        first.records().insert(SpikeRecordV1("metric-1", "sensitive-test-value"))
        first.close()

        val fileBytes = context.getDatabasePath(name).readBytes()
        assertFalse(fileBytes.take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))

        val reopened = openV1(name, passphrase)
        assertEquals("sensitive-test-value", reopened.records().get("metric-1")?.value)
        reopened.close()

        val wrongKey = openV1(name, randomPassphrase())
        try {
            wrongKey.records().get("metric-1")
            fail("Wrong passphrase must not open the encrypted database")
        } catch (_: Exception) {
            // SQLite/Room may wrap the key error differently across versions.
        } finally {
            wrongKey.close()
        }
    }

    @Test fun explicitMigrationKeepsExistingRecord() = runBlocking {
        val name = newDatabaseName()
        val passphrase = randomPassphrase()
        openV1(name, passphrase).use { it.records().insert(SpikeRecordV1("metric-1", "kept")) }

        val migration = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.executeSQL("ALTER TABLE spike_records ADD COLUMN label TEXT NOT NULL DEFAULT 'legacy'")
            }
        }
        val migrated = Room.databaseBuilder(context, SpikeDatabaseV2::class.java, name)
            .setDriver(SQLCipherDriver(passphrase.copyOf(), null, null))
            .addMigrations(migration)
            .build()
        migrated.use {
            val record = it.records().get("metric-1")
            assertEquals("kept", record?.value)
            assertEquals("legacy", record?.label)
            assertNull(it.records().get("nonexistent"))
        }
    }

    @Test fun keystoreEnvelopeReopensDatabaseAndMissingKeyFailsClosed() = runBlocking {
        val name = newDatabaseName()
        val alias = "wp02-spike-${UUID.randomUUID()}".also(keyAliases::add)
        val passphrase = randomPassphrase()
        val envelope = wrapPassphrase(alias, passphrase)
        val restored = unwrapPassphrase(alias, envelope)
        assertArrayEquals(passphrase, restored)

        openV1(name, restored).use { it.records().insert(SpikeRecordV1("metric-1", "kept")) }
        openV1(name, unwrapPassphrase(alias, envelope)).use {
            assertEquals("kept", it.records().get("metric-1")?.value)
        }

        keyStore().deleteEntry(alias)
        try {
            unwrapPassphrase(alias, envelope)
            fail("A missing Keystore key must not generate a replacement over existing history")
        } catch (_: IllegalStateException) {
            // The product must offer recovery/deletion, never silently create an empty database.
        }
    }

    private fun openV1(name: String, passphrase: ByteArray): SpikeDatabaseV1 =
        Room.databaseBuilder(context, SpikeDatabaseV1::class.java, name)
            .setDriver(SQLCipherDriver(passphrase.copyOf(), null, null))
            .build()

    private fun newDatabaseName(): String = "wp02-spike-${UUID.randomUUID()}.db".also(databaseNames::add)

    private fun randomPassphrase(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    private data class Envelope(
        val formatVersion: Int,
        val keyVersion: Int,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
    )

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun wrapPassphrase(alias: String, passphrase: ByteArray): Envelope {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, generator.generateKey())
        return Envelope(1, 1, cipher.iv, cipher.doFinal(passphrase))
    }

    private fun unwrapPassphrase(alias: String, envelope: Envelope): ByteArray {
        require(envelope.formatVersion == 1 && envelope.keyVersion == 1)
        val key = keyStore().getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("Keystore key missing")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.nonce))
        return cipher.doFinal(envelope.ciphertext)
    }
}

@Entity(tableName = "spike_records")
data class SpikeRecordV1(@PrimaryKey val id: String, val value: String)

@Entity(tableName = "spike_records")
data class SpikeRecordV2(
    @PrimaryKey val id: String,
    val value: String,
    @ColumnInfo(defaultValue = "'legacy'") val label: String,
)

@Dao
interface SpikeDaoV1 {
    @Insert suspend fun insert(record: SpikeRecordV1)
    @Query("SELECT * FROM spike_records WHERE id = :id") suspend fun get(id: String): SpikeRecordV1?
}

@Dao
interface SpikeDaoV2 {
    @Query("SELECT * FROM spike_records WHERE id = :id") suspend fun get(id: String): SpikeRecordV2?
}

@Database(entities = [SpikeRecordV1::class], version = 1, exportSchema = false)
abstract class SpikeDatabaseV1 : RoomDatabase() {
    abstract fun records(): SpikeDaoV1
}

@Database(entities = [SpikeRecordV2::class], version = 2, exportSchema = false)
abstract class SpikeDatabaseV2 : RoomDatabase() {
    abstract fun records(): SpikeDaoV2
}
