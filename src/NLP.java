import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NLP.java - Analytics core for the News & Social Media Analytics Engine.
 *
 * Pipeline:  COLLECT (parseItems) -> PROCESS (tokenize) -> ANALYZE (sentiment, keywords,
 *            topics, trends) -> PRESENT (Map/List structure serialised to JSON).
 *
 * Data structures used (Data Structures and Algorithms-3):
 *   HashMap / HashSet   - word counts, sentiment lexicon, topic tables, O(1) lookups
 *   PriorityQueue       - min-heap of size K for Top-K keywords and emerging terms
 *   Two time windows    - "earlier" vs "recent" comparison for trend detection
 */
public class NLP {

    // ------------------------------------------------------------------ lexicons
    static final Set<String> STOP = setOf(
        "a about above after again against all also am an and any are as at be because been before being below between both "
      + "but by can could did do does doing down during each few for from further had has have having he her here hers him his "
      + "how i if in into is it its just me more most my no nor not of off on once only or other our out over own same she should "
      + "so some such than that the their them then there these they this those through to too under until up us very was we "
      + "were what when where which while who whom why will with would you your yours ca say says said new one two get got "
      + "today now still even many much like really week next");
    static final Set<String> KEEP_SHORT = setOf("ai");

    static final Map<String, Double> LEXICON = new HashMap<>();
    static {
        load(LEXICON,
            "good:1.5 great:2.5 excellent:3 amazing:3 awesome:3 love:3 happy:2 best:3 better:1.5 win:2 won:2 success:2.5 "
          + "successful:2.5 strong:1.5 growth:1.5 improve:2 boost:2 benefit:2 helpful:2 safe:1.5 hope:1.5 support:1.5 welcome:2 "
          + "exciting:2.5 impressive:2.5 brilliant:3 innovative:2 positive:2 progress:2 secure:1.5 affordable:1.5 reliable:2 "
          + "fantastic:3 wonderful:3 proud:2.5 thrilled:3 celebrate:2.5 breakthrough:3 recover:2 optimistic:2.5 gain:1.5 "
          + "recommend:2 enjoy:2.5 smooth:1.5 praise:2.5 triumph:3 efficient:2 promising:2.5 cheer:2.5 rally:2 faster:1.5 "
          + "fast:1 healthy:2 protect:1.5 delight:2.5 beautiful:2.5 perfect:3 improvement:2");
        load(LEXICON,
            "bad:-2 terrible:-3 awful:-3 worst:-3.5 hate:-3 angry:-2.5 sad:-2 fail:-2.5 failure:-2.5 crisis:-3 scandal:-3 "
          + "fear:-2.5 worry:-2 worried:-2 concern:-1.5 risk:-1.5 threat:-2.5 loss:-2 lose:-2 decline:-1.5 crash:-3 drop:-1.5 "
          + "layoff:-2.5 expensive:-1.5 delay:-1.5 shortage:-2 poor:-2 weak:-1.5 slow:-1 broken:-2.5 problem:-1.5 disaster:-3.5 "
          + "attack:-3 violence:-3 flood:-2 outage:-2.5 misinformation:-2.5 fraud:-3 ban:-1.5 protest:-1 disappointing:-2.5 "
          + "disappoint:-2.5 unsafe:-2.5 harm:-2.5 danger:-2.5 dangerous:-2.5 toxic:-2.5 corrupt:-3 unfair:-2.5 ridiculous:-2 "
          + "mess:-2 chaos:-3 panic:-2.5 pollution:-2 injury:-2 defeat:-2 struggle:-1.5 criticize:-2 controversy:-2 lawsuit:-1.5 "
          + "frustrated:-2 frustrating:-2 breach:-2.5 hurt:-2 ruin:-3 serious:-1 damage:-2.5 drought:-2 heatwave:-1 breaches:-2.5 "
          + "hacker:-2 worse:-2 cancel:-1.5 cancelled:-1.5");
    }
    static final Set<String> NEGATORS = setOf("not no never hardly neither without cannot");
    static final Map<String, Double> BOOSTERS = new HashMap<>();
    static final Map<String, Double> DAMPENERS = new HashMap<>();
    static {
        load(BOOSTERS, "very:1.4 extremely:1.6 really:1.3 so:1.2 highly:1.4 absolutely:1.5 totally:1.4 incredibly:1.5");
        load(DAMPENERS, "slightly:0.6 somewhat:0.7 barely:0.5 little:0.7");
    }

