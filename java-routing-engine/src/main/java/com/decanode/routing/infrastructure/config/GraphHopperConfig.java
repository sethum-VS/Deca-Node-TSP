package com.decanode.routing.infrastructure.config;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.GHUtility;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.graphhopper.util.CustomModel;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.json.Statement.Op.LIMIT;

/**
 * Initializes GraphHopper as a Spring-managed singleton bean.
 * The OSM PBF file is loaded once at startup; subsequent startups
 * reuse the cached graph from disk.
 */
@Configuration
public class GraphHopperConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphHopperConfig.class);

    @Value("${graphhopper.osm.file:/data/sri-lanka.osm.pbf}")
    private String osmFile;

    @Value("${graphhopper.graph.location:/data/graph-cache}")
    private String graphLocation;

    private GraphHopper hopper;

    @Bean
    public GraphHopper graphHopper() {
        log.info("═══════════════════════════════════════════════");
        log.info("  GraphHopper initializing...");
        log.info("  OSM file  : {}", osmFile);
        log.info("  Graph cache: {}", graphLocation);
        log.info("═══════════════════════════════════════════════");

        hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(graphLocation);

        // Encoded values for car routing
        hopper.setEncodedValuesString("car_access, car_average_speed, road_access, road_environment, max_speed, ferry_speed, road_class");

        CustomModel customModel = new CustomModel();
        customModel.setDistanceInfluence(15.0); // Favor speed over distance

        // Required baseline speed statement (GH 11 rejects empty speed list)
        customModel.addToSpeed(If("true", LIMIT, "100"));

        // Boost motorways and trunk roads
        customModel.addToPriority(If("road_class == MOTORWAY", MULTIPLY, "1.2"));
        customModel.addToPriority(If("road_class == TRUNK", MULTIPLY, "1.1"));

        hopper.setProfiles(new Profile("expressway_car")
                .setCustomModel(customModel));

        // Enable Contraction Hierarchies for fast routing queries
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("expressway_car"));

        long start = System.currentTimeMillis();
        hopper.importOrLoad();
        long elapsed = System.currentTimeMillis() - start;

        log.info("═══════════════════════════════════════════════");
        log.info("  GraphHopper ready in {} ms", elapsed);
        log.info("═══════════════════════════════════════════════");

        return hopper;
    }

    @PreDestroy
    public void shutdown() {
        if (hopper != null) {
            log.info("Shutting down GraphHopper...");
            hopper.close();
        }
    }
}
