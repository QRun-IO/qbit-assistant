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

package com.kingsrook.qbits.assistant.uiapi;


import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** How the assistant reaches data: spec'd `/qqq/v1` operations called with the
 ** user's own session, so every tool call passes the same authentication,
 ** permission, record-lock, validation and audit path as the UI.
 **
 ** {@link CoreUiApiGateway} implements it over core's `UiApiInvoker`
 ** (QRun-IO/qqq#1128); a host may inject another (tests).
 *******************************************************************************/
public interface UiApiGateway
{
   /*******************************************************************************
    ** Whether METHOD template is a spec'd operation this gateway will call.
    *******************************************************************************/
   boolean isInvocable(String method, String pathTemplate);

   /*******************************************************************************
    ** Run the call. Refuses (QException) an operation that is not spec'd, or a
    ** call without a session. Otherwise it returns the response as the UI would
    ** see it, error statuses included.
    *******************************************************************************/
   UiApiResult invoke(UiApiCall call) throws QException;
}
