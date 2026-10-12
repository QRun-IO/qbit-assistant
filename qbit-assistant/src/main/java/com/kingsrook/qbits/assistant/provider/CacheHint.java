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
 ** A provider-neutral prompt-cache breakpoint after a block: LONG is the
 ** shared app prefix (Anthropic: 1 hour), SHORT the per-user / per-turn part
 ** (Anthropic: 5 minutes). Providers without caching ignore hints. Longer
 ** hints must come before shorter ones in the request.
 *******************************************************************************/
public enum CacheHint
{
   NONE,
   SHORT,
   LONG
}
