package com.nexustrade.model.enums;

public enum InstrumentType {
    STOCK("STK", "Common Stock", 2),
    OPTION("OPT", "Option Contract", 4),
    FUTURE("FUT", "Futures Contract", 4),
    FOREX("FX", "Foreign Exchange", 5),
    BOND("BND", "Fixed Income Bond", 4),
    ETF("ETF", "Exchange Traded Fund", 2),
    INDEX("IDX", "Market Index", 2);

    private final String code;
    private final String description;
    private final int priceDecimals;

    InstrumentType(String code, String description, int priceDecimals) {
        this.code = code;
        this.description = description;
        this.priceDecimals = priceDecimals;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getPriceDecimals() {
        return priceDecimals;
    }

    public static InstrumentType fromCode(String code) {
        for (InstrumentType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown instrument type code: " + code);
    }
}
