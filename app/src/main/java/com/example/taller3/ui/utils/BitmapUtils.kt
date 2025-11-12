package com.example.taller3.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun circularBitmapFromUrl(
    context: Context,
    url: String,
    sizePx: Int,
    borderPx: Int
): Bitmap? = suspendCoroutine { continuation ->
    val loader = context.imageLoader
    val request = ImageRequest.Builder(context)
        .data(url)
        .allowHardware(false) // para poder manipular el bitmap
        .size(sizePx, sizePx)
        .target {
            val drawable = it
            val squared = drawable.toBitmap(width = sizePx, height = sizePx, config = Bitmap.Config.ARGB_8888)
            val result = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val paint = Paint()
            val rect = Rect(0, 0, sizePx, sizePx)

            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)

            paint.color = -0x1 // color blanco
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(squared, rect, rect, paint)

            paint.xfermode = null
            paint.style = Paint.Style.STROKE
            paint.color = -0x7f000001 // un grisito oscuro
            paint.strokeWidth = borderPx.toFloat()
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx - borderPx) / 2f, paint)

            continuation.resume(result)
        }
        .build()

    loader.enqueue(request)
}

fun descriptorFromBitmap(bmp: Bitmap?): BitmapDescriptor? {
    return if (bmp != null) BitmapDescriptorFactory.fromBitmap(bmp) else null
}
