package tools.FormatOutput;

public class StringMaker {
    // Pad characters on the left
    public static String setWidthLeft(String s, int width, char character){
        int length = s.length();
        if(width <= length)
            return s;
        else{
            int num = width - length;
            StringBuilder sb = new StringBuilder();
            while(sb.length()<num) sb.append(character);
            sb.append(s);
            return sb.toString();
        }
    }

    public static StringBuilder setWidthLeft(StringBuilder sb, int width, char character){
        if(width <= sb.length())
            return new StringBuilder(sb);
        else {
            int num = width - sb.length();
            StringBuilder outcome = new StringBuilder();
            while(outcome.length()<num) outcome.append(character);
            outcome.append(sb);
            return outcome;
        }
    }

    public static String setWidthRight(String s, int width, char character){
        if(width <= s.length())
            return s;
        else{
            StringBuilder sb = new StringBuilder(s);
            while(sb.length()<width) sb.append(character);
            return sb.toString();
        }
    }

    public static StringBuilder setWidthRight(StringBuilder sb, int width, char character){
        if(width <= sb.length())
            return new StringBuilder(sb);
        else {
            StringBuilder outcome = new StringBuilder(sb);
            while(outcome.length()<width) outcome.append(character);
            return outcome;
        }
    }
}
