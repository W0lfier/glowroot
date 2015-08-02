/*
 * Copyright 2014-2015 the original author or authors.
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
package org.glowroot.microbenchmarks.support;

import org.glowroot.plugin.api.Agent;
import org.glowroot.plugin.api.config.ConfigService;
import org.glowroot.plugin.api.transaction.ErrorMessage;
import org.glowroot.plugin.api.transaction.MessageSupplier;
import org.glowroot.plugin.api.transaction.TimerName;
import org.glowroot.plugin.api.transaction.TraceEntry;
import org.glowroot.plugin.api.transaction.TransactionService;
import org.glowroot.plugin.api.weaving.BindThrowable;
import org.glowroot.plugin.api.weaving.BindTraveler;
import org.glowroot.plugin.api.weaving.IsEnabled;
import org.glowroot.plugin.api.weaving.OnAfter;
import org.glowroot.plugin.api.weaving.OnBefore;
import org.glowroot.plugin.api.weaving.OnThrow;
import org.glowroot.plugin.api.weaving.Pointcut;

public class TraceEntryWorthyAspect {

    private static final TransactionService transactionService = Agent.getTransactionService();
    private static final ConfigService configService =
            Agent.getConfigService("glowroot-microbenchmarks");

    @Pointcut(className = "org.glowroot.microbenchmarks.core.support.TraceEntryWorthy",
            methodName = "doSomethingTraceEntryWorthy", methodParameterTypes = {},
            timerName = "trace entry worthy")
    public static class TraceEntryWorthyAdvice {

        private static final TimerName timerName =
                transactionService.getTimerName(TraceEntryWorthyAdvice.class);

        @IsEnabled
        public static boolean isEnabled() {
            return configService.isEnabled();
        }

        @OnBefore
        public static TraceEntry onBefore() {
            return transactionService.startTraceEntry(MessageSupplier.from("trace entry worthy"),
                    timerName);
        }

        @OnThrow
        public static void onThrow(@BindThrowable Throwable t,
                @BindTraveler TraceEntry traceEntry) {
            traceEntry.endWithError(ErrorMessage.from(t));
        }

        @OnAfter
        public static void onReturn(@BindTraveler TraceEntry traceEntry) {
            traceEntry.end();
        }
    }
}
