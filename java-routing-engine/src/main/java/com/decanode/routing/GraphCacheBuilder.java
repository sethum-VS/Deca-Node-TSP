package com.decanode.routing;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;

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

        hopper.setProfiles(new Profile("car").setCustomModel(
                GHUtility.loadCustomModelFromJar("car.json")
        ));

        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));

        hopper.importOrLoad();

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("══════════════════════════════════════════");
        System.out.println("  Graph cache built in " + elapsed + " ms");
        System.out.println("══════════════════════════════════════════");

        hopper.close();
    }
}
