package com.nexustrade.model.enums;

public enum OrderStatus {
    PENDING("PND", "Order submitted but not yet processed"),
    OPEN("OPN", "Order is active in the order book"),
    PARTIALLY_FILLED("PFL", "Order has been partially executed"),
    FILLED("FLD", "Order has been completely executed"),
    CANCELLED("CXL", "Order was cancelled by user or system"),
    REJECTED("REJ", "Order was rejected due to validation failure"),
    EXPIRED("EXP", "Order expired without execution");

    private final String code;
    private final String description;

    OrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == EXPIRED;
    }

    public boolean isActive() {
        return this == PENDING || this == OPEN || this == PARTIALLY_FILLED;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status code: " + code);
    }
}
