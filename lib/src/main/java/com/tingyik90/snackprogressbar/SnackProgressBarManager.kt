package com.tingyik90.snackprogressbar

import android.support.annotation.*
import android.support.annotation.IntRange
import android.support.design.widget.BaseTransientBottomBar
import android.support.design.widget.CoordinatorLayout
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.util.*

/**
 * Manager class handling all the SnackProgressBars added.
 * <p>
 * This class queues the SnackProgressBars to be shown.
 * It will dismiss the SnackProgressBar according to its desired duration before showing the next in queue.
 * </p>
 *
 * @constructor If possible, the root view of the activity should be provided and can be any type of layout.
 * The constructor will loop and crawl up the view hierarchy to find a suitable parent view if non-root view is provided.
 *
 * <p>If a CoordinatorLayout is provided, the FloatingActionButton will animate with the SnackProgressBar.
 * Else, [setViewsToMove] needs to be called to select the views to be animated.
 * Note that [setViewsToMove] can still be used for CoordinatorLayout to move other views.</p>
 *
 * <p>Swipe to dismiss behaviour can be set via [SnackProgressBar.setSwipeToDismiss].
 * This is provided via [BaseTransientBottomBar] for CoordinatorLayout, but provided via
 * [SnackProgressBarLayout] for other layout types.</p>
 *
 * @param view View to hold the SnackProgressBar.
 */
@Keep
class SnackProgressBarManager(view: View) {

    @Retention(AnnotationRetention.SOURCE)
    @IntRange(from = 1)
    annotation class OneUp

    companion object {

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(LENGTH_LONG, LENGTH_SHORT, LENGTH_INDEFINITE)
        @IntRange(from = 1)
        annotation class ShowDuration

        /**
         * Show the SnackProgressBar indefinitely.
         * Note that this will be changed to LENGTH_SHORT and dismissed
         * if there is another SnackProgressBar in queue before and after.
         */
        const val LENGTH_INDEFINITE = BaseTransientBottomBar.LENGTH_INDEFINITE.toLong()
        /**
         * Show the SnackProgressBar for a short period of time.
         */
        const val LENGTH_SHORT = BaseTransientBottomBar.LENGTH_SHORT.toLong()
        /**
         * Show the SnackProgressBar for a long period of time.
         */
        const val LENGTH_LONG = BaseTransientBottomBar.LENGTH_LONG.toLong()

        /**
         * Default SnackProgressBar background color as per Material Design.
         */
        @JvmField
        val BACKGROUND_COLOR_DEFAULT = R.color.background
        /**
         * Default message text color as per Material Design.
         */
        @JvmField
        val MESSAGE_COLOR_DEFAULT = R.color.textWhitePrimary
        /**
         * Default action text color as per Material Design i.e. R.color.colorAccent.
         */
        @JvmField
        val ACTION_COLOR_DEFAULT = R.color.colorAccent
        /**
         * Default progressBar color as per Material Design i.e. R.color.colorAccent.
         */
        @JvmField
        val PROGRESSBAR_COLOR_DEFAULT = R.color.colorAccent
        /**
         * Default overlayLayout color i.e. android.R.color.white.
         */
        @JvmField
        val OVERLAY_COLOR_DEFAULT = android.R.color.white
    }

    /* variables */
    private val onDisplayIdDefault = -1
    private val storedBars = HashMap<Int, SnackProgressBar>()
    private val queueBars = ArrayList<SnackProgressBar>()
    private val queueOnDisplayIds = ArrayList<Int>()
    private val queueDurations = ArrayList<Long>()

    private var currentQueue = 0
    private var currentBar: SnackProgressBar? = null
    private var currentCore: SnackProgressBarCore? = null
    private var parentView: ViewGroup = findSuitableParent(view)
    private var viewsToMove: Array<View>? = null
    private var backgroundColor = BACKGROUND_COLOR_DEFAULT
    private var messageTextColor = MESSAGE_COLOR_DEFAULT
    private var actionTextColor = ACTION_COLOR_DEFAULT
    private var progressBarColor = PROGRESSBAR_COLOR_DEFAULT
    private var overlayColor = OVERLAY_COLOR_DEFAULT
    private var overlayLayoutAlpha = 0.8f
    private var onDisplayListener: OnDisplayListener? = null

