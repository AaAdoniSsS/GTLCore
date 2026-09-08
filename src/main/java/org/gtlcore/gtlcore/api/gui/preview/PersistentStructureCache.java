package org.gtlcore.gtlcore.api.gui.preview;

import org.gtlcore.gtlcore.mixin.gtm.api.machine.IMultiblockStateInvoker;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;

import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent preview cache ported from GTL Host Multiblock Preview Certificate Cache 0.16.1.
 *
 * Runtime predicate/lambda objects are never serialized. The disk file stores relative positions
 * and stable predicate indices into the freshly-created BlockPattern. This keeps all live supplier,
 * tooltip and predicate objects from the current game session while skipping the expensive matcher.
 */
public final class PersistentStructureCache {

    private static final int MAGIC = 0x47544C43; // GTLC
    private static final int FORMAT = 1;
    private static final int MAX_ENTRIES = 20_000_000;
    // v6 uses a cheap structural SHA-256 locator plus runtime certificate verification.
    // The locator intentionally does NOT hash predicate candidates/semantics: correctness comes from the verifier.
    private static final String CACHE_NAMESPACE = "structure-result-v6";
    private static final String LEGACY_V5_CACHE_NAMESPACE = "structure-result-v5";
    private static final String LEGACY_V3_CACHE_NAMESPACE = "structure-result-v3";
    private static final String LEGACY_CACHE_NAMESPACE = "structure-result-v2";

    private static final Map<Object, SessionData> SESSION_RESULTS = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Cached file payloads; each controller still verifies the predicates before replay. */
    private static final Map<Path, CacheData> FILE_DATA_CACHE = new ConcurrentHashMap<>();
    /** BlockPattern -> predicate index is immutable for preview lifetime; avoid rebuilding it on every replay. */
    private static final Map<Object, PredicateIndex> PREDICATE_INDEX_CACHE = Collections.synchronizedMap(new IdentityHashMap<>());

    private PersistentStructureCache() {}

    /**
     * v6 cheap structural locator for a host preview.
     *
     * This is deliberately NOT a proof that predicate behavior is unchanged. It only identifies a
     * likely cached certificate cheaply. Every disk/session hit is verified by executing the current
     * predicates against the current preview world before the cached result is trusted.
     *
     * Inputs are therefore limited to stable structure/page topology: exact relative preview positions,
     * facing/flip metadata, BlockPattern dimensions/repetitions/directions/center offsets, identity-sharing
     * topology of TraceabilityPredicate objects, and cheap addCache/controller/common/limited shape bits.
     */
    public static byte[] definitionFingerprint(java.util.Collection<BlockPos> positions, IMultiController controller,
                                               String cacheIdentity) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        putString(md, CACHE_NAMESPACE + "-cheap-structural-certificate-v1");
        putString(md, cacheIdentity);

        hashRelativePositionsStructural(md, positions, controller);

        putString(md, controller.self().getFrontFacing().toString());
        putString(md, controller.self().getUpwardsFacing().toString());
        putString(md, Boolean.toString(controller.self().allowFlip()));

