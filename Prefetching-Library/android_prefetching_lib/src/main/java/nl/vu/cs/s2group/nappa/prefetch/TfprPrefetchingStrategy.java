package nl.vu.cs.s2group.nappa.prefetch;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.vu.cs.s2group.nappa.graph.ActivityNode;
import nl.vu.cs.s2group.nappa.util.NappaUtil;

/**
 * This strategy employs a Link Analysis approach implementing a PageRank-based algorithm
 * using the time a user spends in the activities and how frequent the user access the
 * activities to decide which nodes to select.
 * <p>
 * Only a subgraph of the ENG is considered in the calculations. The score calculations are
 * performed at runtime and are not persisted in the database.
 * <p>
 * Strategy inspired on the paper Personalized PageRank for Web Page Prediction Based
 * on Access Time-Length and Frequency from 2007.
 * <p>
 * This strategy accepts the following configurations:
 * <ul>
 * </ul>
 *
 * @see <a href="https://dl.acm.org/doi/10.1109/WI.2007.145">Personalized PageRank paper</a>
 */
public class TfprPrefetchingStrategy extends AbstractPrefetchingStrategy {
    private static final String LOG_TAG = TfprPrefetchingStrategy.class.getSimpleName();

    public TfprPrefetchingStrategy() {
        super();

        /*
          On the testing phase, the TFPR score was rather low.
          The TFPR score was higher for pages with longer time (e.g. 30+ seconds) and
          lower for pages with short time (e.g., 5- seconds).
          Low TFPR scores were always below 10%, often below 5%.
          High TFPR scores were always between 40% to 50~55%.
          While testing, there was only 2 occasions where a page obtained a TFPR score equal
          or higher than 60%, which is the default lower threshold for the weight score:

          * The long time was increased to a few minutes;
          * The current node had a single child only;

          In both cases the TFPR score was between 60% and 75%.

          Since this is not always the case, it seems reasonable to reduce the lower threshold.
          The value is only overridden if it was not already overridden using the user-defined
          configurations.
         */
        if (scoreLowerThreshold == DEFAULT_SCORE_LOWER_THRESHOLD) scoreLowerThreshold = 0.4f;
    }

    @Override
    public boolean needVisitTime() {
        return true;
    }

    @Override
    public boolean needSuccessorsVisitTime() {
        return true;
    }

    @NonNull
    @Override
    public List<String> getTopNUrlToPrefetchForNode(@NotNull ActivityNode node, Integer maxNumber) {
        long startTime = new Date().getTime();

        // Prepare the graph
        TfprGraph graph = makeSubgraph(node);
        calculateVisitTimeScores(graph);

        // Verifies if we have any data in our subgraph.
        if (graph.aggregateVisitTime == 0) {
            logStrategyExecutionDuration(node, startTime);
            return new ArrayList<>();
        }

        // Run page rank
        runTfprAlgorithm(graph);

        // Select all successors nodes with score above the threshold
        List<ActivityNode> selectedNodes = getSuccessorListSortByTfprScore(graph, node);

        // Select all URLs that fits the budget
        List<String> selectedUrls = getUrls(node, selectedNodes);

        logStrategyExecutionDuration(node, startTime);

        return selectedUrls;
    }

    @NotNull
    private List<String> getUrls(ActivityNode currentNode, @NotNull List<ActivityNode> nodes) {
        List<String> urls = new ArrayList<>();

        for (ActivityNode node : nodes) {
            int remainingUrlBudget = maxNumberOfUrlToPrefetch - urls.size();
            List<String> nodUrls = NappaUtil.getUrlsFromCandidateNode(currentNode, node, remainingUrlBudget);
            urls.addAll(nodUrls);

            Log.d(LOG_TAG, "Selected node " + node.getActivitySimpleName() +
                    " containing the URLS " + nodUrls);
            if (urls.size() >= maxNumberOfUrlToPrefetch) break;
        }

        return urls;
    }

