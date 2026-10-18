package app.bertopia

import app.bertopia.store.ShareOutcome
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

class IosPackSharer : PackSharer {
    override fun share(path: String, fileName: String, onDone: (ShareOutcome) -> Unit) {
        val presenter = presentingController()
        if (presenter == null) {
            onDone(ShareOutcome.Failed)
            return
        }
        val activity = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        activity.popoverPresentationController?.sourceView = presenter.view
        activity.completionWithItemsHandler = { _, completed, _, _ ->
            onDone(if (completed) ShareOutcome.Shared else ShareOutcome.Dismissed)
        }
        presenter.presentViewController(activity, animated = true, completion = null)
    }

    private fun presentingController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
}
