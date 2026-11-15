package com.routemate.app.voice.infrastructure

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.routemate.app.MainActivity
import com.routemate.app.R
import com.routemate.app.RouteMateApplication
import com.routemate.app.room.application.RoomSession
import com.routemate.app.voice.domain.VoiceStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** The user-started call owns the service. Activity navigation never owns the media collection. */
class VoiceCallService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var observer:Job?=null
    private var attached:RoomSession?=null
    private var wake:PowerManager.WakeLock?=null
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"RouteMate 통화",NotificationManager.IMPORTANCE_LOW).apply{description="진행 중인 그룹 통화와 마이크·퇴장 조작"})
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val session=(application as RouteMateApplication).graph.session
        when(intent?.action){
            LEAVE->{session.leave();return START_NOT_STICKY}
            MICROPHONE->{session.toggleMicrophone();return START_NOT_STICKY}
        }
        if(!session.state.value.callActive){stopSelf(startId);return START_NOT_STICKY}
        attached=session
        try {
            val type=if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw SecurityException("Microphone permission required")
            ServiceCompat.startForeground(this,NOTIFICATION,notification(),type)
        }catch(_:Exception){session.runtimeStopped();stopSelf(startId);return START_NOT_STICKY}
        if(wake==null){
            wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RouteMate:voice").apply{setReferenceCounted(false);acquire(10*60_000L)}
            scope.launch{while(isActive){delay(5*60_000L);if(session.state.value.callActive)wake?.acquire(10*60_000L)}}
        }
        observer?.cancel()
        observer=scope.launch{
            session.state.map{NotificationState(it.snapshot.room?.name.orEmpty(),it.voice.status,it.voice.microphoneEnabled,it.voice.participants.size,it.callActive)}
                .distinctUntilChanged().collect{value->
                    if(value.active)getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification())
                }
        }
        return START_NOT_STICKY
    }
    private fun notification():Notification {
        val state=attached?.state?.value
        val open=Intent(this,MainActivity::class.java).putExtra("showCall",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val content=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(value:String,request:Int)=PendingIntent.getService(this,request,Intent(this,VoiceCallService::class.java).setAction(value),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val status=when(state?.voice?.status){VoiceStatus.CONNECTED->if(state.voice.microphoneEnabled)"마이크 켜짐" else "마이크 꺼짐";VoiceStatus.RECONNECTING->"연결 복구 중";else->"음성 연결 중"}
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(state?.snapshot?.room?.name?:"RouteMate 통화").setContentText(status)
            .setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0,if(state?.voice?.microphoneEnabled==true)"마이크 끄기" else "마이크 켜기",action(MICROPHONE,2))
            .addAction(0,"나가기",action(LEAVE,3)).build()
    }
    override fun onDestroy(){
        attached?.runtimeStopped();observer?.cancel();scope.cancel()
        wake?.let{if(it.isHeld)it.release()};wake=null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    private data class NotificationState(val name:String,val status:VoiceStatus,val mic:Boolean,val count:Int,val active:Boolean)
    companion object {
        const val START="com.routemate.app.voice.START"
        const val LEAVE="com.routemate.app.voice.LEAVE"
        const val MICROPHONE="com.routemate.app.voice.MICROPHONE"
        private const val CHANNEL="routemate_voice"
        private const val NOTIFICATION=1401
    }
}