    /**
     * Interface definition for a callback to be invoked when the SnackProgressBar is shown or dismissed.
     */
    interface OnDisplayListener {
        /**
         * Called when the SnackProgressBar is shown.
         *
         * @param onDisplayId OnDisplayId assigned to the SnackProgressBar which is shown.
         */
        fun onShown(onDisplayId: Int)

        /**
         * Called when the SnackProgressBar is dismissed.
         *
         * @param onDisplayId OnDisplayId assigned to the SnackProgressBar which is shown.
         */
        fun onDismissed(onDisplayId: Int)
    }

    /**
     * Registers a callback to be invoked when the SnackProgressBar is shown or dismissed.
     *
     * @param onDisplayListener The callback that will run. This value may be null.
     */
    fun setOnDisplayListener(onDisplayListener: OnDisplayListener?): SnackProgressBarManager {
        this.onDisplayListener = onDisplayListener
        return this
    }

    /**
     * Stores a SnackProgressBar into SnackProgressBarManager to perform further action.
     * The SnackProgressBar is uniquely identified by the storeId and will be overwritten
     * by another SnackProgressBar with the same storeId.
     *
     * @param snackProgressBar SnackProgressBar to be added.
     * @param storeId          OneUp of the SnackProgressBar to be added.
     * @see SnackProgressBar
     *
     * @see .getSnackProgressBar
     */
    fun put(snackProgressBar: SnackProgressBar, @OneUp storeId: Int) {
        storedBars.put(storeId, snackProgressBar)
    }

    /**
     * Retrieves the SnackProgressBar that was previously added into SnackProgressBarManager.
     *
     * @param storeId OneUp of the SnackProgressBar stored in SnackProgressBarManager.
     * @return SnackProgressBar of the storeId. Can be null.
     * @see .put
     */
    fun getSnackProgressBar(@OneUp storeId: Int): SnackProgressBar? {
        return storedBars[storeId]
    }

    /**
     * Retrieves the SnackProgressBar that is currently or was showing.
     *
     * @return SnackProgressBar that is currently or was showing. Return null if nothing was ever shown.
     */
    fun getLastShown(): SnackProgressBar? {
        return currentBar
    }

    /**
     * Shows the SnackProgressBar based on its storeId with the specified duration.
     * If another SnackProgressBar is already showing, this SnackProgressBar will be queued
     * and shown accordingly after those queued are dismissed.
     *
     * @param storeId  OneUp of the SnackProgressBar stored in SnackProgressBarManager.
     * @param duration Duration to show the SnackProgressBar of either
     * [LENGTH_SHORT], [LENGTH_LONG], [LENGTH_INDEFINITE]
     * or any positive millis.
     * @see .put
     */
    fun show(@OneUp storeId: Int, @ShowDuration duration: Long) {
        val snackProgressBar = storedBars[storeId]
        snackProgressBar?.run { addToQueue(this, duration, onDisplayIdDefault) } ?:
                throw IllegalArgumentException("SnackProgressBar with storeId = $storeId is not found!")
    }

    /**
     * Shows the SnackProgressBar based on its storeId with the specified duration.
     * If another SnackProgressBar is already showing, this SnackProgressBar will be queued
     * and shown accordingly after those queued are dismissed.
     *
     * @param storeId     OneUp of the SnackProgressBar stored in SnackProgressBarManager.
     * @param duration    Duration to show the SnackProgressBar of either
     * [LENGTH_SHORT], [LENGTH_LONG], [LENGTH_INDEFINITE]
     * or any positive millis.
     * @param onDisplayId OnDisplayId attached to the SnackProgressBar when implementing the OnDisplayListener.
     * @see .put
     */
    fun show(@OneUp storeId: Int, @ShowDuration duration: Long, @OneUp onDisplayId: Int) {
        val snackProgressBar = storedBars[storeId]
        snackProgressBar?.run { show(this, duration, onDisplayId) } ?:
                throw IllegalArgumentException("SnackProgressBar with storeId = $storeId is not found!")
    }

