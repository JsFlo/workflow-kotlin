/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.ui

@WorkflowUiExperimentalApi
internal object NamedBinding : ViewFactory<Named<*>>
by BuilderBinding(
    type = Named::class,
    viewConstructor = { initialRendering, initialHints, contextForNewView, container ->
      // Have the ViewRegistry build the view for wrapped.
      initialHints[ViewRegistry]
          .buildView(
              initialRendering.wrapped,
              initialHints,
              contextForNewView,
              container
          )
          .also { view ->
            // Rendering updates will be instances of Named, but the view
            // was built to accept updates matching the type of wrapped.
            // So replace the view's update function with one of our
            // own, which calls through to the original.

            val wrappedUpdater = view.getShowRendering<Any>()!!

            view.bindShowRendering(initialRendering, initialHints) { rendering, environment ->
              wrappedUpdater.invoke(rendering.wrapped, environment)
            }
          }
    }
)