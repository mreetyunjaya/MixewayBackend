package io.mixeway.integrations.opensourcescan.plugins.dependencytrack.model;

import java.util.List;

public class DTrackGetVulnsForProject {
    private List<DTrackVuln> dTrackVulns;

    public List<DTrackVuln> getdTrackVulns() {
        return dTrackVulns;
    }

    public void setdTrackVulns(List<DTrackVuln> dTrackVulns) {
        this.dTrackVulns = dTrackVulns;
    }
}