    /**
     * Shows the SnackProgressBar with the specified duration.
     * If another SnackProgressBar is already showing, this SnackProgressBar will be queued
     * and shown accordingly after those queued are dismissed.
     *
     * @param snackProgressBar SnackProgressBar to be shown.
     * @param duration         Duration to show the SnackProgressBar of either
     * [LENGTH_SHORT], [LENGTH_LONG], [LENGTH_INDEFINITE]
     * or any positive millis.
     */
    fun show(snackProgressBar: SnackProgressBar, @ShowDuration duration: Long) {
        addToQueue(snackProgressBar, duration, onDisplayIdDefault)
    }

    /**
     * Shows the SnackProgressBar with the specified duration.
     * If another SnackProgressBar is already showing, this SnackProgressBar will be queued
     * and shown accordingly after those queued are dismissed.
     *
     * @param snackProgressBar SnackProgressBar to be shown.
     * @param duration         Duration to show the SnackProgressBar of either
     * [LENGTH_SHORT], [LENGTH_LONG], [LENGTH_INDEFINITE]
     * or any positive millis.
     * @param onDisplayId      OnDisplayId attached to the SnackProgressBar when implementing the OnDisplayListener.
     */
    fun show(snackProgressBar: SnackProgressBar, @ShowDuration duration: Long, @OneUp onDisplayId: Int) {
        addToQueue(snackProgressBar, duration, onDisplayId)
    }

    /**
     * Updates the currently showing SnackProgressBar without dismissing it to the SnackProgressBar
     * with the corresponding storeId i.e. updating without animation.
     * Note: This does not change the queue.
     *
     * @param storeId OneUp of the SnackProgressBar stored in SnackProgressBarManager.
     */
    fun updateTo(@OneUp storeId: Int) {
        val snackProgressBar = storedBars[storeId]
        snackProgressBar?.run { updateTo(this) } ?:
                throw IllegalArgumentException("SnackProgressBar with storeId = $storeId is not found!")
    }

    /**
     * Updates the currently showing SnackProgressBar without dismissing it to the new SnackProgressBar
     * i.e. updating without animation.
     * Note: This does not change the queue.
     *
     * @param snackProgressBar SnackProgressBar to be updated to.
     */
    fun updateTo(snackProgressBar: SnackProgressBar) {
        currentCore?.updateTo(snackProgressBar)
    }

    /**
     * Dismisses the currently showing SnackProgressBar and show the next in queue.
     */
    fun dismiss() {
        currentCore?.dismiss()
        nextQueue()
    }

    /**
     * Dismisses all the SnackProgressBar that is in queue.
     * The currently shown SnackProgressBar will also be dismissed.
     */
    fun dismissAll() {
        // reset queue before dismissing currentCore to prevent next in queue from showing
        resetQueue()
        currentCore?.dismiss()
    }

    /**
     * Passes the view (e.g. FloatingActionButton) to move up or down as SnackProgressBar is shown or dismissed.
     *
     *
     * This is not required for FloatingActionButton if a CoordinatorLayout is provided, but will be required for
     * other layout types. This can be used to animate any view besides FloatingActionButton as well.
     *
     *
     * @param viewToMove View to be animated along with the SnackProgressBar.
     */
    fun setViewToMove(viewToMove: View): SnackProgressBarManager {
        val viewsToMove = arrayOf(viewToMove)
        setViewsToMove(viewsToMove)
        return this
    }

    /**
     * Passes the views (e.g. FloatingActionButton) to move up or down as SnackProgressBar is shown or dismissed.
     *
     *
     * This is not required for FloatingActionButton if a CoordinatorLayout is provided, but will be required for
     * other layout types. This can be used to animate any views besides FloatingActionButton as well.
     *
     *
     * @param viewsToMove Views to be animated along with the SnackProgressBar.
     */
    fun setViewsToMove(viewsToMove: Array<View>): SnackProgressBarManager {
        this.viewsToMove = viewsToMove
        return this
    }

