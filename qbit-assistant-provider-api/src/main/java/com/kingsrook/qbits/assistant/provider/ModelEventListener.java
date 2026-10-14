/*
 * Copyright 2026 Kingsrook, LLC
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

package com.kingsrook.qbits.assistant.provider;


/*******************************************************************************
 ** Receives streamed output while a model call runs. Called on the
 ** provider's thread; implementations must be quick and thread-safe.
 ** Thinking content is never passed here.
 *******************************************************************************/
public interface ModelEventListener
{

   /*******************************************************************************
    ** A chunk of visible answer text.
    *******************************************************************************/
   void onTextDelta(String text);

   /*******************************************************************************
    ** A tool_use block started streaming (name known, input still arriving).
    *******************************************************************************/
   default void onToolUseStart(String toolUseId, String toolName)
   {
   }

}
