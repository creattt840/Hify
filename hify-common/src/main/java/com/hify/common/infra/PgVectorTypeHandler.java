package com.hify.common.infra;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;

/**
 * pgvector vector 类型 ↔ Java float[] 转换器。
 * 用法：在 PO 的 embedding 字段上加 @TableField(typeHandler = PgVectorTypeHandler.class)
 */
@MappedTypes(float[].class)
public class PgVectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType)
            throws SQLException {
        // float[] → '[1.2, 3.4, 5.6]'::vector
        StringBuilder sb = new StringBuilder("[");
        for (int j = 0; j < parameter.length; j++) {
            if (j > 0) sb.append(",");
            sb.append(parameter[j]);
        }
        sb.append("]");
        ps.setObject(i, sb.toString(), Types.OTHER);
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseVector(rs.getString(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseVector(rs.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseVector(cs.getString(columnIndex));
    }

    private float[] parseVector(String value) {
        if (value == null || value.isEmpty()) return new float[0];
        // '[1.2,3.4,5.6]' → float[]{1.2, 3.4, 5.6}
        String[] parts = value.replace("[", "").replace("]", "").split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
