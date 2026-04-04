package com.decanode.routing;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;

import com.graphhopper.util.CustomModel;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;

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
                "car_access, car_average_speed, road_access, road_environment, max_speed, ferry_speed"
        );

        CustomModel customModel = new CustomModel();
        customModel.setDistanceInfluence(15.0); // Favor speed over distance
        customModel.addToPriority(If("road_class == MOTORWAY", MULTIPLY, "1.2"));
        customModel.addToPriority(If("road_class == TRUNK", MULTIPLY, "1.1"));

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
