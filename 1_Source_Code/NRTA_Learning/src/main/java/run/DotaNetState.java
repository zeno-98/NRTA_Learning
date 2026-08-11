package run;

import ta.Clock;
import ta.Location;
import ta.TA;
import ta.Value;

import java.util.LinkedList;
import java.util.List;

public class DotaNetState {
    private final List<Location> locVec;
    private final ClockValuation v;

    public DotaNetState(List<TA> TAs){
        this.locVec = new LinkedList<>();
        this.v = new ClockValuation();
        for(TA ta : TAs){
            this.locVec.add(ta.getQ0());
            for(Clock clock : ta.getAllClocks()){
                this.v.put(clock, Value.ZERO);
            }
        }

    }
}
