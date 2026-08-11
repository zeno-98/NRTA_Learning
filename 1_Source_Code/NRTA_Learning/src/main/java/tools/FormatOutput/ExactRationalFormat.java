package tools.FormatOutput;

import language.actions.DelayTimedAction;
import language.actions.RTARegionAction;
import language.words.DelayTimedWord;
import language.words.RTARegionWord;
import ta.Value;

/** Exact, non-rounding serialization for artifact and plotting data. */
public final class ExactRationalFormat {
    private ExactRationalFormat() {}

    public static String value(Value value) {
        if (value.isInfinite()) {
            return "+inf";
        }
        if (value.isMinus_infinite()) {
            return "-inf";
        }
        return value.getValue().toFractionString();
    }

    public static String delayWord(DelayTimedWord word) {
        if (word == null || word.isEmpty()) {
            return "epsilon";
        }
        StringBuilder output = new StringBuilder();
        for (DelayTimedAction action : word) {
            output.append('(')
                    .append(action.getAction().getName())
                    .append(',')
                    .append(value(action.getDelayTime()))
                    .append(')');
        }
        return output.toString();
    }

    public static String regionWord(RTARegionWord word) {
        if (word == null || word.isEmpty()) {
            return "epsilon";
        }
        StringBuilder output = new StringBuilder();
        for (RTARegionAction action : word) {
            output.append(regionAction(action));
        }
        return output.toString();
    }

    public static String regionAction(RTARegionAction action) {
        return "(" + action.getAction().getName() + ","
                + interval(action) + ")";
    }

    public static String interval(RTARegionAction action) {
        return (action.isLowerClosed() ? "[" : "(")
                + value(action.getLower()) + "," + value(action.getUpper())
                + (action.isUpperClosed() ? "]" : ")");
    }
}
