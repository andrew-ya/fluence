/*
 * Copyright 2018 Fluence Labs Limited
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

package fluence.statemachine.config

/**
 * State machine settings.
 *
 * @param sessionExpirationPeriod period after which the session becomes expired,
 *                                measured as difference between the current `txCounter` value and
 *                                its value at the last activity in the session.
 * @param moduleFiles sequence of files with WASM module code
 * @param logLevel level of logging ( OFF / ERROR / WARN / INFO / DEBUG / TRACE )
 */
case class StateMachineConfig(sessionExpirationPeriod: Long, moduleFiles: List[String], logLevel: String)
