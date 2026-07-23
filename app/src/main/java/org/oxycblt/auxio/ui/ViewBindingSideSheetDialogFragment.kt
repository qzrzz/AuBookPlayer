/*
 * Copyright (c) 2026 Auxio Project
 * ViewBindingSideSheetDialogFragment.kt is part of Auxio.
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

package org.oxycblt.auxio.ui

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.sidesheet.SideSheetDialog
import javax.inject.Inject
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.unlikelyToBeNull
import timber.log.Timber as L

/**
 * Lifecycle-aware [DialogFragment] that hosts content in a Material 3 side sheet (drawer).
 *
 * Uses [SideSheetDialog], which defaults to the **end** edge (right in LTR). Do not call
 * [SideSheetDialog.setSheetEdge] before the dialog content is set — it throws
 * `IllegalStateException` when the sheet view is still null.
 *
 * Material's modal side sheet is full-height by default, which leaves empty bands above/below
 * short content. After the dialog starts we shrink the sheet to [ViewGroup.LayoutParams.WRAP_CONTENT]
 * and pin it to the top-end, with system-bar margins so content sits flush without status/nav gaps.
 */
abstract class ViewBindingSideSheetDialogFragment<VB : ViewBinding> : DialogFragment() {
    private var _binding: VB? = null
    @Inject lateinit var uiSettings: UISettings

    protected abstract fun onCreateBinding(inflater: LayoutInflater): VB

    protected open fun onBindingCreated(binding: VB, savedInstanceState: Bundle?) {}

    protected open fun onDestroyBinding(binding: VB) {}

    protected val binding: VB?
        get() = _binding

    protected fun requireBinding() =
        requireNotNull(_binding) {
            "ViewBinding was not available. Fragment state was ${lifecycle.currentState}"
        }

    override fun getTheme(): Int = R.style.ThemeOverlay_Auxio_SideSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return SideSheetDialog(requireContext(), theme).apply {
            setDismissWithSheetAnimationEnabled(true)
            // Avoid system-bar padding on the dialog chrome (creates top/bottom blank bands).
            // Margins are applied to the sheet itself in [onStart].
            setFitsSystemWindows(false)
        }
    }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = onCreateBinding(inflater).also { _binding = it }.root

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onBindingCreated(requireBinding(), savedInstanceState)
        L.d("Side sheet fragment created")
    }

    override fun onStart() {
        super.onStart()
        configureSheetToHugContent()
    }

    final override fun onDestroyView() {
        super.onDestroyView()
        onDestroyBinding(unlikelyToBeNull(_binding))
        _binding = null
        L.d("Side sheet fragment destroyed")
    }

    /**
     * Shrink the modal side sheet to the content height and pin it under the status bar on the end
     * edge, so short panels do not leave full-screen empty regions.
     */
    private fun configureSheetToHugContent() {
        val sideSheetDialog = dialog as? SideSheetDialog ?: return
        val sheet =
            sideSheetDialog.findViewById<View>(com.google.android.material.R.id.m3_side_sheet)
                ?: return
        val sheetWidth = resources.getDimensionPixelSize(R.dimen.side_sheet_width)
        val edgeMargin = resources.getDimensionPixelSize(R.dimen.spacing_medium)

        fun applySheetLayout(topInset: Int, bottomInset: Int, endInset: Int) {
            val lp =
                (sheet.layoutParams as? CoordinatorLayout.LayoutParams)
                    ?: CoordinatorLayout.LayoutParams(sheetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.width = sheetWidth
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.TOP or Gravity.END
            lp.topMargin = topInset + edgeMargin
            lp.bottomMargin = bottomInset + edgeMargin
            lp.marginEnd = maxOf(endInset, edgeMargin)
            lp.marginStart = 0
            sheet.layoutParams = lp
            // Cap height so long content still scrolls inside NestedScrollView.
            sheet.post {
                val parent = sheet.parent as? View ?: return@post
                val maxHeight =
                    (parent.height - lp.topMargin - lp.bottomMargin).coerceAtLeast(0)
                if (maxHeight > 0 && sheet.height > maxHeight) {
                    lp.height = maxHeight
                    sheet.layoutParams = lp
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(sheet) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isRtl = sheet.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val endInset = if (isRtl) bars.left else bars.right
            applySheetLayout(bars.top, bars.bottom, endInset)
            insets
        }
        // Initial layout before the first inset dispatch (and for APIs that skip it).
        applySheetLayout(0, 0, 0)
        ViewCompat.requestApplyInsets(sheet)
    }
}
