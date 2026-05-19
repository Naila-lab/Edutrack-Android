package com.example.edutrack.ui.flashcard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.edutrack.R

class FlashcardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcard) // Pastikan file XML ini ada, bawaan saat bikin Activity baru

        // Kode untuk memasukkan FlashcardFragment ke dalam Activity ini
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, FlashcardFragment())
                .commit()
        }
    }
}