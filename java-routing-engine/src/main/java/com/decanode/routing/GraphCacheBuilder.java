package com.decanode.routing;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;

import com.graphhopper.util.CustomModel;
import static com.graphhopper.json.Statement.If;
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

        CustomModel customModel = new CustomModel();
        customModel.setDistanceInfluence(5.0); // Severely drop distance importance

        // 1. Strict Speed Limits (Sri Lanka Mapping)
        // road_class == MOTORWAY automatically applies to both the highway and its on-ramps
        customModel.addToSpeed(If("road_class == MOTORWAY", LIMIT, "100"));
        // Explicitly cap A-Class
        customModel.addToSpeed(If("road_class == TRUNK || road_class == PRIMARY", LIMIT, "70"));
        // Explicitly cap B-Class & Minor
        customModel.addToSpeed(If("road_class == SECONDARY || road_class == TERTIARY || road_class == RESIDENTIAL || road_class == UNCLASSIFIED", LIMIT, "50"));
        // The Safety Net: Catch any weird leftover OSM tags
        customModel.addToSpeed(If("true", LIMIT, "40"));

        // 2. Aggressive Priority Penalties (The forcing function)
        // This safely penalizes normal roads, while leaving the expressway and its ramps untouched
        customModel.addToPriority(If("road_class != MOTORWAY", MULTIPLY, "0.5"));

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
