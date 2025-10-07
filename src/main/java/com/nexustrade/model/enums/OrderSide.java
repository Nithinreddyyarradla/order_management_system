package com.nexustrade.model.enums;

public enum OrderSide {
    BUY("B", "Buy order - Acquiring the instrument"),
    SELL("S", "Sell order - Disposing of the instrument");

    private final String code;
    private final String description;

    OrderSide(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }

    public static OrderSide fromCode(String code) {
        for (OrderSide side : values()) {
            if (side.code.equalsIgnoreCase(code)) {
                return side;
            }
        }
        throw new IllegalArgumentException("Unknown order side code: " + code);
    }
}
