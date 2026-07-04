package com.flowboardx.engine;

import lombok.Getter;
import lombok.Setter;

@Getter
public class GraphEdgeWrapper {
    private final GraphNodeWrapper source;
    private final GraphNodeWrapper target;
    private final String branchLabel; // null, "true", or "false" (condition branches)

    /** Set once the source node finishes: true if this edge actually carried data, false if skip-cascaded. */
    @Setter
    private volatile Boolean resolution;

    public GraphEdgeWrapper(GraphNodeWrapper source, GraphNodeWrapper target, String branchLabel) {
        this.source = source;
        this.target = target;
        this.branchLabel = branchLabel;
    }
}
