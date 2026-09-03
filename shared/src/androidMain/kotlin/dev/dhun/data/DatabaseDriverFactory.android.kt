package dev.dhun.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.dhun.database.DhunDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = DhunDatabase.Schema,
        context = context,
        name = DatabaseFactory.FILE_NAME,
        callback = object : AndroidSqliteDriver.Callback(DhunDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )
}
