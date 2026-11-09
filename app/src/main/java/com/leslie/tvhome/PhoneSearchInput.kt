package com.leslie.tvhome
import android.content.Context
import android.net.ConnectivityManager
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.security.SecureRandom

/** QR-paired, temporary LAN keyboard for Tigris Search ONLY; not global input injection. */
internal class PhoneSearchInput(private val onText:(String)->Unit):NanoHTTPD(8791) {
 private val token=ByteArray(20).also{SecureRandom().nextBytes(it)}
  .joinToString(""){"%02x".format(it.toInt() and 255)}
 private val path="/$token"
 override fun serve(session:IHTTPSession):Response {
  if (session.uri != path && session.uri != "$path/")
   return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Not found")
  if (session.method==Method.GET) {
   val html="""<!doctype html><html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
   <title>Tigris Keyboard</title><style>body{background:#111820;color:white;font:18px system-ui;padding:30px}
   input,button{font:inherit;padding:16px;border-radius:10px;width:100%;box-sizing:border-box;margin:8px 0}</style>
   <h2>Tigris TV Keyboard</h2><p>Send text only to the open Tigris Search screen.</p>
   <form method="post"><input name="text" maxlength="160" autofocus><button>Send to TV</button></form></html>"""
   return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8",html).apply {
    addHeader("Cache-Control","no-store");addHeader("Referrer-Policy","no-referrer")
    addHeader("X-Content-Type-Options","nosniff")
   }
  }
  if (session.method!=Method.POST)
   return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED,"text/plain","Not allowed")
  val length=session.headers["content-length"]?.toIntOrNull()
  if (length==null || length !in 0..1024)
   return newFixedLengthResponse(Response.Status.BAD_REQUEST,"text/plain","Invalid size")
  return try {
   session.parseBody(HashMap())
   val value=session.parameters["text"]?.firstOrNull().orEmpty().take(160)
    .filter {it >= ' ' && it != '\u007f'}
   onText(value)
   newFixedLengthResponse(Response.Status.OK,"text/plain","Sent to TV")
  } catch (_:Exception) {newFixedLengthResponse(Response.Status.BAD_REQUEST,"text/plain","Invalid input")}
 }
 fun localUrl(ctx:Context):String? {
  val cm=ctx.getSystemService(ConnectivityManager::class.java)?:return null
  val props=cm.getLinkProperties(cm.activeNetwork?:return null)?:return null
  val ip=props.linkAddresses.map{it.address}
   .firstOrNull{it is Inet4Address && it.isSiteLocalAddress}?.hostAddress?:return null
  return "http://$ip:8791$path/"
 }
}