    static final Map<String, Set<String>> TOPICS = new LinkedHashMap<>();   // insertion order = tie-break order
    static {
        TOPICS.put("Technology", setOf("ai artificial intelligence software app apps chip chips smartphone phone tech technology cyber hack "
            + "hacker data cloud robot algorithm internet startup device laptop deepfake gadget code digital online platform developer update innovation"));
        TOPICS.put("Business & Economy", setOf("market stock economy economic inflation price sales company business profit revenue investor "
            + "bank trade jobs layoff customer brand retail growth tax earnings consumer demand supply oil quarterly"));
        TOPICS.put("Politics", setOf("government election vote voter minister president parliament policy law lawmaker senate bill "
            + "regulation campaign party political court congress opposition debate commission"));
        TOPICS.put("Health", setOf("health hospital doctor vaccine disease patient medical medicine virus cancer fitness mental diet clinic drug treatment flu wellness"));
        TOPICS.put("Sports", setOf("match game team player coach league tournament cup goal score champion season cricket football tennis olympic stadium final fans"));
        TOPICS.put("Entertainment", setOf("movie film music song album actor actress celebrity series show concert trailer box festival director streaming award soundtrack"));
        TOPICS.put("Environment", setOf("climate weather rain flood pollution emission carbon energy solar forest wildlife heatwave storm green renewable earthquake drought plastic"));
        TOPICS.put("Education", setOf("school student university college exam teacher education course campus scholarship learning degree academic curriculum classroom admission semester"));
    }

    private static Set<String> setOf(String words) {
        return new HashSet<>(Arrays.asList(words.trim().split("\\s+")));
    }

    private static void load(Map<String, Double> target, String pairs) {
        for (String p : pairs.trim().split("\\s+")) {
            int i = p.lastIndexOf(':');
            target.put(p.substring(0, i), Double.parseDouble(p.substring(i + 1)));
        }
    }

    // ------------------------------------------------------------- data classes
    public static class Item {
        final LocalDate date; final String source; final String text;
        Item(LocalDate d, String s, String t) { date = d; source = s; text = t; }
    }

    static class Row {
        LocalDate date; String source, text, label, topic; double score;
    }

