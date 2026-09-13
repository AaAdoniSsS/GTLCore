package org.gtlcore.gtlcore.integration.ae2.wireless;

import appeng.api.networking.IGridConnection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Server-thread scheduler. Every member has exactly one live entry and one scheduling location. */
final class WirelessAeMemberScheduler {

    enum Kind {
        DIRTY,
        RETRY_DUE,
        CLEANUP
    }

    enum State {
        DIRTY,
        VALIDATING,
        WIRELESS,
        WIRED,
        UNLOADED,
        RETRY_WAIT,
        TOPOLOGY_WAIT,
        CLEANUP
    }

    record Key(UUID frequency, WirelessAeSavedData.MemberKey member) {}

    record Outcome(State state, String reason) {

        static Outcome of(State state) {
            return new Outcome(state, "none");
        }
    }

    @FunctionalInterface
    interface Processor {

        Outcome process(Key key, State previous);
    }

    private static final class Entry {

        final Key key;
        final long order;
        State state = State.DIRTY;
        long due;
        long queuedAt;
        long firstFailure = -1;
        int failures;
        int backoff;
        String reason = "none";

        Entry(Key key, long order) {
            this.key = key;
            this.order = order;
        }
    }

    private static final class Network {

        final Map<Key, Entry> members = new HashMap<>();
        final List<Entry> insertionOrder = new ArrayList<>();
        final LinkedHashMap<Key, Entry> ready = new LinkedHashMap<>();
        /** Topology invalidation is expanded over several ticks instead of flooding ready at once. */
        int invalidationCursor;
        boolean invalidationActive;
        // Detached maps are drained one connection at a time; no iterator survives a tick.
        final ArrayDeque<Map<WirelessAeSavedData.MemberKey, IGridConnection>> retired = new ArrayDeque<>();
        long serviceTick = -1;
        int serviced;
    }

    private final Map<UUID, Network> networks = new HashMap<>();
    private final LinkedHashMap<UUID, Network> runnable = new LinkedHashMap<>();
    // Entry identity is stable. Maintain this on transitions/removal, never by a census.
    private final Set<Entry> retrying = new LinkedHashSet<>();
    private final TreeSet<Entry> deadlines = new TreeSet<>(Comparator.comparingLong((Entry e) -> e.due)
            .thenComparingLong(e -> e.order));
    private long tick;
    private long sequence;

    void beginTick(long tick) {
        this.tick = tick;
    }

    private Entry entry(Network network, Key key) {
        Entry existing = network.members.get(key);
        if (existing != null) return existing;
        Entry created = new Entry(key, sequence++);
        network.members.put(key, created);
        network.insertionOrder.add(created);
        return created;
    }

    void enqueue(UUID frequency, WirelessAeSavedData.MemberKey member, Kind kind) {
        if (kind == Kind.CLEANUP) {
            Network network = networks.computeIfAbsent(frequency, ignored -> new Network());
            Key key = new Key(frequency, member);
            Entry entry = entry(network, key);
            entry.state = State.CLEANUP;
            ready(network, entry);
        } else {
            if (kind == Kind.RETRY_DUE) {
                Network network = networks.computeIfAbsent(frequency, ignored -> new Network());
                Key key = new Key(frequency, member);
                Entry entry = entry(network, key);
                deadlines.remove(entry);
                entry.state = State.RETRY_WAIT;
                ready(network, entry);
            } else dirty(frequency, member, false);
        }
    }

    void discover(UUID frequency, WirelessAeSavedData.MemberKey member) {
        Network network = networks.computeIfAbsent(frequency, ignored -> new Network());
        Key key = new Key(frequency, member);
        if (!network.members.containsKey(key)) dirty(frequency, member, false);
    }

