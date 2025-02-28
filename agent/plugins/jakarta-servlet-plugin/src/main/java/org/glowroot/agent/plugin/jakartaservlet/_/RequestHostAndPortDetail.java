/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.agent.plugin.jakartaservlet._;

import org.glowroot.agent.plugin.api.checker.Nullable;

public class RequestHostAndPortDetail {

    // -1 is used as error return value by RequestInvoker.getRemotePort()
    public static final int UNSET = -2;
    public @Nullable String remoteAddress;
    public @Nullable String remoteHostname;
    public int remotePort = UNSET;
    public @Nullable String localAddress;
    public @Nullable String localHostname;
    public int localPort = UNSET;
    public @Nullable String serverHostname;
    public int serverPort = UNSET;
}
