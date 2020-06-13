package nl.vu.cs.s2group.nappa.util;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import nl.vu.cs.s2group.nappa.PrefetchingLib;
import nl.vu.cs.s2group.nappa.graph.ActivityNode;
import nl.vu.cs.s2group.nappa.prefetchurl.ParameteredUrl;

/**
 * This class containing common utility methods for the NAPPA Prefetching Library
 */
public class NappaUtil {

    private NappaUtil() {
        throw new IllegalStateException("NappaUtil is a utility class and should be instantiated!");
    }

    /**
     * Parses the list of candidate nodes and verifies if the URLs requested in a candidate node can
     * be requested with the Extras captured in the visited node
     *
     * @param visitedNode    Represents the node which the user is currently visiting
     * @param candidateNodes Represents a list containing all nodes with the potential to be visited
     *                       in the near future
     * @return All URLs requested by all candidate nodes that can be requested with the information
     * present in the currently visited node
     */
    @NotNull
    public static List<String> getUrlsFromCandidateNodes(ActivityNode visitedNode, @NotNull List<ActivityNode> candidateNodes) {
        List<String> candidateUrls = new LinkedList<>();

        for (ActivityNode candidateNode : candidateNodes) {
            candidateUrls.addAll(getUrlsFromCandidateNode(visitedNode, candidateNode));
        }

        return candidateUrls;
    }

    /**
     * Verifies if the URLs requested in the candidate node can be requested with the Extras captured
     * in the visited node
     *
     * @param visitedNode   Represents the node which the user is currently visiting
     * @param candidateNode Represents a node with the potential to be visited in the near future
     * @return All URLs requested in the candidate node that can be requested with the information
     * present in the currently visited node
     */
    @NotNull
    public static List<String> getUrlsFromCandidateNode(@NotNull ActivityNode visitedNode, @NotNull ActivityNode candidateNode) {
        List<String> candidateUrls = new LinkedList<>();
        long activityId = PrefetchingLib.getActivityIdFromName(visitedNode.activityName);
        Map<String, String> extrasMap = PrefetchingLib.getExtrasMap().get(activityId);

        if (extrasMap == null || extrasMap.isEmpty()) return candidateUrls;

        for (ParameteredUrl parameteredUrl : candidateNode.parameteredUrlList) {
            if (extrasMap.keySet().containsAll(parameteredUrl.getParamKeys())) {
                String urlWithExtras = parameteredUrl.fillParams(extrasMap);
                candidateUrls.add(urlWithExtras);
            }
        }

        return candidateUrls;
    }
}
