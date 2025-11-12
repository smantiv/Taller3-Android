package com.example.taller3.ui.utils

import android.content.Context
import android.graphics.*
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Descarga una imagen y la convierte a Bitmap circular con borde opcional.
 */
suspend fun circularBitmapFromUrl(
    context: Context,
    imageUrl: String,
    sizePx: Int = 160,
    borderPx: Int = 6
): Bitmap? {
    val loader = ImageLoader(context)
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(false) // necesitamos CPU para canvas
        .build()

    val result = loader.execute(request)
    if (result !is SuccessResult) return null
    val drawable = result.drawable

    val raw = Bitmap.createBitmap(
        sizePx, sizePx, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(raw)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)

    // recorte circular
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = sizePx / 2f
    c.drawCircle(radius, radius, radius, paint) // máscara
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    c.drawBitmap(raw, 0f, 0f, paint)

    // borde
    if (borderPx > 0) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderPx.toFloat()
            color = Color.WHITE
        }
        c.drawCircle(radius, radius, radius - borderPx / 2f, borderPaint)
    }
    return output
}

/** Convierte el Bitmap a BitmapDescriptor para el Marker. */
fun descriptorFromBitmap(bitmap: Bitmap?): BitmapDescriptor? =
    bitmap?.let { BitmapDescriptorFactory.fromBitmap(it) }