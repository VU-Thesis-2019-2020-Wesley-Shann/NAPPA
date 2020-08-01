package nl.vu.cs.s2group.nappa.prefetch;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import nl.vu.cs.s2group.nappa.Nappa;
import nl.vu.cs.s2group.nappa.graph.ActivityNode;
import nl.vu.cs.s2group.nappa.nappaexperimentation.MetricNappaPrefetchingStrategyExecutionTime;
import nl.vu.cs.s2group.nappa.util.NappaConfigMap;
import nl.vu.cs.s2group.nappa.util.NappaUtil;

/**
 * This strategy employs a Greedy approach using the time a user spends in the activities
 * and the how frequent the user access the activities to decide which nodes to select.
 * This strategy runs recursively on the children of the current most probable node.
 * Only a single child is select per recursion.
 * <p>
 * This strategy accepts the following configurations:
 * <ul>
 *     <li>{@link PrefetchingStrategyConfigKeys#WEIGHT_FREQUENCY_SCORE}</li>
 *     <li>{@link PrefetchingStrategyConfigKeys#WEIGHT_TIME_SCORE}</li>
 * </ul>
 */
public class GreedyPrefetchingStrategyOnVisitFrequencyAndTime extends AbstractPrefetchingStrategy {
    private static final String LOG_TAG = GreedyPrefetchingStrategyOnVisitFrequencyAndTime.class.getSimpleName();

    private static final float DEFAULT_WEIGHT_FREQUENCY_SCORE = 0.5f;
    private static final float DEFAULT_WEIGHT_TIME_SCORE = 0.5f;

    protected final float weightFrequencyScore;
    protected final float weightTimeScore;

    private List<String> already_visited_successors;

    List<String> logs;

    private int runCount = 0;
    private int recursionCount;

    @Override
    public boolean needVisitTime() {
        return true;
    }

    public GreedyPrefetchingStrategyOnVisitFrequencyAndTime() {
        super();

        weightFrequencyScore = NappaConfigMap.get(
                PrefetchingStrategyConfigKeys.WEIGHT_FREQUENCY_SCORE,
                DEFAULT_WEIGHT_FREQUENCY_SCORE);

        weightTimeScore = NappaConfigMap.get(
                PrefetchingStrategyConfigKeys.WEIGHT_TIME_SCORE,
                DEFAULT_WEIGHT_TIME_SCORE);

        if ((weightFrequencyScore + weightTimeScore) != 1.0)
            throw new IllegalArgumentException("The sum of the time and frequency weight must be 1!");
    }

    @NonNull
    @Override
    public List<String> getTopNUrlToPrefetchForNode(@NonNull ActivityNode node, Integer maxNumber) {
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Nappa.strategyPredictionExecutionCount++;
        if (node.successors.size() == 0) Nappa.strategyPredictionNoSuccessor++;
        runCount++;
        recursionCount = 0;
        logs = new ArrayList<>();
        Log.d(LOG_TAG, "==================================");
        Log.d(LOG_TAG, "Starting Run #" + runCount );
        Log.d(LOG_TAG, "----------------------------------");
        Log.d(LOG_TAG, "Node data " + node.toString());
        boolean wasSuccessful;
        long startTime = System.nanoTime();
//        long startTime = System.currentTimeMillis();
        already_visited_successors = new ArrayList<>();
        List<String> urls;
        try {
            urls = getTopNUrlToPrefetchForNode(node, 1, new ArrayList<>());
            wasSuccessful = true;
        } catch (Exception e) {
            Nappa.strategyPredictionException++;
            urls = new ArrayList<>();
            wasSuccessful = false;
            logs.add("Something wrong happened: " + e.toString() + "\n" + Arrays.toString(e.getStackTrace()));
        }
        long endTime = System.nanoTime();
//        Log.d("MYTAG", startTime + ", " + endTime + ", " + (endTime - startTime));
//        long endTime = System.currentTimeMillis();
        MetricNappaPrefetchingStrategyExecutionTime.log(LOG_TAG, startTime, endTime, urls.size(), node.successors.size(), already_visited_successors.size(), wasSuccessful);
//        logStrategyExecutionDuration(node, startTime);
        if (Nappa.predictedNextActivity.size() == 0 && node.successors.size() > 0) Nappa.strategyPredictionInsufficientScore++;
        for (String log : logs) {
            Log.d(LOG_TAG, log);
        }
        Log.d(LOG_TAG, "Next visited child will be " + Nappa.predictedNextActivity.toString() + "\n");
        Log.d(LOG_TAG, "==================================");

        return urls;
    }

