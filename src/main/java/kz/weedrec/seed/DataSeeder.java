package kz.weedrec.seed;

import kz.weedrec.model.*;
import kz.weedrec.repository.*;
import kz.weedrec.service.InteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Seeds an empty database with synthetic demo data. Deterministic (seed 42).
 * Detections imitate the output of the CV model.
 * <p>
 * Farmer behaviour follows a hidden model that is never stored or exposed by the API:
 * <ul>
 *   <li>every treatment has a true quality, which drives the objective {@code effectiveness};</li>
 *   <li>every farmer has a farm style (chemical-intensive, organic, budget, balanced) plus individual noise:
 *       an affinity for each treatment category and a price sensitivity.</li>
 * </ul>
 * Utility of a treatment for a farmer = quality + category affinity - price penalty. Utility drives views,
 * saves, the choice of treatment and the rating, so different farmers rate the same treatments differently
 * and the data carries a personal signal, not just global popularity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final FieldRepository fields;
    private final WeedRepository weeds;
    private final TreatmentRepository treatments;
    private final DetectionRepository detections;
    private final InteractionRepository interactions;
    private final InteractionService interactionService;
    private final PasswordEncoder encoder;

    @Value("${app.seed.enabled:true}")
    private boolean enabled;

    @Value("${app.seed.farmers:35}")
    private int farmerCount;

    @Value("${app.seed.agronomists:4}")
    private int agronomistCount;

    private final Random rnd = new Random(42);

    enum FarmStyle {
        CHEMICAL_INTENSIVE(0.8, -0.4, -0.4, -0.3, 0.2),
        ORGANIC(-1.0, 0.5, 0.8, 0.5, 0.4),
        BUDGET(0.0, 0.2, 0.0, 0.3, 1.0),
        BALANCED(0.0, 0.0, 0.0, 0.0, 0.5);

        final double chemical, mechanical, biological, agrotechnical, priceSensitivity;

        FarmStyle(double chemical, double mechanical, double biological, double agrotechnical, double priceSensitivity) {
            this.chemical = chemical;
            this.mechanical = mechanical;
            this.biological = biological;
            this.agrotechnical = agrotechnical;
            this.priceSensitivity = priceSensitivity;
        }

        double affinity(Treatment.Category c) {
            return switch (c) {
                case CHEMICAL -> chemical;
                case MECHANICAL -> mechanical;
                case BIOLOGICAL -> biological;
                case AGROTECHNICAL -> agrotechnical;
            };
        }
    }

    record Preference(FarmStyle style, Map<Treatment.Category, Double> affinity, double priceSensitivity) {}

    static final String[] CROPS = {"wheat", "barley", "sunflower", "rapeseed", "corn", "flax"};
    static final String[] REGIONS = {"Akmola Region", "Kostanay Region", "North Kazakhstan Region",
            "Pavlodar Region", "Karaganda Region", "East Kazakhstan Region"};
    static final double[][] REGION_COORDS = {{71.4, 51.2}, {63.6, 53.2}, {69.1, 54.9}, {76.9, 52.3}, {73.1, 49.8}, {82.6, 49.9}};
    static final String[] FIRST_M = {"Askhat", "Yerlan", "Dauren", "Sergey", "Nurlan", "Baurzhan", "Vladimir",
            "Marat", "Timur", "Arman", "Dmitry"};
    static final String[] FIRST_F = {"Aigerim", "Aliya", "Zhanna", "Olga", "Gulnara"};
    static final String[] LAST_M = {"Nurlanov", "Zhumabekov", "Ivanov", "Kassymov", "Abenov", "Tulegenov",
            "Bekmukhanov", "Akhmetov", "Smagulov", "Omarov"};
    static final String[] LAST_NEUTRAL = {"Seitkali", "Kim", "Petrenko", "Shevchenko"};
    static final String[] FARMS = {"Bereke", "Niva", "Zher-Ana", "Kolos", "Altyn Dan", "Stepnoe", "Saryarka", "Dostyk",
            "Agro-Yesil", "Tselinnoe", "Baiterek", "Zarechnoe"};
    static final String[] SOILS = {"chernozem", "dark chestnut", "chestnut", "meadow chernozem"};

    @Override
    public void run(String... args) {
        if (!enabled || users.count() > 0) {
            log.info("Seeding skipped (enabled={}, users in DB: {})", enabled, users.count());
            return;
        }
        log.info("Seeding demo data...");
        List<Weed> weedList = weeds.saveAll(weeds());

        List<Treatment> catalog = treatments.saveAll(treatments());
        Map<String, Double> quality = new HashMap<>();
        catalog.forEach(t -> quality.put(t.getId(), 2.6 + rnd.nextDouble() * 2.2));

        List<User> userList = users.saveAll(users());
        Map<String, Preference> prefs = preferences(userList);
        List<Field> fieldList = fields.saveAll(fields(userList));
        List<Detection> detList = detections.saveAll(detections(fieldList, weedList));
        List<Interaction> intList = interactions.saveAll(interactions(detList, fieldList, catalog, quality, prefs));

        Map<String, long[]> counters = new HashMap<>();
        for (Interaction i : intList) {
            long[] c = counters.computeIfAbsent(i.getTreatmentId(), k -> new long[2]);
            if (i.getType() == Interaction.Type.VIEW) c[0]++;
            if (i.getType() == Interaction.Type.APPLY) c[1]++;
        }
        for (Treatment t : catalog) {
            long[] c = counters.getOrDefault(t.getId(), new long[2]);
            t.getStats().setViews(c[0]);
            t.getStats().setApplications(c[1]);
        }
        treatments.saveAll(catalog);
        catalog.forEach(t -> interactionService.recomputeRating(t.getId()));

        log.info("Done: weeds {}, treatments {}, users {}, fields {}, detections {}, interactions {}",
                weedList.size(), catalog.size(), userList.size(), fieldList.size(), detList.size(), intList.size());
        Map<FarmStyle, Long> styles = new EnumMap<>(FarmStyle.class);
        prefs.values().forEach(p -> styles.merge(p.style(), 1L, Long::sum));
        log.info("Farm styles (hidden): {}", styles);
        log.info("Demo accounts (password password123): admin@weedrec.kz, agronomist@weedrec.kz, farmer@weedrec.kz");
    }

    // ------------------------------------------------------------------ weeds

    private List<Weed> weeds() {
        return List.of(
                w("avena_fatua", "Wild oat", "Avena fatua", "monocot", "annual"),
                w("setaria_viridis", "Green foxtail", "Setaria viridis", "monocot", "annual"),
                w("echinochloa_crus_galli", "Barnyard grass", "Echinochloa crus-galli", "monocot", "annual"),
                w("elymus_repens", "Couch grass", "Elymus repens", "monocot", "perennial"),
                w("cirsium_arvense", "Creeping thistle", "Cirsium arvense", "dicot", "perennial"),
                w("sonchus_arvensis", "Perennial sow-thistle", "Sonchus arvensis", "dicot", "perennial"),
                w("convolvulus_arvensis", "Field bindweed", "Convolvulus arvensis", "dicot", "perennial"),
                w("chenopodium_album", "Common lambsquarters", "Chenopodium album", "dicot", "annual"),
                w("amaranthus_retroflexus", "Redroot pigweed", "Amaranthus retroflexus", "dicot", "annual"),
                w("sinapis_arvensis", "Wild mustard", "Sinapis arvensis", "dicot", "annual"),
                w("fallopia_convolvulus", "Wild buckwheat", "Fallopia convolvulus", "dicot", "annual"),
                w("salsola_tragus", "Russian thistle", "Salsola tragus", "dicot", "annual"));
    }

    private Weed w(String id, String name, String latin, String type, String life) {
        return Weed.builder().id(id).name(name).latinName(latin).type(type).lifecycle(life).build();
    }

    // ------------------------------------------------------------------ catalog

    private record Ingredient(String name, String trade, String purpose, List<String> weeds, List<String> crops,
                              double dose, String unit, double basePrice) {}

    private List<Treatment> treatments() {
        List<String> grass = List.of("avena_fatua", "setaria_viridis", "echinochloa_crus_galli");
        List<String> cereals = List.of("wheat", "barley");
        List<Ingredient> ingredients = List.of(
                new Ingredient("fenoxaprop-P-ethyl", "Fenox", "against annual grass weeds in cereals",
                        grass, cereals, 0.8, "L/ha", 6500),
                new Ingredient("clodinafop-propargyl", "Clodin", "against wild oat and green foxtail in wheat",
                        List.of("avena_fatua", "setaria_viridis"), List.of("wheat"), 0.4, "L/ha", 7200),
                new Ingredient("pinoxaden", "Pinox", "against grass weeds in wheat and barley",
                        grass, cereals, 0.6, "L/ha", 8900),
                new Ingredient("2,4-D ester", "Ester-D", "against annual and some perennial broadleaf weeds",
                        List.of("chenopodium_album", "amaranthus_retroflexus", "sinapis_arvensis", "cirsium_arvense"),
                        List.of("wheat", "barley", "corn"), 0.6, "L/ha", 2800),
                new Ingredient("dicamba", "Dicam", "against broadleaf weeds, including 2,4-D-tolerant ones and wild buckwheat",
                        List.of("fallopia_convolvulus", "chenopodium_album", "convolvulus_arvensis", "salsola_tragus"),
                        List.of("wheat", "barley", "corn"), 0.25, "L/ha", 3600),
                new Ingredient("tribenuron-methyl", "Triben", "a sulfonylurea against annual broadleaf weeds and sow-thistles",
                        List.of("sinapis_arvensis", "chenopodium_album", "sonchus_arvensis", "cirsium_arvense"),
                        List.of("wheat", "barley", "sunflower"), 0.025, "kg/ha", 3100),
                new Ingredient("metsulfuron-methyl", "Metsul", "against a broad range of broadleaf weeds in cereals",
                        List.of("sinapis_arvensis", "chenopodium_album", "amaranthus_retroflexus", "fallopia_convolvulus"),
                        cereals, 0.01, "kg/ha", 2600),
                new Ingredient("clopyralid", "Clopyr", "against creeping perennials such as thistles; allowed on rapeseed and flax",
                        List.of("cirsium_arvense", "sonchus_arvensis"), List.of("wheat", "barley", "rapeseed", "flax"), 0.3, "L/ha", 5400),
                new Ingredient("florasulam", "Floras", "against broadleaf weeds, effective at low temperatures",
                        List.of("chenopodium_album", "sinapis_arvensis", "fallopia_convolvulus", "salsola_tragus"),
                        cereals, 0.05, "L/ha", 3900),
                new Ingredient("MCPA", "Mcpa", "a mild hormone herbicide against broadleaf weeds, allowed on flax",
                        List.of("sinapis_arvensis", "chenopodium_album", "amaranthus_retroflexus"),
                        List.of("wheat", "barley", "flax"), 1.0, "L/ha", 2400),
                new Ingredient("S-metolachlor", "Metolach", "a soil herbicide against annual grasses and pigweed",
                        List.of("echinochloa_crus_galli", "setaria_viridis", "amaranthus_retroflexus"),
                        List.of("sunflower", "corn", "rapeseed"), 1.6, "L/ha", 7800),
                new Ingredient("quizalofop-P-ethyl", "Quizal", "against grass weeds, including couch grass, in broadleaf crops",
                        List.of("avena_fatua", "setaria_viridis", "echinochloa_crus_galli", "elymus_repens"),
                        List.of("sunflower", "rapeseed", "flax"), 1.0, "L/ha", 6100),
                new Ingredient("glyphosate", "Glyphos", "a non-selective herbicide for fallow and stubble before sowing",
                        List.of("elymus_repens", "cirsium_arvense", "sonchus_arvensis", "convolvulus_arvensis",
                                "avena_fatua", "chenopodium_album"),
                        List.of(CROPS), 2.5, "L/ha", 4300));

        String[] brands = {"Agrogard", "DalaProtect", "Niva-Pro", "StepClean"};
        String[] forms = {"EC", "WG", "ME"};
        List<Treatment> out = new ArrayList<>();
        int brandIdx = 0;
        for (Ingredient ing : ingredients) {
            int variants = 2 + rnd.nextInt(2);
            for (int v = 0; v < variants; v++) {
                String brand = brands[(brandIdx++) % brands.length];
                double priceK = 0.85 + rnd.nextDouble() * 0.35;
                out.add(Treatment.builder()
                        .name(brand + " " + ing.trade() + " " + forms[v])
                        .description("Herbicide based on " + ing.name() + ", formulation " + forms[v] + ". "
                                + "Used " + ing.purpose() + ". Synthetic demo item.")
                        .category(Treatment.Category.CHEMICAL)
                        .activeIngredient(ing.name())
                        .targetWeeds(ing.weeds())
                        .allowedCrops(ing.crops())
                        .dosage(new Treatment.Dosage(ing.dose(), ing.unit()))
                        .pricePerHa((double) Math.round(ing.basePrice() * priceK / 10) * 10)
                        .stats(new Treatment.Stats())
                        .build());
            }
        }

        List<String> allCrops = List.of(CROPS);
        out.add(nonChem("Pre-emergence harrowing", "Harrowing 3-5 days after sowing kills annual weed seedlings "
                        + "at the white-thread stage.", Treatment.Category.MECHANICAL,
                List.of("chenopodium_album", "amaranthus_retroflexus", "setaria_viridis", "sinapis_arvensis"),
                List.of("wheat", "barley", "corn", "sunflower"), 1200));
        out.add(nonChem("Inter-row cultivation", "Mechanical weed removal between the rows of row crops.",
                Treatment.Category.MECHANICAL,
                List.of("amaranthus_retroflexus", "chenopodium_album", "echinochloa_crus_galli", "convolvulus_arvensis"),
                List.of("sunflower", "corn"), 1800));
        out.add(nonChem("Stubble disking", "Shallow tillage after harvest makes weed seeds germinate "
                        + "and cuts creeping perennials.", Treatment.Category.MECHANICAL,
                List.of("cirsium_arvense", "sonchus_arvensis", "elymus_repens", "avena_fatua"), allCrops, 2500));
        out.add(nonChem("Bare fallow with tillage", "Repeated cultivation of fallow exhausts the roots "
                        + "of perennial weeds.", Treatment.Category.AGROTECHNICAL,
                List.of("elymus_repens", "cirsium_arvense", "sonchus_arvensis", "convolvulus_arvensis"), allCrops, 4000));
        out.add(nonChem("Crop rotation", "Alternating cereals and row crops breaks the cycle "
                        + "of specialised weeds.", Treatment.Category.AGROTECHNICAL,
                List.of("avena_fatua", "setaria_viridis", "salsola_tragus", "fallopia_convolvulus"), allCrops, 0));
        out.add(nonChem("Higher seeding rate", "A denser stand makes the crop compete harder with weeds.",
                Treatment.Category.AGROTECHNICAL,
                List.of("chenopodium_album", "sinapis_arvensis", "fallopia_convolvulus"), List.of("wheat", "barley", "flax"), 900));
        out.add(nonChem("Cover crop (winter rye)", "Allelopathic suppression of annual weeds by a cover crop.",
                Treatment.Category.BIOLOGICAL,
                List.of("amaranthus_retroflexus", "chenopodium_album", "setaria_viridis"),
                List.of("sunflower", "corn"), 3500));
        out.add(nonChem("Fungal bioherbicide (experimental)", "An experimental biological product based on "
                        + "plant-pathogenic fungi against thistles.", Treatment.Category.BIOLOGICAL,
                List.of("cirsium_arvense", "sonchus_arvensis"), List.of("wheat", "barley", "rapeseed"), 5200));
        return out;
    }

    private Treatment nonChem(String name, String desc, Treatment.Category cat, List<String> weeds,
                              List<String> crops, double price) {
        return Treatment.builder().name(name).description(desc).category(cat).targetWeeds(weeds)
                .allowedCrops(crops).pricePerHa(price).stats(new Treatment.Stats()).build();
    }

    // ------------------------------------------------------------------ users and fields

    private List<User> users() {
        String hash = encoder.encode("password123");
        List<User> list = new ArrayList<>();
        list.add(user("admin@weedrec.kz", hash, User.Role.ADMIN, "Administrator", null, REGIONS[0], 0));
        list.add(user("agronomist@weedrec.kz", hash, User.Role.AGRONOMIST, "Aliya Omarova", null, REGIONS[0], 12));
        list.add(user("farmer@weedrec.kz", hash, User.Role.FARMER, "Askhat Nurlanov", "Bereke Farm", REGIONS[0], 8));
        for (int i = 1; i <= agronomistCount; i++)
            list.add(user("agro" + i + "@weedrec.kz", hash, User.Role.AGRONOMIST, name(), null, pick(REGIONS), 5 + rnd.nextInt(20)));
        for (int i = 1; i <= farmerCount; i++)
            list.add(user("farmer" + i + "@weedrec.kz", hash, User.Role.FARMER, name(), pick(FARMS) + " Farm",
                    pick(REGIONS), 1 + rnd.nextInt(30)));
        return list;
    }

    private User user(String email, String hash, User.Role role, String fullName, String farm, String region, int exp) {
        return User.builder().email(email).passwordHash(hash).role(role)
                .profile(User.Profile.builder().fullName(fullName).farmName(farm).region(region)
                        .experienceYears(exp).phone("+7 7" + (10 + rnd.nextInt(90)) + " " + (1000000 + rnd.nextInt(8999999))).build())
                .createdAt(Instant.now().minus(30 + rnd.nextInt(300), ChronoUnit.DAYS))
                .build();
    }

    /** Hidden profile per farmer: a farm style plus individual noise (sigma 0.3). */
    private Map<String, Preference> preferences(List<User> userList) {
        FarmStyle[] styles = FarmStyle.values();
        Map<String, Preference> out = new HashMap<>();
        for (User u : userList) {
            if (u.getRole() != User.Role.FARMER) continue;
            FarmStyle s = styles[rnd.nextInt(styles.length)];
            Map<Treatment.Category, Double> aff = new EnumMap<>(Treatment.Category.class);
            for (Treatment.Category c : Treatment.Category.values())
                aff.put(c, s.affinity(c) + rnd.nextGaussian() * 0.3);
            double priceSens = Math.max(0, s.priceSensitivity + rnd.nextGaussian() * 0.15);
            out.put(u.getId(), new Preference(s, aff, priceSens));
        }
        return out;
    }

    private List<Field> fields(List<User> userList) {
        List<Field> list = new ArrayList<>();
        for (User u : userList) {
            if (u.getRole() != User.Role.FARMER) continue;
            int r = Arrays.asList(REGIONS).indexOf(u.getProfile().getRegion());
            int n = 1 + rnd.nextInt(3);
            for (int i = 1; i <= n; i++) {
                double[] c = REGION_COORDS[Math.max(r, 0)];
                list.add(Field.builder()
                        .userId(u.getId())
                        .name("Field #" + i)
                        .crop(pick(CROPS))
                        .areaHa((double) (50 + rnd.nextInt(450)))
                        .soilType(pick(SOILS))
                        .location(new GeoJsonPoint(c[0] + rnd.nextGaussian() * 0.5, c[1] + rnd.nextGaussian() * 0.3))
                        .build());
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ detections (CV model imitation)

    private List<Detection> detections(List<Field> fieldList, List<Weed> weedList) {
        List<Detection> list = new ArrayList<>();
        for (Field f : fieldList) {
            int n = 2 + rnd.nextInt(4);
            for (int i = 0; i < n; i++) {
                List<Weed> shuffled = new ArrayList<>(weedList);
                Collections.shuffle(shuffled, rnd);
                List<Detection.DetectedWeed> found = new ArrayList<>();
                int k = 1 + rnd.nextInt(3);
                for (int j = 0; j < k; j++) {
                    found.add(new Detection.DetectedWeed(shuffled.get(j).getId(),
                            round2(0.55 + rnd.nextDouble() * 0.44), round2(0.5 + rnd.nextDouble() * 25)));
                }
                list.add(Detection.builder()
                        .fieldId(f.getId()).userId(f.getUserId())
                        .imageUrl("https://storage.example/weedrec/" + f.getId() + "/img_" + (1000 + rnd.nextInt(9000)) + ".jpg")
                        .modelVersion(rnd.nextDouble() < 0.7 ? "yolov8-weeds-v1.2" : "yolov8-weeds-v1.1")
                        .detectedAt(Instant.now().minus(rnd.nextInt(120), ChronoUnit.DAYS).minus(rnd.nextInt(24), ChronoUnit.HOURS))
                        .weeds(found)
                        .verifiedByAgronomist(rnd.nextDouble() < 0.3)
                        .build());
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ interactions

    /** Price is normalised over the catalog and centred: cheaper than average is a bonus, pricier is a penalty. */
    private static double utility(Treatment t, Preference p, Map<String, Double> quality, double maxPrice) {
        double pricePenalty = p.priceSensitivity() * (t.getPricePerHa() / maxPrice - 0.5) * 2;
        return quality.get(t.getId()) + p.affinity().get(t.getCategory()) - pricePenalty;
    }

    private List<Interaction> interactions(List<Detection> detList, List<Field> fieldList, List<Treatment> catalog,
                                           Map<String, Double> quality, Map<String, Preference> prefs) {
        Map<String, Field> fieldById = new HashMap<>();
        fieldList.forEach(f -> fieldById.put(f.getId(), f));
        double maxPrice = catalog.stream().mapToDouble(Treatment::getPricePerHa).max().orElse(1);
        List<Interaction> list = new ArrayList<>();
        Set<String> rated = new HashSet<>();

        for (Detection d : detList) {
            Field f = fieldById.get(d.getFieldId());
            Preference pref = prefs.get(d.getUserId());
            Map<String, Double> util = new HashMap<>();
            catalog.forEach(t -> util.put(t.getId(), utility(t, pref, quality, maxPrice)));

            Set<String> found = new HashSet<>();
            d.getWeeds().forEach(w -> found.add(w.getWeedId()));
            List<Treatment> suitable = catalog.stream()
                    .filter(t -> t.getAllowedCrops().contains(f.getCrop()))
                    .filter(t -> t.getTargetWeeds().stream().anyMatch(found::contains))
                    .toList();
            Instant t0 = d.getDetectedAt();

            // views suitable treatments, closest to the farmer's taste first, plus 1-2 out of curiosity;
            // noise is drawn up front because a sort comparator must be consistent
            Map<String, Double> viewKey = new HashMap<>();
            suitable.forEach(t -> viewKey.put(t.getId(), util.get(t.getId()) + rnd.nextGaussian() * 0.8));
            List<Treatment> viewed = new ArrayList<>(suitable.stream()
                    .sorted(Comparator.comparingDouble((Treatment t) -> -viewKey.get(t.getId())))
                    .limit(2 + rnd.nextInt(4))
                    .toList());
            for (int i = 0; i < 1 + rnd.nextInt(2); i++) {
                Treatment a = catalog.get(rnd.nextInt(catalog.size())), b = catalog.get(rnd.nextInt(catalog.size()));
                viewed.add(util.get(a.getId()) >= util.get(b.getId()) ? a : b);
            }

            for (Treatment t : viewed) {
                list.add(inter(d, t, Interaction.Type.VIEW, null, null, t0.plus(rnd.nextInt(48), ChronoUnit.HOURS)));
                double pSave = 0.05 + 0.4 / (1 + Math.exp(-2 * (util.get(t.getId()) - 3.7)));   // ~5-45%
                if (rnd.nextDouble() < pSave)
                    list.add(inter(d, t, Interaction.Type.SAVE, null, null, t0.plus(48 + rnd.nextInt(24), ChronoUnit.HOURS)));
            }
            if (suitable.isEmpty() || rnd.nextDouble() < 0.2) continue;

            Map<String, Double> applyKey = new HashMap<>();
            suitable.forEach(t -> applyKey.put(t.getId(), util.get(t.getId()) + rnd.nextGaussian() * 0.6));
            Treatment applied = suitable.stream()
                    .max(Comparator.comparingDouble((Treatment t) -> applyKey.get(t.getId())))
                    .orElseThrow();
            Instant tApply = t0.plus(3 + rnd.nextInt(5), ChronoUnit.DAYS);
            Interaction apply = inter(d, applied, Interaction.Type.APPLY, null, null, tApply);
            apply.setFieldId(f.getId());
            list.add(apply);

            // the rating is subjective (utility), effectiveness is objective (quality only)
            String key = d.getUserId() + "|" + applied.getId();
            if (rnd.nextDouble() < 0.7 && rated.add(key)) {
                double q = quality.get(applied.getId());
                int rating = (int) Math.max(1, Math.min(5, Math.round(util.get(applied.getId()) + rnd.nextGaussian() * 0.6)));
                int eff = (int) Math.max(20, Math.min(99, Math.round(q * 18 + rnd.nextGaussian() * 8)));
                Interaction r = inter(d, applied, Interaction.Type.RATING, rating, eff,
                        tApply.plus(10 + rnd.nextInt(15), ChronoUnit.DAYS));
                r.setFieldId(f.getId());
                list.add(r);
            }
        }
        return list;
    }

    private Interaction inter(Detection d, Treatment t, Interaction.Type type, Integer rating, Integer eff, Instant ts) {
        return Interaction.builder().userId(d.getUserId()).treatmentId(t.getId()).type(type)
                .detectionId(d.getId()).rating(rating).effectiveness(eff)
                .timestamp(ts.isAfter(Instant.now()) ? Instant.now() : ts).build();
    }

    // ------------------------------------------------------------------ helpers

    private String name() {
        if (rnd.nextDouble() < 0.2) return pick(FIRST_M) + " " + pick(LAST_NEUTRAL);
        if (rnd.nextDouble() < 0.3) return pick(FIRST_F) + " " + pick(LAST_M) + "a";   // Nurlanov -> Nurlanova
        return pick(FIRST_M) + " " + pick(LAST_M);
    }

    private <T> T pick(T[] arr) { return arr[rnd.nextInt(arr.length)]; }

    private static double round2(double v) { return Math.round(v * 100) / 100.0; }
}
