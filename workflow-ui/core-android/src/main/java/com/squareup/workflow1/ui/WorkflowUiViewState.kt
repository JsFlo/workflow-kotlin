package com.squareup.workflow1.ui

import android.view.View
import com.squareup.workflow1.ui.WorkflowUiViewState.New
import com.squareup.workflow1.ui.WorkflowUiViewState.Started

/**
 * Function attached to a view created by [ViewFactory], to allow it
 * to respond to [View.showRendering].
 */
@WorkflowUiExperimentalApi
public typealias ViewShowRendering<RenderingT> =
  (@UnsafeVariance RenderingT, ViewEnvironment) -> Unit

/**
 * [View tag][View.setTag] that holds the functions and state backing [View.showRendering], etc.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal sealed class WorkflowUiViewState<out RenderingT : Any> {
  @PublishedApi
  internal abstract val showing: RenderingT
  abstract val environment: ViewEnvironment
  abstract val showRendering: ViewShowRendering<RenderingT>

  /** [bindShowRendering] has been called, [start] has not. */
  data class New<out RenderingT : Any>(
    override val showing: RenderingT,
    override val environment: ViewEnvironment,
    override val showRendering: ViewShowRendering<RenderingT>,

    val starter: (View) -> Unit = { view ->
      view.showRendering(view.getRendering()!!, view.environment!!)
    }
  ) : WorkflowUiViewState<RenderingT>()

  /** [start] has been called. It's safe to call [showRendering] now. */
  data class Started<out RenderingT : Any>(
    override val showing: RenderingT,
    override val environment: ViewEnvironment,
    override val showRendering: ViewShowRendering<RenderingT>
  ) : WorkflowUiViewState<RenderingT>()
}

/**
 * Intended for use by implementations of [ViewFactory.buildView].
 *
 * Establishes [showRendering] as the implementation of [View.showRendering]
 * for the receiver, possibly replacing the existing one. Likewise sets / updates
 * the values returned by [View.getRendering] and [View.environment].
 *
 * - [View.start] must be called exactly once before [View.showRendering].
 *
 * @throws IllegalStateException when called after [View.start]
 *
 * @see DecorativeViewFactory
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> View.bindShowRendering(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  showRendering: ViewShowRendering<RenderingT>
) {
  workflowTag = when (workflowTagOrNull) {
    is New<*> -> New(initialRendering, initialViewEnvironment, showRendering, starter)
    else -> New(initialRendering, initialViewEnvironment, showRendering)
  }
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Makes the initial call to [View.showRendering], along with any wrappers that have been
 * added via [ViewRegistry.buildView], or [DecorativeViewFactory.viewStarter].
 *
 * - It is an error to call this method more than once.
 * - It is an error to call [View.showRendering] without having called this method first.
 */
@WorkflowUiExperimentalApi
public fun View.start() {
  val current = workflowTagAsNew
  workflowTag = Started(current.showing, current.environment, current.showRendering)
  current.starter(this)
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * True if this view is able to show [rendering].
 *
 * Returns `false` if [View.bindShowRendering] has not been called, so it is always safe to
 * call this method. Otherwise returns the [compatibility][compatible] of the current
 * [View.getRendering] and the new one.
 */
@WorkflowUiExperimentalApi
public fun View.canShowRendering(rendering: Any): Boolean {
  return getRendering<Any>()?.matches(rendering) == true
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Sets the rendering associated with this view, and displays it by invoking
 * the [ViewShowRendering] function previously set by [bindShowRendering].
 *
 * @throws IllegalStateException if [bindShowRendering] has not been called.
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> View.showRendering(
  rendering: RenderingT,
  viewEnvironment: ViewEnvironment
) {
  workflowTagAsStarted.let { tag ->
    check(tag.showing.matches(rendering)) {
      "Expected $this to be able to show rendering $rendering, but that did not match " +
        "previous rendering ${tag.showing}. " +
        "Consider using WorkflowViewStub to display arbitrary types."
    }

    // Update the tag's rendering and viewEnvironment.
    workflowTag = Started(rendering, viewEnvironment, tag.showRendering)
    // And do the actual showRendering work.
    tag.showRendering.invoke(rendering, viewEnvironment)
  }
}

/**
 * Returns the most recent rendering shown by this view cast to [RenderingT],
 * or null if [bindShowRendering] has never been called.
 *
 * @throws ClassCastException if the current rendering is not of type [RenderingT]
 */
@WorkflowUiExperimentalApi
public inline fun <reified RenderingT : Any> View.getRendering(): RenderingT? {
  // Can't use a val because of the parameter type.
  return when (val showing = workflowTagOrNull?.showing) {
    null -> null
    else -> showing as RenderingT
  }
}

/**
 * Returns the most recent [ViewEnvironment] applied to this view, or null if [bindShowRendering]
 * has never been called.
 */
@WorkflowUiExperimentalApi
public val View.environment: ViewEnvironment?
  get() = workflowTagOrNull?.environment

/**
 * Returns the function set by the most recent call to [bindShowRendering], or null
 * if that method has never been called.
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> View.getShowRendering(): ViewShowRendering<RenderingT>? {
  return workflowTagOrNull?.showRendering
}

@WorkflowUiExperimentalApi
internal var View.starter: (View) -> Unit
  get() = workflowTagAsNew.starter
  set(value) {
    workflowTag = workflowTagAsNew.copy(starter = value)
  }

@WorkflowUiExperimentalApi
@PublishedApi
internal val View.workflowTagOrNull: WorkflowUiViewState<*>?
  get() = getTag(R.id.workflow_ui_view_state) as? WorkflowUiViewState<*>

@WorkflowUiExperimentalApi
private var View.workflowTag: WorkflowUiViewState<*>
  get() = workflowTagOrNull ?: error(
    "Expected $this to have been built by a ViewFactory. " +
      "Perhaps the factory did not call View.bindShowRendering."
  )
  set(value) = setTag(R.id.workflow_ui_view_state, value)

@WorkflowUiExperimentalApi
private val View.workflowTagAsNew: New<*>
  get() = workflowTag as? New<*> ?: error(
    "Expected $this to be un-started, but View.start() has been called"
  )

@WorkflowUiExperimentalApi
private val View.workflowTagAsStarted: Started<*>
  get() = workflowTag as? Started<*> ?: error(
    "Expected $this to have been started, but View.start() has not been called"
  )

@WorkflowUiExperimentalApi
private fun Any.matches(other: Any) = compatible(this, other)
