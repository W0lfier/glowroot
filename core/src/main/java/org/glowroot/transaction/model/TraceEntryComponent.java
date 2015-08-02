/*
 * Copyright 2011-2015 the original author or authors.
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
package org.glowroot.transaction.model;

import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import com.google.common.base.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.plugin.api.transaction.ErrorMessage;
import org.glowroot.plugin.api.transaction.MessageSupplier;

import static com.google.common.base.Preconditions.checkNotNull;

// this supports updating by a single thread and reading by multiple threads
class TraceEntryComponent implements Iterable<TraceEntryImpl> {

    private static final Logger logger = LoggerFactory.getLogger(TraceEntryComponent.class);

    private final long startTick;
    // not volatile, so depends on memory barrier in Transaction for visibility
    private boolean completed;
    // not volatile, so depends on memory barrier in Transaction for visibility
    private long endTick;

    private final TraceEntryImpl rootEntry;

    @Nullable
    private TraceEntryImpl activeEntry;

    private TraceEntryImpl tailEntry;

    // entries.size() is accessed a lot, but only by transaction thread, so storing size separately
    // so it can be accessed without synchronization
    private int entryCount;

    // this doesn't need to be volatile since it is only accessed by the transaction thread
    private boolean entryLimitExceeded;

    private final Ticker ticker;

    TraceEntryComponent(MessageSupplier messageSupplier, TimerImpl timer, long startTick,
            Ticker ticker) {
        this.startTick = startTick;
        this.ticker = ticker;
        rootEntry = new TraceEntryImpl(null, messageSupplier, null, 0, startTick, -1, timer);
        activeEntry = rootEntry;
        tailEntry = rootEntry;
    }

    TraceEntryImpl getRootEntry() {
        return rootEntry;
    }

    // this does not include root trace entry
    @Override
    public Iterator<TraceEntryImpl> iterator() {
        return new TraceEntryIterator(rootEntry);
    }

    // this does not include root trace entry
    // IMPORTANT: this does not include limit exceeded or limit extended marker trace entries
    int getEntryCount() {
        return entryCount;
    }

    long getStartTick() {
        return startTick;
    }

    boolean isCompleted() {
        return completed;
    }

    long getEndTick() {
        return endTick;
    }

    // duration of trace in nanoseconds
    long getDuration() {
        return completed ? endTick - startTick : ticker.read() - startTick;
    }

    TraceEntryImpl pushEntry(long startTick, MessageSupplier messageSupplier,
            @Nullable QueryData queryData, long queryExecutionCount, TimerImpl timer) {
        TraceEntryImpl entry = createEntry(startTick, messageSupplier, queryData,
                queryExecutionCount, null, timer, false);
        tailEntry.setNextTraceEntry(entry);
        tailEntry = entry;
        activeEntry = entry;
        entryCount++;
        return entry;
    }

    // typically pop() methods don't require the objects to pop, but for safety, the entry is
    // passed in just to make sure it is the one on top (and if not, then pop until it is found,
    // preventing any nasty bugs from a missed pop, e.g. an entry never being marked as complete)
    void popEntry(TraceEntryImpl entry, long endTick) {
        popEntrySafe(entry);
        if (activeEntry == null) {
            this.endTick = endTick;
            this.completed = true;
        }
    }

    TraceEntryImpl addEntry(long startTick, long endTick,
            @Nullable MessageSupplier messageSupplier,
            @Nullable ErrorMessage errorMessage, boolean limitBypassed) {
        TraceEntryImpl entry =
                createEntry(startTick, messageSupplier, null, 1, errorMessage, null, limitBypassed);
        tailEntry.setNextTraceEntry(entry);
        tailEntry = entry;
        entryCount++;
        entry.setEndTick(endTick);
        return entry;
    }

    void addEntryLimitExceededMarkerIfNeeded() {
        if (entryLimitExceeded) {
            return;
        }
        entryLimitExceeded = true;
        TraceEntryImpl entry = TraceEntryImpl.getLimitExceededMarker();
        tailEntry.setNextTraceEntry(entry);
        tailEntry = entry;
        // note: intentionally not incrementing entryCount
    }

    private TraceEntryImpl createEntry(long startTick, @Nullable MessageSupplier messageSupplier,
            @Nullable QueryData queryData, long queryExecutionCount,
            @Nullable ErrorMessage errorMessage, @Nullable TimerImpl timer, boolean limitBypassed) {
        if (entryLimitExceeded && !limitBypassed) {
            // just in case the entryLimit property is changed in the middle of a trace this resets
            // the flag so that it can be triggered again (and possibly then a second limit marker)
            entryLimitExceeded = false;
            // also a different marker ("limit extended") is placed in the entries so that the ui
            // can display this scenario sensibly
            TraceEntryImpl entry = TraceEntryImpl.getLimitExtendedMarker();
            tailEntry.setNextTraceEntry(entry);
            tailEntry = entry;
            // note: intentionally not incrementing entryCount
        }
        int nestingLevel;
        if (entryLimitExceeded && limitBypassed) {
            // limit bypassed entries have no proper nesting, so put them directly under the root
            nestingLevel = 1;
        } else {
            // activeEntry is only null when transaction is complete
            checkNotNull(activeEntry);
            nestingLevel = activeEntry.getNestingLevel() + 1;
        }
        TraceEntryImpl entry = new TraceEntryImpl(activeEntry, messageSupplier, queryData,
                queryExecutionCount, startTick, nestingLevel, timer);
        entry.setErrorMessage(errorMessage);
        return entry;
    }

    private void popEntrySafe(TraceEntryImpl entry) {
        if (activeEntry == null) {
            logger.error("entry stack is empty, cannot pop entry: {}", entry);
            return;
        }
        if (activeEntry == entry) {
            activeEntry = activeEntry.getParentTraceEntry();
        } else {
            // somehow(?) a pop was missed (or maybe too many pops), this is just damage control
            popEntryBailout(entry);
        }
    }

    // split typically unused path into separate method to not affect inlining budget
    private void popEntryBailout(TraceEntryImpl expectingEntry) {
        logger.error("found entry {} at top of stack when expecting entry {}", activeEntry,
                expectingEntry);
        while (activeEntry != null && activeEntry != expectingEntry) {
            activeEntry = activeEntry.getParentTraceEntry();
        }
        if (activeEntry != null) {
            // now perform pop
            activeEntry = activeEntry.getParentTraceEntry();
        } else {
            logger.error("popped entire stack, never found entry: {}", expectingEntry);
        }
    }

    private static class TraceEntryIterator implements Iterator<TraceEntryImpl> {
        private TraceEntryImpl curr;
        private TraceEntryIterator(TraceEntryImpl rootEntry) {
            this.curr = rootEntry;
        }
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
        @Override
        public TraceEntryImpl next() {
            TraceEntryImpl next = curr.getNextTraceEntry();
            if (next == null) {
                throw new NoSuchElementException();
            }
            curr = next;
            return curr;
        }
        @Override
        public boolean hasNext() {
            return curr.getNextTraceEntry() != null;
        }
    }
}