    /**
     * Auxiliary method to recursively find the best successor. This method works in three stages.
     * <p>
     * The first stage is to find the successor of the current node with the best score.
     * If no successor is found or if the score of the best successor is insufficient
     * (i.e., the calculated score is lower than the lower bound threshold), then the recursion
     * stops and the current list of URLs is returned.
     * <p>
     * If the score is higher, then the second stage starts. In this stage the method verifies
     * if there is sufficient budget (i.e., number of URLs) to add all URLs of the best successor.
     * In the case there isn't, only a subset of the URLs of the best successor is added to the URL
     * list.
     * <p>
     * In the third stage the method verifies whether to continue the recursion or return the
     * current URL list. If the URL budget is completely filled, then the URL list is returned,
     * otherwise, the recursion continues by invoking this method with the best successor object,
     * score and current URL list
     *
     * @param node        The current node in the recursion. Either the node that started the
     *                    recursion or of of its descendant
     * @param parentScore The score of the parent node.
     * @param urlList     The list of URLs to prefetch
     * @return The {@code urlList} after completing all recursions
     */
    private List<String> getTopNUrlToPrefetchForNode(@NonNull ActivityNode node, float parentScore, List<String> urlList) {
        recursionCount++;
        logs.add("------------- Recursion #" + recursionCount + " -----------");
        logs.add("Best successors so far " + already_visited_successors.toString());
        logs.add("Checking node " + node.getActivitySimpleName() + " with " + node.successors.size() + " successors");
        // Fetches the data to start the calculations - Aggregate visit time and frequency
        float totalAggregateTime = NappaUtil.getSuccessorsAggregateVisitTime(node);
        int totalAggregateFrequency = NappaUtil.getSuccessorsTotalAggregateVisitFrequency(node, lastNSessions);
        Map<String, Integer> successorsAggregateFrequencyMap = NappaUtil.mapSuccessorsAggregateVisitFrequency(node, lastNSessions);
        // Temporary variables to find the best successor
        ActivityNode bestSuccessor = null;
        float bestSuccessorScore = 0;

        // Loop all successors and saves the successor with the best score. In case of drawn, the
        //  first current best successor is picked
        for (ActivityNode successor : node.successors.keySet()) {
            logs.add("Check successor " + successor.getActivitySimpleName());
            // Skips nodes already selected as best successors
            if (already_visited_successors.contains(successor.getActivitySimpleName())) {
                logs.add("This successor was visited in a previous recursion");
                continue;
            }

            Integer successorFrequency = successorsAggregateFrequencyMap.get(successor.activityName);
            if (successorFrequency == null)
                successorFrequency = 0;
//                throw new NoSuchElementException("Unable to obtain the successor frequency count!");
            float successorTime = successor.getAggregateVisitTime().totalDuration;

            float successorTimeScore = totalAggregateTime == 0 ? 0 : (successorTime / totalAggregateTime * weightTimeScore);
            float successorFrequencyScore = totalAggregateFrequency == 0 ? 0 : ((float) successorFrequency / totalAggregateFrequency * weightFrequencyScore);

            float successorScore = parentScore * (successorTimeScore + successorFrequencyScore);
            logs.add("Data:");
            logs.add("\t- Successor time = " + successorTime);
            logs.add("\t- Successor frequency = " + successorFrequency);
            logs.add("\t- Aggregated time = " + totalAggregateTime);
            logs.add("\t- Aggregated frequency = " + totalAggregateFrequency);
            logs.add("Calculated scores:");
            logs.add("\t- Parent score = " + parentScore);
            logs.add("\t- Successor time score = " + successorTimeScore);
            logs.add("\t- Successor frequency score = " + successorFrequencyScore);
            logs.add("\t- Successor final score = " + successorScore);

            if (bestSuccessor == null || successorScore > bestSuccessorScore) {
                logs.add("Switch best successor");
                bestSuccessor = successor;
                bestSuccessorScore = successorScore;
            }
        }

        // Register the node as already visited
        if (bestSuccessor != null)
            already_visited_successors.add(bestSuccessor.getActivitySimpleName());

        // Verifies if this node has any successor. If it has, verifies if the successor with the best score has a score high enough
        if (bestSuccessor == null || bestSuccessorScore < scoreLowerThreshold) {
            if (bestSuccessor == null)
                logs.add("this node has no successors");
            else
                logs.add("Best successor has score " + bestSuccessorScore + " which is lower than the acceptable threshold of " + scoreLowerThreshold);
            return urlList;
        }
        Nappa.predictedNextActivity.add(bestSuccessor.activityName);

        // Fetches the URLs from the bestSuccessor and the remaining URL budget
        int remainingUrlBudget = maxNumberOfUrlToPrefetch - urlList.size();
        List<String> bestSuccessorUrls = NappaUtil.getUrlsFromCandidateNode(node, bestSuccessor, remainingUrlBudget);

        logs.add(node.getActivitySimpleName() +
                " best successor is " + bestSuccessor.getActivitySimpleName() +
                " with score " + bestSuccessorScore +
                " containing the URLS " + bestSuccessorUrls);

        // Add the remaining URLs to the list of URLs to prefetch
        urlList.addAll(bestSuccessorUrls);


        // Verifies if there is any URL budget left
        if (urlList.size() >= maxNumberOfUrlToPrefetch) return urlList;
        return getTopNUrlToPrefetchForNode(bestSuccessor, bestSuccessorScore, urlList);
    }
}