    // --------------------------------------------------------- COLLECT: parsing
    private static final Pattern RE_FULL = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})\\s*\\|\\s*([^|]+?)\\s*\\|\\s*(.+)$");
    private static final Pattern RE_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})\\s*\\|\\s*(.+)$");

    private static LocalDate validDate(String s) {
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    /** One non-empty line = one post. Formats: "text" | "YYYY-MM-DD | text" | "YYYY-MM-DD | Source | text" */
    public static List<Item> parseItems(String raw, int limit) {
        List<Item> items = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            line = line.trim();
            if (line.length() < 3) continue;
            LocalDate d = null; String src = "User input", text = line;
            Matcher m = RE_FULL.matcher(line);
            if (m.matches() && validDate(m.group(1)) != null) {
                d = validDate(m.group(1)); src = m.group(2); text = m.group(3);
            } else {
                m = RE_DATE.matcher(line);
                if (m.matches() && validDate(m.group(1)) != null) { d = validDate(m.group(1)); text = m.group(2); }
            }
            items.add(new Item(d, src, text));
            if (items.size() >= limit) break;
        }
        return items;
    }

    // ------------------------------------------------------- PROCESS: tokenizing
    private static final Pattern WORD = Pattern.compile("[a-z]+");

    static List<String> tokenize(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        t = t.replaceAll("https?://\\S+", " ").replaceAll("n't\\b", " not");
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(t);
        while (m.find()) out.add(m.group());
        return out;
    }

    /** Candidate base forms of a word (cheap stemmer for lexicon look-ups). */
    static List<String> forms(String w) {
        List<String> out = new ArrayList<>();
        out.add(w);
        int n = w.length();
        if (w.endsWith("ies") && n > 4) out.add(w.substring(0, n - 3) + "y");
        if (w.endsWith("es") && n > 3) out.add(w.substring(0, n - 2));
        if (w.endsWith("s") && n > 3) out.add(w.substring(0, n - 1));
        if (w.endsWith("ed") && n > 4) {
            String b = w.substring(0, n - 2);
            out.add(b); out.add(b + "e");
            if (b.length() > 2 && b.charAt(b.length() - 1) == b.charAt(b.length() - 2)) out.add(b.substring(0, b.length() - 1));
        }
        if (w.endsWith("ing") && n > 5) {
            String b = w.substring(0, n - 3);
            out.add(b); out.add(b + "e");
            if (b.length() > 2 && b.charAt(b.length() - 1) == b.charAt(b.length() - 2)) out.add(b.substring(0, b.length() - 1));
        }
        return out;
    }

    static Double lookup(String word, Map<String, Double> table) {
        for (String f : forms(word)) {
            Double v = table.get(f);
            if (v != null) return v;
        }
        return null;
    }

    static String canonical(String w) {
        int n = w.length();
        if (w.endsWith("ies") && n > 4) return w.substring(0, n - 3) + "y";
        if (w.endsWith("s") && !w.endsWith("ss") && n > 3) return w.substring(0, n - 1);
        return w;
    }

    // ---------------------------------------------------- ANALYZE 1: sentiment

    /** Returns compound score in (-1, 1); the label is derived by {@link #labelOf(double)}. */
    static double sentiment(String text, List<String> tokens) {
        double total = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Double v = lookup(tokens.get(i), LEXICON);
            if (v == null || v == 0) continue;
            double val = v;
            if (i > 0 && BOOSTERS.containsKey(tokens.get(i - 1))) val *= BOOSTERS.get(tokens.get(i - 1));
            else if (i > 0 && DAMPENERS.containsKey(tokens.get(i - 1))) val *= DAMPENERS.get(tokens.get(i - 1));
            boolean negated = false;
            for (String w : tokens.subList(Math.max(0, i - 3), i)) if (NEGATORS.contains(w)) negated = true;
            if (negated) val *= -0.75;
            total += val;
        }
        int excl = 0;
        for (char c : text.toCharArray()) if (c == '!') excl++;
        excl = Math.min(excl, 3);
        if (total != 0) total += Math.copySign(0.3 * excl, total);
        return total / Math.sqrt(total * total + 15);          // squash to (-1, 1)
    }

    static String labelOf(double c) {
        return c >= 0.15 ? "positive" : c <= -0.15 ? "negative" : "neutral";
    }

    // ---------------------------------------------- ANALYZE 2: topic classification
    static String classifyTopic(List<String> tokens) {
        Map<String, Integer> scores = new HashMap<>();
        for (String tok : tokens) {
            List<String> fs = forms(tok);
            for (Map.Entry<String, Set<String>> e : TOPICS.entrySet()) {
                for (String f : fs) {
                    if (e.getValue().contains(f)) { scores.merge(e.getKey(), 1, Integer::sum); break; }
                }
            }
        }
        if (scores.isEmpty()) return "General";
        int best = Collections.max(scores.values());
        for (String topic : TOPICS.keySet()) {
            Integer s = scores.get(topic);
            if (s != null && s == best) return topic;
        }
        return "General";
    }

    // ------------------------------------------------ ANALYZE 3: keyword terms
    static Set<String> docTerms(List<String> tokens) {
        int n = tokens.size();
        boolean[] keep = new boolean[n];
        for (int i = 0; i < n; i++) {
            String t = tokens.get(i);
            keep[i] = !STOP.contains(t) && (t.length() > 2 || KEEP_SHORT.contains(t));
        }
        Set<String> terms = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (!keep[i]) continue;
            String c = canonical(tokens.get(i));
            terms.add(c);
            if (i + 1 < n && keep[i + 1]) terms.add(c + " " + canonical(tokens.get(i + 1)));
        }
        return terms;
    }

    /** Heap entry for keywords: ordered by (score, document frequency, term). */
    static class Cand implements Comparable<Cand> {
        final double score; final int df; final String term;
        Cand(double s, int d, String t) { score = s; df = d; term = t; }
        public int compareTo(Cand o) {
            int c = Double.compare(score, o.score);
            if (c != 0) return c;
            c = Integer.compare(df, o.df);
            return c != 0 ? c : term.compareTo(o.term);
        }
    }

    /** Heap entry for emerging terms: ordered by (score, recent count, term). */
    static class Em implements Comparable<Em> {
        final double score; final int recent, earlier; final String term; final double growth;
        Em(double s, int r, String t, int e, double g) { score = s; recent = r; term = t; earlier = e; growth = g; }
        public int compareTo(Em o) {
            int c = Double.compare(score, o.score);
            if (c != 0) return c;
            c = Integer.compare(recent, o.recent);
            return c != 0 ? c : term.compareTo(o.term);
        }
    }

    /** Top-K selection using a min-heap of size K: O(n log K). Returns best-first. */
    static <T extends Comparable<T>> List<T> topK(Iterable<T> source, int k) {
        PriorityQueue<T> heap = new PriorityQueue<>();
        for (T x : source) {
            heap.add(x);
            if (heap.size() > k) heap.poll();          // drop the smallest
        }
        List<T> out = new ArrayList<>(heap);
        out.sort(Collections.reverseOrder());
        return out;
    }

    private static double r3(double x) { return Math.round(x * 1000.0) / 1000.0; }
    private static double r1(double x) { return Math.round(x * 10.0) / 10.0; }

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ----------------------------------------------------------- main analysis
    public static Map<String, Object> analyze(List<Item> items) {
        final int topKw = 24;
        int n = items.size();
        List<Row> rows = new ArrayList<>();
        Map<String, List<Integer>> termDocs = new HashMap<>();

        for (int idx = 0; idx < n; idx++) {
            Item it = items.get(idx);
            List<String> toks = tokenize(it.text);
            double score = sentiment(it.text, toks);
            Row r = new Row();
            r.date = it.date; r.source = it.source; r.text = it.text;
            r.score = r3(score); r.label = labelOf(score); r.topic = classifyTopic(toks);
            rows.add(r);
            for (String term : docTerms(toks)) termDocs.computeIfAbsent(term, k -> new ArrayList<>()).add(idx);
        }

        // ---- overall sentiment and sources
        Map<String, Integer> lab = new HashMap<>();
        double sum = 0;
        Map<String, Integer> srcCount = new LinkedHashMap<>();
        for (Row r : rows) {
            lab.merge(r.label, 1, Integer::sum);
            sum += r.score;
            srcCount.merge(r.source, 1, Integer::sum);
        }
        double avg = n == 0 ? 0 : r3(sum / n);
        List<Object> sources = new ArrayList<>();
        srcCount.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> sources.add(obj("name", e.getKey(), "count", e.getValue())));

        // ---- topics: [count, positive, negative, neutral]
        Map<String, int[]> tstat = new LinkedHashMap<>();
        for (Row r : rows) {
            int[] a = tstat.computeIfAbsent(r.topic, k -> new int[4]);
            a[0]++;
            a["positive".equals(r.label) ? 1 : "negative".equals(r.label) ? 2 : 3]++;
        }
        List<Map<String, Object>> topics = new ArrayList<>();
        for (Map.Entry<String, int[]> e : tstat.entrySet()) {
            int[] a = e.getValue();
            topics.add(obj("name", e.getKey(), "count", a[0], "positive", a[1], "negative", a[2], "neutral", a[3]));
        }
        topics.sort((a, b) -> (Integer) b.get("count") - (Integer) a.get("count"));

        // ---- keywords (heap-based Top-K)
        List<Cand> cands = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : termDocs.entrySet()) {
            int df = e.getValue().size();
            boolean bi = e.getKey().indexOf(' ') >= 0;
            if (df < 2 && n > 10) continue;
            cands.add(new Cand(df * (bi ? 1.5 : 1.0), df, e.getKey()));
        }
        List<Cand> top = topK(cands, topKw * 3);
        Map<String, Integer> bigrams = new HashMap<>();
        for (Cand c : top) if (c.term.indexOf(' ') >= 0) bigrams.put(c.term, c.df);
        List<Object> keywords = new ArrayList<>();
        for (Cand c : top) {
            if (c.term.indexOf(' ') < 0 && absorbed(c.term, c.df, bigrams)) continue;   // covered by a stronger phrase
            double s = 0;
            for (int i : termDocs.get(c.term)) s += rows.get(i).score;
            keywords.add(obj("term", c.term, "count", c.df, "sentiment", r3(s / c.df)));
            if (keywords.size() >= topKw) break;
        }

        // ---- timeline and trend detection (two-window comparison)
        List<Row> dated = new ArrayList<>();
        for (Row r : rows) if (r.date != null) dated.add(r);
        Map<String, Object> timeline = obj("dates", new ArrayList<>(), "series", new LinkedHashMap<>(), "sentiment", new ArrayList<>());
        List<Object> trending = new ArrayList<>(), emerging = new ArrayList<>();

        if (!dated.isEmpty()) {
            LocalDate dmin = dated.get(0).date, dmax = dated.get(0).date;
            for (Row r : dated) {
                if (r.date.isBefore(dmin)) dmin = r.date;
                if (r.date.isAfter(dmax)) dmax = r.date;
            }
            int span = (int) ChronoUnit.DAYS.between(dmin, dmax);
            List<LocalDate> days = new ArrayList<>();
            for (int d = 0; d <= span; d++) days.add(dmin.plusDays(d));

            List<Object> dateStrs = new ArrayList<>();
            for (LocalDate d : days) dateStrs.add(d.toString());

            Map<LocalDate, Map<String, Integer>> perDay = new HashMap<>();
            Map<LocalDate, List<Double>> sentDay = new HashMap<>();
            for (Row r : dated) {
                perDay.computeIfAbsent(r.date, k -> new HashMap<>()).merge(r.topic, 1, Integer::sum);
                sentDay.computeIfAbsent(r.date, k -> new ArrayList<>()).add(r.score);
            }
            Map<String, Object> series = new LinkedHashMap<>();
            for (int t = 0; t < Math.min(5, topics.size()); t++) {
                String name = (String) topics.get(t).get("name");
                List<Object> vals = new ArrayList<>();
                for (LocalDate d : days) vals.add(perDay.getOrDefault(d, Collections.emptyMap()).getOrDefault(name, 0));
                series.put(name, vals);
            }
            List<Object> sentSeries = new ArrayList<>();
            for (LocalDate d : days) {
                List<Double> l = sentDay.get(d);
                if (l == null) sentSeries.add(null);
                else sentSeries.add(r3(l.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
            }
            timeline = obj("dates", dateStrs, "series", series, "sentiment", sentSeries);

            if (days.size() >= 2) {
                double mid = span / 2.0;
                boolean[] recent = new boolean[n], early = new boolean[n];
                int nr = 0, ne = 0;
                for (int i = 0; i < n; i++) {
                    Row r = rows.get(i);
                    if (r.date == null) continue;
                    long off = ChronoUnit.DAYS.between(dmin, r.date);
                    if (off > mid) { recent[i] = true; nr++; } else { early[i] = true; ne++; }
                }
                nr = Math.max(nr, 1); ne = Math.max(ne, 1);

                Map<String, Integer> tr = new LinkedHashMap<>(), te = new HashMap<>();
                for (int i = 0; i < n; i++) {
                    if (recent[i]) tr.merge(rows.get(i).topic, 1, Integer::sum);
                    else if (early[i]) te.merge(rows.get(i).topic, 1, Integer::sum);
                }
                final int fnr = nr, fne = ne;
                tr.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> {
                    double shareR = 100.0 * e.getValue() / fnr;
                    double shareE = 100.0 * te.getOrDefault(e.getKey(), 0) / fne;
                    trending.add(obj("topic", e.getKey(), "recent", e.getValue(), "earlier", te.getOrDefault(e.getKey(), 0),
                            "share", r1(shareR), "change", r1(shareR - shareE)));
                });

                List<Em> em = new ArrayList<>();
                for (Map.Entry<String, List<Integer>> e : termDocs.entrySet()) {
                    int rec = 0, ear = 0;
                    for (int i : e.getValue()) { if (recent[i]) rec++; if (early[i]) ear++; }
                    if (rec >= 4 && e.getValue().size() >= 4) {
                        double growth = ((rec + 1.0) / nr) / ((ear + 1.0) / ne);
                        if (growth >= 1.6) em.add(new Em(growth * Math.sqrt(rec), rec, e.getKey(), ear, growth));   // growth, weighted by volume
                    }
                }
                List<Em> best = topK(em, 24);
                Map<String, Integer> phrases = new HashMap<>();
                for (Em x : best) if (x.term.indexOf(' ') >= 0) phrases.put(x.term, x.recent);
                for (Em x : best) {
                    if (x.term.indexOf(' ') < 0 && absorbed(x.term, x.recent, phrases)) continue;
                    emerging.add(obj("term", x.term, "earlier", x.earlier, "recent", x.recent, "growth", r1(x.growth)));
                    if (emerging.size() >= 8) break;
                }
            }
        }

        List<Object> outItems = new ArrayList<>();
        for (int i = 0; i < Math.min(400, rows.size()); i++) {
            Row r = rows.get(i);
            outItems.add(obj("date", r.date == null ? null : r.date.toString(), "source", r.source, "text", r.text,
                    "label", r.label, "score", r.score, "topic", r.topic));
        }

        return obj(
            "total", n,
            "has_dates", !dated.isEmpty(),
            "sentiment", obj("positive", lab.getOrDefault("positive", 0), "negative", lab.getOrDefault("negative", 0),
                             "neutral", lab.getOrDefault("neutral", 0), "average", avg),
            "sources", sources,
            "topics", topics,
            "keywords", keywords,
            "timeline", timeline,
            "trending", trending,
            "emerging", emerging,
            "items", outItems);
    }

    /** True if a single word is already covered by a phrase containing it with a similar count. */
    private static boolean absorbed(String word, int count, Map<String, Integer> phrases) {
        for (Map.Entry<String, Integer> p : phrases.entrySet()) {
            if (Arrays.asList(p.getKey().split(" ")).contains(word) && p.getValue() >= 0.7 * count) return true;
        }
        return false;
    }
}
