package com.mecheri636.offline

import android.app.Application
import com.mecheri636.offline.data.db.AppDatabase

class MecheriOfflineApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
