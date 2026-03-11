package com.hacaller.hamediaclient

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class HAMediaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        setupBouncyCastle()
    }

    private fun setupBouncyCastle() {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }
}
