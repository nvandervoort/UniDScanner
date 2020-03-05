package com.capstone.unid.unidscanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import kotlinx.android.synthetic.main.list_item_authorization_card.view.*
import java.util.*

/**
 * AuthorizationListAdapter
 * Created by nathanvandervoort on 10/23/17.
 */
class AuthorizationListAdapter(val context: Context, private val bearerToken: String?, private var auths: List<Authorization>) : RecyclerView.Adapter<AuthorizationListAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val authTitle: TextView = view.auth_title
        val authCreatorName: TextView = view.auth_creator_name
        val authSubtext: TextView = view.auth_subtext
        val subtextIcon: ImageView = view.auth_time_icon
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent?.context).inflate(R.layout.list_item_authorization_card, parent, false)
        return ViewHolder(v)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        if (holder == null) return
        val auth = auths[position]
        holder.authTitle.text = auth.name
        holder.authCreatorName.text = auth.creator

        if (auth is EventAuthorization) {
            holder.authSubtext.text = expireTimeFormat(auth.startTime, auth.endTime)
            holder.subtextIcon.visibility = View.VISIBLE
        } else {
            holder.subtextIcon.visibility = View.GONE
            holder.authSubtext.text = auth.description
        }


        holder.view.setOnClickListener {
            if (bearerToken == null) {
                context.showToast(context.getString(R.string.bearer_token_lost))
                return@setOnClickListener
            }
            val intent = Intent(context, BarcodeCaptureActivity::class.java)
            intent.putExtra(context.getString(R.string.admin_bearer_token), bearerToken)
            intent.putExtra(context.getString(R.string.AUTH_DOC_ID), auth.id)
            intent.putExtra(context.getString(R.string.IS_EVENT), auth is EventAuthorization)
            (context as Activity).startActivityForResult(intent, SCANNING_ACTIVITY)
        }

    }

    override fun getItemCount() = auths.size

    fun notifyDataSetChanged(newAuths: List<Authorization>) {
        auths = newAuths
        super.notifyDataSetChanged()
    }

    private fun expireTimeFormat(startTime: Date, endTime: Date): String =
            "${startTime.getFancyDateFormat()} ${startTime.getFancyTimeFormat()} - ${endTime.getFancyTimeFormat()}"

    companion object {
        const val SCANNING_ACTIVITY = 2
    }

}
