package com.ahmer.pdfviewer.link

import com.ahmer.pdfviewer.model.LinkTapEvent

interface LinkHandler {
    /**
     * Called when link was tapped by user
     *
     * @param event current event
     */
    fun handleLinkEvent(event: LinkTapEvent?)
}