package com.routemate.app.voice.infrastructure

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.routemate.app.voice.domain.CallRuntime

class AndroidCallRuntime(context:Context):CallRuntime {
    private val application=context.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private val stop=Runnable{application.stopService(Intent(application,VoiceCallService::class.java))}
    override fun start(){
        handler.removeCallbacks(stop)
        ContextCompat.startForegroundService(application,Intent(application,VoiceCallService::class.java).setAction(VoiceCallService.START))
    }
    override fun stop(){handler.removeCallbacks(stop);handler.postDelayed(stop,700)}
    override fun event(code:String){Log.i("RouteMateCall",code)}
}