    /**
     * Take the successors of the current node, sort by their TFPR score and return an array
     * with all the successors that have a TFPR score higher than the lower threshold score.
     *
     * @param graph       The subgraph after running the TFPR algorithm
     * @param currentNode The {@link Activity} the user navigated to
     * @return An array of successors sorted by TFPR score
     */
    @NotNull
    private List<ActivityNode> getSuccessorListSortByTfprScore(@NotNull TfprGraph graph, @NotNull ActivityNode currentNode) {
        // sort nodes from the highest TFPR score to the lowest
        //noinspection ConstantConditions We do not add null values to the map
        List<TfprNode> sortedSuccessors = graph.graph.get(currentNode.activityName).successors;
        Collections.sort(sortedSuccessors, (node1, node2) -> (int) (node2.tfprScore - node1.tfprScore));

        // filter out all nodes with low TFPR score
        List<ActivityNode> sortedSuccessorsAboveThreshold = new ArrayList<>();
        for (TfprNode successor : sortedSuccessors) {
            if (successor.tfprScore >= scoreLowerThreshold) {
                sortedSuccessorsAboveThreshold.add(successor.node);
            } else {
                // Since the list is sorted, all subsequent nodes will have insufficient TFPR score
                break;
            }
        }

        return sortedSuccessorsAboveThreshold;
    }

    /**
     * Run the TFPR algorithm to calculate the TFPR score
     *
     * @param graph A subgraph with parents/successors linked and visit time weights
     *              calculated
     */
    private void runTfprAlgorithm(TfprGraph graph) {
        for (int i = 0; i < numberOfIterations; i++) {
            for (TfprNode node : graph.graph.values()) {
                // This variable needs a better name
                float sumBu = 0;

                for (TfprNode parent : node.parents) {
                    //noinspection ConstantConditions The hash should never return null
                    sumBu += parent.tfprScore * parent.aggregateVisitTimeFromSuccessors.get(node.node.activityName) /
                            parent.totalAggregateVisitTimeFromSuccessors;
                }

                node.tfprScore = dampingFactor * node.aggregateVisitTime / graph.aggregateVisitTime + (1 - dampingFactor) * sumBu;
            }
        }
    }

    /**
     * Create an instance of {@link TfprGraph} with a subgraph G of all nodes to consider
     * in this calculation. The subgraph includes the following nodes:
     *
     * <ul>
     *     <li> The current node </li>
     *     <li> All successors of the current node </li>
     *     <li> All parents of all successors of the current node </li>
     * </ul>
     * <p>
     * All the {@link TfprNode} created here contains the links to their respective {@link
     * ActivityNode} object and their parents and successors present in the subgraph.
     *
     * @param currentNode Represents the {@link android.app.Activity} the user navigated to
     * @return A instance of {@link TfprGraph} without the aggregate visit time weights.
     */
    @NotNull
    private TfprGraph makeSubgraph(@NotNull ActivityNode currentNode) {
        TfprGraph tfprGraph = new TfprGraph();

        // Creates a TFPR node for the current node
        TfprNode currentNodeTfprNode = new TfprNode(currentNode);
        tfprGraph.graph.put(currentNode.activityName, currentNodeTfprNode);

        // Create TFPR nodes for all successors of the current node and add parent-successor links
        for (ActivityNode successor : currentNode.successors.keySet()) {
            TfprNode successorTfprNode = getOrCreateTfprNode(tfprGraph, successor);
            linkNodesAsSuccessorParent(successorTfprNode, currentNodeTfprNode);

            // Create TFPR nodes for all the parents of this successor and add parent-successor links
            for (ActivityNode successorParent : successor.ancestors.keySet()) {
                TfprNode successorParentTfprNode = getOrCreateTfprNode(tfprGraph, successorParent);
                linkNodesAsSuccessorParent(successorTfprNode, successorParentTfprNode);
            }
        }

        return tfprGraph;
    }

    /**
     * Link the nodes {@code successor} and {@code parent} if they are not linked yet
     *
     * @param successor The successor node.
     * @param parent    The parent node.
     */
    private void linkNodesAsSuccessorParent(@NotNull TfprNode successor, @NotNull TfprNode parent) {
        if (!parent.successors.contains(successor)) parent.successors.add(successor);
        if (!successor.parents.contains(parent)) successor.parents.add(parent);
    }

