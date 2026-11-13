package relay;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Both strategies choose from the same list of available, below-capacity technicians. */
interface AssignmentStrategy {
    Comparator<Map<String, Object>> ranking(String category);

    default Map<String, Object> choose(List<Map<String, Object>> candidates, String category) {
        return candidates.stream().min(ranking(category)).orElse(null);
    }

    Comparator<Map<String, Object>> LOAD_ORDER =
        Comparator.<Map<String, Object>>comparingInt(t -> ((Number) t.get("workload")).intValue())
                  .thenComparingInt(t -> ((Number) t.get("id")).intValue());

    final class LeastLoad implements AssignmentStrategy {
        public Comparator<Map<String, Object>> ranking(String category) {
            return LOAD_ORDER;
        }
    }

    final class SkillFirst implements AssignmentStrategy {
        public Comparator<Map<String, Object>> ranking(String category) {
            return Comparator.<Map<String, Object>, Boolean>comparing(t -> !category.equals(t.get("skill")))
                    .thenComparing(LOAD_ORDER);
        }
    }
}
