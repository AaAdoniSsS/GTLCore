package org.gtlcore.gtlcore.integration.ae2.graph.core;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Portable scoped integer certificates and an independent arithmetic checker. */
public final class CountProof {

    private CountProof() {}

    public record Row(Map<Integer, BigInteger> terms, BigInteger upper) {

        public Row {
            terms = Collections.unmodifiableMap(new TreeMap<>(terms));
        }
    }

    public record Fraction(BigInteger numerator, BigInteger denominator) {

        public Fraction {
            if (denominator.signum() <= 0) throw new IllegalArgumentException("Nonpositive denominator");
        }

        Fraction add(Fraction other) {
            BigInteger n = numerator.multiply(other.denominator).add(other.numerator.multiply(denominator));
            BigInteger d = denominator.multiply(other.denominator), gcd = n.gcd(d);
            return new Fraction(n.divide(gcd), d.divide(gcd));
        }

        Fraction multiply(BigInteger value) {
            return new Fraction(numerator.multiply(value), denominator);
        }
    }

    /** Axioms describe the exact scope, including branch/domain assumptions, never an implicit whole-order claim. */
    public record Certificate(String scope, int variables, List<Row> axioms, List<List<Row>> forbidden,
                              List<Fraction> farkas, boolean closed) {

        public Certificate {
            axioms = List.copyOf(axioms);
            forbidden = forbidden.stream().map(List::copyOf).toList();
            farkas = List.copyOf(farkas);
        }
    }

    public enum Verdict {
        VERIFIED,
        INVALID,
        INCOMPLETE
    }

    /** Opt-in diagnostic archive. Truncation is explicit and cannot be confused with a complete proof. */
    public static final class Journal {

        private final long maximumBytes;
        private long bytes;
        private boolean truncated;
        private final List<Certificate> entries = new ArrayList<>();
        private final List<ExecutionProof.Certificate> executions = new ArrayList<>();

        public Journal(long maximumBytes) {
            if (maximumBytes <= 0) throw new IllegalArgumentException("Nonpositive proof archive size");
            this.maximumBytes = maximumBytes;
        }

        public synchronized void add(Certificate certificate) {
            long size = 256L + certificate.axioms.stream().mapToLong(CountProof::size).sum() +
                    certificate.forbidden.stream().flatMap(List::stream).mapToLong(CountProof::size).sum() +
                    certificate.farkas.stream().mapToLong(f -> 96L + (f.numerator.bitLength() + f.denominator.bitLength()) / 8).sum();
            if (size > maximumBytes - bytes) {
                truncated = true;
                return;
            }
            bytes += size;
            entries.add(certificate);
        }

        public synchronized List<Certificate> entries() {
            return List.copyOf(entries);
        }

        public synchronized List<ExecutionProof.Certificate> executions() {
            return List.copyOf(executions);
        }

        public synchronized boolean truncated() {
            return truncated;
        }

        synchronized void markIncomplete() {
            truncated = true;
        }

        public synchronized void add(ExecutionProof.Certificate proof) {
            long size = 512L + 96L * proof.initial().size() * (2L + proof.inputs().size() * 2L + proof.states().size());
            if (size > maximumBytes - bytes) {
                truncated = true;
                return;
            }
            bytes += size;
            executions.add(proof);
        }