    void dirty(UUID frequency, WirelessAeSavedData.MemberKey member, boolean manual) {
        Network network = networks.computeIfAbsent(frequency, ignored -> new Network());
        Key key = new Key(frequency, member);
        Entry entry = entry(network, key);
        deadlines.remove(entry);
        if (manual) {
            retrying.remove(entry);
            if (entry.failures > 0) WirelessAeDiagnostics.count("retry_manual_reset");
            entry.failures = entry.backoff = 0;
            entry.firstFailure = -1;
        }
        entry.state = State.DIRTY;
        ready(network, entry);
    }

    void invalidateNetwork(UUID frequency) {
        Network network = networks.get(frequency);
        if (network == null) return;
        if (!network.invalidationActive && network.insertionOrder.size() > network.members.size() * 2L + 32) {
            network.insertionOrder.removeIf(entry -> network.members.get(entry.key) != entry);
        }
        if (!network.invalidationActive) {
            network.invalidationCursor = 0;
            network.invalidationActive = true;
        }
        runnable.putIfAbsent(frequency, network);
    }

    void invalidateAllNetworks() {
        for (UUID frequency : java.util.List.copyOf(networks.keySet())) invalidateNetwork(frequency);
    }

    void forgetNetwork(UUID frequency) {
        Network network = networks.remove(frequency);
        if (network == null) return;
        for (Entry entry : network.members.values()) {
            deadlines.remove(entry);
            retrying.remove(entry);
        }
        runnable.remove(frequency);
    }

    void changed(UUID frequency, WirelessAeSavedData.MemberKey member, boolean present) {
        dirty(frequency, member, present);
        if (!present) networks.get(frequency).members.get(new Key(frequency, member)).state = State.CLEANUP;
    }

    void forget(UUID frequency, WirelessAeSavedData.MemberKey member) {
        Network network = networks.get(frequency);
        if (network == null) return;
        Key key = new Key(frequency, member);
        Entry entry = network.members.remove(key);
        if (entry != null) {
            deadlines.remove(entry);
            retrying.remove(entry);
        }
        network.ready.remove(key);
        trim(frequency, network);
    }

    void cancel(UUID frequency, WirelessAeSavedData.MemberKey member) {
        Network network = networks.get(frequency);
        if (network == null) return;
        Key key = new Key(frequency, member);
        Entry entry = network.members.remove(key);
        if (entry != null) {
            deadlines.remove(entry);
            retrying.remove(entry);
        }
        network.ready.remove(key);
        trim(frequency, network);
    }

    void cancelMember(WirelessAeSavedData.MemberKey member) {
        for (UUID frequency : java.util.List.copyOf(networks.keySet())) cancel(frequency, member);
    }

