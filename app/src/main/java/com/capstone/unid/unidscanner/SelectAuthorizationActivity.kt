package com.capstone.unid.unidscanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.android.synthetic.main.activity_select_authorization.*
import java.lang.RuntimeException
import java.util.*

class SelectAuthorizationActivity : AppCompatActivity() {
    private lateinit var authListAdapter: AuthorizationListAdapter
    private var auths: List<Authorization> = listOf()
    private val db = FirebaseFirestore.getInstance()

    private var optionsMenu: Menu? = null
    private var eventsShowing = true  // keep track of which Auth list is showing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_authorization)

        Log.d("SELECT_AUTH", "SelectAuthorizationActivity content view set")

        val bearerToken = intent?.getStringExtra(getString(R.string.admin_bearer_token))
        if (bearerToken == null) showBearerTokenLostAlert()

        val gameRecyclerView = authorization_recycler_view
        val llm = LinearLayoutManager(this@SelectAuthorizationActivity)
        gameRecyclerView.layoutManager = llm
        authListAdapter = AuthorizationListAdapter(this@SelectAuthorizationActivity, bearerToken, auths)
        gameRecyclerView.adapter = authListAdapter

        swipe_refresh_auths.setOnRefreshListener { refresh() }

        swipe_refresh_auths.isRefreshing = true
        refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AuthorizationListAdapter.SCANNING_ACTIVITY
                && resultCode == Activity.RESULT_OK) {
            logout()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.select_auth_activity_options, menu)
        optionsMenu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh_authorizations ->  { swipe_refresh_auths.isRefreshing = true; refresh() }
            R.id.switch_auth_type -> switchAuthType()
            R.id.logout -> logout()
        }
        return true
    }

    private fun switchAuthType() {
        swipe_refresh_auths.isRefreshing = true
        eventsShowing = !eventsShowing
        refresh()
    }

    private fun Task<QuerySnapshot>.getAuthResults(cls: Class<out Authorization>): List<Authorization> =
            result.map {
                try {
                    val auth = it.toObject(cls)
                    auth.id = it.id
                    auth
                } catch(e: RuntimeException) {
                    Authorization()
                }
            }.filter { !it.error }

    private fun refresh() {
        if (eventsShowing) {
            title = "Event Selection"  // set activity title
            optionsMenu?.findItem(R.id.switch_auth_type)?.setTitle(R.string.switch_to_facilities)
            db.collection("events")
                    .whereGreaterThan("endTime", Date())
                    .orderBy("endTime")
                    .get()
                    .addOnCompleteListener { task: Task<QuerySnapshot> ->
                        if (task.isSuccessful) {
                            auths = task.getAuthResults(EventAuthorization::class.java)
                            authListAdapter.notifyDataSetChanged(auths)
                            swipe_refresh_auths.isRefreshing = false
                        } else {
                            showToast("Error loading events")
                            swipe_refresh_auths.isRefreshing = false
                        }
                    }
        } else {
            title = "Facility Selection"  // set activity title
            optionsMenu?.findItem(R.id.switch_auth_type)?.setTitle(R.string.switch_to_events)
            db.collection("facilities")
                    .orderBy("creator")
                    .get()
                    .addOnCompleteListener { task: Task<QuerySnapshot> ->
                        if (task.isSuccessful) {
                            auths = task.getAuthResults(FacilityAuthorization::class.java)
                            authListAdapter.notifyDataSetChanged(auths)
                            swipe_refresh_auths.isRefreshing = false
                        } else {
                            showToast("Error loading facilities")
                            swipe_refresh_auths.isRefreshing = false
                        }
                    }
        }
    }

    private fun logout() {
        val intent = Intent(this, LoginActivity::class.java)
        setResult(RESULT_OK, intent)
        finish()
    }

    companion object {
        val EXAMPLE_AUTHS = listOf(
                FacilityAuthorization("Recreation Center", "a",
                        "Facility with exercise equipment, field, pool, activities, and classes. Open to all enrolled students.",
                        "Rec Cen Staff"),
                EventAuthorization("Delirium ft. tChami", "b",
                        "EDM concert, ticket required.",
                        "AS Program Board Presents", "Thunderdome",
                        "10-28-2017 20:30".getSimpleDate(), "10-28-2017 23:30".getSimpleDate()),
                EventAuthorization("It", "c",
                        "Horror/Thriller film, screened at IV Theater 1 for $4",
                        "Magic Lantern Films", "IV Theater 1",
                        "01-01-2000 21:00".getSimpleDate(), "01-01-2000 23:15".getSimpleDate()))

        // all check-in dates guaranteed to be later than this
        private val EARLIEST_DATE = "00-00-1944 12:00".getSimpleDate()
    }

}
