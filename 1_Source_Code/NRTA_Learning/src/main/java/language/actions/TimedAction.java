package language.actions;

import lombok.AllArgsConstructor;
import ta.Action;

@AllArgsConstructor
public class TimedAction {
    protected Action action;

    public Action getAction(){
        return this.action;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }
}