    void forgetAt(Predicate<WirelessAeSavedData.MemberKey> predicate) {
        for (Network network : networks.values()) {
            var iterator = network.members.values().iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (!predicate.test(entry.key.member())) continue;
                deadlines.remove(entry);
                retrying.remove(entry);
                network.ready.remove(entry.key);
                iterator.remove();
            }
        }
        runnable.entrySet().removeIf(e -> e.getValue().ready.isEmpty() && e.getValue().retired.isEmpty() && !e.getValue().invalidationActive);
    }

    void retire(UUID frequency, Map<WirelessAeSavedData.MemberKey, IGridConnection> connections) {
        Network network = networks.computeIfAbsent(frequency, ignored -> new Network());
        for (Entry entry : network.members.values()) {
            deadlines.remove(entry);
            retrying.remove(entry);
        }
        network.members.clear();
        network.insertionOrder.clear();
        network.ready.clear();
        network.invalidationCursor = 0;
        network.invalidationActive = false;
        if (connections != null && !connections.isEmpty()) network.retired.addLast(connections);
        if (!network.retired.isEmpty()) runnable.putIfAbsent(frequency, network);
        trim(frequency, network);
    }

    void retainNetworks(Predicate<UUID> exists) {
        for (var network : networks.entrySet()) {
            if (exists.test(network.getKey())) continue;
            for (Entry entry : network.getValue().members.values()) {
                deadlines.remove(entry);
                entry.state = State.CLEANUP;
                ready(network.getValue(), entry);
            }
        }
    }

    private void ready(Network network, Entry entry) {
        if (network.ready.putIfAbsent(entry.key, entry) == null) entry.queuedAt = tick;
        runnable.putIfAbsent(entry.key.frequency(), network);
    }

    /** Returns after at most 32 operations / 8 per network, sharing one wall-clock deadline. */
    void run(long deadline, Processor processor, Consumer<IGridConnection> destroy) {
        int invalidated = 0;
        for (Network network : networks.values()) {
            while (invalidated < 64 && network.invalidationActive &&
                    network.invalidationCursor < network.insertionOrder.size()) {
                Entry entry = network.insertionOrder.get(network.invalidationCursor++);
                if (network.members.get(entry.key) != entry || entry.state == State.CLEANUP) continue;
                deadlines.remove(entry);
                entry.state = State.DIRTY;
                ready(network, entry);
                invalidated++;
            }
            if (network.invalidationCursor >= network.insertionOrder.size()) {
                network.invalidationActive = false;
            }
            if (invalidated >= 64) break;
        }
        if (invalidated > 0) WirelessAeDiagnostics.add("topology_invalidations_promoted", invalidated);
        for (int i = 0; i < 32 && !deadlines.isEmpty() && deadlines.first().due <= tick &&
                System.nanoTime() < deadline; i++) {
            Entry entry = deadlines.pollFirst();
            if (entry.failures > 0) WirelessAeDiagnostics.count("retry_due_enqueued");
            ready(networks.get(entry.key.frequency()), entry);
        }
        int operations = 0;
        int visits = Math.min(64, runnable.size());
        for (int i = 0; i < visits && !runnable.isEmpty() && operations < 32 && System.nanoTime() < deadline; i++) {
            var first = runnable.entrySet().iterator();
            var next = first.next();
            UUID frequency = next.getKey();
            Network network = next.getValue();
            first.remove();
            if (network.serviceTick != tick) {
                network.serviceTick = tick;
                network.serviced = 0;
            }
            long started = WirelessAeDiagnostics.start();
            while (network.serviced < 8 && operations < 32 && System.nanoTime() < deadline) {
                if (!network.retired.isEmpty()) {
                    Map<WirelessAeSavedData.MemberKey, IGridConnection> map = network.retired.peekFirst();
                    var entries = map.entrySet().iterator();
                    IGridConnection connection = entries.next().getValue();
                    entries.remove();
                    if (map.isEmpty()) network.retired.removeFirst();
                    WirelessAeDiagnostics.run("connection_cleanup", () -> destroy.accept(connection));
                    WirelessAeDiagnostics.count("stale_connections_removed");
                } else if (!network.ready.isEmpty()) {
                    var entries = network.ready.entrySet().iterator();
                    Entry entry = entries.next().getValue();
                    entries.remove();
                    WirelessAeDiagnostics.queueWait((int) Math.min(Integer.MAX_VALUE, tick - entry.queuedAt));
                    WirelessAeDiagnostics.sample("network_work", frequency, entry.key.member(), entry.state.name());
                    State previous = entry.state;
                    entry.state = State.VALIDATING;
                    if (entry.failures > 0) WirelessAeDiagnostics.count("retry_attempted");
                    Outcome outcome;
                    try {
                        outcome = processor.process(entry.key, previous);
                    } catch (RuntimeException error) {
                        WirelessAeDiagnostics.failure("member_work", frequency, entry.key.member(), error);
                        outcome = new Outcome(State.RETRY_WAIT, "exception");
                    }
                    // Callbacks may unbind or replace an entry while AE2 updates its grid.
                    if (network.members.get(entry.key) == entry) finish(network, entry, outcome);
                    WirelessAeDiagnostics.count("members_processed");
                } else break;
                operations++;
                network.serviced++;
            }
            WirelessAeDiagnostics.max("network_members_per_tick_max", network.serviced);
            WirelessAeDiagnostics.end("network_maintenance", started);
            if (!network.ready.isEmpty() || !network.retired.isEmpty() || network.invalidationActive) {
                if (network.serviced >= 8) WirelessAeDiagnostics.count("network_member_quota_hits");
                runnable.putIfAbsent(frequency, network);
            }
            trim(frequency, network);
        }
        if (!runnable.isEmpty() && (operations >= 32 || System.nanoTime() >= deadline))
            WirelessAeDiagnostics.count("budget_exhaustions");
    }

    private void finish(Network network, Entry entry, Outcome outcome) {
        State old = entry.state;
        entry.state = outcome.state();
        entry.reason = outcome.reason();
        WirelessAeDiagnostics.count("state_" + entry.state);
        if (entry.state == State.CLEANUP) {
            retrying.remove(entry);
            network.members.remove(entry.key);
            deadlines.remove(entry);
            network.ready.remove(entry.key);
            return;
        }
        boolean retry = entry.state == State.RETRY_WAIT || entry.state == State.UNLOADED;
        if (retry) {
            retrying.add(entry);
            if (entry.firstFailure < 0) entry.firstFailure = tick;
            entry.failures = Math.min(Integer.MAX_VALUE - 1, entry.failures) + 1;
            entry.backoff = Math.min(400, entry.backoff == 0 ? 40 : entry.backoff * 2);
            WirelessAeDiagnostics.count("retry_scheduled");
            WirelessAeDiagnostics.sample("retry_failure", entry.key.frequency(), entry.key.member(), entry.reason);
        } else {
            retrying.remove(entry);
            if (entry.failures > 0) WirelessAeDiagnostics.count("retry_recovered");
            entry.failures = entry.backoff = 0;
            entry.firstFailure = -1;
        }
        // A new dirty event raised during processing takes precedence over the next periodic deadline.
        if (retry && !network.ready.containsKey(entry.key)) {
            deadlines.remove(entry);
            entry.due = tick + entry.backoff;
            deadlines.add(entry);
        }
    }

    int queuedNetworks() {
        return runnable.size();
    }

    int retryCount() {
        return retrying.size();
    }

    void diagnostics() {
        if (!WirelessAeDiagnostics.enabled()) return;
        int ready = 0;
        int pendingInvalidation = 0;
        long oldest = 0;
        int streak = 0;
        for (Network n : networks.values()) {
            ready += n.ready.size();
            if (n.invalidationActive) pendingInvalidation += Math.max(0, n.insertionOrder.size() - n.invalidationCursor);
        }
        int sampled = 0;
        var samples = retrying.iterator();
        while (sampled < 8 && samples.hasNext()) {
            Entry e = samples.next();
            sampled++;
            oldest = Math.max(oldest, tick - e.firstFailure);
            streak = Math.max(streak, e.failures);
            WirelessAeDiagnostics.sample("retry_pending", e.key.frequency(), e.key.member(),
                    "reason=" + e.reason + ",age=" + (tick - e.firstFailure) + ",failures=" + e.failures + ",due=" + e.due);
        }
        WirelessAeDiagnostics.gauge("retry_diagnostic_members_sampled", sampled);
        WirelessAeDiagnostics.gauge("ready_members", ready);
        WirelessAeDiagnostics.gauge("topology_invalidation_pending", pendingInvalidation);
        WirelessAeDiagnostics.gauge("retry_sample_oldest_age_ticks", oldest);
        WirelessAeDiagnostics.gauge("retry_sample_failure_streak_max", streak);
        WirelessAeDiagnostics.gauge("scheduled_members", deadlines.size());
    }

    private void trim(UUID frequency, Network network) {
        if (network.ready.isEmpty() && network.retired.isEmpty() && !network.invalidationActive) runnable.remove(frequency);
        if (network.members.isEmpty() && network.retired.isEmpty()) networks.remove(frequency, network);
    }

    void clear() {
        networks.clear();
        runnable.clear();
        deadlines.clear();
        retrying.clear();
        tick = sequence = 0;
    }
}
