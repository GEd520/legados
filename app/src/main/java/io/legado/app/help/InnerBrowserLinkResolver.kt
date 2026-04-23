package io.legado.app.help

import android.view.View
import io.legado.app.utils.openInInnerBrowser
import io.noties.markwon.LinkResolver

object InnerBrowserLinkResolver : LinkResolver {

    override fun resolve(view: View, link: String) {
        view.context.openInInnerBrowser(link)
    }

}
