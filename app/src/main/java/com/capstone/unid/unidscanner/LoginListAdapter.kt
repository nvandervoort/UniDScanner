package com.capstone.unid.unidscanner

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.list_item_login.view.*
import java.util.*

/**
 * LoginListAdapter
 * Created by nathanvandervoort on 12/15/17.
 *
 * adapter for list of known Workday logins in [LoginActivity]
 */
class LoginListAdapter(private val activity: LoginActivity) : RecyclerView.Adapter<LoginListAdapter.ViewHolder>() {
    private val prefs = activity.privatePrefs

    override fun getItemCount(): Int {
        return prefs.getInt(LOGIN_COUNT_KEY, 0)
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): LoginListAdapter.ViewHolder {
        val v = LayoutInflater.from(parent?.context).inflate(R.layout.list_item_login, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: LoginListAdapter.ViewHolder?, @SuppressLint("RecyclerView") position: Int) {
        if (holder == null) return

        holder.name.text = prefs.getString(LOGIN_ENTRY_NAME_KEY(position), "Error loading entry $position")
        holder.lastUsed.text = prefs.getString(LOGIN_ENTRY_LAST_USED_KEY(position), "00-00-0000 01:00").getSimpleDate().getFancyDateTimeFormat()
        holder.view.setOnClickListener {
            activity.showProgress(true)
            // test bearer token -> start activity if works, else go get new one
            SingleToast.show(activity, "Validating", Toast.LENGTH_LONG)
            val token = getTokenByIndex(position)
            if (token != null) {
                WorkdayApiHandler(activity, token).isConnected(object: VolleyCallback<Boolean>() {
                    override fun onSuccess(response: Boolean) {
                        if (response) {
                            SingleToast.hide()
                            activity.startAuthSelectActivity(token)
                        }
                        else {
                            activity.showProgress(false)
                            SingleToast.show(activity, "Error connecting to Workday Student")
                        }
                    }

                    override fun onFailure() {
                        SingleToast.show(activity, "Token expired, logging in")
                        Thread.sleep(200)
                        activity.requestToken(position)
                    }
                })
            } else SingleToast.show(activity, "Error loading token")
        }

        holder.deleteEntry.setImageResource(R.drawable.ic_delete_black_36dp)
        holder.deleteEntry.tag = false
        holder.deleteEntry.setOnClickListener {
            if (it.tag == true) deleteLoginEntry(position)
            else {
                it.tag = true
                (it as ImageButton).setImageResource(R.drawable.ic_delete_forever_black_36dp)
            }
        }
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view){
        val name: TextView = view.login_name
        val lastUsed: TextView = view.last_used
        val deleteEntry: ImageButton = view.delete_login_entry
    }

    private fun updateLoginEntryDate(index: Int, editor: SharedPreferences.Editor) {
        editor.putString(LOGIN_ENTRY_LAST_USED_KEY(index), Date().getSimpleDateTimeFormat())
    }

    fun addNewLoginEntry(username: String, token: String) {
        val nextIndex = itemCount
        val editor = prefs.edit()
        editor.putString(LOGIN_ENTRY_NAME_KEY(nextIndex), username)
        updateLoginEntryDate(nextIndex, editor)
        editor.putString(LOGIN_ENTRY_TOKEN_KEY(nextIndex), token)
        editor.putInt(LOGIN_COUNT_KEY, nextIndex + 1)
        editor.apply()
        notifyDataSetChanged()
        activity.showProgress(false)
    }

    private fun deleteLoginEntry(index: Int) {
        val prefValues = prefs.all
        val numEntries = prefValues[LOGIN_COUNT_KEY] as Int
        val editor = prefs.edit()
        editor.remove(LOGIN_ENTRY_NAME_KEY(index))
        editor.remove(LOGIN_ENTRY_LAST_USED_KEY(index))
        editor.remove(LOGIN_ENTRY_TOKEN_KEY(index))
        editor.putInt(LOGIN_COUNT_KEY, numEntries - 1)

        for (i in index+1 until numEntries) {
            editor.putString(LOGIN_ENTRY_NAME_KEY(i-1), prefValues[LOGIN_ENTRY_NAME_KEY(i)] as String)
            editor.putString(LOGIN_ENTRY_LAST_USED_KEY(i-1), prefValues[LOGIN_ENTRY_LAST_USED_KEY(i)] as String)
            editor.putString(LOGIN_ENTRY_TOKEN_KEY(i-1), prefValues[LOGIN_ENTRY_TOKEN_KEY(i)] as String)
        }

        editor.apply()
        notifyDataSetChanged()
    }

    internal fun deleteAllLoginEntries() {
        prefs.edit().clear().apply()
        notifyDataSetChanged()
    }

    private fun getTokenByIndex(index: Int): String? {
        val editor = prefs.edit()
        updateLoginEntryDate(index, editor)
        editor.apply()
        notifyDataSetChanged()
        return prefs.getString(LOGIN_ENTRY_TOKEN_KEY(index), null)
    }

    fun setTokenByIndex(index: Int, token: String) {
        val editor = prefs.edit()
        editor.putString(LOGIN_ENTRY_TOKEN_KEY(index), token)
        updateLoginEntryDate(index, editor)
        editor.apply()
    }

    @Suppress("FunctionName")
    companion object {
        internal const val LOGIN_COUNT_KEY = "login_count"

        private fun LOGIN_ENTRY_NAME_KEY(index: Int) = "login_entry_name_$index"
        private fun LOGIN_ENTRY_LAST_USED_KEY(index: Int) = "login_entry_last_used_$index"
        private fun LOGIN_ENTRY_TOKEN_KEY(index: Int) = "login_entry_token_$index"
    }

}