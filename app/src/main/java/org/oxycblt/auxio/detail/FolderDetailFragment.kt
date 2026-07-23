/*
 * Copyright (c) 2026 Auxio Project
 * FolderDetailFragment.kt is part of Auxio.
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

package org.oxycblt.auxio.detail

import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentDetailBinding
import org.oxycblt.auxio.detail.list.FolderDetailListAdapter
import org.oxycblt.auxio.list.Item
import org.oxycblt.auxio.list.menu.Menu
import org.oxycblt.auxio.music.FolderDecision
import org.oxycblt.auxio.music.PlaylistDecision
import org.oxycblt.auxio.music.PlaylistMessage
import org.oxycblt.auxio.music.SongFolder
import org.oxycblt.auxio.playback.PlaybackDecision
import org.oxycblt.auxio.util.collect
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.getPlural
import org.oxycblt.auxio.util.navigateSafe
import org.oxycblt.auxio.util.showToast
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A [DetailFragment] that shows information for a particular [SongFolder].
 *
 * Folders are app-level groupings by source directory (not Musikr [MusicParent]s). Playback uses
 * the folder's song list directly, with last-progress resume like playlists.
 */
@AndroidEntryPoint
class FolderDetailFragment : DetailFragment<SongFolder, Song>() {
    private val args: FolderDetailFragmentArgs by navArgs()
    private val folderListAdapter = FolderDetailListAdapter(this)
    private var getImageLauncher: ActivityResultLauncher<String>? = null
    private var pendingCoverFolder: SongFolder? = null

    override fun getDetailListAdapter() = folderListAdapter

    override fun getToolbarParent() = detailModel.currentFolder.value

    override fun onBindingCreated(binding: FragmentDetailBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        getImageLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                val folder = pendingCoverFolder
                pendingCoverFolder = null
                if (uri == null || folder == null) {
                    L.w("No URI/folder for cover picker")
                    return@registerForActivityResult
                }
                L.d("Received cover URI $uri for folder ${folder.key}")
                musicModel.setFolderCover(folder, uri)
            }

