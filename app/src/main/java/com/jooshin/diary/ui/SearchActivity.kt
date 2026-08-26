package com.jooshin.diary.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jooshin.diary.data.AppDatabase
import com.jooshin.diary.databinding.ActivitySearchBinding
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: EntryAdapter
    private val dao by lazy { AppDatabase.get(this).diaryDao() }

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var lastQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarSearch)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarSearch.setNavigationOnClickListener { finish() }

        adapter = EntryAdapter(showDate = true) { e ->
            startActivity(
                android.content.Intent(this, EntryEditorActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ENTRY_ID, e.id)
            )
        }
        binding.recyclerResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerResults.adapter = adapter

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim() ?: ""
                pending?.let { handler.removeCallbacks(it) }
                val r = Runnable { runSearch(q) }
                pending = r
                handler.postDelayed(r, 250)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (lastQuery.isNotEmpty()) runSearch(lastQuery)
    }

    private fun runSearch(q: String) {
        lastQuery = q
        if (q.isEmpty()) {
            adapter.submitList(emptyList())
            binding.tvSearchEmpty.visibility = View.VISIBLE
            binding.tvSearchEmpty.text = "검색어를 입력하세요."
            return
        }
        lifecycleScope.launch {
            val list = dao.search(q)
            adapter.submitList(list)
            binding.tvSearchEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.tvSearchEmpty.text = "검색 결과가 없습니다."
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