        public synchronized void write(Path path) throws IOException {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
                output.writeInt(0x43475032);
                output.writeBoolean(truncated);
                output.writeInt(entries.size());
                for (var proof : entries) {
                    output.writeUTF(proof.scope);
                    output.writeInt(proof.variables);
                    rows(output, proof.axioms);
                    output.writeInt(proof.forbidden.size());
                    for (var clause : proof.forbidden) rows(output, clause);
                    output.writeInt(proof.farkas.size());
                    for (var weight : proof.farkas) {
                        integer(output, weight.numerator);
                        integer(output, weight.denominator);
                    }
                    output.writeBoolean(proof.closed);
                }
                output.writeInt(executions.size());
                for (var proof : executions) {
                    output.writeUTF(proof.scope());
                    output.writeInt(proof.kind().ordinal());
                    vectors(output, List.of(proof.initial(), proof.goal()));
                    vectors(output, proof.inputs());
                    vectors(output, proof.outputs());
                    vectors(output, proof.states());
                    output.writeInt(proof.marked().size());
                    for (int id : new TreeSet<>(proof.marked())) output.writeInt(id);
                }
            }
        }
    }

    private static long size(Row row) {
        return 128L + row.upper.bitLength() / 8 + row.terms.values().stream().mapToLong(v -> 96L + v.bitLength() / 8).sum();
    }

    static Certificate certificate(String scope, int variables, List<ExactLinearProgram.Constraint> rows,
                                   List<CountConflict> conflicts, ExactRational[] weights, boolean closed) {
        return new Certificate(scope, variables, rows.stream().map(CountProof::row).toList(),
                conflicts.stream().map(c -> c.assumptions().stream().map(CountProof::row).toList()).toList(),
                weights == null ? List.of() : Arrays.stream(weights).map(w -> new Fraction(w.numerator(), w.denominator())).toList(), closed);
    }

    private static Row row(ExactLinearProgram.Constraint row) {
        return new Row(row.terms(), row.upper());
    }

    /** No planner, presolver, LP tableau or conflict-analysis routine is used here. */
    public static Verdict verify(Certificate proof, long maximumWork) {
        if (proof.variables < 0 || proof.variables > 16384 || maximumWork <= 0) return Verdict.INVALID;
        for (var row : proof.axioms) if (!valid(row, proof.variables)) return Verdict.INVALID;
        for (var clause : proof.forbidden) for (var row : clause) if (!valid(row, proof.variables)) return Verdict.INVALID;
        long[] work = { maximumWork };
        try {
            List<List<Row>> established = new ArrayList<>();
            for (var clause : proof.forbidden) {
                List<Row> assumed = new ArrayList<>(proof.axioms);
                assumed.addAll(clause);
                if (!contradiction(proof.variables, assumed, established, work)) return Verdict.INVALID;
                established.add(clause);
            }
            if (!proof.farkas.isEmpty()) {
                if (proof.farkas.size() != proof.axioms.size()) return Verdict.INVALID;
                Fraction total = new Fraction(BigInteger.ZERO, BigInteger.ONE);
                Map<Integer, Fraction> columns = new HashMap<>();
                for (int i = 0; i < proof.axioms.size(); i++) {
                    tick(work);
                    var weight = proof.farkas.get(i);
                    if (weight.numerator.signum() < 0) return Verdict.INVALID;
                    var row = proof.axioms.get(i);
                    total = total.add(weight.multiply(row.upper));
                    for (var term : row.terms.entrySet()) {
                        tick(work);
                        columns.merge(term.getKey(), weight.multiply(term.getValue()), Fraction::add);
                    }
                }
                if (total.numerator.signum() >= 0 || columns.values().stream().anyMatch(v -> v.numerator.signum() < 0)) return Verdict.INVALID;
                return Verdict.VERIFIED;
            }
            return !proof.closed || contradiction(proof.variables, proof.axioms, established, work) ? Verdict.VERIFIED : Verdict.INVALID;
        } catch (CheckLimit limit) {
            return Verdict.INCOMPLETE;
        }
    }

    private static boolean valid(Row row, int variables) {
        return row.upper != null && row.terms.entrySet().stream().allMatch(e -> e.getKey() >= 0 && e.getKey() < variables && e.getValue() != null);
    }

    private static boolean contradiction(int variables, List<Row> axioms, List<List<Row>> clauses, long[] work) {
        BigInteger[] low = new BigInteger[variables], high = new BigInteger[variables];
        Arrays.fill(low, BigInteger.ZERO);
        boolean changed;
        do {
            changed = false;
            List<Row> rows = new ArrayList<>(axioms);
            for (var clause : clauses) {
                Row pending = null;
                boolean ignored = false;
                for (var premise : clause) {
                    BigInteger min = endpoint(premise, low, high, false, work);
                    if (min != null && min.compareTo(premise.upper) > 0) {
                        ignored = true;
                        break;
                    }
                    BigInteger max = endpoint(premise, low, high, true, work);
                    if (max == null || max.compareTo(premise.upper) > 0) {
                        if (pending != null) {
                            ignored = true;
                            break;
                        }
                        pending = premise;
                    }
                }
                if (ignored) continue;
                if (pending == null) return true;
                Map<Integer, BigInteger> opposite = new HashMap<>();
                pending.terms.forEach((key, value) -> opposite.put(key, value.negate()));
                rows.add(new Row(opposite, pending.upper.negate().subtract(BigInteger.ONE)));
            }
            for (var row : rows) {
                tick(work);
                BigInteger known = BigInteger.ZERO;
                int unknown = 0;
                for (var term : row.terms.entrySet()) {
                    tick(work);
                    if (term.getValue().signum() == 0) continue;
                    BigInteger bound = term.getValue().signum() > 0 ? low[term.getKey()] : high[term.getKey()];
                    if (bound == null) unknown++;
                    else known = known.add(bound.multiply(term.getValue()));
                }
                if (unknown == 0 && known.compareTo(row.upper) > 0) return true;
                for (var term : row.terms.entrySet()) {
                    tick(work);
                    int id = term.getKey();
                    BigInteger coefficient = term.getValue();
                    if (coefficient.signum() == 0) continue;
                    BigInteger old = coefficient.signum() > 0 ? low[id] : high[id];
                    if (unknown - (old == null ? 1 : 0) != 0) continue;
                    BigInteger remainder = row.upper.subtract(known).add(old == null ? BigInteger.ZERO : old.multiply(coefficient));
                    if (coefficient.signum() > 0) {
                        BigInteger bound = floor(remainder, coefficient);
                        if (high[id] == null || bound.compareTo(high[id]) < 0) {
                            high[id] = bound;
                            changed = true;
                        }
                    } else {
                        BigInteger bound = floor(remainder, coefficient.negate()).negate();
                        if (bound.compareTo(low[id]) > 0) {
                            low[id] = bound;
                            changed = true;
                        }
                    }
                    if (high[id] != null && low[id].compareTo(high[id]) > 0) return true;
                }
            }
        } while (changed);
        return false;
    }

    private static BigInteger endpoint(Row row, BigInteger[] low, BigInteger[] high, boolean maximum, long[] work) {
        BigInteger result = BigInteger.ZERO;
        for (var term : row.terms.entrySet()) {
            tick(work);
            if (term.getValue().signum() == 0) continue;
            BigInteger bound = (term.getValue().signum() > 0) == maximum ? high[term.getKey()] : low[term.getKey()];
            if (bound == null) return null;
            result = result.add(bound.multiply(term.getValue()));
        }
        return result;
    }

    private static BigInteger floor(BigInteger numerator, BigInteger positive) {
        BigInteger[] result = numerator.divideAndRemainder(positive);
        return result[1].signum() < 0 ? result[0].subtract(BigInteger.ONE) : result[0];
    }

    private static final class CheckLimit extends RuntimeException {

        CheckLimit() {
            super(null, null, false, false);
        }
    }

    private static void tick(long[] work) {
        if (--work[0] < 0) throw new CheckLimit();
    }

    private static void integer(DataOutputStream out, BigInteger value) throws IOException {
        byte[] bytes = value.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static BigInteger integer(DataInputStream in) throws IOException {
        int size = length(in, 65536);
        if (size == 0) throw new IOException("Empty integer");
        byte[] bytes = in.readNBytes(size);
        if (bytes.length != size) throw new EOFException();
        return new BigInteger(bytes);
    }

    private static int length(DataInputStream in, int max) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > max) throw new IOException("Certificate size exceeds limit");
        return length;
    }

    private static void rows(DataOutputStream out, List<Row> rows) throws IOException {
        out.writeInt(rows.size());
        for (var row : rows) {
            out.writeInt(row.terms.size());
            for (var term : row.terms.entrySet()) {
                out.writeInt(term.getKey());
                integer(out, term.getValue());
            }
            integer(out, row.upper);
        }
    }

    private static List<Row> rows(DataInputStream in) throws IOException {
        List<Row> rows = new ArrayList<>();
        for (int remaining = length(in, 65536); remaining > 0; remaining--) {
            Map<Integer, BigInteger> terms = new LinkedHashMap<>();
            for (int count = length(in, 16384); count > 0; count--) {
                int key = in.readInt();
                if (terms.put(key, integer(in)) != null) throw new IOException("Duplicate column");
            }
            rows.add(new Row(terms, integer(in)));
        }
        return rows;
    }

    private static void vectors(DataOutputStream out, List<List<BigInteger>> vectors) throws IOException {
        out.writeInt(vectors.size());
        for (var vector : vectors) {
            out.writeInt(vector.size());
            for (var value : vector) integer(out, value);
        }
    }

    private static List<List<BigInteger>> vectors(DataInputStream in) throws IOException {
        List<List<BigInteger>> result = new ArrayList<>();
        for (int n = length(in, 65536); n > 0; n--) {
            List<BigInteger> vector = new ArrayList<>();
            for (int m = length(in, 16384); m > 0; m--) vector.add(integer(in));
            result.add(vector);
        }
        return result;
    }

    public static Journal read(Path path) throws IOException {
        if (Files.size(path) > 64L << 20) throw new IOException("Certificate archive too large");
        Journal journal = new Journal(128L << 20);
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != 0x43475032) throw new IOException("Unsupported certificate format");
            journal.truncated = input.readBoolean();
            for (int remaining = length(input, 8192); remaining > 0; remaining--) {
                String scope = input.readUTF();
                int variables = length(input, 16384);
                List<Row> axioms = rows(input);
                List<List<Row>> forbidden = new ArrayList<>();
                for (int n = length(input, 8192); n > 0; n--) forbidden.add(rows(input));
                List<Fraction> weights = new ArrayList<>();
                for (int n = length(input, 65536); n > 0; n--) weights.add(new Fraction(integer(input), integer(input)));
                journal.add(new Certificate(scope, variables, axioms, forbidden, weights, input.readBoolean()));
            }
            for (int n = length(input, 8192); n > 0; n--) {
                String scope = input.readUTF();
                int kind = length(input, ExecutionProof.Kind.values().length - 1);
                var boundaries = vectors(input);
                if (boundaries.size() != 2) throw new IOException("Invalid execution boundaries");
                var inputs = vectors(input);
                var outputs = vectors(input);
                var states = vectors(input);
                Set<Integer> marked = new LinkedHashSet<>();
                for (int m = length(input, 16384); m > 0; m--) marked.add(input.readInt());
                journal.add(new ExecutionProof.Certificate(scope, ExecutionProof.Kind.values()[kind], boundaries.get(0), boundaries.get(1), inputs, outputs, states, marked));
            }
            if (input.read() != -1) throw new IOException("Trailing certificate bytes");
        }
        return journal;
    }

    public static void main(String[] args) throws IOException {
        Journal journal = read(Path.of(args[0]));
        boolean valid = !journal.truncated();
        for (var proof : journal.entries()) {
            Verdict result = verify(proof, 20_000_000);
            System.out.println(proof.scope + ": " + result + "; closed=" + proof.closed);
            valid &= result == Verdict.VERIFIED;
        }
        for (var proof : journal.executions()) {
            Verdict result = ExecutionProof.verify(proof, 20_000_000);
            System.out.println(proof.scope() + ": " + result + "; kind=" + proof.kind());
            valid &= result == Verdict.VERIFIED;
        }
        if (!valid) throw new IllegalStateException("Incomplete or invalid proof archive");
    }
}
