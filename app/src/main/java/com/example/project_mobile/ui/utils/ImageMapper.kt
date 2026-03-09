package com.example.project_mobile.ui.utils

import com.example.project_mobile.R
import com.example.project_mobile.data.Barber

object ImageMapper {
    /**
     * Maps a Barber object to its corresponding drawable resource ID.
     * This keeps UI logic (drawable IDs) out of the data layer.
     */
    fun getBarberImageRes(barber: Barber?): Int {
        if (barber == null) return 0
        
        // 1. Try mapping by image key stored in Firestore
        val resByImage = when (barber.image) {
            "johnwick" -> R.drawable.johnwick
            "trump" -> R.drawable.trump
            "dominic" -> R.drawable.dominic
            else -> 0
        }
        if (resByImage != 0) return resByImage
        
        // 2. Fallback to mapping by name (case-insensitive) for better reliability
        val nameLower = barber.name.lowercase()
        return when {
            nameLower.contains("john") || nameLower.contains("wick") -> R.drawable.johnwick
            nameLower.contains("trump") || nameLower.contains("donald") -> R.drawable.trump
            nameLower.contains("dominic") || nameLower.contains("toretto") -> R.drawable.dominic
            else -> 0
        }
    }
}
