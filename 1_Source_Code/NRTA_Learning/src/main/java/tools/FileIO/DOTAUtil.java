package tools.FileIO;

import exception.OtherExceptions;
import ta.*;
import tools.Tuple.Interval;
import tools.Tuple.Pair;

import java.util.*;

public class DOTAUtil extends TAUtil{
    public static String SINK_LOC_NAME = "sink";
    public static String NEG_DOTA_SUFFIX = "_neg";

    public static Pair<Boolean, RTA> TA2DOTA(TA ta){
        if(ta.getX().size()>1)
            return new Pair<>(false,null);
        else{
            RTA RTA = new RTA(ta.getName());
            RTA.setQ0(ta.getQ0());
            RTA.setQ(ta.getQ());
            RTA.setF(ta.getF());
            RTA.setSigma(ta.getSigma());
            RTA.setDelta(ta.getDelta());
            for(Map.Entry<String, Clock> entry : ta.getX().entrySet()){
                RTA.addClock(entry.getValue());
            }
            return new Pair<>(true, RTA);
        }
    }
    // Complete and complement a DOTA
    public static RTA complementAndNegative(RTA RTA){
        if(RTA.getX().size()!=1)
            throw new RuntimeException(OtherExceptions.NOT_A_DOTA);
        String name = RTA.getName();
        RTA RTA_neg = new RTA(name);
        // Add sink location
        Location sink_loc = new Location(DOTAUtil.SINK_LOC_NAME,true, RTA);
        RTA_neg.addClock(RTA.getSingle_clock());
        RTA_neg.setSigma(RTA.getSigma());
        RTA_neg.addLocation(sink_loc);
        // Add locations
        for(Map.Entry<String,Location> entry : RTA.getQ().entrySet()){
            Location old_loc = entry.getValue();
            Location new_loc = new Location(old_loc.getName(),!old_loc.is_accepting(), RTA);
            RTA_neg.addLocation(new_loc);
        }
        // Set initial location
        RTA_neg.setQ0(RTA_neg.getLocation(RTA.getQ0().getName()));

        for(Map.Entry<Integer, Transition> entry : RTA.getDelta().entrySet()){
            Transition old_trans = entry.getValue();
            Transition new_trans = new Transition(
                    RTA_neg.getLocation(old_trans.getSource().getName()),
                    old_trans.getAction(),
                    old_trans.getGuard(),
                    old_trans.getReset(),
                    RTA_neg.getLocation(old_trans.getTarget().getName()));
            RTA_neg.addTransition(new_trans);
        }

        Set<Clock> reset = new HashSet<>(RTA_neg.getX().values());
        Clock single_clock = RTA_neg.getSingle_clock();
        Set<Action> sigma = new HashSet<>(RTA_neg.getSigma().values());
        // Add transitions to sink
        for(Map.Entry<String,Location> entry : RTA_neg.getQ().entrySet()){
            Location source = entry.getValue();
            List<Transition> add_trans = DOTAUtil.buildSinkTrans(source,sink_loc,single_clock,reset,sigma);
            for(Transition transition : add_trans)
                RTA_neg.addTransition(transition);
        }

        return RTA_neg;
    }

    // Compute additional edges to sink location after complement in single-clock case
    // These edges all reset clock by default
    private static List<Transition> buildSinkTrans(Location source, Location sink, Clock x, Set<Clock> reset, Set<Action> Sigma){
        List<Transition> outcome = new LinkedList<>();
        for(Action action : Sigma){
            List<Transition> temp = buildSinkTrans_action(source, sink, x, reset, action);
            outcome.addAll(temp);
        }
        return outcome;
    }

    public static List<Transition> buildSinkTrans_action(Location source, Location sink, Clock x, Set<Clock> reset,Action sigma){
        List<Transition> trans = source.getActionTransitions(sigma);
        List<Transition> outcome = new LinkedList<>();

        List<Interval> left_intervals = new LinkedList<>();
        left_intervals.add(Interval.NON_NEGATIVE_REAL);
        if(trans != null) {
            for (Transition transition : trans) {
                Constraint constraint = transition.getGuard();
                int num = 0;
                boolean left_closed = true;
                boolean right_closed = false;
                Value left = Value.ZERO;
                Value right = Value.INFINITE;
                for (AtomConstraint atom : constraint.getAtomConstraints()) {
                    if (num >= 2) throw new RuntimeException(OtherExceptions.WRONG_FORM_OF_SINGLE_CLOCK_CONSTRAINT);
                    if (atom.getClock1().isZeroClock()) {
                        left = atom.getUpperbound().negative();
                        left_closed = atom.isClosed();
                    } else if (atom.getClock2().isZeroClock()) {
                        right = atom.getUpperbound();
                        right_closed = atom.isClosed();
                    }
                    num++;
                }
                Interval cut = new Interval(left_closed, left, right, right_closed);
                boolean did = false;
                List<Interval> next_intervals = new LinkedList<>();
                for (int i = 0; i < left_intervals.size(); i++) {
                    Interval interval = left_intervals.get(i);
                    Pair<Boolean, List<Interval>> reply = interval.cut(cut);
                    if (did && !reply.p0()) {
                        next_intervals.addAll(left_intervals.subList(i, left_intervals.size()));
                        break;
                    }
                    did = reply.p0();
                    next_intervals.addAll(reply.p1());
                }
                left_intervals = next_intervals;
            }
        }
        for(Interval interval : left_intervals){
            Constraint guard = new Constraint();
            Value upperbound = interval.p1();
            if(!upperbound.isInfinite()) {
                AtomConstraint atom0 = new AtomConstraint(x, Clock.getZeroClock(), interval.isRight_closed(), upperbound);
                guard.addAtomConstraint(atom0);
            }
            upperbound = interval.p0();
            if(!Value.eq(upperbound, Value.ZERO) || !interval.isLeft_closed()) {
                AtomConstraint atom1 = new AtomConstraint(Clock.getZeroClock(), x, interval.isLeft_closed(), upperbound.negative());
                guard.addAtomConstraint(atom1);
            }
            Transition transition = new Transition(source,sigma,guard,reset,sink);
            outcome.add(transition);
        }
        return outcome;
    }
}
