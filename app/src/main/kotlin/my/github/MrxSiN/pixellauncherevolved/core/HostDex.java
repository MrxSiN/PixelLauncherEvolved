package my.github.MrxSiN.pixellauncherevolved.core;

import android.os.Process;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.UsingFieldData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DexKit side of {@link Host}: matches a missed symbol against the fingerprint
 * recorded from a build where it resolved, and records fresh fingerprints once
 * per launcher build.
 *
 * Loaded only when a name misses or a new launcher build is first seen, so a
 * start whose names all resolve never loads {@code libdexkit} or this class.
 *
 * Fingerprint lines ({@code host.db}), tab separated, lists joined by U+0001:
 * <pre>
 * C name super interfaces source modifiers methodCount fieldCount memberNames
 * M owner name parameterTypes returnType modifiers strings invokes fields callers
 * F owner name type modifiers readers writers
 * </pre>
 * Every class name is the one the sources use. Each process keeps its own file;
 * a build with none yet starts from {@code ple/<package>.db} shipped in the
 * module, recorded from the launcher it was built against. The recorder fingerprints each
 * class {@code ple/host.syms} names, its launcher superclasses, and their
 * members whose name the sources use.
 */
final class HostDex {

    static final Object LOCK = new Object();

    private static final String SEP = "\u0001";
    /** A match must score at least this, lead the runner-up by MARGIN and by DOMINANCE times. */
    private static final double ACCEPT = 0.25;
    private static final double MARGIN = 0.1;
    private static final double DOMINANCE = 1.5;
    private static final int MAX_CLASS_CANDIDATES = 3000;

    private static DexKitBridge bridge;
    private static boolean settled;
    /** Background work (recorder, self-test) holding the bridge open. */
    private static int busy;
    private static Db db;
    /** Nested finds (a class match resolving its superclass) share one bridge. */
    private static int depth;

    private HostDex() {
    }

    // ---- lookups (under LOCK) ------------------------------------------------------------------

    static String findClass(String original) {
        depth++;
        try {
            String[] fp = db().classes.get(original);
            return fp == null ? null : matchClass(fp, null);
        } catch (Throwable error) {
            Host.log.warn("Host: class match failed for " + original, error);
            return null;
        } finally {
            depth--;
            release();
        }
    }

    /** {@code allow}: a class matched although it has a fingerprint of its own (the self-test). */
    private static String matchClass(String[] fp, String allow) throws Exception {
        {
            if (fp[2].isEmpty()) return null;
            Class<?> superType = Host.cls(Host.loader, fp[2]);
            if (superType == null) return null;
            DexKitBridge b = bridge();
            ClassMatcher matcher = new ClassMatcher().superClass(superType.getName(), StringMatchType.Equals, false);
            int methods = Integer.parseInt(fp[6]);
            int fields = Integer.parseInt(fp[7]);
            if (superType == Object.class) matcher.methodCount(methods / 2, methods * 2 + 2).fieldCount(fields / 2, fields * 2 + 2);
            List<ClassData> found = b.findClass(FindClass.create().matcher(matcher));
            HashSet<String> members = stable(fp[8]);
            HashSet<String> interfaces = set(fp[3]);
            double best = -1, second = -1;
            String bestName = null;
            int n = Math.min(found.size(), MAX_CLASS_CANDIDATES);
            for (int i = 0; i < n; i++) {
                ClassData candidate = found.get(i);
                String name = candidate.getName();
                if (!name.equals(allow) && db.classes.containsKey(Host.original(name))) continue;
                HashSet<String> names = new HashSet<>();
                for (MethodData m : candidate.getMethods()) if (m.isMethod() && m.getName().length() > 2) names.add(m.getName());
                for (FieldData f : candidate.getFields()) if (f.getName().length() > 2) names.add(f.getName());
                HashSet<String> ifaces = new HashSet<>();
                for (ClassData c : candidate.getInterfaces()) ifaces.add(Host.original(c.getName()));
                String source = candidate.getSourceFile();
                double score = 0.8 * jaccard(members, names) + 0.1 * jaccard(interfaces, ifaces)
                        + (!fp[4].isEmpty() && fp[4].equals(source) ? 0.1 : 0);
                if (score > best) {
                    second = best;
                    best = score;
                    bestName = name;
                } else if (score > second) {
                    second = score;
                }
            }
            return accept(bestName, best, second, n) ? bestName : null;
        }
    }

