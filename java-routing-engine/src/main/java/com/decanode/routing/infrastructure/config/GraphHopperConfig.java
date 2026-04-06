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
import static com.graphhopper.json.Statement.ElseIf;
import static com.graphhopper.json.Statement.Else;
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

        // ── RDA Hierarchical Priority Custom Model ──────────────────
        // Tuned for Sri Lankan road network (RDA classification):
        //   E-Class : MOTORWAY                 (Expressways + ramps)
        //   A-Class : TRUNK, PRIMARY           (National highways)
        //   B-Class : SECONDARY                (Provincial roads)
        //   C/D-Class: TERTIARY, UNCLASSIFIED, RESIDENTIAL (Local roads)
        CustomModel customModel = new CustomModel();
        customModel.setDistanceInfluence(20.0);

        // 1. Hierarchical Speed Limits based on practical Sri Lankan averages
        customModel.addToSpeed(If("road_class == MOTORWAY", LIMIT, "100"));
        customModel.addToSpeed(ElseIf("road_class == TRUNK || road_class == PRIMARY", LIMIT, "70"));
        customModel.addToSpeed(ElseIf("road_class == SECONDARY", LIMIT, "50"));
        customModel.addToSpeed(ElseIf("road_class == TERTIARY || road_class == UNCLASSIFIED || road_class == RESIDENTIAL", LIMIT, "40"));
        customModel.addToSpeed(Else(LIMIT, "30"));

        // 2. Hierarchical Priority Gradient (smooth decision-making curve)
        customModel.addToPriority(If("road_class == MOTORWAY", MULTIPLY, "1.0"));
        customModel.addToPriority(ElseIf("road_class == TRUNK || road_class == PRIMARY", MULTIPLY, "0.85"));
        customModel.addToPriority(ElseIf("road_class == SECONDARY", MULTIPLY, "0.70"));
        customModel.addToPriority(Else(MULTIPLY, "0.50"));

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
