package com.decanode.routing;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;

import com.graphhopper.util.CustomModel;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.ElseIf;
import static com.graphhopper.json.Statement.Else;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.json.Statement.Op.LIMIT;

/**
 * Standalone CLI utility to pre-build the GraphHopper routing graph cache.
 * Called during Docker image build so the graph-cache is baked into the image,
 * eliminating cold-start graph construction at runtime.
 *
 * Usage: java -cp app.jar com.decanode.routing.GraphCacheBuilder <pbf-path> <cache-dir>
 */
public class GraphCacheBuilder {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: GraphCacheBuilder <osm-pbf-path> <graph-cache-dir>");
            System.exit(1);
        }

        String osmFile = args[0];
        String cacheDir = args[1];

        System.out.println("══════════════════════════════════════════");
        System.out.println("  GraphHopper Cache Builder");
        System.out.println("  OSM file  : " + osmFile);
        System.out.println("  Cache dir : " + cacheDir);
        System.out.println("══════════════════════════════════════════");

        long start = System.currentTimeMillis();

        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(cacheDir);

        hopper.setEncodedValuesString(
                "car_access, car_average_speed, road_access, road_environment, max_speed, ferry_speed, road_class"
        );

        // ── RDA Hierarchical Priority Custom Model ──────────────────
        // Tuned for Sri Lankan road network (RDA classification):
        //   E-Class : MOTORWAY, MOTORWAY_LINK  (Expressways)
        //   A-Class : TRUNK, PRIMARY           (National highways)
        //   B-Class : SECONDARY                (Provincial roads)
        //   C/D-Class: TERTIARY, UNCLASSIFIED, RESIDENTIAL (Local roads)
        CustomModel customModel = new CustomModel();
        customModel.setDistanceInfluence(20.0);

        // 1. Hierarchical Speed Limits based on practical Sri Lankan averages
        customModel.addToSpeed(If("road_class == MOTORWAY || road_class == MOTORWAY_LINK", LIMIT, "100"));
        customModel.addToSpeed(ElseIf("road_class == TRUNK || road_class == PRIMARY", LIMIT, "70"));
        customModel.addToSpeed(ElseIf("road_class == SECONDARY", LIMIT, "50"));
        customModel.addToSpeed(ElseIf("road_class == TERTIARY || road_class == UNCLASSIFIED || road_class == RESIDENTIAL", LIMIT, "40"));
        customModel.addToSpeed(Else(LIMIT, "30"));

        // 2. Hierarchical Priority Gradient (smooth decision-making curve)
        customModel.addToPriority(If("road_class == MOTORWAY || road_class == MOTORWAY_LINK", MULTIPLY, "1.0"));
        customModel.addToPriority(ElseIf("road_class == TRUNK || road_class == PRIMARY", MULTIPLY, "0.85"));
        customModel.addToPriority(ElseIf("road_class == SECONDARY", MULTIPLY, "0.70"));
        customModel.addToPriority(Else(MULTIPLY, "0.50"));

        hopper.setProfiles(new Profile("expressway_car")
                .setCustomModel(customModel));

        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("expressway_car"));

        hopper.importOrLoad();

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("══════════════════════════════════════════");
        System.out.println("  Graph cache built in " + elapsed + " ms");
        System.out.println("══════════════════════════════════════════");

        hopper.close();
    }
}