    /**
     * Verifies if the provided node exists in the graph. If so, return it, otherwise
     * create a new node and add it to the graph
     *
     * @param tfprGraph The graph to add a new {@link TfprNode} if it doesn't contain
     *                  the provided node
     * @param node      The node used as reference to get an existent or create a new
     *                  {@link TfprNode}
     * @return Either an existent or a new node
     */
    private TfprNode getOrCreateTfprNode(@NotNull TfprGraph tfprGraph, @NotNull ActivityNode node) {
        if (tfprGraph.graph.containsKey(node.activityName))
            return tfprGraph.graph.get(node.activityName);

        TfprNode newNode = new TfprNode(node);
        tfprGraph.graph.put(node.activityName, newNode);

        return newNode;
    }

    /**
     * Take an instance of {@link TfprGraph} with liked parents and successors and run the
     * calculations to obtain the visit time weight required to run the TFPR algorithm.
     *
     * @param tfprGraph The subgraph to run the TFPR algorithm
     */
    private void calculateVisitTimeScores(@NotNull TfprGraph tfprGraph) {
        for (TfprNode tfprNode : tfprGraph.graph.values()) {
            // Obtain t(u)
            tfprNode.aggregateVisitTime = tfprNode.node.getAggregateVisitTime().totalDuration;

            // Obtain partial SUM(t(w)) | w e G
            tfprGraph.aggregateVisitTime += tfprNode.aggregateVisitTime;

            // Obtain SUM(t(v, w)) | w e F_v
            tfprNode.totalAggregateVisitTimeFromSuccessors = NappaUtil.getSuccessorsAggregateVisitTimeOriginatedFromNode(tfprNode.node);

            // Obtain t(v, u) for all pairs of v -> u where v is the current node
            tfprNode.aggregateVisitTimeFromSuccessors = NappaUtil.getSuccessorsAggregateVisitTimeOriginatedFromNodeMap(tfprNode.node);
        }

    }

    private static class TfprGraph {
        /**
         * Represents G, the subgraph used to compute the TFPR score. This subgraph is
         * stored as a hash map that maps the name of the activity to its TFPR node.
         */
        Map<String, TfprNode> graph;

        /**
         * Represents SUM(t(w)) | w e G, the total time spent on all pages of the tree.
         */
        long aggregateVisitTime;

        public TfprGraph() {
            graph = new HashMap<>();
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder graphAsStr = new StringBuilder();

            for (TfprNode node : graph.values()) {
                graphAsStr.append(node.toString()).append('\n');
            }

            return "TfprGraph{\n" +
                    graphAsStr.toString() +
                    '}';
        }
    }

    private static class TfprNode {
        /**
         * Represents B_u, the set of pages that link to page u.
         */
        List<TfprNode> parents;

        /**
         * Represents F_v, the set of pages that page v links to.
         * From the point of view of each parent
         */
        List<TfprNode> successors;

        /**
         * A reference to the default node representation. Needed to obtain the URLs
         */
        ActivityNode node;

        /**
         * Represents Tvu, the  sum  of  the  time–lengths  spent  on  visiting  page  u
         * when  page  v  and  u  were  visited consecutively. if more occurrences of the
         * path v→u  occurs and the user stay on page u for a longer  time, the  value
         * of t(v, u) will be larger, thus t(v,u) covers both information of access
         * time-length and access frequency of a page u.
         */
        Map<String, Long> aggregateVisitTimeFromSuccessors;

        /**
         * Represents TFPR(u)
         */
        float tfprScore;

        /**
         * Represent t(u), the total time spent on page u.
         */
        long aggregateVisitTime;

        /**
         * Represents SUM(t(v, w)) | w e F_v, the total time spent on all pages when accessed from
         * a page v.
         */
        long totalAggregateVisitTimeFromSuccessors;

        public TfprNode(ActivityNode node) {
            parents = new ArrayList<>();
            successors = new ArrayList<>();
            this.node = node;
        }

        @NonNull
        @Override
        public String toString() {
            return "TfprNode{" +
                    node.getActivitySimpleName() + " : " +
                    "TFPR = " + tfprScore +
                    '}';
        }
    }
}
