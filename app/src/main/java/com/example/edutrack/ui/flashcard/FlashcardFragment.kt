package com.example.edutrack.ui.flashcard

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import com.example.edutrack.R

// ── Data model ────────────────────────────────────────────────────────────────
data class Flashcard(
    val id: Long = System.currentTimeMillis(),
    var topik: String,
    var question: String,
    var answer: String,
    var colorIndex: Int = 0
)

// ── Fragment ──────────────────────────────────────────────────────────────────
class FlashcardFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabCreate: Button          // ← fix: Button biasa
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvEmpty: TextView

    private val allCards = mutableListOf<Flashcard>()
    private lateinit var adapter: FlashcardAdapter2

    private val gson = Gson()
    private val PREFS_KEY = "flashcards_v2"

    private var activeFilter = "Semua"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_flashcard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerFlashcards)
        fabCreate    = view.findViewById(R.id.fabCreate)
        chipGroup    = view.findViewById(R.id.chipGroupFilter)
        tvEmpty      = view.findViewById(R.id.tvEmpty)

        adapter = FlashcardAdapter2(
            cards    = mutableListOf(),
            onEdit   = { card -> showCardDialog(card) },
            onDelete = { card -> deleteCard(card) }
        )

        recyclerView.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter

        loadCards()
        fabCreate.setOnClickListener { showCardDialog(null) }
    }

    // ── Filter Chips ──────────────────────────────────────────────────────────
    private fun rebuildFilterChips() {
        chipGroup.removeAllViews()
        val topikList = mutableListOf("Semua") + allCards.map { it.topik }.distinct().sorted()
        topikList.forEach { topik ->
            val chip = Chip(requireContext()).apply {
                text = topik
                isCheckable = true
                isChecked = (topik == activeFilter)
                setChipBackgroundColorResource(
                    if (topik == activeFilter) R.color.pomodoroFocus else R.color.bgCard
                )
                setTextColor(
                    if (topik == activeFilter)
                        ContextCompat.getColor(requireContext(), R.color.white)
                    else
                        ContextCompat.getColor(requireContext(), R.color.textSecondary)
                )
                chipStrokeWidth = 1f
                setChipStrokeColorResource(R.color.divider)
                setOnClickListener {
                    activeFilter = topik
                    rebuildFilterChips()
                    applyFilter()
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun applyFilter() {
        val filtered = if (activeFilter == "Semua") allCards.toList()
        else allCards.filter { it.topik == activeFilter }
        adapter.setData(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────
    private fun showCardDialog(existing: Flashcard?) {
        val dv      = layoutInflater.inflate(R.layout.dialog_flashcard, null)
        val etQ     = dv.findViewById<EditText>(R.id.etQuestion)
        val etA     = dv.findViewById<EditText>(R.id.etAnswer)
        val etTopik = dv.findViewById<EditText>(R.id.etTopik)
        val rgColor = dv.findViewById<RadioGroup>(R.id.rgColor)

        existing?.let {
            etQ.setText(it.question)
            etA.setText(it.answer)
            etTopik.setText(it.topik)
            val rb = when (it.colorIndex) {
                1 -> R.id.rbBlue; 2 -> R.id.rbPurple; 3 -> R.id.rbOrange; else -> R.id.rbGreen
            }
            rgColor.check(rb)
        }

        AlertDialog.Builder(requireContext(), R.style.StudyApp_Dialog)
            .setTitle(if (existing == null) "Buat Flashcard Baru" else "Edit Flashcard")
            .setView(dv)
            .setPositiveButton("Simpan") { _, _ ->
                val q = etQ.text.toString().trim()
                val a = etA.text.toString().trim()
                val t = etTopik.text.toString().trim().ifEmpty { "Umum" }
                val c = when (rgColor.checkedRadioButtonId) {
                    R.id.rbBlue -> 1; R.id.rbPurple -> 2; R.id.rbOrange -> 3; else -> 0
                }
                if (q.isEmpty() || a.isEmpty()) {
                    Toast.makeText(requireContext(), "Pertanyaan & jawaban tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing == null) {
                    allCards.add(0, Flashcard(topik = t, question = q, answer = a, colorIndex = c))
                } else {
                    existing.topik = t; existing.question = q
                    existing.answer = a; existing.colorIndex = c
                }
                saveCards()
                rebuildFilterChips()
                applyFilter()
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun deleteCard(card: Flashcard) {
        AlertDialog.Builder(requireContext(), R.style.StudyApp_Dialog)
            .setTitle("Hapus Flashcard?")
            .setMessage("\"${card.question}\" akan dihapus permanen.")
            .setPositiveButton("Hapus") { _, _ ->
                allCards.remove(card)
                saveCards()
                if (activeFilter != "Semua" && allCards.none { it.topik == activeFilter }) {
                    activeFilter = "Semua"
                }
                rebuildFilterChips()
                applyFilter()
            }
            .setNegativeButton("Batal", null).show()
    }

    // ── Persistence ───────────────────────────────────────────────────────────
    private fun saveCards() {
        requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
            .edit().putString(PREFS_KEY, gson.toJson(allCards)).apply()
    }

    private fun loadCards() {
        val json = requireContext().getSharedPreferences("study_prefs", Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<Flashcard>>() {}.type
            allCards.addAll(gson.fromJson(json, type))
        }
        if (allCards.isEmpty()) {
            allCards.add(Flashcard(topik = "RPL", question = "Apa itu diagram sequence?",
                answer = "Diagram UML yang menggambarkan interaksi antar objek dalam urutan waktu.", colorIndex = 0))
        }
        rebuildFilterChips()
        applyFilter()
    }
}

// ── Adapter ───────────────────────────────────────────────────────────────────
class FlashcardAdapter2(
    private val cards:    MutableList<Flashcard>,
    private val onEdit:   (Flashcard) -> Unit,
    private val onDelete: (Flashcard) -> Unit
) : RecyclerView.Adapter<FlashcardAdapter2.CardVH>() {

    private val flippedSet = mutableSetOf<Long>()

    private val frontDrawables = listOf(
        R.drawable.bg_flashcard_green, R.drawable.bg_flashcard_blue,
        R.drawable.bg_flashcard_purple, R.drawable.bg_flashcard_orange
    )
    private val backDrawables = listOf(
        R.drawable.bg_flashcard_green_dark, R.drawable.bg_flashcard_blue_dark,
        R.drawable.bg_flashcard_purple_dark, R.drawable.bg_flashcard_orange_dark
    )

    fun setData(list: List<Flashcard>) {
        cards.clear(); cards.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CardVH(LayoutInflater.from(parent.context).inflate(R.layout.item_flashcard, parent, false))

    override fun getItemCount() = cards.size
    override fun onBindViewHolder(h: CardVH, pos: Int) = h.bind(cards[pos])

    inner class CardVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTopik   : TextView    = itemView.findViewById(R.id.tvCardTopik)
        private val tvFront   : TextView    = itemView.findViewById(R.id.tvCardFront)
        private val tvBack    : TextView    = itemView.findViewById(R.id.tvCardBack)
        private val tvHint    : TextView    = itemView.findViewById(R.id.tvCardHint)
        private val btnEdit   : ImageButton = itemView.findViewById(R.id.btnCardEdit)
        private val btnDelete : ImageButton = itemView.findViewById(R.id.btnCardDelete)
        private val cardFront : View        = itemView.findViewById(R.id.layoutCardFront)
        private val cardBack  : View        = itemView.findViewById(R.id.layoutCardBack)

        fun bind(card: Flashcard) {
            tvTopik.text = card.topik
            tvFront.text = card.question
            tvBack.text  = card.answer

            val ci = card.colorIndex.coerceIn(0, 3)
            cardFront.setBackgroundResource(frontDrawables[ci])
            cardBack.setBackgroundResource(backDrawables[ci])

            val isFlipped = flippedSet.contains(card.id)
            cardFront.alpha     = if (isFlipped) 0f else 1f
            cardFront.rotationY = if (isFlipped) 180f else 0f
            cardBack.alpha      = if (isFlipped) 1f else 0f
            cardBack.rotationY  = if (isFlipped) 0f else -180f
            tvHint.text = if (isFlipped) "KETUK UNTUK SOAL" else "KETUK UNTUK JAWABAN"

            itemView.setOnClickListener { flipCard(card) }
            btnEdit.setOnClickListener  { onEdit(card)   }
            btnDelete.setOnClickListener{ onDelete(card) }
        }

        private fun flipCard(card: Flashcard) {
            val isFlipped = flippedSet.contains(card.id)
            itemView.cameraDistance = 8000 * itemView.context.resources.displayMetrics.density

            if (!isFlipped) {
                val scaleUpX   = ObjectAnimator.ofFloat(itemView, "scaleX", 1f, 1.08f).apply { duration = 100 }
                val scaleUpY   = ObjectAnimator.ofFloat(itemView, "scaleY", 1f, 1.08f).apply { duration = 100 }
                val flipOut    = ObjectAnimator.ofFloat(cardFront, "rotationY", 0f, 90f).apply { duration = 180 }
                val flipIn     = ObjectAnimator.ofFloat(cardBack, "rotationY", -90f, 0f).apply { duration = 180 }
                val fadeOut    = ObjectAnimator.ofFloat(cardFront, "alpha", 1f, 0f).apply { duration = 130; startDelay = 50 }
                val fadeIn     = ObjectAnimator.ofFloat(cardBack, "alpha", 0f, 1f).apply { duration = 130; startDelay = 50 }
                val scaleDownX = ObjectAnimator.ofFloat(itemView, "scaleX", 1.08f, 1f).apply { duration = 100 }
                val scaleDownY = ObjectAnimator.ofFloat(itemView, "scaleY", 1.08f, 1f).apply { duration = 100 }
                AnimatorSet().apply {
                    play(scaleUpX).with(scaleUpY)
                    play(flipOut).after(scaleUpX)
                    play(fadeOut).with(flipOut)
                    play(flipIn).after(flipOut)
                    play(fadeIn).with(flipIn)
                    play(scaleDownX).with(scaleDownY).after(flipIn)
                    start()
                }
                flippedSet.add(card.id)
            } else {
                val flipOut = ObjectAnimator.ofFloat(cardBack, "rotationY", 0f, 90f).apply { duration = 180 }
                val flipIn  = ObjectAnimator.ofFloat(cardFront, "rotationY", -90f, 0f).apply { duration = 180 }
                val fadeOut = ObjectAnimator.ofFloat(cardBack, "alpha", 1f, 0f).apply { duration = 130; startDelay = 50 }
                val fadeIn  = ObjectAnimator.ofFloat(cardFront, "alpha", 0f, 1f).apply { duration = 130; startDelay = 50 }
                AnimatorSet().apply {
                    play(flipOut).before(flipIn)
                    play(fadeOut).with(flipOut)
                    play(fadeIn).with(flipIn)
                    start()
                }
                flippedSet.remove(card.id)
            }
            tvHint.text = if (!isFlipped) "KETUK UNTUK SOAL" else "KETUK UNTUK JAWABAN"
        }
    }
}