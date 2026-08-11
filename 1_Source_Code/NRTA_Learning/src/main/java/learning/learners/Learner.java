package learning.learners;

import language.words.ResetLogicalTimedWord;
import ta.Action;
import ta.Clock;
import ta.NTA;
import learning.teachers.Teacher;
import lombok.Getter;
import tools.IdCounter;

import java.util.Set;


public abstract class Learner {
    private static final IdCounter id_counter = new IdCounter();
    @Getter
    protected final String hypothesis_name;
    protected final int id;

    protected final Teacher teacher;
    @Getter
    protected final Set<Clock> clockSet;
    @Getter
    protected final Set<Action> actionSet;
    protected boolean prepared;
    public Learner(Teacher teacher, Set<Clock> clockSet, Set<Action> actionSet, String hypothesis_name){
        this.id = id_counter.createId();
        this.hypothesis_name = hypothesis_name;
        this.teacher = teacher;
        this.clockSet = clockSet;
        this.actionSet = actionSet;
    }

    public abstract void refine(ResetLogicalTimedWord ctx);
    public abstract NTA generateHypothesis();
    public abstract boolean isPrepared();
    public abstract NTA learn();
    public abstract void show();
}
