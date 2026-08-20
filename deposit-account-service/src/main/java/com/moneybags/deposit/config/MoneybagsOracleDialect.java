package com.moneybags.deposit.config;

import org.hibernate.dialect.OracleDialect;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

/** Normalizes Oracle JDBC metadata types that predate their standard JDBC codes. */
public class MoneybagsOracleDialect extends OracleDialect {
    private static final int ORACLE_TIMESTAMP_WITH_TIME_ZONE = -101;

    @Override
    public JdbcType resolveSqlTypeDescriptor(String columnTypeName, int jdbcTypeCode, int precision, int scale,
                                             JdbcTypeRegistry jdbcTypeRegistry) {
        if (jdbcTypeCode == ORACLE_TIMESTAMP_WITH_TIME_ZONE) {
            return jdbcTypeRegistry.getDescriptor(SqlTypes.TIMESTAMP_WITH_TIMEZONE);
        }
        return super.resolveSqlTypeDescriptor(columnTypeName, jdbcTypeCode, precision, scale, jdbcTypeRegistry);
    }
}