    /** {@code owner#name} of the method matching {@code name}; {@code parameters} null matches any overload. */
    static String findMethod(Class<?> type, String name, Class<?>[] parameters, boolean declaredOnly) {
        depth++;
        try {
            for (Class<?> c = type; c != null; c = declaredOnly ? null : c.getSuperclass()) {
                String owner = Host.original(c.getName());
                List<String[]> records = db().members.get(owner + '#' + name);
                if (records == null) continue;
                for (String[] fp : records) {
                    if (fp[0].charAt(0) != 'M') continue;
                    String[] types = list(fp[3]);
                    if (parameters != null && !sameParameters(types, parameters)) continue;
                    String match = matchMethod(c, stableOf(owner), name, fp, types, parameters);
                    if (match != null) return c.getName() + '#' + match;
                }
            }
            return null;
        } catch (Throwable error) {
            Host.log.warn("Host: method match failed for " + type.getName() + '#' + name, error);
            return null;
        } finally {
            depth--;
            release();
        }
    }

    static String findField(Class<?> type, String name) {
        depth++;
        try {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                String owner = Host.original(c.getName());
                List<String[]> records = db().members.get(owner + '#' + name);
                if (records == null) continue;
                for (String[] fp : records) {
                    if (fp[0].charAt(0) != 'F') continue;
                    String match = matchField(c, stableOf(owner), name, fp);
                    if (match != null) return c.getName() + '#' + match;
                }
            }
            return null;
        } catch (Throwable error) {
            Host.log.warn("Host: field match failed for " + type.getName() + '#' + name, error);
            return null;
        } finally {
            depth--;
            release();
        }
    }

    private static String matchMethod(Class<?> c, HashSet<String> stable, String name, String[] fp, String[] types, Class<?>[] parameters) {
        ClassData data = bridge().getClassData(c.getName());
        if (data == null) return null;
        boolean isStatic = Modifier.isStatic(Integer.parseInt(fp[5]));
        HashSet<String> strings = set(fp[6]), invokes = set(fp[7]), fields = set(fp[8]), callers = set(fp[9]);
        double best = -1, second = -1;
        String bestName = null;
        int n = 0;
        for (MethodData m : data.getMethods()) {
            if (!m.isMethod()) continue;
            String candidate = m.getName();
            if (candidate.equals(name) && parameters == null) continue;
            if (candidate.length() > 2 && !candidate.equals(name) && stable.contains(candidate)) continue;
            if (Modifier.isStatic(m.getModifiers()) != isStatic) continue;
            List<String> actual = m.getParamTypeNames();
            if (actual.size() != types.length) continue;
            boolean ok = typeMatches(fp[4], m.getReturnTypeName());
            for (int i = 0; ok && i < types.length; i++) {
                ok = parameters != null ? parameters[i].getTypeName().equals(actual.get(i)) : typeMatches(types[i], actual.get(i));
            }
            if (!ok) continue;
            n++;
            double score = score(strings, set(m.getUsingStrings()), 0.3, invokes, invokes(m), 0.3,
                    fields, fields(m), 0.15, callers, names(m.getCallers()), 0.25);
            if (score > best) {
                second = best;
                best = score;
                bestName = candidate;
            } else if (score > second) {
                second = score;
            }
        }
        return accept(bestName, best, second, n) ? bestName : null;
    }

    private static String matchField(Class<?> c, HashSet<String> stable, String name, String[] fp) {
        ClassData data = bridge().getClassData(c.getName());
        if (data == null) return null;
        boolean isStatic = Modifier.isStatic(Integer.parseInt(fp[4]));
        HashSet<String> readers = set(fp[5]), writers = set(fp[6]);
        double best = -1, second = -1;
        String bestName = null;
        int n = 0;
        for (FieldData f : data.getFields()) {
            String candidate = f.getName();
            if (candidate.equals(name)) continue;
            if (candidate.length() > 2 && stable.contains(candidate)) continue;
            if (Modifier.isStatic(f.getModifiers()) != isStatic || !typeMatches(fp[3], f.getTypeName())) continue;
            n++;
            double score = score(readers, names(f.getReaders()), 0.5, writers, names(f.getWriters()), 0.5, null, null, 0, null, null, 0);
            if (score > best) {
                second = best;
                best = score;
                bestName = candidate;
            } else if (score > second) {
                second = score;
            }
        }
        return accept(bestName, best, second, n) ? bestName : null;
    }

    private static boolean accept(String name, double best, double second, int candidates) {
        if (name == null || best < ACCEPT) return false;
        return candidates == 1 || (best - second >= MARGIN && best >= DOMINANCE * second);
    }

    /** Weighted Jaccard over the features the fingerprint has; 1 when it has none. */
    private static double score(HashSet<String> a1, HashSet<String> b1, double w1,
                                HashSet<String> a2, HashSet<String> b2, double w2,
                                HashSet<String> a3, HashSet<String> b3, double w3,
                                HashSet<String> a4, HashSet<String> b4, double w4) {
        double total = 0, weight = 0;
        if (a1 != null && !a1.isEmpty()) { total += w1 * jaccard(a1, b1); weight += w1; }
        if (a2 != null && !a2.isEmpty()) { total += w2 * jaccard(a2, b2); weight += w2; }
        if (a3 != null && !a3.isEmpty()) { total += w3 * jaccard(a3, b3); weight += w3; }
        if (a4 != null && !a4.isEmpty()) { total += w4 * jaccard(a4, b4); weight += w4; }
        return weight == 0 ? 1 : total / weight;
    }

    private static double jaccard(HashSet<String> a, HashSet<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1;
        int both = 0;
        for (String s : a) if (b.contains(s)) both++;
        int union = a.size() + b.size() - both;
        return union == 0 ? 0 : (double) both / union;
    }

    private static boolean sameParameters(String[] recorded, Class<?>[] parameters) {
        if (recorded.length != parameters.length) return false;
        for (int i = 0; i < recorded.length; i++) {
            if (!typeMatches(recorded[i], parameters[i].getTypeName())) return false;
        }
        return true;
    }

    /** {@code current} is the running build's name for the type recorded as {@code recorded}. */
    private static boolean typeMatches(String recorded, String current) {
        if (recorded.equals(current) || recorded.equals(Host.original(current))) return true;
        return obfuscated(recorded) && obfuscated(current);
    }

    /** A shrinker name, which changes between builds and so matches any other. */
    private static boolean obfuscated(String type) {
        int end = type.length();
        while (end > 1 && type.charAt(end - 1) == ']') end -= 2;
        int start = Math.max(type.lastIndexOf('.', end - 1), type.lastIndexOf('$', end - 1)) + 1;
        return start > 0 && end - start <= 2;
    }

    // ---- fingerprint features --------------------------------------------------------------------

    private static HashSet<String> invokes(MethodData m) {
        HashSet<String> out = new HashSet<>();
        for (MethodData i : m.getInvokes()) out.add(Host.original(i.getClassName()) + '.' + i.getName());
        return out;
    }

    private static HashSet<String> fields(MethodData m) {
        HashSet<String> out = new HashSet<>();
        for (UsingFieldData u : m.getUsingFields()) {
            FieldData f = u.getField();
            out.add(Host.original(f.getClassName()) + '.' + f.getName());
        }
        return out;
    }

    private static HashSet<String> names(Collection<MethodData> methods) {
        HashSet<String> out = new HashSet<>();
        for (MethodData m : methods) out.add(Host.original(m.getClassName()) + '.' + m.getName());
        return out;
    }

    private static HashSet<String> stableOf(String owner) {
        String[] fp = db.classes.get(owner);
        return fp == null ? new HashSet<>() : stable(fp[8]);
    }

    private static HashSet<String> stable(String members) {
        HashSet<String> out = new HashSet<>();
        for (String s : list(members)) if (s.length() > 2) out.add(s);
        return out;
    }

    // ---- bridge --------------------------------------------------------------------------------------

    private static DexKitBridge bridge() {
        if (bridge == null) {
            long start = System.nanoTime();
            System.loadLibrary("dexkit");
            bridge = DexKitBridge.create(Host.apk);
            Host.log.info("Host: DexKit opened in " + (System.nanoTime() - start) / 1000 + " us");
        }
        return bridge;
    }

    /** After install, a late miss opens DexKit only for as long as it takes to answer it. */
    private static void release() {
        if (settled && busy == 0 && depth == 0 && bridge != null) {
            bridge.close();
            bridge = null;
            db = null;
        }
    }

    static void settle() {
        synchronized (LOCK) {
            settled = true;
            String recorded = Host.answer("r");
            boolean record = recorded == null && !Host.launcherKey.equals(header(new File(Host.dir, "host.db")));
            if (!record && recorded == null) Host.mark("r", "1");
            // A file named selftest here asks for the self-test on the next start.
            boolean test = new File(Host.dir, "selftest").delete();
            if (record || test) {
                busy++;
                Thread thread = new Thread(() -> {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    if (record) record();
                    if (test) selfTest();
                    synchronized (LOCK) {
                        busy--;
                        release();
                    }
                }, "PLE-HostDex");
                thread.setDaemon(true);
                thread.start();
            } else {
                release();
            }
        }
    }

    // ---- recorder ------------------------------------------------------------------------------------

    private static void record() {
        long start = System.nanoTime();
        try {
            ArrayList<String> classes = new ArrayList<>();
            HashSet<String> names = new HashSet<>();
            try (ZipFile zip = new ZipFile(Host.moduleApk)) {
                ZipEntry entry = zip.getEntry("ple/host.syms");
                try (BufferedReader in = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
                    for (String line; (line = in.readLine()) != null; ) {
                        if (line.startsWith("S\t")) classes.add(line.substring(2));
                        else if (line.startsWith("N\t")) names.add(line.substring(2));
                    }
                }
            }
            StringBuilder out = new StringBuilder(1 << 16).append('#').append(Host.launcherKey).append('\n');
            HashSet<String> done = new HashSet<>();
            HashSet<String> written = new HashSet<>();
            HashMap<String, String> renamed = renamedMembers();
            for (String name : classes) {
                // Only what already resolved: the recorder never matches, so a
                // class another process has cannot be matched into this one.
                String current = Host.currentIfKnown(name);
                Class<?> type = load(current != null ? current : name);
                for (Class<?> c = type; c != null && !platform(c.getName()); c = c.getSuperclass()) {
                    if (!done.add(c.getName())) break;
                    synchronized (LOCK) {
                        recordClass(c, names, renamed, out, written);
                    }
                }
            }
            Db previous;
            synchronized (LOCK) {
                previous = db();
            }
            for (Map.Entry<String, String[]> e : previous.classes.entrySet()) {
                if (!written.contains(e.getKey())) out.append(String.join("\t", e.getValue())).append('\n');
            }
            for (Map.Entry<String, ArrayList<String[]>> e : previous.members.entrySet()) {
                if (written.contains(e.getKey())) continue;
                for (String[] fp : e.getValue()) out.append(String.join("\t", fp)).append('\n');
            }
            File file = new File(Host.dir, "host.db");
            File temp = new File(Host.dir, "host.db.tmp");
            try (FileOutputStream stream = new FileOutputStream(temp)) {
                stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!temp.renameTo(file)) throw new IllegalStateException("rename failed");
            Host.mark("r", "1");
            synchronized (LOCK) {
                db = null;
            }
            Host.log.info("Host: fingerprints of " + done.size() + " classes recorded in " + (System.nanoTime() - start) / 1_000_000 + " ms");
        } catch (Throwable error) {
            Host.log.warn("Host: fingerprints not recorded", error);
        }
    }

    /**
     * Pretends every fingerprinted member and class of this build was renamed
     * and checks the matcher finds it again. Logs found / wrong / not found.
     */
    private static void selfTest() {
        selfTest(0);
        selfTest(3);
        selfTest(1);
    }

    /** One in {@code drift + 1} of each body feature dropped and one foreign one added, as a build's worth of drift. */
    private static String[] drift(String[] fp, int drift, int... lists) {
        if (drift == 0) return fp;
        String[] out = fp.clone();
        for (int i : lists) {
            StringBuilder kept = new StringBuilder();
            for (String item : fp[i].isEmpty() ? new String[0] : fp[i].split(SEP, -1)) {
                if ((item.hashCode() & drift) == 0) continue;
                if (kept.length() > 0) kept.append(SEP);
                kept.append(item);
            }
            if (kept.length() > 0) kept.append(SEP);
            out[i] = kept.append("drift").toString();
        }
        return out;
    }

    private static void selfTest(int drift) {
        long start = System.nanoTime();
        int ok = 0, wrong = 0, none = 0;
        StringBuilder report = new StringBuilder();
        ArrayList<String[]> members = new ArrayList<>();
        ArrayList<String[]> classes;
        try {
            synchronized (LOCK) {
                Db d = db();
                for (ArrayList<String[]> list : d.members.values()) members.addAll(list);
                classes = new ArrayList<>(d.classes.values());
            }
            for (String[] fp : members) {
                synchronized (LOCK) {
                    depth++;
                    try {
                        Class<?> c = Host.cls(Host.loader, fp[1]);
                        if (c == null) continue;
                        HashSet<String> stable = stableOf(fp[1]);
                        stable.remove(fp[2]);
                        String got = fp[0].equals("M")
                                ? matchMethod(c, stable, "\0", drift(fp, drift, 6, 7, 8, 9), list(fp[3]), null)
                                : matchField(c, stable, "\0", drift(fp, drift, 5, 6));
                        if (fp[2].equals(got)) ok++;
                        else if (got == null) none++;
                        else {
                            wrong++;
                            report.append("\n  wrong ").append(fp[1]).append('#').append(fp[2]).append(" -> ").append(got);
                        }
                    } finally {
                        depth--;
                    }
                }
            }
            int cOk = 0, cWrong = 0, cNone = 0;
            for (String[] fp : classes) {
                synchronized (LOCK) {
                    depth++;
                    try {
                        String current = Host.currentIfKnown(fp[1]);
                        String got = matchClass(drift(fp, drift, 8), current != null ? current : fp[1]);
                        if (fp[1].equals(got)) cOk++;
                        else if (got == null) cNone++;
                        else {
                            cWrong++;
                            report.append("\n  wrong class ").append(fp[1]).append(" -> ").append(got);
                        }
                    } finally {
                        depth--;
                    }
                }
            }
            Host.log.info("Host self-test, drift " + (drift == 0 ? "none" : "1/" + (drift + 1)) + ": members found " + ok + ", wrong " + wrong + ", not found " + none
                    + "; classes found " + cOk + ", wrong " + cWrong + ", not found " + cNone
                    + " in " + (System.nanoTime() - start) / 1_000_000 + " ms" + report);
        } catch (Throwable error) {
            Host.log.warn("Host: self-test failed", error);
        }
    }

    private static void recordClass(Class<?> c, HashSet<String> names, HashMap<String, String> renamed,
                                    StringBuilder out, HashSet<String> written) {
        ClassData data = bridge().getClassData(c.getName());
        if (data == null) return;
        String owner = Host.original(c.getName());
        List<MethodData> methods = data.getMethods();
        List<FieldData> fields = data.getFields();
        ArrayList<String> members = new ArrayList<>();
        ArrayList<String> interfaces = new ArrayList<>();
        for (MethodData m : methods) if (m.isMethod()) members.add(m.getName());
        for (FieldData f : fields) members.add(f.getName());
        for (ClassData i : data.getInterfaces()) interfaces.add(Host.original(i.getName()));
        ClassData superType = data.getSuperClass();
        String source = data.getSourceFile();
        line(out, "C", owner, superType == null ? "" : Host.original(superType.getName()), join(interfaces),
                source == null ? "" : esc(source), Integer.toString(data.getModifiers()),
                Integer.toString(methods.size()), Integer.toString(fields.size()), join(members));
        written.add(owner);
        for (MethodData m : methods) {
            if (!m.isMethod()) continue;
            String name = original(renamed, c, m.getName());
            if (!names.contains(name)) continue;
            ArrayList<String> types = new ArrayList<>();
            for (String t : m.getParamTypeNames()) types.add(Host.original(t));
            line(out, "M", owner, name, join(types), Host.original(m.getReturnTypeName()), Integer.toString(m.getModifiers()),
                    join(m.getUsingStrings()), join(invokes(m)), join(fields(m)), join(names(m.getCallers())));
            written.add(owner + '#' + name);
        }
        for (FieldData f : fields) {
            String name = original(renamed, c, f.getName());
            if (!names.contains(name)) continue;
            line(out, "F", owner, name, Host.original(f.getTypeName()), Integer.toString(f.getModifiers()),
                    join(names(f.getReaders())), join(names(f.getWriters())));
            written.add(owner + '#' + name);
        }
    }

    /** {@code currentOwner#currentName} to the source name, for members matched by DexKit. */
    private static HashMap<String, String> renamedMembers() {
        HashMap<String, String> out = new HashMap<>();
        synchronized (Host.map) {
            for (Map.Entry<String, String> e : Host.map.entrySet()) {
                String key = e.getKey(), value = e.getValue();
                char kind = key.charAt(0);
                if (value.isEmpty() || (kind != 'm' && kind != 'f' && kind != 'n')) continue;
                int hash = key.indexOf('#');
                int end = key.indexOf('(', hash);
                out.put(value, key.substring(hash + 1, end < 0 ? key.length() : end));
            }
        }
        return out;
    }

    private static String original(HashMap<String, String> renamed, Class<?> owner, String name) {
        String original = renamed.get(owner.getName() + '#' + name);
        return original != null ? original : name;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, Host.loader);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean platform(String name) {
        return name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.") || name.startsWith("dalvik.");
    }

    // ---- fingerprint file ------------------------------------------------------------------------

    private static final class Db {
        final HashMap<String, String[]> classes = new HashMap<>();
        final HashMap<String, ArrayList<String[]>> members = new HashMap<>();
    }

    private static Db db() throws Exception {
        if (db != null) return db;
        Db loaded = new Db();
        File file = new File(Host.dir, "host.db");
        if (file.isFile()) {
            try (Reader in = new FileReader(file, StandardCharsets.UTF_8)) {
                parse(in, loaded);
            }
        } else {
            try (ZipFile zip = new ZipFile(Host.moduleApk)) {
                ZipEntry entry = zip.getEntry("ple/" + Host.packageName + ".db");
                if (entry != null) {
                    try (Reader in = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                        parse(in, loaded);
                    }
                }
            }
        }
        return db = loaded;
    }

    private static void parse(Reader reader, Db into) throws Exception {
        BufferedReader in = new BufferedReader(reader, 1 << 16);
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] fp = line.split("\t", -1);
            switch (fp[0]) {
                case "C":
                    if (fp.length == 9) into.classes.put(fp[1], fp);
                    break;
                case "M":
                    if (fp.length == 10) into.members.computeIfAbsent(fp[1] + '#' + fp[2], k -> new ArrayList<>(1)).add(fp);
                    break;
                case "F":
                    if (fp.length == 7) into.members.computeIfAbsent(fp[1] + '#' + fp[2], k -> new ArrayList<>(1)).add(fp);
                    break;
                default:
                    break;
            }
        }
    }

    private static String header(File file) {
        if (!file.isFile()) return null;
        try (BufferedReader in = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line = in.readLine();
            return line != null && line.startsWith("#") ? line.substring(1) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void line(StringBuilder out, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) out.append('\t');
            out.append(fields[i]);
        }
        out.append('\n');
    }

    private static String join(Collection<String> items) {
        StringBuilder out = new StringBuilder();
        for (String s : items) {
            if (out.length() > 0) out.append(SEP);
            out.append(esc(s));
        }
        return out.toString();
    }

    private static String[] list(String joined) {
        if (joined.isEmpty()) return new String[0];
        String[] items = joined.split(SEP, -1);
        for (int i = 0; i < items.length; i++) items[i] = unesc(items[i]);
        return items;
    }

    private static HashSet<String> set(String joined) {
        HashSet<String> out = new HashSet<>();
        for (String s : list(joined)) out.add(s);
        return out;
    }

    private static HashSet<String> set(Collection<String> items) {
        return new HashSet<>(items);
    }

    static String esc(String s) {
        StringBuilder out = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String r = c == '\\' ? "\\\\" : c == '\t' ? "\\t" : c == '\n' ? "\\n" : c == '\r' ? "\\r" : c == '\u0001' ? "\\1" : null;
            if (r != null && out == null) out = new StringBuilder(s.length() + 8).append(s, 0, i);
            if (out != null) {
                if (r != null) out.append(r);
                else out.append(c);
            }
        }
        return out == null ? s : out.toString();
    }

    static String unesc(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 == s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            out.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n == '1' ? '\u0001' : n);
        }
        return out.toString();
    }
}
