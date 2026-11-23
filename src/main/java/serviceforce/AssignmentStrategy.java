package serviceforce;

import java.util.Comparator;
import java.util.List;
import serviceforce.DomainModels.Technician;

/** Both strategies choose from the same list of available, below-capacity technicians. */
interface AssignmentStrategy {
    Comparator<Technician> ranking(String category);

    default Technician choose(List<Technician> candidates, String category) {
        return candidates.stream().min(ranking(category)).orElse(null);
    }

    Comparator<Technician> LOAD_ORDER = Comparator.comparingInt(Technician::workload)
            .thenComparingInt(Technician::id);

    final class LeastLoad implements AssignmentStrategy {
        public Comparator<Technician> ranking(String category) {
            return LOAD_ORDER;
        }
    }

    final class SkillFirst implements AssignmentStrategy {
        public Comparator<Technician> ranking(String category) {
            return Comparator.<Technician, Boolean>comparing(t -> !category.equals(t.skill()))
                    .thenComparing(LOAD_ORDER);
        }
    }
}