        Object pattern = controller.getPattern();
        if (pattern == null) throw new IllegalStateException("controller.getPattern() returned null");
        hashPatternStructural(md, pattern);
        return md.digest();
    }

    private static void hashRelativePositionsStructural(MessageDigest md, java.util.Collection<BlockPos> positions,
                                                        IMultiController controller) {
        BlockPos center = controller.self().getPos();
        long[] packed = new long[positions.size()];
        int count = 0;
        boolean fallback = false;
        for (BlockPos pos : positions) {
            int x = pos.getX() - center.getX(), y = pos.getY() - center.getY(), z = pos.getZ() - center.getZ();
            if (x < -1_048_575 || x > 1_048_575 || y < -1_048_575 || y > 1_048_575 ||
                    z < -1_048_575 || z > 1_048_575) {
                fallback = true;
                break;
            }
            packed[count++] = ((long) (x + 1_048_575) << 42) |
                    ((long) (y + 1_048_575) << 21) | (z + 1_048_575);
        }
        putInt(md, positions.size());
        if (!fallback) {
            Arrays.sort(packed);
            byte[] buffer = new byte[64 * 1024];
            int offset = 0;
            for (long value : packed) {
                if (offset + 8 > buffer.length) {
                    md.update(buffer, 0, offset);
                    offset = 0;
                }
                for (int shift = 56; shift >= 0; shift -= 8) buffer[offset++] = (byte) (value >>> shift);
            }
            if (offset > 0) md.update(buffer, 0, offset);
            return;
        }
        List<RelPos> relative = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            relative.add(new RelPos(pos.getX() - center.getX(), pos.getY() - center.getY(), pos.getZ() - center.getZ()));
        }
        relative.sort(Comparator.comparingInt(RelPos::x).thenComparingInt(RelPos::y).thenComparingInt(RelPos::z));
        for (RelPos pos : relative) {
            putInt(md, pos.x);
            putInt(md, pos.y);
            putInt(md, pos.z);
        }
    }

    private static void hashPatternStructural(MessageDigest md, Object pattern) throws Exception {
        Field aisleField = LazyPreviewRuntime.findField(pattern.getClass(), "aisleRepetitions");
        Field dirsField = LazyPreviewRuntime.findField(pattern.getClass(), "structureDir");
        Field centerField = LazyPreviewRuntime.findField(pattern.getClass(), "centerOffset");
        Field matchesField = LazyPreviewRuntime.findField(pattern.getClass(), "blockMatches");
        if (aisleField == null || dirsField == null || centerField == null || matchesField == null) {
            throw new NoSuchFieldException("BlockPattern structural fields");
        }
        aisleField.setAccessible(true);
        dirsField.setAccessible(true);
        centerField.setAccessible(true);
        matchesField.setAccessible(true);

        int[][] aisles = (int[][]) aisleField.get(pattern);
        putInt(md, aisles.length);
        for (int[] a : aisles) {
            putInt(md, a.length);
            for (int v : a) putInt(md, v);
        }

        Object[] dirs = (Object[]) dirsField.get(pattern);
        putInt(md, dirs.length);
        for (Object dir : dirs) putString(md, String.valueOf(dir));

        int[] center = (int[]) centerField.get(pattern);
        putInt(md, center.length);
        for (int v : center) putInt(md, v);

        Object[] a0 = (Object[]) matchesField.get(pattern);
        putInt(md, a0.length);
        int cells = 0;
        for (Object xObj : a0) {
            Object[] a1 = (Object[]) xObj;
            putInt(md, a1.length);
            for (Object yObj : a1) {
                Object[] a2 = (Object[]) yObj;
                putInt(md, a2.length);
                cells = Math.addExact(cells, a2.length);
            }
        }

        IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();
        List<Object> unique = new ArrayList<>();
        int[] layout = new int[cells];
        int li = 0;
        for (Object xObj : a0) {
            Object[] a1 = (Object[]) xObj;
            for (Object yObj : a1) {
                Object[] a2 = (Object[]) yObj;
                for (Object predicate : a2) {
                    if (predicate == null) {
                        layout[li++] = -1;
                        continue;
                    }
                    Integer id = ids.get(predicate);
                    if (id == null) {
                        id = unique.size();
                        ids.put(predicate, id);
                        unique.add(predicate);
                    }
                    layout[li++] = id;
                }
            }
        }

        // Cheap structural class bits prevent an old certificate from silently omitting positions that
        // changed between ANY/non-ANY while avoiding all expensive candidate/BlockState canonicalization.
        putInt(md, unique.size());
        for (Object pObj : unique) {
            if (pObj instanceof TraceabilityPredicate p) {
                md.update((byte) (p.isController ? 1 : 0));
                md.update((byte) (p.addCache() ? 1 : 0));
                putInt(md, p.common == null ? -1 : p.common.size());
                putInt(md, p.limited == null ? -1 : p.limited.size());
            } else {
                putString(md, pObj.getClass().getName());
            }
        }

        putInt(md, layout.length);
        hashIntsCompact(md, layout);
    }

    private static void hashIntsCompact(MessageDigest md, int[] values) {
        byte[] buffer = new byte[64 * 1024];
        int off = 0;
        for (int v : values) {
            if (off + 4 > buffer.length) {
                md.update(buffer, 0, off);
                off = 0;
            }
            buffer[off++] = (byte) (v >>> 24);
            buffer[off++] = (byte) (v >>> 16);
            buffer[off++] = (byte) (v >>> 8);
            buffer[off++] = (byte) v;
        }
        if (off > 0) md.update(buffer, 0, off);
    }

    /** Replay a same-session result without touching disk. */
    public static boolean tryReplaySession(IMultiController controller) {
        SessionData session = controller == null ? null : SESSION_RESULTS.get(controller);
        if (session == null) return false;
        try {
            long verifyStart = System.nanoTime();
            boolean verified = verifyCachedSolution(controller, session.data);
            long verifyNs = System.nanoTime() - verifyStart;
            LazyPreviewRuntime.recordVerification(verifyNs, verified);
            if (!verified) {
                SESSION_RESULTS.remove(controller);
                return false;
            }
            apply(controller, session.data);
            return true;
        } catch (Throwable t) {
            LazyPreviewRuntime.log("WARNING: failed to verify/replay same-session structure cache " + session.identity + ": " + t);
            SESSION_RESULTS.remove(controller);
            return false;
        }
    }

    public static boolean tryReplay(IMultiController controller, String cacheIdentity, byte[] strictFingerprint) {
        if (controller == null || strictFingerprint == null) return false;
        SessionData session = SESSION_RESULTS.get(controller);
        if (session != null && cacheIdentity.equals(session.identity) &&
                (session.fingerprint == null || MessageDigest.isEqual(session.fingerprint, strictFingerprint)) &&
                tryReplaySession(controller))
            return true;
        long start = System.nanoTime();
        Path strictPath = cachePath(cacheIdentity, strictFingerprint);
        if (Files.isRegularFile(strictPath)) {
            try {
                CacheData data = readCached(strictPath, cacheIdentity, strictFingerprint);
                long verifyStart = System.nanoTime();
                boolean verified;
                try {
                    verified = verifyCachedSolution(controller, data);
                } catch (Throwable verifyError) {
                    verified = false;
                    LazyPreviewRuntime.log("WARNING: cache certificate verifier failed id=" + cacheIdentity +
                            ": " + verifyError);
                }
                long verifyNs = System.nanoTime() - verifyStart;
                LazyPreviewRuntime.recordVerification(verifyNs, verified);
                if (!verified) {
                    LazyPreviewRuntime.log("persistent structure cache VERIFY FAIL id=" + cacheIdentity +
                            " key=" + hexShort(strictFingerprint) + " verify_ms=" + (verifyNs / 1_000_000L) +
                            "; falling back to stock matcher");
                    return false;
                }
                long applyStart = System.nanoTime();
                apply(controller, data);
                long applyMs = (System.nanoTime() - applyStart) / 1_000_000L;
                SESSION_RESULTS.put(controller, new SessionData(cacheIdentity, strictFingerprint.clone(), data));
                long totalMs = (System.nanoTime() - start) / 1_000_000L;
                LazyPreviewRuntime.log("persistent structure cache VERIFIED HIT id=" + cacheIdentity +
                        " key=" + hexShort(strictFingerprint) + " predicates=" + data.predicates.size() +
                        " renderMask=" + data.renderMask.size() + " verify_ms=" + (verifyNs / 1_000_000L) +
                        " apply_ms=" + applyMs + " total_ms=" + totalMs);
                return true;
            } catch (Throwable t) {
                LazyPreviewRuntime.log("WARNING: ignored invalid persistent structure cache " + strictPath + ": " + t);
                FILE_DATA_CACHE.remove(strictPath);
                try {
                    Files.deleteIfExists(strictPath);
                } catch (Throwable ignored) {}
            }
        }

        return false;
    }

    /**
     * v6 migration does not reconstruct any historical fingerprint. A historical cache file is only
     * a candidate certificate: decode it, execute the current verifier, and promote the first one that
     * still proves valid. This makes migration cost proportional to certificate verification rather
     * than the old multi-second semantic fingerprint.
     */
    public static boolean tryPromoteAnyLegacyCertificate(IMultiController controller, String cacheIdentity,
                                                         byte[] structuralFingerprint) {
        if (controller == null || structuralFingerprint == null) return false;
        String[] namespaces = { LEGACY_V5_CACHE_NAMESPACE, LEGACY_V3_CACHE_NAMESPACE, LEGACY_CACHE_NAMESPACE };
        for (String namespace : namespaces) {
            Path dir = namespaceDir(namespace, cacheIdentity);
            if (!Files.isDirectory(dir)) continue;
            long scanStart = System.nanoTime();
            try {
                List<Path> files;
                try (var stream = Files.list(dir)) {
                    files = stream.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().matches("[0-9a-fA-F]{64}\\.bin"))
                            .sorted((a, b) -> {
                                try {
                                    return Long.compare(Files.getLastModifiedTime(b).toMillis(),
                                            Files.getLastModifiedTime(a).toMillis());
                                } catch (Throwable ignored) {
                                    return a.getFileName().toString().compareTo(b.getFileName().toString());
                                }
                            })
                            .limit(32)
                            .toList();
                }
                for (Path oldPath : files) {
                    String name = oldPath.getFileName().toString();
                    byte[] oldFingerprint;
                    CacheData data;
                    try {
                        oldFingerprint = parseHexFingerprint(name.substring(0, 64));
                        data = readCached(oldPath, cacheIdentity, oldFingerprint);
                    } catch (Throwable badFile) {
                        continue;
                    }

                    long verifyStart = System.nanoTime();
                    boolean verified;
                    try {
                        verified = verifyCachedSolution(controller, data);
                    } catch (Throwable verifyError) {
                        verified = false;
                    }
                    long verifyNs = System.nanoTime() - verifyStart;
                    LazyPreviewRuntime.recordVerification(verifyNs, verified);
                    if (!verified) continue;

                    apply(controller, data);
                    SESSION_RESULTS.put(controller,
                            new SessionData(cacheIdentity, structuralFingerprint.clone(), data));
                    storeData(cacheIdentity, structuralFingerprint, data);
                    LazyPreviewRuntime.certificatePromotion(cacheIdentity, namespace);
                    long scanNs = System.nanoTime() - scanStart;
                    LazyPreviewRuntime.recordLegacyScanNanos(scanNs);
                    LazyPreviewRuntime.log("persistent structure cache CERTIFICATE-PROMOTE VERIFIED HIT id=" +
                            cacheIdentity + " source=" + namespace + " old=" + hexShort(oldFingerprint) +
                            " v6=" + hexShort(structuralFingerprint) + " predicates=" + data.predicates.size() +
                            " verify_ms=" + (verifyNs / 1_000_000L) + " scan_ms=" + (scanNs / 1_000_000L));
                    return true;
                }
            } catch (Throwable t) {
                LazyPreviewRuntime.log("WARNING: legacy certificate scan failed id=" + cacheIdentity +
                        " namespace=" + namespace + ": " + t);
            }
            LazyPreviewRuntime.recordLegacyScanNanos(System.nanoTime() - scanStart);
        }
        return false;
    }

    /** Capture a successful matcher result and publish it to session + disk atomically. */
    public static void write(IMultiController controller, Object matchedPattern, String cacheIdentity, byte[] strictFingerprint) {
        try {
            CacheData data = capture(controller, matchedPattern);
            SESSION_RESULTS.put(controller,
                    new SessionData(cacheIdentity,
                            strictFingerprint == null ? null : strictFingerprint.clone(), data));
            if (strictFingerprint == null) return;
            storeData(cacheIdentity, strictFingerprint, data);
            LazyPreviewRuntime.cacheWrite(cacheIdentity);
        } catch (Throwable t) {
            LazyPreviewRuntime.log("WARNING: failed to write persistent structure cache id=" + cacheIdentity + ": " + t);
        }
    }

    private static void storeData(String cacheIdentity, byte[] fingerprint, CacheData data) throws Exception {
        Path path = cachePath(cacheIdentity, fingerprint);
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp-" + Long.toUnsignedString(System.nanoTime()));
        try {
            write(tmp, cacheIdentity, fingerprint, data);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Throwable ignored) {}
        }
        FILE_DATA_CACHE.put(path, data);
    }

    public static Path cachePath(String cacheIdentity, byte[] fingerprint) {
        String id = sanitize(cacheIdentity);
        return gameDir().resolve("cache").resolve("gtl_lazy_preview_fix")
                .resolve(CACHE_NAMESPACE).resolve(id).resolve(hex(fingerprint) + ".bin");
    }

    private static Path namespaceDir(String namespace, String cacheIdentity) {
        return gameDir().resolve("cache").resolve("gtl_lazy_preview_fix")
                .resolve(namespace).resolve(sanitize(cacheIdentity));
    }

    private static byte[] parseHexFingerprint(String text) {
        if (text == null || text.length() != 64) throw new IllegalArgumentException("bad alias fingerprint length");
        byte[] out = new byte[32];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(text.charAt(i * 2), 16);
            int lo = Character.digit(text.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad alias fingerprint hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static CacheData readCached(Path path, String identity, byte[] fingerprint) throws Exception {
        CacheData cached = FILE_DATA_CACHE.get(path);
        if (cached != null) return cached;
        CacheData data = read(path, identity, fingerprint);
        FILE_DATA_CACHE.put(path, data);
        return data;
    }

    private static String sanitize(String id) {
        return id.replace(':', '_').replace('/', '_').replace('\\', '_')
                .replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path gameDir() {
        return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
    }

    private static CacheData capture(IMultiController controller, Object matchedPattern) throws Exception {
        MultiblockState state = controller.getMultiblockState();
        Object context = state.getMatchContext();
        Object predicateObj = contextGet(context, "predicates");
        Map<?, ?> predicateMap = predicateObj instanceof Map<?, ?> map ? map : Map.of();

        if (matchedPattern == null) throw new IllegalArgumentException("matchedPattern is null");
        PredicateIndex index = buildPredicateIndex(matchedPattern);
        Object controllerPos = controller.self().getPos();
        int[] c = xyz(controllerPos);

        List<PredicateEntry> predicates = new ArrayList<>(predicateMap.size());
        for (Map.Entry<?, ?> e : predicateMap.entrySet()) {
            Integer predicateId = index.ids.get(e.getValue());
            if (predicateId == null) {
                throw new IllegalStateException("matched TraceabilityPredicate is absent from current BlockPattern");
            }
            int[] p = xyz(e.getKey());
            predicates.add(new PredicateEntry(p[0] - c[0], p[1] - c[1], p[2] - c[2], predicateId));
        }
        predicates.sort(Comparator.comparingInt((PredicateEntry e) -> e.x)
                .thenComparingInt(e -> e.y).thenComparingInt(e -> e.z));

        List<RelPos> renderMask = captureRenderMaskRelative(context, c);
        return new CacheData(state.isNeededFlip(), predicates, renderMask);
    }

    private static void apply(IMultiController controller, CacheData data) throws Exception {
        MultiblockState state = controller.getMultiblockState();
        state.getMatchContext().reset();
        PredicateIndex index = buildPredicateIndex(controller.getPattern());
        BlockPos center = controller.self().getPos();
        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();
        for (PredicateEntry entry : data.predicates) {
            if (entry.predicateId < 0 || entry.predicateId >= index.values.size()) {
                throw new IllegalStateException("Cached predicate index is out of range");
            }
            predicateMap.put(center.offset(entry.x, entry.y, entry.z),
                    (TraceabilityPredicate) index.values.get(entry.predicateId));
        }
        state.getMatchContext().set("predicates", predicateMap);
        it.unimi.dsi.fastutil.longs.LongOpenHashSet renderMask = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (RelPos pos : data.renderMask) {
            renderMask.add(center.offset(pos.x, pos.y, pos.z).asLong());
        }
        state.getMatchContext().set("renderMask", renderMask);
        state.setNeededFlip(data.neededFlip);

        // Preview-only replay: the visible GTLCore outputs are formed state + predicate map + renderMask.
        // Do not call onStructureFormed(), which can attach live machine traits/parts and custom runtime logic.
        Field formed = LazyPreviewRuntime.findField(controller.getClass(), "isFormed");
        if (formed == null) throw new NoSuchFieldException("MultiblockControllerMachine.isFormed");
        formed.setAccessible(true);
        formed.setBoolean(controller, true);
    }

    /**
     * Validate a cached matcher certificate against the current preview world without re-running
     * BlockPattern's aisle/repetition search. The cache already tells us which TraceabilityPredicate
     * accepted every non-ANY position. We replay those exact predicate tests on a clean MultiblockState,
     * keeping one layer-count map per logical pattern Z layer and the stock global-count map.
     *
     * This catches dynamic predicates that read config/static/runtime state only when test() executes,
     * even when the final BlockPattern object graph (and therefore the SHA-256 key) is unchanged.
     */
    private static boolean verifyCachedSolution(IMultiController controller, CacheData data) throws Exception {
        if (controller == null || data == null) return false;
        MultiblockState state = controller.getMultiblockState();
        if (!(state instanceof IMultiblockStateInvoker access)) {
            throw new IllegalStateException("MultiblockState lifecycle interface is unavailable");
        }

        Object pattern = controller.getPattern();
        if (pattern == null) throw new IllegalStateException("controller.getPattern() returned null");
        PredicateIndex index = buildPredicateIndex(pattern);

        Object controllerPosObj = controller.self().getPos();
        int[] center = xyz(controllerPosObj);
        Object frontFacing = controller.self().getFrontFacing();
        Object upwardsFacing = controller.self().getUpwardsFacing();

        Method transform = LazyPreviewRuntime.findMethodByNameAndArity(pattern.getClass(),
                "setActualRelativeOffset", 6);
        if (transform == null) throw new NoSuchMethodException("BlockPattern.setActualRelativeOffset");
        transform.setAccessible(true);
        int[] basisZ = xyz(transform.invoke(pattern, 0, 0, 1, frontFacing, upwardsFacing, data.neededFlip));

        access.cleanState();
        Object verifyContext = state.getMatchContext();
        Map<BlockPos, TraceabilityPredicate> verifiedPredicateMap = new HashMap<>();
        contextSet(verifyContext, "predicates", verifiedPredicateMap);
        Map<Integer, Map<SimplePredicate, Integer>> layerMaps = new HashMap<>();

        for (PredicateEntry e : data.predicates) {
            if (e.predicateId < 0 || e.predicateId >= index.values.size()) {
                return false;
            }
            Object pObj = index.values.get(e.predicateId);
            if (!(pObj instanceof TraceabilityPredicate predicate)) {
                throw new IllegalStateException("unexpected predicate runtime type: " + pObj.getClass().getName());
            }

            int logicalZ = e.x * basisZ[0] + e.y * basisZ[1] + e.z * basisZ[2];
            Map<SimplePredicate, Integer> layer = layerMaps.computeIfAbsent(logicalZ, ignored -> new HashMap<>());
            state.getLayerCount().clear();
            state.getLayerCount().putAll(layer);

            BlockPos pos = new BlockPos(center[0] + e.x, center[1] + e.y, center[2] + e.z);
            if (!predicate.addCache()) return false; // certificate entries must still be non-ANY
            if (!access.updateState(pos, predicate)) return false;
            state.addPosCache(pos); // stock BlockPattern does this before predicate.test()
            verifiedPredicateMap.put(pos, predicate); // stock savePredicate path also publishes before test
            if (!predicate.test(state)) return false;
            layer.clear();
            layer.putAll(state.getLayerCount());
        }

        // Stock BlockPattern checks every layer-local minimum at the end of that layer.
        for (Map<SimplePredicate, Integer> layer : layerMaps.values()) {
            for (Map.Entry<SimplePredicate, Integer> entry : layer.entrySet()) {
                int min = entry.getKey().minLayerCount;
                if (min != -1 && entry.getValue() < min) return false;
            }
        }

        // And global minimums once after the whole successful pattern.
        for (Map.Entry<SimplePredicate, Integer> entry : state.getGlobalCount().entrySet()) {
            int min = entry.getKey().minCount;
            if (min != -1 && entry.getValue() < min) return false;
        }

        // A runtime branch may still pass while choosing a different disableRenderFormed branch.
        // Compare the validator's current renderMask with the cached one before trusting the replay.
        Object context = state.getMatchContext();
        List<RelPos> currentMask = captureRenderMaskRelative(context, center);
        return currentMask.equals(data.renderMask);
    }

    private static List<RelPos> captureRenderMaskRelative(Object context, int[] center) {
        LongSet mask = ((PatternMatchContext) context).getOrDefault("renderMask", LongSets.EMPTY_SET);
        List<RelPos> renderMask = new ArrayList<>(mask.size());
        for (long value : mask) {
            BlockPos pos = BlockPos.of(value);
            renderMask.add(new RelPos(pos.getX() - center[0], pos.getY() - center[1], pos.getZ() - center[2]));
        }
        renderMask.sort(Comparator.comparingInt(RelPos::x).thenComparingInt(RelPos::y).thenComparingInt(RelPos::z));
        return renderMask;
    }

    private static void write(Path path, String cacheIdentity, byte[] fingerprint, CacheData data) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeUTF(cacheIdentity);
            out.writeInt(fingerprint.length);
            out.write(fingerprint);
            out.writeBoolean(data.neededFlip);
            out.writeInt(data.predicates.size());
            for (PredicateEntry e : data.predicates) {
                out.writeInt(e.x);
                out.writeInt(e.y);
                out.writeInt(e.z);
                out.writeInt(e.predicateId);
            }
            out.writeInt(data.renderMask.size());
            for (RelPos p : data.renderMask) {
                out.writeInt(p.x);
                out.writeInt(p.y);
                out.writeInt(p.z);
            }
        }
    }

    private static CacheData read(Path path, String expectedIdentity, byte[] expectedFingerprint) throws Exception {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (in.readInt() != MAGIC) throw new IllegalStateException("bad magic");
            if (in.readInt() != FORMAT) throw new IllegalStateException("unsupported cache format");
            if (!expectedIdentity.equals(in.readUTF())) throw new IllegalStateException("machine/cache identity mismatch");
            int fpLen = in.readInt();
            if (fpLen != expectedFingerprint.length || fpLen <= 0 || fpLen > 128) {
                throw new IllegalStateException("fingerprint length mismatch");
            }
            byte[] actual = in.readNBytes(fpLen);
            if (!MessageDigest.isEqual(expectedFingerprint, actual)) throw new IllegalStateException("fingerprint mismatch");
            boolean neededFlip = in.readBoolean();
            int predCount = checkedCount(in.readInt(), "predicate");
            List<PredicateEntry> predicates = new ArrayList<>(predCount);
            for (int i = 0; i < predCount; i++) {
                predicates.add(new PredicateEntry(in.readInt(), in.readInt(), in.readInt(), in.readInt()));
            }
            int maskCount = checkedCount(in.readInt(), "renderMask");
            List<RelPos> mask = new ArrayList<>(maskCount);
            for (int i = 0; i < maskCount; i++) mask.add(new RelPos(in.readInt(), in.readInt(), in.readInt()));
            if (in.read() != -1) throw new IllegalStateException("trailing bytes");
            return new CacheData(neededFlip, predicates, mask);
        } catch (EOFException e) {
            throw new IllegalStateException("truncated cache", e);
        }
    }

    private static int checkedCount(int n, String what) {
        if (n < 0 || n > MAX_ENTRIES) throw new IllegalStateException("invalid " + what + " count: " + n);
        return n;
    }

    private static PredicateIndex buildPredicateIndex(Object pattern) throws Exception {
        PredicateIndex cached = PREDICATE_INDEX_CACHE.get(pattern);
        if (cached != null) return cached;
        Field blockMatches = LazyPreviewRuntime.findField(pattern.getClass(), "blockMatches");
        if (blockMatches == null) throw new NoSuchFieldException("BlockPattern.blockMatches");
        blockMatches.setAccessible(true);
        Object[] a0 = (Object[]) blockMatches.get(pattern);
        IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();
        List<Object> values = new ArrayList<>();
        for (Object xObj : a0) {
            Object[] a1 = (Object[]) xObj;
            for (Object yObj : a1) {
                Object[] a2 = (Object[]) yObj;
                for (Object predicate : a2) {
                    if (predicate != null && !ids.containsKey(predicate)) {
                        ids.put(predicate, values.size());
                        values.add(predicate);
                    }
                }
            }
        }
        PredicateIndex result = new PredicateIndex(ids, values);
        PREDICATE_INDEX_CACHE.put(pattern, result);
        return result;
    }

    private static Object contextGet(Object context, String key) {
        return ((PatternMatchContext) context).get(key);
    }

    private static void contextSet(Object context, String key, Object value) {
        ((PatternMatchContext) context).set(key, value);
    }

    private static int[] xyz(Object value) {
        BlockPos pos = (BlockPos) value;
        return new int[] { pos.getX(), pos.getY(), pos.getZ() };
    }

    private static void putInt(MessageDigest md, int value) {
        md.update((byte) (value >>> 24));
        md.update((byte) (value >>> 16));
        md.update((byte) (value >>> 8));
        md.update((byte) value);
    }

    private static void putString(MessageDigest md, String value) {
        byte[] b = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        putInt(md, b.length);
        md.update(b);
    }

    public static String hex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte v : bytes) b.append(Character.forDigit((v >>> 4) & 15, 16)).append(Character.forDigit(v & 15, 16));
        return b.toString();
    }

    public static String hexShort(byte[] bytes) {
        String h = hex(bytes);
        return h.substring(0, Math.min(16, h.length()));
    }

    private record RelPos(int x, int y, int z) {}

    private record PredicateEntry(int x, int y, int z, int predicateId) {}

    private record CacheData(boolean neededFlip, List<PredicateEntry> predicates, List<RelPos> renderMask) {}

    private record PredicateIndex(IdentityHashMap<Object, Integer> ids, List<Object> values) {}

    private record SessionData(String identity, byte[] fingerprint, CacheData data) {}
}