    /**
     * Sets the transparency of the overlayLayout which blocks user input.
     *
     * @param alpha Alpha between 0f to 1f. Default = 0.8f.
     */
    fun setOverlayLayoutAlpha(@FloatRange(from = 0.0, to = 1.0) alpha: Float): SnackProgressBarManager {
        overlayLayoutAlpha = alpha
        currentCore?.setOverlayLayout(overlayColor, overlayLayoutAlpha)
        return this
    }

    /**
     * Sets the overlayLayout color.
     *
     * @param colorId R.color id.
     * @see .OVERLAY_COLOR_DEFAULT
     */
    fun setOverlayLayoutColor(@ColorRes colorId: Int): SnackProgressBarManager {
        overlayColor = colorId
        // update the UI now if applicable
        currentCore?.setOverlayLayout(overlayColor, overlayLayoutAlpha)
        return this
    }

    /**
     * Sets the SnackProgressBar background color.
     *
     * @param colorId R.color id.
     * @see .BACKGROUND_COLOR_DEFAULT
     */
    fun setBackgroundColor(@ColorRes colorId: Int): SnackProgressBarManager {
        backgroundColor = colorId
        // update the UI now if applicable
        currentCore?.setColor(backgroundColor, messageTextColor, actionTextColor, progressBarColor)
        return this
    }

    /**
     * Sets the message text color.
     *
     * @param colorId R.color id.
     * @see .MESSAGE_COLOR_DEFAULT
     */
    fun setMessageTextColor(@ColorRes colorId: Int): SnackProgressBarManager {
        messageTextColor = colorId
        // update the UI now if applicable
        currentCore?.setColor(backgroundColor, messageTextColor, actionTextColor, progressBarColor)
        return this
    }

    /**
     * Sets the action text color.
     *
     * @param colorId R.color id.
     * @see .ACTION_COLOR_DEFAULT
     */
    fun setActionTextColor(@ColorRes colorId: Int): SnackProgressBarManager {
        actionTextColor = colorId
        // update the UI now if applicable
        currentCore?.setColor(backgroundColor, messageTextColor, actionTextColor, progressBarColor)
        return this
    }

    /**
     * Sets the ProgressBar color.
     *
     * @param colorId R.color id.
     * @see .PROGRESSBAR_COLOR_DEFAULT
     */
    fun setProgressBarColor(@ColorRes colorId: Int): SnackProgressBarManager {
        progressBarColor = colorId
        // update the UI now if applicable
        currentCore?.setColor(backgroundColor, messageTextColor, actionTextColor, progressBarColor)
        return this
    }

    /**
     * Sets the progress for SnackProgressBar of TYPE_DETERMINATE that is currently showing.
     * It will also update the progress in % if it is shown.
     *
     * @param progress Progress of the ProgressBar.
     */
    fun setProgress(@IntRange(from = 0) progress: Int): SnackProgressBarManager {
        currentCore?.setProgress(progress)
        return this
    }

    /**
     * Adds the SnackProgressBar to queue.
     *
     * @param snackProgressBar SnackProgressBar to be added to queue.
     * @param duration         Duration to show the SnackProgressBar of either
     * [LENGTH_SHORT], [LENGTH_LONG], [LENGTH_INDEFINITE]
     * or any positive millis.
     * @param onDisplayId      OnDisplayId attached to the SnackProgressBar when implementing the OnDisplayListener.
     */
    private fun addToQueue(snackProgressBar: SnackProgressBar, duration: Long, onDisplayId: Int) {
        // get the queue number as the last of queue list
        val queue = queueBars.size
        // create a new object
        val queueBar = SnackProgressBar(snackProgressBar.getType(),
                snackProgressBar.getMessage(),
                snackProgressBar.getAction(),
                snackProgressBar.getIconBitmap(),
                snackProgressBar.getIconResource(),
                snackProgressBar.getProgressMax(),
                snackProgressBar.isAllowUserInput(),
                snackProgressBar.isSwipeToDismiss(),
                snackProgressBar.isShowProgressPercentage(),
                snackProgressBar.getOnActionClickListener())
        // put in queue
        queueBars.add(queueBar)
        queueOnDisplayIds.add(onDisplayId)
        queueDurations.add(duration)
        // play queue if first item
        if (queue == 0) {
            playQueue(queue)
        }
    }

