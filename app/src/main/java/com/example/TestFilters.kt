package com.example

import android.graphics.Bitmap
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.NoFilterRender

fun test(bitmap: Bitmap) {
    val f = ImageObjectFilterRender()
    f.setImage(bitmap)
    // Try some scaling/positioning
    f.setScale(100f, 10f)
    f.setPosition(0f, 90f)
}

