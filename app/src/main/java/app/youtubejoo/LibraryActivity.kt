package app.youtubejoo

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Shows bookmarks or history. Returns the chosen URL via result extra "url". */
class LibraryActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var mode: String
    private val items = mutableListOf<Store.Entry>()
    private lateinit var adapter: ArrayAdapter<Store.Entry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        mode = intent.getStringExtra("mode") ?: "bookmarks"
        title = getString(if (mode == "history") R.string.history_title else R.string.bookmarks_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val list = ListView(this)
        val empty = TextView(this).apply { text = getString(R.string.empty); gravity = Gravity.CENTER }
        val root = FrameLayout(this)
        root.addView(list, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        list.emptyView = empty
        setContentView(root)

        adapter = object : ArrayAdapter<Store.Entry>(this, android.R.layout.simple_list_item_2, android.R.id.text1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                val e = items[position]
                v.findViewById<TextView>(android.R.id.text1).apply { text = e.title; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                v.findViewById<TextView>(android.R.id.text2).apply { text = e.url; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            setResult(RESULT_OK, Intent().putExtra("url", items[pos].url)); finish()
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this).setMessage(R.string.delete_q)
                .setPositiveButton(android.R.string.ok) { _, _ -> store.remove(mode, items[pos].url); reload() }
                .setNegativeButton(android.R.string.cancel, null).show()
            true
        }
        reload()
    }

    private fun reload() {
        items.clear()
        items.addAll(if (mode == "history") store.history() else store.bookmarks())
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (mode == "history") menu.add(0, 1, 0, R.string.clear_all).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) { store.clearHistory(); reload(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