    /**
     * Plays the queue.
     *
     * @param queue Queue number of SnackProgressBar.
     */
    private fun playQueue(queue: Int) {
        // check if queue number is bounded
        if (queue < queueBars.size) {
            // get the variables
            currentQueue = queue
            val snackProgressBar = queueBars[queue]
            val onDisplayId = queueOnDisplayIds[queue]
            var duration = queueDurations[queue]
            // change duration to LENGTH_SHORT if is not last item
            if (duration == LENGTH_INDEFINITE) {
                if (queue < queueBars.size - 1) {
                    duration = LENGTH_SHORT
                }
            }
            // create SnackProgressBarCore
            val snackProgressBarCore = SnackProgressBarCore.make(
                    parentView, snackProgressBar, duration, viewsToMove)
            snackProgressBarCore.setOverlayLayout(overlayColor, overlayLayoutAlpha)
            snackProgressBarCore.setColor(backgroundColor, messageTextColor, actionTextColor, progressBarColor)
            val finalDuration = duration
            snackProgressBarCore.addCallback(object : BaseTransientBottomBar.BaseCallback<SnackProgressBarCore>() {
                override fun onShown(snackProgressBarCore: SnackProgressBarCore?) {
                    // set current
                    currentBar = snackProgressBar
                    currentCore = snackProgressBarCore
                    // callback onDisplayListener
                    onDisplayListener?.run {
                        if (onDisplayId != onDisplayIdDefault) {
                            this.onShown(onDisplayId)
                        }
                    }
                }

                override fun onDismissed(snackProgressBarCore: SnackProgressBarCore?, event: Int) {
                    // remove overlayLayout
                    snackProgressBarCore?.removeOverlayLayout()
                    // reset current
                    currentCore = null
                    // callback onDisplayListener
                    onDisplayListener?.run {
                        if (onDisplayId != onDisplayIdDefault) {
                            this.onDismissed(onDisplayId)
                        }
                    }
                    // play next if this item is dismissed automatically later
                    if (finalDuration != LENGTH_INDEFINITE) {
                        nextQueue()
                    }
                }
            })
            // reset queue if this is last item in queue with LENGTH_INDEFINITE
            if (duration == LENGTH_INDEFINITE) {
                resetQueue()
            }
            snackProgressBarCore.show()
        } else {
            // else, queue is done
            resetQueue()
        }
    }

    /**
     * Play the next item in queue.
     */
    private fun nextQueue() {
        playQueue(currentQueue + 1)
    }

    /**
     * Reset queue.
     */
    private fun resetQueue() {
        currentQueue = 0
        queueBars.clear()
        queueOnDisplayIds.clear()
        queueDurations.clear()
    }

    /**
     * Loop and crawl up the providedView hierarchy to find a suitable parent providedView.
     *
     * @param providedView View to hold the SnackProgressBar.
     * If possible, it should be the root providedView of the activity and can be any type of layout.
     * @return Suitable parent providedView of the activity.
     */
    private fun findSuitableParent(providedView: View): ViewGroup {
        var view: View? = providedView
        var fallback: ViewGroup? = null
        do {
            if (view is CoordinatorLayout) {
                // use the CoordinatorLayout found
                return view
            } else if (view is FrameLayout) {
                if (view.id == android.R.id.content) {
                    // use the content providedView since CoordinatorLayout not found
                    return view
                } else {
                    // Non-content providedView, but use it as fallback
                    fallback = view
                }
            }
            if (view != null) {
                // loop and crawl up the providedView hierarchy and try to find a parent
                val parent = view.parent
                view = if (parent is View) parent else null
            }
        } while (view != null)
        // use fallback since CoordinatorLayout and other alternative not found
        fallback?.run { return this } ?:
                throw IllegalArgumentException("No suitable parent found from the given view. " + "Please provide a valid view.")
    }
}