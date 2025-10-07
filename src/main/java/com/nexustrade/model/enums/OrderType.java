package com.nexustrade.model.enums;

public enum OrderType {
    MARKET("MKT", "Market Order - Execute immediately at best available price"),
    LIMIT("LMT", "Limit Order - Execute at specified price or better"),
    STOP("STP", "Stop Order - Becomes market order when stop price is reached"),
    STOP_LIMIT("STPLMT", "Stop Limit Order - Becomes limit order when stop price is reached");

    private final String code;
    private final String description;

    OrderType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static OrderType fromCode(String code) {
        for (OrderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown order type code: " + code);
    }
}
