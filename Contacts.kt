package com.phoneassistant.ui.contacts

import android.app.Application
import android.os.Bundle
import android.view.*
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phoneassistant.MainActivity
import com.phoneassistant.data.db.AppDatabase
import com.phoneassistant.data.model.Contact
import com.phoneassistant.data.model.ContactNote
import com.phoneassistant.data.model.FavoriteContact
import com.phoneassistant.data.repository.BlockedRepo
import com.phoneassistant.data.repository.CallerIdRepository
import com.phoneassistant.data.repository.ContactsRepository
import com.phoneassistant.databinding.FragmentContactsBinding
import com.phoneassistant.databinding.FragmentDetailBinding
import com.phoneassistant.databinding.ItemContactBinding
import kotlinx.coroutines.launch

// ─── Contacts List ViewModel ─────────────────────────────────────────────────
class ContactsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ContactsRepository(app)
    private var all = listOf<Contact>()
    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts
    val loading = MutableLiveData(false)

    fun load() = viewModelScope.launch {
        loading.value = true
        all = repo.getAll()
        _contacts.value = all
        loading.value = false
    }

    fun search(q: String) {
        _contacts.value = if (q.isBlank()) all
        else { val lq = q.lowercase(); all.filter { c -> c.name.lowercase().contains(lq) || c.phoneNumbers.any { it.number.contains(lq) } } }
    }
}

// ─── Contacts Adapter ────────────────────────────────────────────────────────
class ContactsAdapter(
    private val onCall: (Contact) -> Unit,
    private val onClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactsAdapter.VH>(object : DiffUtil.ItemCallback<Contact>() {
    override fun areItemsTheSame(a: Contact, b: Contact) = a.id == b.id
    override fun areContentsTheSame(a: Contact, b: Contact) = a == b
}) {
    private val palette = listOf(0xFF1976D2, 0xFF388E3C, 0xFFD32F2F, 0xFF7B1FA2, 0xFFF57C00, 0xFF0288D1, 0xFF00796B).map { it.toInt() }

    inner class VH(private val b: ItemContactBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Contact) {
            b.tvName.text = c.name
            b.tvNumber.text = c.phoneNumbers.firstOrNull()?.let { "${it.number} • ${it.type}" } ?: ""
            b.tvInitials.text = c.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
            b.avatarBg.setBackgroundColor(palette[(c.name.firstOrNull()?.code ?: 0) % palette.size])
            b.ivStar.visibility = if (c.isFavorite) View.VISIBLE else View.GONE
            b.btnCall.setOnClickListener { onCall(c) }
            b.root.setOnClickListener { onClick(c) }
        }
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(ItemContactBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))
}

// ─── Contacts Fragment ───────────────────────────────────────────────────────
class ContactsFragment : Fragment() {
    private var _b: FragmentContactsBinding? = null
    private val b get() = _b!!
    private val vm: ContactsViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentContactsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ContactsAdapter(
            onCall = { c -> c.phoneNumbers.firstOrNull()?.let { (activity as? MainActivity)?.call(it.number) } },
            onClick = { c ->
                val detail = ContactDetailFragment().apply { arguments = Bundle().apply { putLong("id", c.id) } }
                parentFragmentManager.beginTransaction().replace(id, detail).addToBackStack(null).commit()
            }
        )
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter
        b.search.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            override fun onQueryTextChange(q: String?): Boolean { vm.search(q ?: ""); return true }
        })
        vm.contacts.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            b.tvCount.text = "${list.size} contact${if (list.size > 1) "s" else ""}"
            b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        vm.loading.observe(viewLifecycleOwner) { b.progress.visibility = if (it) View.VISIBLE else View.GONE }
        vm.load()
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ─── Contact Detail Fragment ─────────────────────────────────────────────────
class ContactDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val db          = AppDatabase.get(app)
    private val contactRepo = ContactsRepository(app)
    private val callerRepo  = CallerIdRepository(app)
    private val blockedRepo = BlockedRepo(app)

    val contact    = MutableLiveData<Contact?>()
    val callerInfo = MutableLiveData<String?>()
    val note       = MutableLiveData("")
    val isFav      = MutableLiveData(false)
    val isBlocked  = MutableLiveData(false)

    fun load(contactId: Long) = viewModelScope.launch {
        val c = contactRepo.getAll().find { it.id == contactId }
        contact.value = c
        note.value    = db.contactNoteDao().get(contactId)?.note ?: ""
        isFav.value   = db.favoriteDao().find(contactId) != null

        c?.phoneNumbers?.firstOrNull()?.let { p ->
            isBlocked.value = blockedRepo.isBlocked(p.number)
            val info = callerRepo.identify(p.number)
            callerInfo.value = info?.let { i ->
                listOfNotNull(
                    i.carrier?.let { "📡 $it" },
                    i.lineType?.let { "📱 $it" },
                    i.location?.let { "📍 $it" },
                    if (i.isSpam) "⚠️ Signalé comme SPAM" else null
                ).joinToString("\n").takeIf { it.isNotBlank() }
            }
        }
    }

    fun saveNote(id: Long, text: String) = viewModelScope.launch {
        db.contactNoteDao().save(ContactNote(id, text)); note.value = text
    }
    fun toggleFav(id: Long) = viewModelScope.launch {
        if (isFav.value == true) { db.favoriteDao().delete(id); isFav.value = false }
        else { db.favoriteDao().insert(FavoriteContact(id)); isFav.value = true }
    }
    fun toggleBlock(number: String) = viewModelScope.launch {
        if (isBlocked.value == true) { blockedRepo.unblock(number); isBlocked.value = false }
        else { blockedRepo.block(number); isBlocked.value = true }
    }
}

class ContactDetailFragment : Fragment() {
    private var _b: FragmentDetailBinding? = null
    private val b get() = _b!!
    private val vm: ContactDetailViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDetailBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val contactId = arguments?.getLong("id") ?: return
        vm.load(contactId)

        vm.contact.observe(viewLifecycleOwner) { c ->
            c ?: return@observe
            b.tvName.text = c.name
            b.tvInitials.text = c.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
            b.chipGroup.removeAllViews()
            c.phoneNumbers.forEach { p ->
                val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                    text = "${p.number} (${p.type})"
                    setOnClickListener { (activity as? MainActivity)?.call(p.number) }
                }
                b.chipGroup.addView(chip)
            }
        }
        vm.callerInfo.observe(viewLifecycleOwner) { info ->
            b.cardCallerInfo.visibility = if (info != null) View.VISIBLE else View.GONE
            b.tvCallerInfo.text = info
        }
        vm.note.observe(viewLifecycleOwner) { b.etNote.setText(it) }
        vm.isFav.observe(viewLifecycleOwner) { b.btnFav.text = if (it) "★ Retirer des favoris" else "☆ Ajouter aux favoris" }
        vm.isBlocked.observe(viewLifecycleOwner) { b.btnBlock.text = if (it) "✅ Débloquer" else "🚫 Bloquer ce numéro" }

        b.btnSaveNote.setOnClickListener { vm.saveNote(contactId, b.etNote.text.toString()) }
        b.btnFav.setOnClickListener { vm.toggleFav(contactId) }
        b.btnBlock.setOnClickListener {
            val number = vm.contact.value?.phoneNumbers?.firstOrNull()?.number ?: return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (vm.isBlocked.value == true) "Débloquer ce numéro ?" else "Bloquer ce numéro ?")
                .setMessage(number)
                .setPositiveButton("Confirmer") { _, _ -> vm.toggleBlock(number) }
                .setNegativeButton("Annuler", null).show()
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
