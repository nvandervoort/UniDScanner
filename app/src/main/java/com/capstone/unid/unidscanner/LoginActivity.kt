package com.capstone.unid.unidscanner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.AlertDialog
import android.app.LoaderManager.LoaderCallbacks
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_login.*
import net.smartam.leeloo.client.request.OAuthClientRequest
import java.util.*


/**
 * A login screen that offers login with Workday.
 */
class LoginActivity : AppCompatActivity(), LoaderCallbacks<Cursor> {
    private lateinit var loginEntryListAdapter: LoginListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        Log.d("LOGIN_ACTIVITY", "onCreate called")

        val workdayFont = Typeface.createFromAsset(assets, "fonts/khmnettra.ttf")
        login_with_workday.typeface = workdayFont

        loginEntryListAdapter = LoginListAdapter(this)
        login_recycler_view.layoutManager = LinearLayoutManager(this)
        login_recycler_view.adapter = loginEntryListAdapter

        add_new_user.setOnClickListener { requestToken() }

        val uri = intent?.data
        if (uri != null && uri.toString().startsWith(redirectUri)) {
            showProgress(true)
            val loginEntryIndex = privatePrefs.getInt(LOGIN_ENTRY_INDEX_KEY, -1)

            val rawToken = uri.encodedFragment
            if (!rawToken.endsWith("&token_type=Bearer")) return
            val token = rawToken.removeSurrounding("access_token=","&token_type=Bearer")

            // Test that the API endpoint is being hit
            SingleToast.show(this@LoginActivity, "Testing login", Toast.LENGTH_LONG)
            val testAPI = WorkdayApiHandler(this, token)
            testAPI.isConnected(object: VolleyCallback<Boolean>() {
                override fun onSuccess(response: Boolean) {
                    if (response) {
                        if (loginEntryIndex == -1) createNewLoginEntry(token)
                        else {
                            loginEntryListAdapter.setTokenByIndex(loginEntryIndex, token)
                            SingleToast.show(this@LoginActivity, "Login updated")
                            startAuthSelectActivity(token)
                        }
                    }
                    else SingleToast.show(this@LoginActivity,"Error retreiving data from Workday Student")
                }

                override fun onFailure() {
                    SingleToast.show(this@LoginActivity, "Error connecting to Workday Student")
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent?.data == null || !intent?.data.toString().startsWith(redirectUri)) {
            showProgress(false)
        }
    }

    private fun createNewLoginEntry(token: String) {
        SingleToast.show(this, "Login successful")
        val newLoginEntryEditText = EditText(this)
        newLoginEntryEditText.hint = "username"

        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.leftMargin = resources.getDimensionPixelSize(R.dimen.dialog_edittext_margin)
        params.rightMargin = resources.getDimensionPixelSize(R.dimen.dialog_edittext_margin)
        newLoginEntryEditText.layoutParams = params
        container.addView(newLoginEntryEditText)

        AlertDialog.Builder(this)
                .setTitle("Remember User?")
                .setView(container)
                .setPositiveButton("Save", { _, _ ->
                            loginEntryListAdapter.addNewLoginEntry(newLoginEntryEditText.text.toString(), token)
                            startAuthSelectActivity(token)
                })
                .setNegativeButton("Don't Save", { _, _ ->
                            startAuthSelectActivity(token)
                        })
                .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTHORIZATION_SELECT) {
            showProgress(false)
            if (resultCode == Activity.RESULT_OK) {
                loginEntryListAdapter.notifyDataSetChanged()
                showToast("User successfully logged out")
            }
        } else showProgress(false)
    }

    internal fun requestToken(index: Int = -1) {
        showProgress(true)
        val editor = privatePrefs.edit()
        editor.putInt(LOGIN_ENTRY_INDEX_KEY, index)
        editor.apply()

        // build request for bearer token
        val request = OAuthClientRequest
                .authorizationLocation(authLocation)
                .setClientId(clientID)
                .setRedirectURI(redirectUri)
                .buildQueryMessage()

        val oauthIntent = Intent(Intent.ACTION_VIEW, Uri.parse(request.locationUri + "&response_type=access_token"))
        startActivity(oauthIntent)
    }

    internal fun startAuthSelectActivity(token: String) {
        val intent = Intent(this@LoginActivity, SelectAuthorizationActivity::class.java)
        intent.putExtra(getString(R.string.admin_bearer_token), token)
        startActivityForResult(intent, AUTHORIZATION_SELECT)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.login_activity_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear_login_entries -> loginEntryListAdapter.deleteAllLoginEntries()
        }
        return true
    }


    /**
     * Shows the progress UI and hides the login button.
     */
    internal fun showProgress(show: Boolean) {
        val animTime = ANIM_TIME_SHORT

        for (view in arrayOf(login_with_workday, login_recycler_view, add_new_user)) {
            view.visibility = if (show) View.GONE else View.VISIBLE
            view.animate()
                    .setDuration(animTime)
                    .alpha((!show).toInt().toFloat())
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            view.visibility = if (show) View.GONE else View.VISIBLE
                        }
                    })
        }

        login_progress.visibility = if (show) View.VISIBLE else View.GONE
        login_progress.animate()
                .setDuration(animTime)
                .alpha(show.toInt().toFloat())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        login_progress.visibility = if (show) View.VISIBLE else View.GONE
                    }
                })
    }

    override fun onCreateLoader(i: Int, bundle: Bundle?): Loader<Cursor> {
        return CursorLoader(this,
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE + " = ?", arrayOf(ContactsContract.CommonDataKinds.Email
                .CONTENT_ITEM_TYPE),

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC")
    }

    override fun onLoadFinished(cursorLoader: Loader<Cursor>, cursor: Cursor) {
        val emails = ArrayList<String>()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS))
            cursor.moveToNext()
        }
    }

    override fun onLoaderReset(cursorLoader: Loader<Cursor>) {

    }

    companion object {
        const val AUTHORIZATION_SELECT = 3

        /* The following declarations contain sensitive information. */
        private const val authLocation = "authLocation"  // placeholder
        private const val tokenLocation= "tokenLocatio"  // placeholder
        private const val clientID = "clientID"  // placeholder
        private const val clientSecret = "clientSecret"  // placeholder
        private const val redirectUri = "redirectUri"  // placeholder

        private const val LOGIN_ENTRY_INDEX_KEY = "login_entry_token_request_index"
    }

    object ProfileQuery {
        val PROJECTION = arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY)
        const val ADDRESS = 0
        const val IS_PRIMARY = 1
    }
}
