package com.phoneassistant.ui.recents

import android.app.Application
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.phoneassistant.MainActivity
import com.phoneassistant.data.model.CallLogEntry
import com.phoneassistant.data.model.CallType
import com.phoneassistant.data.repository.BlockedRepo
import com.phoneassistant.data.repository.CallLogRepository
import com.phoneassistant.data.repository.CallerIdRepository
import com.phoneassistant.databinding.FragmentRecentsBinding
import com.phoneassistant.databinding.ItemCallBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ─── ViewModel ───────────────────────────────────────────────────────────────
class RecentsViewModel(app: Application) : AndroidViewModel(app) {

    private val callRepo     = CallLogRepository(app)
    private val callerIdRepo = CallerIdRepository(app)
    private val blockedRepo  = BlockedRepo(app)

    private var all = listOf<CallLogEntry>()
    private var filter: CallType? = null

    private val _entries  = MutableLiveData<List<CallLogEntry>>()
    val entries: LiveData<List<CallLogEntry>> = _entries
    val loading = MutableLiveData(false)

    fun load() = viewModelScope.launch {
        loading.value = true
        val raw = callRepo.getAll()
        all = raw.map { e ->
            if (e.name == null) {
                val info = callerIdRepo.identify(e.number)
                e.copy(resolvedInfo = info?.let { i ->
                    listOfNotNull(i.carrier, i.lineType, i.location).joinToString(" • ").takeIf { it.isNotBlank() }
                })
            } else e
        }
        apply(); loading.value = false
    }

    fun setFilter(t: CallType?) { filter = t; apply() }
    fun delete(id: Long) = viewModelScope.launch { callRepo.delete(id); all = all.filter { it.id != id }; apply() }
    fun block(number: String) = viewModelScope.launch { blockedRepo.block(number, "Bloqué depuis l'historique") }
    private fun apply() { _entries.value = if (filter == null) all else all.filter { it.type == filter } }
}

// ─── Adapter ─────────────────────────────────────────────────────────────────
class CallLogAdapter(
    private val onCall: (CallLogEntry) -> Unit,
    private val onSms: (CallLogEntry) -> Unit,
    private val onLong: (CallLogEntry, View) -> Unit
) : ListAdapter<CallLogEntry, CallLogAdapter.VH>(object : DiffUtil.ItemCallback<CallLogEntry>() {
    override fun areItemsTheSame(a: CallLogEntry, b: CallLogEntry) = a.id == b.id
    override fun areContentsTheSame(a: CallLogEntry, b: CallLogEntry) = a == b
}) {
    inner class VH(private val b: ItemCallBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: CallLogEntry) {
            val display = e.name ?: e.resolvedInfo?.substringBefore(" •") ?: e.number
            b.tvName.text = display
            b.tvSub.text = buildString {
                if (e.name != null) append(e.number)
                e.resolvedInfo?.let { if (isNotEmpty()) append("  •  "); append(it) }
            }.takeIf { it.isNotBlank() } ?: e.number

            b.tvTime.text = formatTime(e.timestamp)
            b.tvDuration.text = if (e.duration > 0) formatDur(e.duration) else ""

            val (icon, color) = when (e.type) {
                CallType.INCOMING -> ("↙" to 0xFF4CAF50.toInt())
                CallType.OUTGOING -> ("↗" to 0xFF2196F3.toInt())
                CallType.MISSED   -> ("↙" to 0xFFF44336.toInt())
                CallType.REJECTED -> ("✕" to 0xFFF44336.toInt())
                CallType.BLOCKED  -> ("🚫" to 0xFF9E9E9E.toInt())
            }
            b.tvCallIcon.text = icon
            b.tvCallIcon.setTextColor(color)

            b.tvInitials.text = display.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()

            b.btnCall.setOnClickListener { onCall(e) }
            b.btnSms.setOnClickListener  { onSms(e) }
            b.root.setOnLongClickListener { v -> onLong(e, v); true }
        }
        private fun formatDur(s: Long) = if (s < 60) "${s}s" else "${s/60}min${if(s%60>0)" ${s%60}s" else ""}"
        private fun formatTime(ts: Long): String {
            val diff = System.currentTimeMillis() - ts
            return when {
                diff < 3_600_000  -> "${diff/60000}min"
                diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
                diff < 604_800_000 -> SimpleDateFormat("EEE", Locale.FRENCH).format(Date(ts))
                else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ts))
            }
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        ItemCallBinding.inflate(LayoutInflater.from(p.context), p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── Fragment ─────────────────────────────────────────────────────────────────
class RecentsFragment : Fragment() {
    private var _b: FragmentRecentsBinding? = null
    private val b get() = _b!!
    private val vm: RecentsViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentRecentsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = CallLogAdapter(
            onCall = { (activity as? MainActivity)?.call(it.number) },
            onSms  = { (activity as? MainActivity)?.sms(it.number) },
            onLong = { e, v -> showMenu(e, v) }
        )
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter

        // Chips filtres
        b.chipAll.setOnClickListener      { vm.setFilter(null) }
        b.chipMissed.setOnClickListener   { vm.setFilter(CallType.MISSED) }
        b.chipIn.setOnClickListener       { vm.setFilter(CallType.INCOMING) }
        b.chipOut.setOnClickListener      { vm.setFilter(CallType.OUTGOING) }

        vm.entries.observe(viewLifecycleOwner) {
            adapter.submitList(it)
            b.tvEmpty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
        }
        vm.loading.observe(viewLifecycleOwner) { b.progress.visibility = if (it) View.VISIBLE else View.GONE }
        vm.load()
    }

    private fun showMenu(e: CallLogEntry, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add("📞 Rappeler")
            menu.add("💬 SMS")
            menu.add("➕ Ajouter aux contacts")
            menu.add("🚫 Bloquer")
            menu.add("🗑️ Supprimer")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "📞 Rappeler" -> (activity as? MainActivity)?.call(e.number)
                    "💬 SMS"      -> (activity as? MainActivity)?.sms(e.number)
                    "➕ Ajouter aux contacts" -> {
                        val i = android.content.Intent(android.content.Intent.ACTION_INSERT_OR_EDIT).apply {
                            type = android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE
                            putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, e.number)
                        }
                        startActivity(i)
                    }
                    "🚫 Bloquer"  -> vm.block(e.number)
                    "🗑️ Supprimer" -> vm.delete(e.id)
                }
                true
            }
            show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
