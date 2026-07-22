/*
 * Copyright (c) 2026 Auxio Project
 * FolderListFragment.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.auxio.home.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentHomeListBinding
import org.oxycblt.auxio.databinding.ItemParentBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.home.HomeViewModel
import org.oxycblt.auxio.list.ClickableListListener
import org.oxycblt.auxio.list.ListViewModel
import org.oxycblt.auxio.list.adapter.SelectionIndicatorAdapter
import org.oxycblt.auxio.list.adapter.SimpleDiffCallback
import org.oxycblt.auxio.list.recycler.FastScrollRecyclerView
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.music.IndexingState
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.SongFolder
import org.oxycblt.auxio.playback.formatDurationMsPopup
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.context
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.inflater

/**
 * Home tab listing [SongFolder]s — groups songs by their source directory.
 *
 * Note: does not participate in multi-select (folders are not [org.oxycblt.musikr.Music]).
 */
@AndroidEntryPoint
class FolderListFragment :
    ViewBindingFragment<FragmentHomeListBinding>(),
    ClickableListListener<SongFolder>,
    FastScrollRecyclerView.PopupProvider,
    FastScrollRecyclerView.Listener {
    private val homeModel: HomeViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    private val listModel: ListViewModel by activityViewModels()
    private val musicModel: MusicViewModel by activityViewModels()
    private val folderAdapter = FolderAdapter(this, ::onOpenMenu)

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentHomeListBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentHomeListBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.homeRecycler.apply {
            id = R.id.home_folder_recycler
            adapter = folderAdapter
            popupProvider = this@FolderListFragment
            listener = this@FolderListFragment
        }

        binding.homeNoMusicPlaceholder.apply {
            setImageResource(R.drawable.ic_file_24)
            contentDescription = getString(R.string.lbl_folders)
        }
        binding.homeNoMusicMsg.text = getString(R.string.lng_empty_folders)
        binding.homeNoMusicAction.setOnClickListener { homeModel.startChooseMusicLocations() }

        collectImmediately(homeModel.folderList, ::updateFolders)
        collectImmediately(homeModel.empty, musicModel.indexingState, ::updateNoMusicIndicator)
    }

    override fun onDestroyBinding(binding: FragmentHomeListBinding) {
        super.onDestroyBinding(binding)
        binding.homeRecycler.apply {
            adapter = null
            popupProvider = null
            listener = null
        }
    }

    override fun onClick(item: SongFolder, viewHolder: RecyclerView.ViewHolder) {
        detailModel.showFolder(item)
    }

    private fun onOpenMenu(item: SongFolder) {
        listModel.openMenu(R.menu.folder, item)
    }

    override fun getPopupData(pos: Int): FastScrollRecyclerView.PopupProvider.PopupData? {
        val folder = homeModel.folderList.value.getOrNull(pos) ?: return null
        return when (homeModel.folderSort.mode) {
            is Sort.Mode.ByName ->
                FastScrollRecyclerView.PopupProvider.PopupData(
                    folder.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                )
            is Sort.Mode.ByDuration ->
                FastScrollRecyclerView.PopupProvider.PopupData(
                    folder.durationMs.formatDurationMsPopup()
                )
            is Sort.Mode.ByCount ->
                FastScrollRecyclerView.PopupProvider.PopupData(folder.songs.size.toString())
            else -> null
        }
    }

    override fun onFastScrollingChanged(isFastScrolling: Boolean) {
        homeModel.setFastScrolling(isFastScrolling)
    }

    private fun updateFolders(folders: List<SongFolder>) {
        folderAdapter.update(folders, homeModel.folderInstructions.consume())
    }

    private fun updateNoMusicIndicator(empty: Boolean, indexingState: IndexingState?) {
        val binding = requireBinding()
        val noFolders = homeModel.folderList.value.isEmpty()
        binding.homeRecycler.isInvisible = noFolders
        binding.homeNoMusic.isInvisible = !noFolders
        binding.homeNoMusicAction.isVisible =
            indexingState == null || (empty && indexingState is IndexingState.Completed)
    }

    private class FolderAdapter(
        private val listener: ClickableListListener<SongFolder>,
        private val openMenu: (SongFolder) -> Unit,
    ) : SelectionIndicatorAdapter<SongFolder, FolderViewHolder>(FolderViewHolder.DIFF_CALLBACK) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            FolderViewHolder.from(parent)

        override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
            holder.bind(getItem(position), listener, openMenu)
        }
    }
}

/** ViewHolder for a [SongFolder] row in the home folders tab. */
class FolderViewHolder private constructor(private val binding: ItemParentBinding) :
    SelectionIndicatorAdapter.ViewHolder(binding.root) {

    fun bind(
        folder: SongFolder,
        listener: ClickableListListener<SongFolder>,
        openMenu: (SongFolder) -> Unit,
    ) {
        listener.bind(folder, this)
        binding.parentImage.bind(
            folder.songs,
            binding.context.getString(R.string.desc_folder_image, folder.name),
            R.drawable.ic_file_24,
            folder.key.hashCode(),
        )
        binding.parentName.text = folder.name
        binding.parentInfo.text =
            binding.context.getString(
                R.string.fmt_two,
                binding.context.getPlural(R.plurals.fmt_song_count, folder.songs.size),
                folder.path.resolve(binding.context),
            )
        binding.parentMenu.isInvisible = false
        binding.parentMenu.setOnClickListener { openMenu(folder) }
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.root.isSelected = isActive
        binding.parentImage.setPlaying(isPlaying)
    }

    override fun updateSelectionIndicator(isSelected: Boolean) {
        binding.root.isActivated = isSelected
    }

    companion object {
        fun from(parent: View) =
            FolderViewHolder(ItemParentBinding.inflate(parent.context.inflater))

        val DIFF_CALLBACK =
            object : SimpleDiffCallback<SongFolder>() {
                override fun areContentsTheSame(oldItem: SongFolder, newItem: SongFolder) =
                    oldItem.key == newItem.key &&
                        oldItem.songs.size == newItem.songs.size &&
                        oldItem.durationMs == newItem.durationMs
            }
    }
}
