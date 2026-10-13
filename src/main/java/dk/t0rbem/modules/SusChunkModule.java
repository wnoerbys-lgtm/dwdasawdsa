package dk.t0rbem.modules;

import dk.t0rbem.ChunkTracker;
import dk.t0rbem.render.Projector;
import dk.t0rbem.settings.BoolSetting;
import dk.t0rbem.settings.ModeSetting;
import dk.t0rbem.settings.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2601;
import net.minecraft.class_2609;
import net.minecraft.class_2611;
import net.minecraft.class_2614;
import net.minecraft.class_2627;
import net.minecraft.class_2818;
import net.minecraft.class_332;
import net.minecraft.class_3719;

/**
 * Sus Chunk Finder: markerer mistænkelige chunks ude i verdenen (gennem vægge) som en 3D-ramme.
 *
 * En chunk er "sus" hvis
 *  - Storage: den indeholder mange kister/tønder/shulkers/ovne/hoppers (score over grænsen), og/eller
 *  - Players: der har været spillere i den længe (bruger ChunkTracker, samme data som Chunk Map).
 *
 * Det er en heuristik ud fra de chunks din klient har indlæst, ikke et officielt tjek.
 */
public class SusChunkModule extends Module {
    private record Sus(int cx, int cz, int score, int minutes, int minY, int maxY, boolean storage, boolean players) {}

    private final ModeSetting mode = add(new ModeSetting("Mode", "Both", "Storage", "Players", "Both"));
    private final NumberSetting minScore = add(new NumberSetting("Min storage score", 5, 1, 40, 1));
    private final NumberSetting susMinutes = add(new NumberSetting("Sus minutes", 20, 1, 120, 1));
    private final NumberSetting range = add(new NumberSetting("Range (chunks)", 8, 2, 16, 1));
    private final BoolSetting furnacesHoppers = add(new BoolSetting("Count furnaces/hoppers", true));
    private final BoolSetting includeSelf = add(new BoolSetting("Include yourself", false));
    private final BoolSetting labels = add(new BoolSetting("Labels", true));

    private final Projector proj = new Projector();
    private List<Sus> sus = List.of();
    private int timer;

    public SusChunkModule() {
        super("Sus Chunk", "Markerer mistænkelige chunks (mange kister eller lang tid med spillere) gennem vægge.", Category.RENDER, false);
    }

    @Override
    public void onDisable() {
        sus = List.of();
        timer = 0;
    }

    private int weight(class_2586 be) {
        if (be instanceof class_2627) return 3;   // shulker
        if (be instanceof class_2611) return 2;   // ender chest
        if (be instanceof class_2595) return 1;   // kiste + trapped chest
        if (be instanceof class_3719) return 1;   // tønde
        if (furnacesHoppers.get()) {
            if (be instanceof class_2609) return 1;  // ovn
            if (be instanceof class_2614) return 1;  // hopper
            if (be instanceof class_2601) return 1;  // dispenser/dropper
        }
        return 0;
    }

    @Override
    public void onTick() {
        if (mc.field_1687 == null || mc.field_1724 == null) return;

        boolean useStorage = !mode.get().equals("Players");
        boolean usePlayers = !mode.get().equals("Storage");

        // Chunk Map tæller allerede hvis den er slået til, så vi undgår dobbelt-tælling
        Module chunkMap = ModuleManager.get("Chunk Map");
        if (usePlayers && (chunkMap == null || !chunkMap.enabled)) {
            ChunkTracker.tick(mc, includeSelf.get());
        }

        if (++timer < 20) return; // scan ca. 1 gang i sekundet
        timer = 0;

        int pcx = ((int) Math.floor(mc.field_1724.method_23317())) >> 4;
        int pcz = ((int) Math.floor(mc.field_1724.method_23321())) >> 4;
        int r = range.getInt();
        Map<Long, Integer> times = ChunkTracker.current(mc);
        List<Sus> found = new ArrayList<>();

        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                int score = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

                if (useStorage) {
                    class_2818 chunk = mc.field_1687.method_2935().method_21730(cx, cz);
                    if (chunk != null) {
                        for (class_2586 be : chunk.method_12214().values()) {
                            int w = weight(be);
                            if (w == 0) continue;
                            score += w;
                            int y = be.method_11016().method_10264();
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                        }
                    }
                }

                int minutes = usePlayers ? ChunkTracker.get(times, cx, cz) / 1200 : 0;
                boolean storageSus = useStorage && score >= minScore.getInt();
                boolean playersSus = usePlayers && minutes >= susMinutes.getInt();
                if (!storageSus && !playersSus) continue;

                if (minY > maxY) { // ingen kister at pege på -> brug din egen højde
                    minY = (int) Math.floor(mc.field_1724.method_23318());
                    maxY = minY;
                }
                found.add(new Sus(cx, cz, score, minutes, minY, maxY, storageSus, playersSus));
            }
        }
        sus = found;
    }

    @Override
    public void renderHud(class_332 ctx, float pt) {
        if (mc.field_1724 == null || sus.isEmpty()) return;
        proj.update(ctx, pt);

        for (Sus s : sus) {
            double x1 = s.cx() * 16.0, z1 = s.cz() * 16.0;
            double y1 = s.minY(), y2 = Math.max(s.maxY() + 1, s.minY() + 4);
            int color = s.storage() && s.players() ? 0xFFFF55FF   // begge = lilla
                    : s.storage() ? 0xFFFF5555                   // kister = rød
                    : 0xFFFFAA00;                                // spillere = orange
            proj.wireBox(ctx, x1, y1, z1, x1 + 16, y2, z1 + 16, color);

            if (labels.get()) {
                double[] c = proj.project(x1 + 8, y2 + 0.5, z1 + 8);
                if (c == null) continue;
                String text = "Sus";
                if (s.storage()) text += " " + s.score() + " storage";
                if (s.players()) text += " " + s.minutes() + " min";
                int tx = (int) c[0] - mc.field_1772.method_1727(text) / 2;
                ctx.method_25303(mc.field_1772, text, tx, (int) c[1] - 10, color);
            }
        }
    }
}
