package tools.FormatOutput;

public class JsonFormat {
    private static boolean existLeft = false;

    // Format JSON
    public static String formatJson(String json){
        JsonFormat.existLeft = false;
        StringBuilder result = new StringBuilder();
        int length = json.length();
        int number = 0;
        char key = 0;
        for (int i = 0; i < length; i++){
            key = json.charAt(i);

            if (isEffectSpecChr(i,key,json)){
                if((key == '[') || (key == '{') ){
                    result.append(key);
                    result.append('\n');
                    number++;
                    result.append(indent(number));
                    continue;
                }

                if((key == ']') || (key == '}') ){
                    result.append('\n');
                    number--;
                    result.append(indent(number));
                    result.append(key);
                    continue;
                }

                if((key == ',')){
                    result.append(key);
                    result.append('\n');
                    result.append(indent(number));
                    continue;
                }
            }
            result.append(key);
        }
        return result.toString();
    }

    // Filter valid special characters
    private static boolean isEffectSpecChr(int index, char key, String json) {
        boolean flag = false;

        if (isDouMark(index,String.valueOf(key),json)){
            if (existLeft){
                existLeft = false;
            }else {
                existLeft = true;
            }
        }else {
            if ((key == '[')
                    || (key == '{')
                    || (key == ']')
                    || (key == '}')
                    || (key == ',')){
                if (!existLeft){
                    flag = true;
                }
            }
        }
        return flag;
    }

    // Check if current double quote is a special character
    private static boolean isDouMark(int index, String key, String json) {
        if (key.equals("\"") && index >= 0){
            if (index == 0){
                return true;
            }

            char c = json.charAt(index - 1);
            return c != '\\';
        }
        return false;
    }

    // Add tab spaces
    private static String indent(int number){
        return " ".repeat(Math.max(0, number*2));
    }
}
