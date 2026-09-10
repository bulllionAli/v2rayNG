package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.viewmodel.MainViewModel

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>() {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private val doubleColumnDisplay = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    private var data: MutableList<ServersCache> = mutableListOf()

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        data = newData?.toMutableList() ?: mutableListOf()

        if (position >= 0 && position in data.indices) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = data.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val context = holder.itemMainBinding.root.context
            val guid = data[position].guid
            val profile = data[position].profile

            holder.itemView.setBackgroundColor(Color.TRANSPARENT)

            //Name address
            holder.itemMainBinding.tvName.text = profile.remarks
            holder.itemMainBinding.tvStatistics.text = getShortAddress(profile)
            holder.itemMainBinding.tvType.text = getProtocolDescription(profile)

            //TestResult
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            holder.itemMainBinding.tvTestResult.text = aff?.getTestDelayString().orEmpty()
            val pingFailed = (aff?.testDelayMillis ?: 0L) < 0L
            if (pingFailed) {
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPingRed))
            } else {
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPing))
            }

            // Fade the card's text when the last Real Delay test failed (ping -1ms);
            // restore full opacity as soon as a retest succeeds. Covers the title
            // (including any emoji in the remarks), the address:port line, and the
            // protocol line - not the ping number itself, which stays fully visible.
            val cardContentAlpha = if (pingFailed) 0.35f else 1f
            holder.itemMainBinding.tvName.alpha = cardContentAlpha
            holder.itemMainBinding.tvStatistics.alpha = cardContentAlpha
            holder.itemMainBinding.tvType.alpha = cardContentAlpha

            //layoutIndicator
            if (guid == MmkvManager.getSelectServer()) {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(R.color.colorIndicator)
            } else {
                holder.itemMainBinding.layoutIndicator.setBackgroundResource(0)
            }

            //subscription remarks
            val subRemarks = getSubscriptionRemarks(profile)
            holder.itemMainBinding.tvSubscription.text = subRemarks
            holder.itemMainBinding.layoutSubscription.visibility = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

            //layout
            val inSelectionMode = mainViewModel.isSelectionMode()
            if (inSelectionMode) {
                // Hide the per-row action icons while selecting so a stray tap can't
                // accidentally share/edit/remove a config.
                holder.itemMainBinding.layoutShare.visibility = View.GONE
                holder.itemMainBinding.layoutEdit.visibility = View.GONE
                holder.itemMainBinding.layoutRemove.visibility = View.GONE
                holder.itemMainBinding.layoutMore.visibility = View.GONE
            } else if (doubleColumnDisplay) {
                holder.itemMainBinding.layoutShare.visibility = View.GONE
                holder.itemMainBinding.layoutEdit.visibility = View.GONE
                holder.itemMainBinding.layoutRemove.visibility = View.GONE
                holder.itemMainBinding.layoutMore.visibility = View.VISIBLE

                holder.itemMainBinding.layoutMore.setOnClickListener {
                    adapterListener?.onShare(guid, profile, position, true)
                }
            } else {
                holder.itemMainBinding.layoutShare.visibility = View.VISIBLE
                holder.itemMainBinding.layoutEdit.visibility = View.VISIBLE
                holder.itemMainBinding.layoutRemove.visibility = View.VISIBLE
                holder.itemMainBinding.layoutMore.visibility = View.GONE

                holder.itemMainBinding.layoutShare.setOnClickListener {
                    adapterListener?.onShare(guid, profile, position, false)
                }

                holder.itemMainBinding.layoutEdit.setOnClickListener {
                    adapterListener?.onEdit(guid, position, profile)
                }
                holder.itemMainBinding.layoutRemove.setOnClickListener {
                    adapterListener?.onRemove(guid, position)
                }
            }

            // Selection highlight (reuses the same highlight the old drag-and-drop used).
            // The highlight background is always light gray, which makes the default
            // (near-white in dark theme) text unreadable, so force black text while selected
            // and restore each row's own original color otherwise.
            val isSelected = guid in mainViewModel.selectedGuids
            if (isSelected) {
                holder.onItemSelected()
            } else {
                holder.onItemClear()
            }
            holder.itemMainBinding.tvName.setTextColor(if (isSelected) Color.BLACK else holder.defaultNameColor)
            holder.itemMainBinding.tvStatistics.setTextColor(if (isSelected) Color.BLACK else holder.defaultStatisticsColor)
            holder.itemMainBinding.tvSubscription.setTextColor(if (isSelected) Color.BLACK else holder.defaultSubscriptionColor)

            holder.itemMainBinding.infoContainer.setOnClickListener {
                if (mainViewModel.isSelectionMode()) {
                    // MainViewModel.selectionCountAction changes here, which GroupServerFragment
                    // observes to refresh the whole list (highlight + show/hide action icons).
                    mainViewModel.toggleServerSelection(guid)
                } else {
                    adapterListener?.onSelectServer(guid)
                }
            }

            holder.itemMainBinding.infoContainer.setOnLongClickListener {
                if (!mainViewModel.isSelectionMode()) {
                    mainViewModel.startSelection(guid)
                }
                true
            }
        }

    }

    /**
     * Gets the server address information
     * Hides part of IP or domain information for privacy protection
     * @param profile The server configuration
     * @return Formatted address string
     */
    private fun getAddress(profile: ProfileItem): String {
        return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
    }

    /**
     * Compact "address:port" for the card, e.g. "98aab...:443" or "66.233...:8080".
     * Falls back to [getAddress] when there is no plain server/port (e.g. a custom config).
     * @param profile The server configuration
     * @return Truncated address:port string
     */
    private fun getShortAddress(profile: ProfileItem): String {
        val server = profile.server?.trim().orEmpty()
        val port = profile.serverPort?.trim().orEmpty()
        if (server.isEmpty()) return getAddress(profile)

        val shortServer = if (server.length > 5) server.take(5) + "..." else server
        return if (port.isNotEmpty()) "$shortServer:$port" else shortServer
    }

    /**
     * Gets the subscription remarks information
     * @param profile The server configuration
     * @return Subscription remarks string, or empty string if none
     */
    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        val subRemarks =
            if (mainViewModel.subscriptionId.isEmpty())
                MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            else
                null
        return subRemarks?.toString() ?: ""
    }

    private fun getProtocolDescription(profile: ProfileItem): String {
        if (profile.configType.isComplexType()) {
            return profile.configType.name
        }

        val parts = mutableListOf<String>()
        parts.add(profile.configType.name)

        // Transport: hide tcp or blank
        profile.network?.let { net ->
            if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) {
                parts.add(net)
            }
        }

        // Security: hide blank or tls
        profile.security?.let { sec ->
            if (sec.isNotBlank()) {
                if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                    parts.add("$sec insecure") // TODO
                } else {
                    parts.add(sec)
                }
            }
        }

        return parts.joinToString(" / ")
    }

    fun removeServerSub(guid: String, position: Int) {
        val idx = data.indexOfFirst { it.guid == guid }
        if (idx >= 0) {
            data.removeAt(idx)
            notifyItemRemoved(idx)
            notifyItemRangeChanged(idx, data.size - idx)
        }
    }

    fun setSelectServer(fromPosition: Int, toPosition: Int) {
        notifyItemChanged(fromPosition)
        notifyItemChanged(toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == data.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root) {
        // Read right after inflation, before onBindViewHolder ever runs, so these
        // are guaranteed to be the real theme defaults (white in dark mode, dark
        // in light mode) - not a value we tried to re-derive ourselves later.
        val defaultNameColor: Int = itemMainBinding.tvName.currentTextColor
        val defaultStatisticsColor: Int = itemMainBinding.tvStatistics.currentTextColor
        val defaultSubscriptionColor: Int = itemMainBinding.tvSubscription.currentTextColor
    }

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)
}