        detailModel.setFolder(args.folderKey)
        collectImmediately(detailModel.currentFolder, ::updateFolder)
        collectImmediately(detailModel.folderSongList, ::updateList)
        collect(detailModel.toShow.flow, ::handleShow)
        collect(listModel.menu.flow, ::handleMenu)
        collectImmediately(listModel.selected, ::updateSelection)
        collect(musicModel.playlistDecision.flow, ::handlePlaylistDecision)
        collect(musicModel.folderDecision.flow, ::handleFolderDecision)
        collect(musicModel.playlistMessage.flow, ::handlePlaylistMessage)
        collect(musicModel.folderCoverUpdates, ::onFolderCoverUpdated)
        collectImmediately(
            playbackModel.song,
            playbackModel.parent,
            playbackModel.isPlaying,
            ::updatePlayback,
        )
        collect(playbackModel.playbackDecision.flow, ::handlePlaybackDecision)
    }

    override fun onDestroyBinding(binding: FragmentDetailBinding) {
        super.onDestroyBinding(binding)
        getImageLauncher = null
        detailModel.folderSongInstructions.consume()
    }

    override fun onPlayParent(parent: SongFolder) {
        playbackModel.play(parent)
    }

    override fun onShuffleParent(parent: SongFolder) {
        // Reuse the shuffle control as "last progress" / continue (same as playlists).
        playbackModel.playFromLastProgress(parent)
    }

    override fun onRealClick(item: Song) {
        val folder = detailModel.currentFolder.value ?: return
        playbackModel.play(item, folder)
    }

    override fun onOpenParentMenu() {
        val folder = detailModel.currentFolder.value ?: return
        listModel.openMenu(R.menu.folder, folder)
    }

    override fun onOpenMenu(item: Song) {
        listModel.openMenu(R.menu.song, item, detailModel.playInFolderWith)
    }

    override fun onOpenSortMenu() {
        findNavController().navigateSafe(FolderDetailFragmentDirections.sort())
    }

    private fun onFolderCoverUpdated(key: String) {
        val folder = detailModel.currentFolder.value
        if (folder != null && folder.key == key) {
            requireBinding().detailCover.bind(folder)
        }
    }

    private fun updateFolder(folder: SongFolder?) {
        if (folder == null) {
            L.d("No folder to show, navigating away")
            findNavController().navigateUp()
            return
        }
        val binding = requireBinding()
        val context = requireContext()
        binding.detailNormalToolbar.title = folder.name
        binding.detailCover.bind(folder)
        binding.detailType.text = context.getString(R.string.lbl_folder)
        binding.detailName.text = folder.name
        binding.detailSubhead.isVisible = true
        binding.detailSubhead.text = folder.path.resolve(context)
        binding.detailInfo.text =
            context.getPlural(R.plurals.fmt_song_count, folder.songs.size)

        val playable = folder.songs.isNotEmpty()
        binding.detailPlayButton?.apply {
            isEnabled = playable
            setOnClickListener { playbackModel.play(folder) }
        }
        // Replace shuffle with "last progress" (continue) for folders / audiobooks.
        binding.detailShuffleButton?.apply {
            isEnabled = playable
            text = getString(R.string.lbl_last_progress)
            setIconResource(R.drawable.ic_history_24)
            contentDescription = getString(R.string.desc_last_progress)
            setOnClickListener { playbackModel.playFromLastProgress(folder) }
        }
        configureFolderToolbarContinue()
        setToolbarPlaybackButtonsEnabled(playable)
        updatePlayback(
            playbackModel.song.value,
            playbackModel.parent.value,
            playbackModel.isPlaying.value,
        )
    }

    /** Point the toolbar shuffle action at resume, with matching icon and label. */
    private fun configureFolderToolbarContinue() {
        binding?.detailNormalToolbar?.getMenuButton(R.id.action_shuffle)?.apply {
            setIconResource(R.drawable.ic_history_24)
            contentDescription = getString(R.string.desc_last_progress)
        }
    }

    private fun updateList(list: List<Item>) {
        folderListAdapter.update(list, detailModel.folderSongInstructions.consume())
    }

    private fun handleShow(show: Show?) {
        when (show) {
            is Show.SongDetails -> {
                L.d("Navigating to ${show.song}")
                findNavController()
                    .navigateSafe(FolderDetailFragmentDirections.showSong(show.song.uid))
            }
            is Show.SongAlbumDetails -> {
                L.d("Navigating to the album of ${show.song}")
                findNavController()
                    .navigateSafe(FolderDetailFragmentDirections.showAlbum(show.song.album.uid))
            }
            is Show.AlbumDetails -> {
                L.d("Navigating to ${show.album}")
                findNavController()
                    .navigateSafe(FolderDetailFragmentDirections.showAlbum(show.album.uid))
            }
            is Show.ArtistDetails -> {
                L.d("Navigating to ${show.artist}")
                findNavController()
                    .navigateSafe(FolderDetailFragmentDirections.showArtist(show.artist.uid))
            }
            is Show.SongArtistDecision -> {
                L.d("Navigating to artist choices for ${show.song}")
                findNavController()
                    .navigateSafe(FolderDetailFragmentDirections.showArtistChoices(show.song.uid))
            }
            is Show.AlbumArtistDecision -> {
                L.d("Navigating to artist choices for ${show.album}")
                findNavController()
                    .navigateSafe(FolderDetailFragmentDirections.showArtistChoices(show.album.uid))
            }
            is Show.FolderDetails -> {
                L.d("Navigated to this folder")
                detailModel.toShow.consume()
            }
            is Show.GenreDetails,
            is Show.PlaylistDetails -> {
                error("Unexpected show command $show")
            }
            null -> {}
        }
    }

    private fun handleMenu(menu: Menu?) {
        if (menu == null) return
        val directions =
            when (menu) {
                is Menu.ForSong -> FolderDetailFragmentDirections.openSongMenu(menu.parcel)
                is Menu.ForFolder -> FolderDetailFragmentDirections.openFolderMenu(menu.parcel)
                is Menu.ForSelection -> FolderDetailFragmentDirections.openSelectionMenu(menu.parcel)
                is Menu.ForAlbum,
                is Menu.ForArtist,
                is Menu.ForGenre,
                is Menu.ForPlaylist -> error("Unexpected menu $menu")
            }
        findNavController().navigateSafe(directions)
    }

    private fun updateSelection(selected: List<Music>) {
        folderListAdapter.setSelected(selected.toSet())

        val binding = requireBinding()
        if (selected.isNotEmpty()) {
            binding.detailSelectionToolbar.title = getString(R.string.fmt_selected, selected.size)
            binding.detailToolbar.setVisible(R.id.detail_selection_toolbar)
        } else {
            binding.detailToolbar.setVisible(R.id.detail_normal_toolbar)
        }
    }

    private fun handlePlaylistDecision(decision: PlaylistDecision?) {
        if (decision == null) return
        val directions =
            when (decision) {
                is PlaylistDecision.Add -> {
                    L.d("Adding ${decision.songs.size} songs to a playlist")
                    FolderDetailFragmentDirections.addToPlaylist(
                        decision.songs.map { it.uid }.toTypedArray()
                    )
                }
                is PlaylistDecision.New -> {
                    L.d("Creating new playlist with ${decision.songs.size} songs")
                    FolderDetailFragmentDirections.newPlaylist(
                        decision.songs.map { it.uid }.toTypedArray(),
                        decision.template,
                        decision.reason,
                    )
                }
                is PlaylistDecision.Import,
                is PlaylistDecision.Rename,
                is PlaylistDecision.Export,
                is PlaylistDecision.Delete,
                is PlaylistDecision.SetCover -> error("Unexpected playlist decision $decision")
            }
        findNavController().navigateSafe(directions)
    }

    private fun handleFolderDecision(decision: FolderDecision?) {
        if (decision == null) return
        when (decision) {
            is FolderDecision.SetCover -> {
                L.d("Setting cover for folder ${decision.folder.key}")
                pendingCoverFolder = decision.folder
                requireNotNull(getImageLauncher) { "Image picker launcher was not available" }
                    .launch("image/*")
                musicModel.folderDecision.consume()
            }
        }
    }

    private fun handlePlaylistMessage(message: PlaylistMessage?) {
        if (message == null) return
        requireContext().showToast(message.stringRes)
        musicModel.playlistMessage.consume()
    }

    private fun updatePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        // Folders have no MusicParent; highlight the playing song when it is in this folder.
        val currentFolder = detailModel.currentFolder.value ?: return
        val playingItem =
            song?.takeIf { folderSong -> currentFolder.songs.any { it.uid == folderSong.uid } }
        folderListAdapter.setPlaying(playingItem, isPlaying)
    }

    private fun handlePlaybackDecision(decision: PlaybackDecision?) {
        if (decision == null) return
        val directions =
            when (decision) {
                is PlaybackDecision.PlayFromArtist -> {
                    L.d("Launching play from artist dialog for $decision")
                    FolderDetailFragmentDirections.playFromArtist(decision.song.uid)
                }
                is PlaybackDecision.PlayFromGenre -> {
                    L.d("Launching play from genre dialog for $decision")
                    FolderDetailFragmentDirections.playFromGenre(decision.song.uid)
                }
            }
        findNavController().navigateSafe(directions)
    }
}
