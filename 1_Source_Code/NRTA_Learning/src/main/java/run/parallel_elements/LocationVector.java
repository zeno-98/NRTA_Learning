package run.parallel_elements;

import exception.CheckingExceptions;
import language.actions.LogicalTimedAction;
import lombok.Getter;
import run.ClockValuation;
import ta.*;


import java.util.*;

public class LocationVector implements Comparable<LocationVector>{
    @Getter
    private boolean accepting = true;
    private Map<TA, Location> locationMap = null;

    private LocationVector(Map<TA,Location> locationMap){
        this.locationMap = locationMap;
        for(Map.Entry<TA,Location> entry : locationMap.entrySet()){
            this.accepting = this.accepting & entry.getValue().is_accepting();
        }
    }

    public LocationVector(){
        this.locationMap = new HashMap<>();
    }

    public void put(TA ta, Location location){
        this.locationMap.put(ta,location);
        this.accepting = this.accepting & location.is_accepting();
    }

    public Map<TA,Location> getLocationMap(){
        return this.locationMap;
    }
    public boolean arrive(LocationVector destination){
        for(Map.Entry<TA,Location> entry : destination.locationMap.entrySet()){
            TA ta = entry.getKey();
            Location location = entry.getValue();
            if(location!=locationMap.get(ta)) return false;
        }
        return true;
    }

    public int length(){
        return this.locationMap.size();
    }
    public Location getLocation(TA ta){
        return this.locationMap.get(ta);
    }

    public boolean equals(LocationVector o){
        if(this.locationMap.size()!=o.locationMap.size())
            return false;
        return arrive(o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocationVector other)) return false;
        if (this.locationMap.size() != other.locationMap.size()) return false;
        for (Map.Entry<TA, Location> entry : this.locationMap.entrySet()) {
            Location otherLocation = other.locationMap.get(entry.getKey());
            if (otherLocation == null || !entry.getValue().equals(otherLocation)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        List<Map.Entry<TA, Location>> ordered = this.getOrderedEntries();
        int hash = 1;
        for (Map.Entry<TA, Location> entry : ordered) {
            hash = 31 * hash + entry.getKey().getName().hashCode();
            hash = 31 * hash + entry.getValue().getName().hashCode();
        }
        return hash;
    }

    public boolean ifSatisfyInvariant(ClockValuation clockValuation){
        for(Location location : this.locationMap.values()){
            if(!location.getInvariant().truthValue(clockValuation))
                return false;
        }
        return true;
    }

    public List<SynchronizedTransition> outTransitions(Action action){
        List<List<Transition>> raw = new LinkedList<>();
        for (Map.Entry<TA, Location> entry : this.getOrderedEntries()) {
            raw.add(entry.getValue().getOutgoingTransitions(action));
        }
        return SynchronizedTransition.generate(raw, action);
    }

    public List<SynchronizedTransition> outTransitions(LogicalTimedAction la){
        List<List<Transition>> raw = new LinkedList<>();
        for (Map.Entry<TA, Location> entry : this.getOrderedEntries()) {
            raw.add(entry.getValue().getOutgoingTransitions(la));
        }
        return SynchronizedTransition.generate(raw, la.getAction());
    }

    @Override
    public int compareTo(LocationVector o) {
        if(this.locationMap.size()!=o.locationMap.size())
            throw new RuntimeException(CheckingExceptions.INCOMPARABLE_LOCATION_VECTOR);

        for (Map.Entry<TA, Location> entry : this.getOrderedEntries()) {
            TA ta = entry.getKey();
            Location thisLocation = entry.getValue();
            Location otherLocation = o.locationMap.get(ta);
            if (otherLocation == null) {
                throw new RuntimeException(CheckingExceptions.ILLEGAL_LOCATION_VECTOR);
            }
            int cmp = thisLocation.compareTo(otherLocation);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        int i = 0;
        for(Map.Entry<TA,Location> entry : this.getOrderedEntries()){
            if(i>0) sb.append(',');
            sb.append(entry.getValue().getName());
            i++;
        }
        sb.append('>');
        return sb.toString();
    }

    public void printDetail(){
        for(Map.Entry<TA,Location> entry : this.locationMap.entrySet()){
            TA ta = entry.getKey();
            Location location = entry.getValue();
            System.out.println(ta.getName() + ":" + location.getName() + ":" + location.getInvariant());
        }
    }

    public boolean projectionAccepting(Collection<TA> TAs){
        for(TA ta : TAs){
            if(this.locationMap.containsKey(ta)){
                if(!this.locationMap.get(ta).is_accepting())
                    return false;
            }
        }
        return true;
    }

    public boolean projectionAccepting(TA ta) {
        if (this.locationMap.containsKey(ta)) {
            return this.locationMap.get(ta).is_accepting();
        }
        return true;
    }

    public Constraint getInvariant(){
        Constraint outcome = new Constraint();
        for(Map.Entry<TA,Location> entry : this.getOrderedEntries()){
            outcome.addAll(entry.getValue().getInvariant());
        }
        return outcome;
    }

    private List<Map.Entry<TA, Location>> getOrderedEntries() {
        List<Map.Entry<TA, Location>> ordered = new ArrayList<>(this.locationMap.entrySet());
        ordered.sort(Comparator.comparing((Map.Entry<TA, Location> entry) -> entry.getKey().getName()));
        return ordered;
    }
}
