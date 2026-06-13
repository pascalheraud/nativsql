package ovh.heraud.nativsql.util;

public class CustomCondition {
    private final String expression;
    private final String paramName;
    private final Object value;

    public CustomCondition(String expression, String paramName, Object value) {
        this.expression = expression;
        this.paramName = paramName;
        this.value = value;
    }

    public String getExpression() {
        return expression;
    }

    public String getParamName() {
        return paramName;
    }

    public Object getValue() {
        return value;
    }
}
